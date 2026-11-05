package com.lunamcdev.moonlib.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** The statement execution shared by {@link Database} and {@link Transaction}. */
final class Statements {

    /** Called with the statement and its duration in nanoseconds after each execution. */
    interface Timer {
        void record(String sql, long nanos);
    }

    private Statements() {
    }

    static void bind(PreparedStatement statement, Object[] params) throws SQLException {
        if (params == null) {
            return;
        }
        for (int i = 0; i < params.length; i++) {
            statement.setObject(i + 1, convert(params[i]));
        }
    }

    static Object convert(Object value) {
        if (value instanceof UUID) {
            return value.toString();
        }
        if (value instanceof Enum) {
            return ((Enum<?>) value).name();
        }
        if (value instanceof Instant) {
            return Timestamp.from((Instant) value);
        }
        if (value instanceof java.util.Date && !(value instanceof java.sql.Date)
                && !(value instanceof java.sql.Time) && !(value instanceof Timestamp)) {
            return new Timestamp(((java.util.Date) value).getTime());
        }
        if (value instanceof Character) {
            return value.toString();
        }
        return value;
    }

    static int update(Connection connection, Timer timer, String sql, Object... params) throws SQLException {
        long start = System.nanoTime();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, params);
            return statement.executeUpdate();
        } finally {
            timer.record(sql, System.nanoTime() - start);
        }
    }

    static Optional<Long> insert(Connection connection, Timer timer, String sql, Object... params) throws SQLException {
        long start = System.nanoTime();
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bind(statement, params);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys != null && keys.next()) {
                    long key = keys.getLong(1);
                    return keys.wasNull() ? Optional.<Long>empty() : Optional.of(key);
                }
                return Optional.empty();
            }
        } finally {
            timer.record(sql, System.nanoTime() - start);
        }
    }

    static <T> List<T> query(Connection connection, Timer timer, String sql, RowMapper<T> mapper, int limit,
                             Object... params) throws SQLException {
        long start = System.nanoTime();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, params);
            if (limit > 0) {
                statement.setMaxRows(limit);
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                List<T> out = new ArrayList<>();
                Row row = new Row(resultSet);
                while (resultSet.next()) {
                    T value = mapper.map(row);
                    if (value != null || limit != 1) {
                        out.add(value);
                    }
                    if (limit > 0 && out.size() >= limit) {
                        break;
                    }
                }
                return out;
            }
        } finally {
            timer.record(sql, System.nanoTime() - start);
        }
    }

    static <T> Optional<T> value(Connection connection, Timer timer, String sql, Class<T> type, Object... params)
            throws SQLException {
        List<Object> rows = query(connection, timer, sql, row -> row.resultSet().getObject(1), 1, params);
        if (rows.isEmpty() || rows.get(0) == null) {
            return Optional.empty();
        }
        return Optional.of(coerce(rows.get(0), type, sql));
    }

    @SuppressWarnings("unchecked")
    static <T> T coerce(Object value, Class<T> type, String sql) {
        if (type.isInstance(value)) {
            return (T) value;
        }
        if (value instanceof Number) {
            Number n = (Number) value;
            if (type == Long.class) {
                return (T) Long.valueOf(n.longValue());
            }
            if (type == Integer.class) {
                return (T) Integer.valueOf(n.intValue());
            }
            if (type == Double.class) {
                return (T) Double.valueOf(n.doubleValue());
            }
            if (type == Boolean.class) {
                return (T) Boolean.valueOf(n.intValue() != 0);
            }
        }
        if (type == String.class) {
            return (T) String.valueOf(value);
        }
        if (type == UUID.class) {
            return (T) UUID.fromString(String.valueOf(value));
        }
        if (value instanceof String) {
            String s = (String) value;
            try {
                if (type == Long.class) {
                    return (T) Long.valueOf(s.trim());
                }
                if (type == Integer.class) {
                    return (T) Integer.valueOf(s.trim());
                }
                if (type == Double.class) {
                    return (T) Double.valueOf(s.trim());
                }
            } catch (NumberFormatException e) {
                throw new DatabaseException("Expected a number but the query returned '" + s + "'", sql, e);
            }
            if (type == Boolean.class) {
                return (T) Boolean.valueOf(s.equals("1") || s.equalsIgnoreCase("true"));
            }
        }
        throw new DatabaseException("Cannot convert " + value.getClass().getSimpleName() + " to "
                + type.getSimpleName(), sql, null);
    }

    static int[] batch(Connection connection, Timer timer, String sql, Collection<Object[]> rows) throws SQLException {
        if (rows.isEmpty()) {
            return new int[0];
        }
        long start = System.nanoTime();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (Object[] row : rows) {
                bind(statement, row);
                statement.addBatch();
            }
            return statement.executeBatch();
        } finally {
            timer.record(sql, System.nanoTime() - start);
        }
    }
}
