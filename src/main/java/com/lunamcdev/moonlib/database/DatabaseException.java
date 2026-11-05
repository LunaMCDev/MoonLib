package com.lunamcdev.moonlib.database;

/**
 * An unchecked wrapper for database errors. The message names the statement that failed (never its
 * parameters, which may hold player data), and the cause is the original
 * {@link java.sql.SQLException}.
 *
 * @since 1.2.0
 */
public class DatabaseException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** The statement that failed, or {@code null}. */
    private final String sql;

    /**
     * Creates an exception.
     *
     * @param message what went wrong
     * @param sql     the statement, or {@code null}
     * @param cause   the cause
     */
    public DatabaseException(String message, String sql, Throwable cause) {
        super(sql == null ? message : message + " [" + abbreviate(sql) + "]", cause);
        this.sql = sql;
    }

    /**
     * Returns the statement that failed, if any.
     *
     * @return the SQL, or {@code null}
     */
    public String sql() {
        return sql;
    }

    static String abbreviate(String sql) {
        String flat = sql.replaceAll("\\s+", " ").trim();
        return flat.length() <= 200 ? flat : flat.substring(0, 197) + "...";
    }
}
