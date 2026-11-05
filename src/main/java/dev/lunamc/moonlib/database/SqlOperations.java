package dev.lunamc.moonlib.database;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * The statements you can run, on a {@link Database} (each call borrows its own connection) or
 * inside a {@link Transaction} (every call shares one connection and commits together).
 *
 * <p>Every method uses a prepared statement: put a {@code ?} where a value goes and pass the values
 * as {@code params}. Never build SQL by concatenating player input. Parameters may be any JDBC
 * type plus {@link java.util.UUID} (stored as text), enums (stored by name),
 * {@link java.time.Instant} and {@link java.util.Date} (stored as timestamps).</p>
 *
 * <p>These methods block. Call them from an async task, or use the {@code ...Async} methods on
 * {@link Database}.</p>
 *
 * @since 1.1.1-alpha
 */
public interface SqlOperations {

    /**
     * Runs an {@code INSERT}, {@code UPDATE}, {@code DELETE} or DDL statement.
     *
     * @param sql    the statement
     * @param params the values for its {@code ?}s
     * @return the number of rows changed
     * @throws DatabaseException if the statement fails
     */
    int update(String sql, Object... params);

    /**
     * Runs an {@code INSERT} and returns the generated key, for tables with an auto-increment id.
     *
     * @param sql    the insert
     * @param params the values for its {@code ?}s
     * @return the generated key, or empty if none was generated
     * @throws DatabaseException if the statement fails
     */
    Optional<Long> insert(String sql, Object... params);

    /**
     * Runs a query and maps every row.
     *
     * @param sql    the query
     * @param mapper turns a row into an object
     * @param params the values for its {@code ?}s
     * @param <T>    the result type
     * @return the mapped rows, in order (never {@code null})
     * @throws DatabaseException if the query or the mapper fails
     */
    <T> List<T> query(String sql, RowMapper<T> mapper, Object... params);

    /**
     * Runs a query and maps the first row.
     *
     * @param sql    the query
     * @param mapper turns a row into an object
     * @param params the values for its {@code ?}s
     * @param <T>    the result type
     * @return the first row, or empty if there were none (or the mapper returned {@code null})
     * @throws DatabaseException if the query or the mapper fails
     */
    <T> Optional<T> queryOne(String sql, RowMapper<T> mapper, Object... params);

    /**
     * Runs a query that returns a single value, such as {@code SELECT COUNT(*)} or
     * {@code SELECT tokens FROM ...}.
     *
     * @param sql    the query
     * @param type   the expected type ({@code Long}, {@code Integer}, {@code Double}, {@code String},
     *               {@code Boolean} or anything the driver returns)
     * @param params the values for its {@code ?}s
     * @param <T>    the value type
     * @return the first column of the first row, or empty
     * @throws DatabaseException if the query fails or the value cannot be converted
     */
    <T> Optional<T> value(String sql, Class<T> type, Object... params);

    /**
     * Returns whether a query returns at least one row.
     *
     * @param sql    the query
     * @param params the values for its {@code ?}s
     * @return {@code true} if there is a row
     * @throws DatabaseException if the query fails
     */
    boolean exists(String sql, Object... params);

    /**
     * Runs one statement many times in a single round trip, for example saving every online
     * player's balance.
     *
     * @param sql  the statement
     * @param rows one parameter array per execution
     * @return the update count of each execution
     * @throws DatabaseException if the batch fails
     */
    int[] batch(String sql, Collection<Object[]> rows);

    /**
     * Returns the SQL dialect, for {@link Dialect#upsert} and friends.
     *
     * @return the dialect
     */
    Dialect dialect();
}
