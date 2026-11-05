package com.lunamcdev.moonlib.database;

import java.sql.SQLException;

/**
 * Turns the current row of a result into an object.
 *
 * <pre>{@code
 * RowMapper<PlayerStats> STATS = row -> new PlayerStats(
 *         row.getUuid("uuid"), row.getInt("kills"), row.getLong("blocks_mined"));
 * }</pre>
 *
 * @param <T> the result type
 * @since 1.2.0
 */
@FunctionalInterface
public interface RowMapper<T> {

    /**
     * Maps one row. Do not advance the result yourself.
     *
     * @param row the current row
     * @return the mapped value
     * @throws SQLException if a column cannot be read
     */
    T map(Row row) throws SQLException;
}
