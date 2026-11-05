package com.lunamcdev.moonlib.database;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A small, dependency-free JDBC connection pool.
 *
 * <p>Connections handed out are proxies: calling {@link Connection#close()} returns the real
 * connection to the pool (after rolling back an unfinished transaction and restoring auto-commit)
 * instead of closing it. Idle connections are validated before reuse if they have been idle for a
 * while, and replaced once they reach their maximum lifetime, which avoids the classic MySQL
 * "connection closed after 8 hours" error.</p>
 */
final class ConnectionPool implements AutoCloseable {

    /** Opens a new physical connection. */
    @FunctionalInterface
    interface Factory {
        Connection open() throws SQLException;
    }

    private static final long VALIDATE_AFTER_IDLE_MS = TimeUnit.SECONDS.toMillis(30);

    private final Factory factory;
    private final int maxSize;
    private final long acquireTimeoutMs;
    private final long maxLifetimeMs;
    private final Deque<Pooled> idle = new ArrayDeque<>();
    private final AtomicBoolean closed = new AtomicBoolean();
    private int total;

    ConnectionPool(Factory factory, int maxSize, long acquireTimeoutMs, long maxLifetimeMs) {
        if (maxSize < 1) {
            throw new IllegalArgumentException("Pool size must be at least 1, got " + maxSize);
        }
        this.factory = factory;
        this.maxSize = maxSize;
        this.acquireTimeoutMs = acquireTimeoutMs;
        this.maxLifetimeMs = maxLifetimeMs;
    }

    /**
     * Borrows a connection, opening one if the pool is below its size, or waiting for one to be
     * returned otherwise.
     */
    Connection acquire() throws SQLException {
        long deadline = System.currentTimeMillis() + acquireTimeoutMs;
        while (true) {
            Pooled candidate = null;
            boolean mayOpen = false;
            synchronized (this) {
                if (closed.get()) {
                    throw new SQLException("The database has been closed");
                }
                if (!idle.isEmpty()) {
                    candidate = idle.pollLast();
                } else if (total < maxSize) {
                    total++;
                    mayOpen = true;
                } else {
                    long wait = deadline - System.currentTimeMillis();
                    if (wait <= 0) {
                        throw new SQLException("Timed out after " + acquireTimeoutMs + " ms waiting for a database "
                                + "connection (all " + maxSize + " in use). A query may be leaking connections, or "
                                + "the pool is too small for the load.");
                    }
                    try {
                        wait(wait);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new SQLException("Interrupted while waiting for a database connection", e);
                    }
                    continue;
                }
            }
            if (mayOpen) {
                try {
                    return new Pooled(factory.open()).proxy();
                } catch (SQLException | RuntimeException e) {
                    synchronized (this) {
                        total--;
                        notifyAll();
                    }
                    throw e;
                }
            }
            if (isUsable(candidate)) {
                candidate.borrowed = true;
                return candidate.proxy();
            }
            discard(candidate);
        }
    }

    private boolean isUsable(Pooled pooled) {
        long now = System.currentTimeMillis();
        if (maxLifetimeMs > 0 && now - pooled.createdAt > maxLifetimeMs) {
            return false;
        }
        try {
            if (pooled.real.isClosed()) {
                return false;
            }
            return now - pooled.returnedAt < VALIDATE_AFTER_IDLE_MS || pooled.real.isValid(2);
        } catch (SQLException e) {
            return false;
        }
    }

    private void discard(Pooled pooled) {
        try {
            pooled.real.close();
        } catch (SQLException ignored) {
            // already broken
        }
        synchronized (this) {
            total--;
            notifyAll();
        }
    }

    private void release(Pooled pooled) {
        boolean healthy = true;
        try {
            if (!pooled.real.getAutoCommit()) {
                pooled.real.rollback();
                pooled.real.setAutoCommit(true);
            }
        } catch (SQLException e) {
            healthy = false;
        }
        pooled.borrowed = false;
        pooled.returnedAt = System.currentTimeMillis();
        if (!healthy || closed.get()) {
            discard(pooled);
            return;
        }
        synchronized (this) {
            idle.addLast(pooled);
            notifyAll();
        }
    }

    /** Returns the number of open physical connections, borrowed or idle. */
    synchronized int size() {
        return total;
    }

    /** Returns the number of idle connections. */
    synchronized int idleCount() {
        return idle.size();
    }

    int maxSize() {
        return maxSize;
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        Deque<Pooled> toClose;
        synchronized (this) {
            toClose = new ArrayDeque<>(idle);
            idle.clear();
            notifyAll();
        }
        for (Pooled pooled : toClose) {
            discard(pooled);
        }
    }

    /** One physical connection and its bookkeeping. */
    private final class Pooled {
        final Connection real;
        final long createdAt = System.currentTimeMillis();
        long returnedAt = createdAt;
        volatile boolean borrowed = true;

        Pooled(Connection real) {
            this.real = real;
        }

        Connection proxy() {
            AtomicBoolean handedBack = new AtomicBoolean();
            return (Connection) Proxy.newProxyInstance(ConnectionPool.class.getClassLoader(),
                    new Class<?>[]{Connection.class}, (p, method, args) -> invoke(handedBack, p, method, args));
        }

        private Object invoke(AtomicBoolean handedBack, Object proxy, Method method, Object[] args) throws Throwable {
            switch (method.getName()) {
                case "close":
                    if (handedBack.compareAndSet(false, true)) {
                        release(this);
                    }
                    return null;
                case "isClosed":
                    return handedBack.get() || real.isClosed();
                case "equals":
                    return proxy == args[0];
                case "hashCode":
                    return System.identityHashCode(proxy);
                case "toString":
                    return "PooledConnection[" + real + "]";
                case "unwrap":
                    if (((Class<?>) args[0]).isInstance(real)) {
                        return real;
                    }
                    break;
                default:
                    break;
            }
            if (handedBack.get()) {
                throw new SQLException("This connection was already closed (returned to the pool)");
            }
            try {
                return method.invoke(real, args);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        }
    }
}
