package dev.lunamc.moonlib.database;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * A group of statements that succeed or fail together. Get one from
 * {@link Database#transaction(SqlConsumer)}: it commits when your code returns and rolls back if
 * your code throws, so a half-finished trade or purchase is never saved.
 *
 * <pre>{@code
 * db.transaction(tx -> {
 *     long balance = tx.value("SELECT tokens FROM balances WHERE uuid = ?", Long.class, buyer).orElse(0L);
 *     if (balance < price) {
 *         throw new IllegalStateException("not enough tokens");   // rolls back
 *     }
 *     tx.update("UPDATE balances SET tokens = tokens - ? WHERE uuid = ?", price, buyer);
 *     tx.update("UPDATE balances SET tokens = tokens + ? WHERE uuid = ?", price, seller);
 * });
 * }</pre>
 *
 * <p>Note that MySQL commits immediately on DDL ({@code CREATE}, {@code ALTER}, {@code DROP}), so
 * those cannot be rolled back there.</p>
 *
 * @since 1.1.1-alpha
 */
public final class Transaction implements SqlOperations {

    private final Connection connection;
    private final Dialect dialect;
    private final Statements.Timer timer;

    Transaction(Connection connection, Dialect dialect, Statements.Timer timer) {
        this.connection = connection;
        this.dialect = dialect;
        this.timer = timer;
    }

    @Override
    public int update(String sql, Object... params) {
        try {
            return Statements.update(connection, timer, sql, params);
        } catch (SQLException e) {
            throw new DatabaseException("Update failed", sql, e);
        }
    }

    @Override
    public Optional<Long> insert(String sql, Object... params) {
        try {
            return Statements.insert(connection, timer, sql, params);
        } catch (SQLException e) {
            throw new DatabaseException("Insert failed", sql, e);
        }
    }

    @Override
    public <T> List<T> query(String sql, RowMapper<T> mapper, Object... params) {
        try {
            return Statements.query(connection, timer, sql, mapper, 0, params);
        } catch (SQLException e) {
            throw new DatabaseException("Query failed", sql, e);
        }
    }

    @Override
    public <T> Optional<T> queryOne(String sql, RowMapper<T> mapper, Object... params) {
        try {
            List<T> rows = Statements.query(connection, timer, sql, mapper, 1, params);
            return rows.isEmpty() ? Optional.<T>empty() : Optional.ofNullable(rows.get(0));
        } catch (SQLException e) {
            throw new DatabaseException("Query failed", sql, e);
        }
    }

    @Override
    public <T> Optional<T> value(String sql, Class<T> type, Object... params) {
        try {
            return Statements.value(connection, timer, sql, type, params);
        } catch (SQLException e) {
            throw new DatabaseException("Query failed", sql, e);
        }
    }

    @Override
    public boolean exists(String sql, Object... params) {
        return queryOne(sql, row -> Boolean.TRUE, params).isPresent();
    }

    @Override
    public int[] batch(String sql, Collection<Object[]> rows) {
        try {
            return Statements.batch(connection, timer, sql, rows);
        } catch (SQLException e) {
            throw new DatabaseException("Batch failed", sql, e);
        }
    }

    @Override
    public Dialect dialect() {
        return dialect;
    }

    /**
     * Runs part of the transaction that can be undone on its own: if {@code action} throws, only
     * its changes are rolled back (to a savepoint) and the exception is rethrown; the rest of the
     * transaction can still commit if you catch it.
     *
     * @param action the nested work
     * @throws DatabaseException if the action fails
     */
    public void nested(SqlConsumer<Transaction> action) {
        Savepoint savepoint;
        try {
            savepoint = connection.setSavepoint();
        } catch (SQLException e) {
            throw new DatabaseException("Could not create a savepoint", null, e);
        }
        try {
            action.accept(this);
        } catch (Exception e) {
            try {
                connection.rollback(savepoint);
            } catch (SQLException rollback) {
                e.addSuppressed(rollback);
            }
            throw e instanceof DatabaseException ? (DatabaseException) e
                    : new DatabaseException("Nested transaction failed and was rolled back", null, e);
        }
    }

    /**
     * Returns the transaction's connection, for JDBC features not covered here. Do not close it or
     * change its auto-commit mode.
     *
     * @return the connection
     */
    public Connection connection() {
        return connection;
    }
}
