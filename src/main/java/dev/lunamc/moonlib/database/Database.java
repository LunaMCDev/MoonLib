package dev.lunamc.moonlib.database;

import dev.lunamc.moonlib.MoonContext;
import dev.lunamc.moonlib.MoonLib;
import dev.lunamc.moonlib.config.DataFile;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import javax.sql.DataSource;
import java.io.File;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A SQL database with a built-in connection pool, prepared statements, transactions, migrations
 * and asynchronous queries. Works with the SQLite and MySQL drivers every Spigot-based server
 * already ships, so no extra dependency is needed; MariaDB, PostgreSQL and H2 work too if their
 * driver is available.
 *
 * <pre>{@code
 * // SQLite file in the plugin folder, or MySQL from config.yml:
 * Database db = Database.sqlite(this, "data.db");
 * Database db = Database.fromConfig(this, loadConfig("config.yml"), "database");
 *
 * db.migrations()
 *   .add(1, "create balances", "CREATE TABLE balances (uuid CHAR(36) PRIMARY KEY, tokens BIGINT NOT NULL)")
 *   .add(2, "add multiplier",  "ALTER TABLE balances ADD COLUMN multiplier DOUBLE NOT NULL DEFAULT 1")
 *   .run();
 *
 * // never block the main thread: query asynchronously, then hop back
 * db.queryOneAsync("SELECT tokens FROM balances WHERE uuid = ?", row -> row.getLong("tokens"), player.getUniqueId())
 *   .thenAccept(tokens -> Tasks.entity(player, () -> board.setTokens(player, tokens.orElse(0L))));
 *
 * String upsert = db.dialect().upsert("balances", "uuid", "tokens");
 * db.updateAsync(upsert, player.getUniqueId(), 500L);
 * }</pre>
 *
 * <h2>Threads</h2>
 * The plain methods ({@link #update}, {@link #query}, ...) block the calling thread. Run them from an
 * async task, or use the {@code ...Async} methods, which run on the database's own worker threads
 * (one per pooled connection) and return a {@link CompletableFuture}. Futures complete on a worker
 * thread, so use {@code Tasks.sync}/{@code Tasks.entity} before touching the world. Failures of
 * the write methods ({@code updateAsync}, {@code insertAsync}, {@code batchAsync},
 * {@code transactionAsync}) are always logged, so a lost save is never silent. Calling a
 * blocking method on the server thread logs a one-time warning that names the statement.
 *
 * <h2>Lifecycle</h2>
 * A database created with a plugin closes itself when that plugin shuts down MoonLib, after
 * finishing any queued asynchronous work (up to 10 seconds), so final saves in {@code onDisable}
 * are not lost. Call {@link #close()} to close it earlier.
 *
 * @since 1.1.1-alpha
 */
public final class Database implements SqlOperations, AutoCloseable {

    private final Plugin plugin;
    private final String name;
    private final Dialect dialect;
    private final ConnectionSource source;
    private final ExecutorService executor;
    private final long slowQueryNanos;
    private final boolean warnOnMainThread;
    private final AtomicBoolean warnedMainThread = new AtomicBoolean();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicLong statementCount = new AtomicLong();
    private final Statements.Timer timer;

    private Database(Builder builder, ConnectionSource source) {
        this.plugin = builder.plugin;
        this.name = builder.name;
        this.dialect = builder.dialect;
        this.source = source;
        this.slowQueryNanos = TimeUnit.MILLISECONDS.toNanos(builder.slowQueryMillis);
        this.warnOnMainThread = builder.warnOnMainThread;
        int threads = Math.max(1, builder.poolSize);
        AtomicInteger threadIds = new AtomicInteger();
        String prefix = (plugin == null ? "MoonLib" : plugin.getName()) + "-" + name + "-";
        ThreadPoolExecutor pool = new ThreadPoolExecutor(threads, threads, 60, TimeUnit.SECONDS,
                new LinkedBlockingQueue<Runnable>(), runnable -> {
                    Thread thread = new Thread(runnable, prefix + threadIds.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                });
        pool.allowCoreThreadTimeOut(true);
        this.executor = pool;
        this.timer = (sql, nanos) -> {
            statementCount.incrementAndGet();
            if (slowQueryNanos > 0 && nanos >= slowQueryNanos) {
                logger().warning("[" + name + "] Slow statement took " + TimeUnit.NANOSECONDS.toMillis(nanos)
                        + " ms: " + DatabaseException.abbreviate(sql));
            }
        };
        if (plugin != null && MoonLib.isInitialised(plugin)) {
            MoonContext context = MoonLib.context(plugin);
            context.onShutdown(this);
        }
    }

    // ------------------------------------------------------------------ factories

    /**
     * Opens (creating if needed) an SQLite database file in the plugin's data folder.
     *
     * @param plugin   the owning plugin
     * @param fileName the file name, e.g. {@code "data.db"} or {@code "storage/stats.db"}
     * @return the database
     */
    public static Database sqlite(Plugin plugin, String fileName) {
        return sqlite(plugin, new File(plugin.getDataFolder(), fileName));
    }

    /**
     * Opens (creating if needed) an SQLite database file.
     *
     * @param plugin the owning plugin
     * @param file   the database file
     * @return the database
     */
    public static Database sqlite(Plugin plugin, File file) {
        File parent = file.getAbsoluteFile().getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            throw new DatabaseException("Could not create folder " + parent, null, null);
        }
        return builder(plugin).url("jdbc:sqlite:" + file.getAbsolutePath()).build();
    }

    /**
     * Connects to a MySQL server with sensible defaults (UTF-8, no SSL requirement, public key
     * retrieval allowed so MySQL 8 password auth works).
     *
     * @param plugin   the owning plugin
     * @param host     the host name
     * @param port     the port, usually 3306
     * @param database the database (schema) name
     * @param username the user
     * @param password the password
     * @return the database
     */
    public static Database mysql(Plugin plugin, String host, int port, String database, String username,
                                 String password) {
        return builder(plugin)
                .url("jdbc:mysql://" + host + ":" + port + "/" + database)
                .credentials(username, password)
                .build();
    }

    /**
     * Connects to a MariaDB server. Uses the MariaDB driver if it is installed, and the MySQL
     * driver (which speaks the same protocol) otherwise.
     *
     * @param plugin   the owning plugin
     * @param host     the host name
     * @param port     the port, usually 3306
     * @param database the database (schema) name
     * @param username the user
     * @param password the password
     * @return the database
     */
    public static Database mariadb(Plugin plugin, String host, int port, String database, String username,
                                   String password) {
        boolean hasDriver = classExists("org.mariadb.jdbc.Driver");
        return builder(plugin)
                .url((hasDriver ? "jdbc:mariadb://" : "jdbc:mysql://") + host + ":" + port + "/" + database)
                .dialect(Dialect.MARIADB)
                .credentials(username, password)
                .build();
    }

    /**
     * Creates a database from a config section, so server owners can switch between SQLite and
     * MySQL without a code change:
     *
     * <pre>{@code
     * database:
     *   type: sqlite          # sqlite, mysql or mariadb
     *   file: data.db         # sqlite only
     *   host: localhost
     *   port: 3306
     *   name: prison
     *   username: root
     *   password: ''
     *   pool-size: 8
     *   properties:           # optional extra driver properties
     *     useSSL: false
     * }</pre>
     *
     * @param plugin the owning plugin
     * @param config the file holding the section
     * @param path   the section path, e.g. {@code "database"}
     * @return the database
     * @throws IllegalArgumentException if {@code type} is unknown
     */
    public static Database fromConfig(Plugin plugin, DataFile config, String path) {
        String p = path == null || path.isEmpty() ? "" : path + ".";
        String type = config.getString(p + "type", "sqlite").trim().toLowerCase(Locale.ROOT);
        Builder builder = builder(plugin).poolSize(config.getInt(p + "pool-size", 0));
        if (type.equals("sqlite")) {
            File file = new File(plugin.getDataFolder(), config.getString(p + "file", "data.db"));
            File parent = file.getAbsoluteFile().getParentFile();
            if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
                throw new DatabaseException("Could not create folder " + parent, null, null);
            }
            builder.url("jdbc:sqlite:" + file.getAbsolutePath());
        } else if (type.equals("mysql") || type.equals("mariadb")) {
            boolean maria = type.equals("mariadb") && classExists("org.mariadb.jdbc.Driver");
            builder.url((maria ? "jdbc:mariadb://" : "jdbc:mysql://") + config.getString(p + "host", "localhost")
                            + ":" + config.getInt(p + "port", 3306) + "/" + config.getString(p + "name", plugin.getName().toLowerCase(Locale.ROOT)))
                    .credentials(config.getString(p + "username", "root"), config.getString(p + "password", ""));
            if (type.equals("mariadb")) {
                builder.dialect(Dialect.MARIADB);
            }
        } else {
            throw new IllegalArgumentException("Unknown database type '" + type + "' at " + p + "type"
                    + " (use sqlite, mysql or mariadb)");
        }
        for (String key : config.keys(p + "properties")) {
            builder.property(key, config.getString(p + "properties." + key, ""));
        }
        return builder.build();
    }

    /**
     * Wraps an existing {@link DataSource}, for example a HikariCP pool you configured yourself.
     * MoonLib then does not pool connections itself.
     *
     * @param plugin     the owning plugin
     * @param dataSource the data source
     * @param dialect    its dialect
     * @param threads    worker threads for asynchronous queries
     * @return the database
     */
    public static Database fromDataSource(Plugin plugin, DataSource dataSource, Dialect dialect, int threads) {
        Builder builder = builder(plugin).dialect(dialect).poolSize(threads);
        builder.url = "datasource";
        return new Database(builder, new ConnectionSource() {
            @Override
            public Connection get() throws SQLException {
                return dataSource.getConnection();
            }

            @Override
            public void close() {
                if (dataSource instanceof AutoCloseable) {
                    try {
                        ((AutoCloseable) dataSource).close();
                    } catch (Exception ignored) {
                        // closing anyway
                    }
                }
            }

            @Override
            public int open() {
                return -1;
            }
        });
    }

    /**
     * Starts a builder for full control over the connection.
     *
     * @param plugin the owning plugin (its logger and data folder are used, and the database closes
     *               when it shuts down MoonLib); may be {@code null} for tools and tests
     * @return a builder
     */
    public static Builder builder(Plugin plugin) {
        return new Builder(plugin);
    }

    // ------------------------------------------------------------------ blocking API

    @Override
    public int update(String sql, Object... params) {
        return withConnection(sql, "Update failed", c -> Statements.update(c, timer, sql, params));
    }

    @Override
    public Optional<Long> insert(String sql, Object... params) {
        return withConnection(sql, "Insert failed", c -> Statements.insert(c, timer, sql, params));
    }

    @Override
    public <T> List<T> query(String sql, RowMapper<T> mapper, Object... params) {
        return withConnection(sql, "Query failed", c -> Statements.query(c, timer, sql, mapper, 0, params));
    }

    @Override
    public <T> Optional<T> queryOne(String sql, RowMapper<T> mapper, Object... params) {
        return withConnection(sql, "Query failed", c -> {
            List<T> rows = Statements.query(c, timer, sql, mapper, 1, params);
            return rows.isEmpty() ? Optional.<T>empty() : Optional.ofNullable(rows.get(0));
        });
    }

    @Override
    public <T> Optional<T> value(String sql, Class<T> type, Object... params) {
        return withConnection(sql, "Query failed", c -> Statements.value(c, timer, sql, type, params));
    }

    @Override
    public boolean exists(String sql, Object... params) {
        return queryOne(sql, row -> Boolean.TRUE, params).isPresent();
    }

    @Override
    public int[] batch(String sql, Collection<Object[]> rows) {
        return withConnection(sql, "Batch failed", c -> {
            boolean auto = c.getAutoCommit();
            c.setAutoCommit(false);
            try {
                int[] counts = Statements.batch(c, timer, sql, rows);
                c.commit();
                return counts;
            } catch (SQLException | RuntimeException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(auto);
            }
        });
    }

    /**
     * Runs statements in a transaction: committed if {@code work} returns normally, rolled back if
     * it throws.
     *
     * @param work the statements
     * @throws DatabaseException if the work or the commit fails (after rolling back)
     */
    public void transaction(SqlConsumer<Transaction> work) {
        transactionResult(tx -> {
            work.accept(tx);
            return null;
        });
    }

    /**
     * Runs statements in a transaction and returns a result.
     *
     * @param work the statements
     * @param <T>  the result type
     * @return what {@code work} returned
     * @throws DatabaseException if the work or the commit fails (after rolling back)
     */
    public <T> T transactionResult(SqlFunction<Transaction, T> work) {
        checkThread("transaction");
        try (Connection connection = source.get()) {
            connection.setAutoCommit(false);
            try {
                T result = work.apply(new Transaction(connection, dialect, timer));
                connection.commit();
                return result;
            } catch (Exception e) {
                try {
                    connection.rollback();
                } catch (SQLException rollback) {
                    e.addSuppressed(rollback);
                }
                if (e instanceof DatabaseException) {
                    throw (DatabaseException) e;
                }
                throw new DatabaseException("Transaction failed and was rolled back: " + e.getMessage(), null, e);
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new DatabaseException("Could not run transaction: " + e.getMessage(), null, e);
        }
    }

    // ------------------------------------------------------------------ async API

    /**
     * Runs any work on a database worker thread.
     *
     * @param work the work, given this database
     * @param <T>  the result type
     * @return a future completed with the result, or exceptionally with a {@link DatabaseException}
     */
    public <T> CompletableFuture<T> async(SqlFunction<Database, T> work) {
        CompletableFuture<T> future = new CompletableFuture<>();
        if (closed.get()) {
            future.completeExceptionally(new DatabaseException("The database '" + name + "' is closed", null, null));
            return future;
        }
        try {
            executor.execute(() -> {
                try {
                    future.complete(work.apply(this));
                } catch (Throwable t) {
                    future.completeExceptionally(t instanceof DatabaseException ? t
                            : new DatabaseException(String.valueOf(t.getMessage()), null, t));
                }
            });
        } catch (java.util.concurrent.RejectedExecutionException e) {
            future.completeExceptionally(new DatabaseException("The database '" + name + "' is closed", null, e));
        }
        return future;
    }

    /**
     * Asynchronous {@link #update(String, Object...)}.
     *
     * @param sql    the statement
     * @param params its parameters
     * @return a future of the changed row count
     */
    public CompletableFuture<Integer> updateAsync(String sql, Object... params) {
        return logged(async(db -> db.update(sql, params)));
    }

    /**
     * Asynchronous {@link #insert(String, Object...)}.
     *
     * @param sql    the insert
     * @param params its parameters
     * @return a future of the generated key
     */
    public CompletableFuture<Optional<Long>> insertAsync(String sql, Object... params) {
        return logged(async(db -> db.insert(sql, params)));
    }

    /**
     * Asynchronous {@link #query(String, RowMapper, Object...)}.
     *
     * @param sql    the query
     * @param mapper the row mapper
     * @param params its parameters
     * @param <T>    the row type
     * @return a future of the rows
     */
    public <T> CompletableFuture<List<T>> queryAsync(String sql, RowMapper<T> mapper, Object... params) {
        return async(db -> db.query(sql, mapper, params));
    }

    /**
     * Asynchronous {@link #queryOne(String, RowMapper, Object...)}.
     *
     * @param sql    the query
     * @param mapper the row mapper
     * @param params its parameters
     * @param <T>    the row type
     * @return a future of the first row
     */
    public <T> CompletableFuture<Optional<T>> queryOneAsync(String sql, RowMapper<T> mapper, Object... params) {
        return async(db -> db.queryOne(sql, mapper, params));
    }

    /**
     * Asynchronous {@link #value(String, Class, Object...)}.
     *
     * @param sql    the query
     * @param type   the value type
     * @param params its parameters
     * @param <T>    the value type
     * @return a future of the value
     */
    public <T> CompletableFuture<Optional<T>> valueAsync(String sql, Class<T> type, Object... params) {
        return async(db -> db.value(sql, type, params));
    }

    /**
     * Asynchronous {@link #batch(String, Collection)}.
     *
     * @param sql  the statement
     * @param rows the parameter rows (copied, so the caller may reuse the collection)
     * @return a future of the update counts
     */
    public CompletableFuture<int[]> batchAsync(String sql, Collection<Object[]> rows) {
        List<Object[]> copy = new ArrayList<>(rows);
        return logged(async(db -> db.batch(sql, copy)));
    }

    /**
     * Asynchronous {@link #transaction(SqlConsumer)}.
     *
     * @param work the statements
     * @return a future completed when the transaction commits
     */
    public CompletableFuture<Void> transactionAsync(SqlConsumer<Transaction> work) {
        return logged(async(db -> {
            db.transaction(work);
            return null;
        }));
    }

    /**
     * Asynchronous {@link #transactionResult(SqlFunction)}.
     *
     * @param work the statements
     * @param <T>  the result type
     * @return a future of the result
     */
    public <T> CompletableFuture<T> transactionResultAsync(SqlFunction<Transaction, T> work) {
        return async(db -> db.transactionResult(work));
    }

    /**
     * Logs failures of write futures, which are usually fire-and-forget, so a failed save is
     * never silent. The future still completes exceptionally for code that handles it.
     */
    private <T> CompletableFuture<T> logged(CompletableFuture<T> future) {
        future.whenComplete((value, error) -> {
            if (error != null) {
                logger().log(Level.SEVERE, "[" + name + "] Asynchronous database work failed", error);
            }
        });
        return future;
    }

    // ------------------------------------------------------------------ misc

    /**
     * Starts declaring this plugin's schema migrations.
     *
     * @return a migration builder
     */
    public Migrations migrations() {
        return new Migrations(this, plugin == null ? "default" : plugin.getName());
    }

    /**
     * Borrows a raw connection for JDBC work not covered here. Always close it (use
     * try-with-resources); closing returns it to the pool.
     *
     * @return a connection
     * @throws DatabaseException if no connection could be obtained
     */
    public Connection connection() {
        checkThread("connection()");
        try {
            return source.get();
        } catch (SQLException e) {
            throw new DatabaseException("Could not get a connection: " + e.getMessage(), null, e);
        }
    }

    @Override
    public Dialect dialect() {
        return dialect;
    }

    /**
     * Returns the database's display name, used in logs and thread names.
     *
     * @return the name
     */
    public String name() {
        return name;
    }

    /**
     * Returns how many statements have run, for metrics and debugging.
     *
     * @return the statement count
     */
    public long statementCount() {
        return statementCount.get();
    }

    /**
     * Returns the number of open pooled connections, or {@code -1} when an external data source
     * manages them.
     *
     * @return the open connection count
     */
    public int openConnections() {
        return source.open();
    }

    /**
     * Returns whether {@link #close()} has been called.
     *
     * @return {@code true} once closed
     */
    public boolean isClosed() {
        return closed.get();
    }

    /**
     * Finishes queued asynchronous work (waiting up to 10 seconds), then closes every connection.
     * Safe to call more than once.
     */
    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        if (plugin != null && MoonLib.isInitialised(plugin)) {
            MoonLib.context(plugin).cancelShutdown(this);
        }
        executor.shutdown();
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                List<Runnable> dropped = executor.shutdownNow();
                logger().warning("[" + name + "] Closed with " + dropped.size()
                        + " database task(s) still queued after waiting 10 seconds");
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        source.close();
    }

    @Override
    public String toString() {
        return "Database{" + name + ", " + dialect + (closed.get() ? ", closed" : "") + "}";
    }

    // ------------------------------------------------------------------ internals

    @FunctionalInterface
    private interface ConnectionWork<T> {
        T run(Connection connection) throws SQLException;
    }

    private <T> T withConnection(String sql, String failure, ConnectionWork<T> work) {
        checkThread(sql);
        try (Connection connection = source.get()) {
            return work.run(connection);
        } catch (SQLException e) {
            throw new DatabaseException(failure + ": " + e.getMessage(), sql, e);
        }
    }

    private void checkThread(String what) {
        if (!warnOnMainThread || warnedMainThread.get()) {
            return;
        }
        boolean primary;
        try {
            primary = Bukkit.getServer() != null && Bukkit.isPrimaryThread();
        } catch (Throwable t) {
            primary = false;
        }
        if (primary && warnedMainThread.compareAndSet(false, true)) {
            logger().warning("[" + name + "] A blocking database call ran on the server thread, which freezes "
                    + "the server until it finishes. Use the ...Async methods or an async task instead. Statement: "
                    + DatabaseException.abbreviate(what) + " (this warning is shown once)");
        }
    }

    ClassLoader resourceLoader() {
        return plugin != null ? plugin.getClass().getClassLoader() : Database.class.getClassLoader();
    }

    Logger logger() {
        return plugin != null ? plugin.getLogger() : Logger.getLogger("MoonLib");
    }

    private static boolean classExists(String name) {
        try {
            Class.forName(name);
            return true;
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }

    /** Where connections come from: the built-in pool, or an external data source. */
    interface ConnectionSource {
        Connection get() throws SQLException;

        void close();

        int open();
    }

    /**
     * Configures a {@link Database}.
     */
    public static final class Builder {
        private final Plugin plugin;
        private String url;
        private String username;
        private String password;
        private String driverClass;
        private Dialect dialect;
        private String name = "database";
        private int poolSize;
        private long acquireTimeoutMillis = 10_000;
        private long maxLifetimeMillis = TimeUnit.MINUTES.toMillis(30);
        private long slowQueryMillis = 1_000;
        private boolean warnOnMainThread = true;
        private final Map<String, String> properties = new LinkedHashMap<>();
        private final List<String> initStatements = new ArrayList<>();
        private ConnectionPool.Factory factory;

        private Builder(Plugin plugin) {
            this.plugin = plugin;
        }

        /**
         * Sets the JDBC URL. The dialect and driver are worked out from it.
         *
         * @param jdbcUrl the URL, e.g. {@code jdbc:mysql://localhost:3306/prison}
         * @return this builder
         */
        public Builder url(String jdbcUrl) {
            this.url = jdbcUrl;
            return this;
        }

        /**
         * Sets the user and password.
         *
         * @param user     the user
         * @param pass     the password
         * @return this builder
         */
        public Builder credentials(String user, String pass) {
            this.username = user;
            this.password = pass;
            return this;
        }

        /**
         * Sets the driver class explicitly, for drivers MoonLib does not know.
         *
         * @param className the {@link Driver} class name
         * @return this builder
         */
        public Builder driver(String className) {
            this.driverClass = className;
            return this;
        }

        /**
         * Overrides the dialect worked out from the URL.
         *
         * @param value the dialect
         * @return this builder
         */
        public Builder dialect(Dialect value) {
            this.dialect = value;
            return this;
        }

        /**
         * Names the database in logs and thread names, for plugins with more than one.
         *
         * @param value the name
         * @return this builder
         */
        public Builder name(String value) {
            this.name = value;
            return this;
        }

        /**
         * Sets the number of pooled connections (and async worker threads). Defaults to 1 for SQLite,
         * which allows only one writer at a time, and 8 otherwise. {@code 0} keeps the default.
         *
         * @param size the pool size
         * @return this builder
         */
        public Builder poolSize(int size) {
            this.poolSize = size;
            return this;
        }

        /**
         * Sets how long a call waits for a free connection before failing. Default 10 seconds.
         *
         * @param millis the timeout in milliseconds
         * @return this builder
         */
        public Builder acquireTimeout(long millis) {
            this.acquireTimeoutMillis = millis;
            return this;
        }

        /**
         * Sets how long a connection is reused before being replaced. Default 30 minutes; keep it
         * below MySQL's {@code wait_timeout}. {@code 0} for no limit.
         *
         * @param millis the lifetime in milliseconds
         * @return this builder
         */
        public Builder maxLifetime(long millis) {
            this.maxLifetimeMillis = millis;
            return this;
        }

        /**
         * Logs any statement slower than this. Default 1 second; {@code 0} disables it.
         *
         * @param millis the threshold in milliseconds
         * @return this builder
         */
        public Builder slowQueryThreshold(long millis) {
            this.slowQueryMillis = millis;
            return this;
        }

        /**
         * Turns the one-time warning about blocking calls on the server thread on or off. On by
         * default; turn it off only for small SQLite reads you have measured.
         *
         * @param enabled {@code true} to warn
         * @return this builder
         */
        public Builder warnOnMainThread(boolean enabled) {
            this.warnOnMainThread = enabled;
            return this;
        }

        /**
         * Adds a driver property, such as {@code useSSL} or {@code serverTimezone}.
         *
         * @param key   the property
         * @param value its value
         * @return this builder
         */
        public Builder property(String key, String value) {
            this.properties.put(key, value);
            return this;
        }

        /**
         * Adds a statement run on every new connection, such as {@code SET time_zone = '+00:00'}.
         * SQLite connections already enable foreign keys, WAL journaling and a busy timeout.
         *
         * @param sql the statement
         * @return this builder
         */
        public Builder onConnect(String sql) {
            this.initStatements.add(sql);
            return this;
        }

        /**
         * Supplies connections from custom code instead of a JDBC URL (they are still pooled).
         * Mainly for tests.
         *
         * @param opener opens a new physical connection
         * @return this builder
         */
        Builder connectionFactory(ConnectionPool.Factory opener) {
            this.factory = opener;
            return this;
        }

        /**
         * Creates the database. Connections are opened lazily, on first use.
         *
         * @return the database
         * @throws DatabaseException if the driver cannot be found
         */
        public Database build() {
            if (url == null && factory == null) {
                throw new IllegalStateException("Set a JDBC url before building the database");
            }
            if (dialect == null) {
                dialect = Dialect.fromUrl(url);
            }
            if (poolSize <= 0) {
                poolSize = dialect == Dialect.SQLITE ? 1 : 8;
            }
            List<String> init = new ArrayList<>();
            if (dialect == Dialect.SQLITE) {
                init.add("PRAGMA foreign_keys = ON");
                init.add("PRAGMA journal_mode = WAL");
                init.add("PRAGMA busy_timeout = 5000");
            }
            init.addAll(initStatements);
            ConnectionPool.Factory opener = factory != null ? factory : jdbcFactory();
            ConnectionPool.Factory initialising = () -> {
                Connection connection = opener.open();
                if (!init.isEmpty()) {
                    try (Statement statement = connection.createStatement()) {
                        for (String sql : init) {
                            statement.execute(sql);
                        }
                    } catch (SQLException e) {
                        connection.close();
                        throw e;
                    }
                }
                return connection;
            };
            ConnectionPool pool = new ConnectionPool(initialising, poolSize, acquireTimeoutMillis, maxLifetimeMillis);
            return new Database(this, new ConnectionSource() {
                @Override
                public Connection get() throws SQLException {
                    return pool.acquire();
                }

                @Override
                public void close() {
                    pool.close();
                }

                @Override
                public int open() {
                    return pool.size();
                }
            });
        }

        private ConnectionPool.Factory jdbcFactory() {
            Properties props = new Properties();
            if (dialect.isMySqlFamily()) {
                props.setProperty("useUnicode", "true");
                props.setProperty("characterEncoding", "utf8");
                props.setProperty("useSSL", "false");
                props.setProperty("allowPublicKeyRetrieval", "true");
                props.setProperty("rewriteBatchedStatements", "true");
                props.setProperty("cachePrepStmts", "true");
            }
            props.putAll(properties);
            if (username != null) {
                props.setProperty("user", username);
            }
            if (password != null) {
                props.setProperty("password", password);
            }
            Driver driver = loadDriver();
            String jdbcUrl = url;
            return () -> {
                Connection connection = driver != null ? driver.connect(jdbcUrl, props)
                        : DriverManager.getConnection(jdbcUrl, props);
                if (connection == null) {
                    throw new SQLException("The driver does not accept the URL " + jdbcUrl);
                }
                return connection;
            };
        }

        private Driver loadDriver() {
            List<String> candidates = new ArrayList<>();
            if (driverClass != null) {
                candidates.add(driverClass);
            } else if (url.startsWith("jdbc:sqlite:")) {
                candidates.add("org.sqlite.JDBC");
            } else if (url.startsWith("jdbc:mysql:")) {
                candidates.add("com.mysql.cj.jdbc.Driver");
                candidates.add("com.mysql.jdbc.Driver");
            } else if (url.startsWith("jdbc:mariadb:")) {
                candidates.add("org.mariadb.jdbc.Driver");
            } else if (url.startsWith("jdbc:postgresql:")) {
                candidates.add("org.postgresql.Driver");
            } else if (url.startsWith("jdbc:h2:")) {
                candidates.add("org.h2.Driver");
            }
            for (String candidate : candidates) {
                try {
                    return (Driver) Class.forName(candidate).getDeclaredConstructor().newInstance();
                } catch (ReflectiveOperationException | LinkageError | ClassCastException ignored) {
                    // try the next one
                }
            }
            if (driverClass != null) {
                throw new DatabaseException("JDBC driver " + driverClass + " is not installed", null, null);
            }
            if (!candidates.isEmpty()) {
                // Fall back to DriverManager, which may still find a driver registered another way.
                Logger.getLogger("MoonLib").fine("No direct driver for " + url + "; using DriverManager");
            }
            return null;
        }
    }
}
