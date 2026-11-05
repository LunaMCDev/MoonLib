package dev.lunamc.moonlib.database;

import java.nio.ByteBuffer;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

/**
 * The current row of a query result, with typed getters for the values plugins store most.
 * {@code null} column values come back as {@code null} from the object getters and as {@code 0} or
 * {@code false} from the primitive ones (check {@link #isNull(String)} when that matters).
 *
 * @since 1.4.0
 */
public final class Row {

    private final ResultSet resultSet;

    Row(ResultSet resultSet) {
        this.resultSet = resultSet;
    }

    /**
     * Returns the underlying result set, for anything the getters do not cover.
     *
     * @return the result set, positioned on this row
     */
    public ResultSet resultSet() {
        return resultSet;
    }

    /**
     * Reads a text column.
     *
     * @param column the column label
     * @return the value, or {@code null}
     * @throws SQLException if the column does not exist
     */
    public String getString(String column) throws SQLException {
        return resultSet.getString(column);
    }

    /**
     * Reads an integer column.
     *
     * @param column the column label
     * @return the value, or {@code 0} for {@code NULL}
     * @throws SQLException if the column does not exist
     */
    public int getInt(String column) throws SQLException {
        return resultSet.getInt(column);
    }

    /**
     * Reads a long column.
     *
     * @param column the column label
     * @return the value, or {@code 0} for {@code NULL}
     * @throws SQLException if the column does not exist
     */
    public long getLong(String column) throws SQLException {
        return resultSet.getLong(column);
    }

    /**
     * Reads a decimal column.
     *
     * @param column the column label
     * @return the value, or {@code 0} for {@code NULL}
     * @throws SQLException if the column does not exist
     */
    public double getDouble(String column) throws SQLException {
        return resultSet.getDouble(column);
    }

    /**
     * Reads a boolean column (stored as {@code BOOLEAN}, {@code TINYINT} or {@code INTEGER}).
     *
     * @param column the column label
     * @return the value, or {@code false} for {@code NULL}
     * @throws SQLException if the column does not exist
     */
    public boolean getBoolean(String column) throws SQLException {
        return resultSet.getBoolean(column);
    }

    /**
     * Reads a UUID stored as text ({@code CHAR(36)}, with or without dashes) or as 16 bytes
     * ({@code BINARY(16)}).
     *
     * @param column the column label
     * @return the UUID, or {@code null}
     * @throws SQLException if the column does not exist or does not hold a UUID
     */
    public UUID getUuid(String column) throws SQLException {
        Object value = resultSet.getObject(column);
        if (value == null) {
            return null;
        }
        if (value instanceof UUID) {
            return (UUID) value;
        }
        if (value instanceof byte[] && ((byte[]) value).length == 16) {
            ByteBuffer buffer = ByteBuffer.wrap((byte[]) value);
            return new UUID(buffer.getLong(), buffer.getLong());
        }
        String text = value.toString().trim();
        if (text.length() == 32) {
            text = text.substring(0, 8) + "-" + text.substring(8, 12) + "-" + text.substring(12, 16) + "-"
                    + text.substring(16, 20) + "-" + text.substring(20);
        }
        try {
            return UUID.fromString(text);
        } catch (IllegalArgumentException e) {
            throw new SQLException("Column '" + column + "' does not hold a UUID: '" + text + "'", e);
        }
    }

    /**
     * Reads a timestamp column, or a number of epoch milliseconds.
     *
     * @param column the column label
     * @return the instant, or {@code null}
     * @throws SQLException if the column does not exist
     */
    public Instant getInstant(String column) throws SQLException {
        Object value = resultSet.getObject(column);
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return Instant.ofEpochMilli(((Number) value).longValue());
        }
        Timestamp timestamp = resultSet.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }

    /**
     * Reads an enum stored by name (case-insensitive).
     *
     * @param column the column label
     * @param type   the enum class
     * @param <E>    the enum type
     * @return the constant, or {@code null}
     * @throws SQLException if the column does not exist or names no constant
     */
    public <E extends Enum<E>> E getEnum(String column, Class<E> type) throws SQLException {
        String value = resultSet.getString(column);
        if (value == null) {
            return null;
        }
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new SQLException("Column '" + column + "' holds '" + value + "', which is not a "
                    + type.getSimpleName(), e);
        }
    }

    /**
     * Reads raw bytes.
     *
     * @param column the column label
     * @return the bytes, or {@code null}
     * @throws SQLException if the column does not exist
     */
    public byte[] getBytes(String column) throws SQLException {
        return resultSet.getBytes(column);
    }

    /**
     * Reads any column as the driver's default Java type.
     *
     * @param column the column label
     * @return the value, or {@code null}
     * @throws SQLException if the column does not exist
     */
    public Object getObject(String column) throws SQLException {
        return resultSet.getObject(column);
    }

    /**
     * Returns whether a column is {@code NULL}.
     *
     * @param column the column label
     * @return {@code true} for {@code NULL}
     * @throws SQLException if the column does not exist
     */
    public boolean isNull(String column) throws SQLException {
        return resultSet.getObject(column) == null;
    }
}
