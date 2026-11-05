package com.lunamcdev.moonlib.database;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * The SQL flavour of a database, with helpers for the few statements that differ between
 * engines. Use them to keep one code path for SQLite and MySQL.
 *
 * <pre>{@code
 * db.update("CREATE TABLE IF NOT EXISTS gangs (id " + db.dialect().autoIncrementKey() + ", name VARCHAR(32))");
 *
 * String save = db.dialect().upsert("balances", Arrays.asList("uuid"), Arrays.asList("uuid", "tokens"));
 * db.update(save, player.getUniqueId(), tokens);
 * }</pre>
 *
 * @since 1.2.0
 */
public enum Dialect {
    /** SQLite. */
    SQLITE,
    /** MySQL. */
    MYSQL,
    /** MariaDB. */
    MARIADB,
    /** PostgreSQL. */
    POSTGRESQL,
    /** H2. */
    H2,
    /** Anything else; the helpers use standard SQL where possible. */
    OTHER;

    /**
     * Works out the dialect from a JDBC URL.
     *
     * @param jdbcUrl the URL, e.g. {@code jdbc:mysql://localhost/db}
     * @return the dialect, or {@link #OTHER}
     */
    public static Dialect fromUrl(String jdbcUrl) {
        String url = jdbcUrl == null ? "" : jdbcUrl.toLowerCase(Locale.ROOT);
        if (url.startsWith("jdbc:sqlite:")) {
            return SQLITE;
        }
        if (url.startsWith("jdbc:mysql:")) {
            return MYSQL;
        }
        if (url.startsWith("jdbc:mariadb:")) {
            return MARIADB;
        }
        if (url.startsWith("jdbc:postgresql:")) {
            return POSTGRESQL;
        }
        if (url.startsWith("jdbc:h2:")) {
            return H2;
        }
        return OTHER;
    }

    /**
     * Returns whether this is MySQL or MariaDB.
     *
     * @return {@code true} for the MySQL family
     */
    public boolean isMySqlFamily() {
        return this == MYSQL || this == MARIADB;
    }

    /**
     * Returns a column definition for an auto-incrementing 64-bit primary key.
     *
     * @return for example {@code INTEGER PRIMARY KEY AUTOINCREMENT} on SQLite
     */
    public String autoIncrementKey() {
        switch (this) {
            case SQLITE:
                return "INTEGER PRIMARY KEY AUTOINCREMENT";
            case MYSQL:
            case MARIADB:
                return "BIGINT PRIMARY KEY AUTO_INCREMENT";
            case POSTGRESQL:
                return "BIGSERIAL PRIMARY KEY";
            default:
                return "BIGINT AUTO_INCREMENT PRIMARY KEY";
        }
    }

    /**
     * Builds an "insert, or update if the key exists" statement with one {@code ?} per column, in
     * the order given.
     *
     * @param table      the table
     * @param keyColumns the primary or unique key columns (must also appear in {@code columns})
     * @param columns    every column to write
     * @return the statement
     */
    public String upsert(String table, List<String> keyColumns, List<String> columns) {
        if (!columns.containsAll(keyColumns)) {
            throw new IllegalArgumentException("Every key column must also be in columns: " + keyColumns);
        }
        StringBuilder sql = new StringBuilder(insertPrefix(table, columns));
        List<String> updated = new ArrayList<>(columns);
        updated.removeAll(keyColumns);
        if (isMySqlFamily()) {
            sql.append(" ON DUPLICATE KEY UPDATE ");
            if (updated.isEmpty()) {
                sql.append(keyColumns.get(0)).append(" = ").append(keyColumns.get(0));
            }
            for (int i = 0; i < updated.size(); i++) {
                sql.append(i == 0 ? "" : ", ").append(updated.get(i)).append(" = VALUES(").append(updated.get(i)).append(')');
            }
            return sql.toString();
        }
        if (this == H2) {
            return "MERGE INTO " + table + " (" + String.join(", ", columns) + ") KEY ("
                    + String.join(", ", keyColumns) + ") VALUES (" + placeholders(columns.size()) + ")";
        }
        sql.append(" ON CONFLICT (").append(String.join(", ", keyColumns)).append(") DO ");
        if (updated.isEmpty()) {
            return sql.append("NOTHING").toString();
        }
        sql.append("UPDATE SET ");
        for (int i = 0; i < updated.size(); i++) {
            sql.append(i == 0 ? "" : ", ").append(updated.get(i)).append(" = excluded.").append(updated.get(i));
        }
        return sql.toString();
    }

    /**
     * Varargs form of {@link #upsert(String, List, List)} with a single key column.
     *
     * @param table     the table
     * @param keyColumn the key column (also the first column)
     * @param others    the remaining columns
     * @return the statement
     */
    public String upsert(String table, String keyColumn, String... others) {
        List<String> columns = new ArrayList<>();
        columns.add(keyColumn);
        columns.addAll(Arrays.asList(others));
        return upsert(table, Collections.singletonList(keyColumn), columns);
    }

    /**
     * Builds an insert that silently skips rows whose key already exists.
     *
     * @param table   the table
     * @param columns the columns, one {@code ?} each
     * @return the statement
     */
    public String insertIgnore(String table, List<String> columns) {
        switch (this) {
            case SQLITE:
                return "INSERT OR IGNORE" + insertPrefix(table, columns).substring("INSERT".length());
            case MYSQL:
            case MARIADB:
                return "INSERT IGNORE" + insertPrefix(table, columns).substring("INSERT".length());
            default:
                return insertPrefix(table, columns) + " ON CONFLICT DO NOTHING";
        }
    }

    private static String insertPrefix(String table, List<String> columns) {
        return "INSERT INTO " + table + " (" + String.join(", ", columns) + ") VALUES (" + placeholders(columns.size()) + ")";
    }

    /**
     * Returns {@code n} comma-separated question marks, for {@code IN (...)} lists.
     *
     * @param n how many
     * @return for example {@code ?, ?, ?}
     */
    public static String placeholders(int n) {
        StringBuilder sb = new StringBuilder(Math.max(0, n * 3));
        for (int i = 0; i < n; i++) {
            sb.append(i == 0 ? "?" : ", ?");
        }
        return sb.toString();
    }
}
