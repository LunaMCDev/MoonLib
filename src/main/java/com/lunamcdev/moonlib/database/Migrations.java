package com.lunamcdev.moonlib.database;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.logging.Level;

/**
 * Versioned schema changes, applied once each and in order, so a plugin update can add tables and
 * columns without asking server owners to run SQL by hand.
 *
 * <pre>{@code
 * db.migrations()
 *   .add(1, "create balances",
 *        "CREATE TABLE balances (uuid CHAR(36) PRIMARY KEY, tokens BIGINT NOT NULL DEFAULT 0)")
 *   .add(2, "add multiplier", "ALTER TABLE balances ADD COLUMN multiplier DOUBLE NOT NULL DEFAULT 1")
 *   .addResource(3, "gangs tables", "migrations/003_gangs.sql")     // a .sql file inside your jar
 *   .add(4, "backfill names", tx -> tx.update("UPDATE balances SET multiplier = 2 WHERE tokens > ?", 1_000_000))
 *   .run();
 * }</pre>
 *
 * <p>Applied versions are recorded per plugin in a {@code moonlib_migrations} table, so several
 * plugins can share one database. Each migration runs in its own transaction where the database
 * supports transactional DDL (SQLite, PostgreSQL). Never change a migration that has shipped: add
 * a new one instead.</p>
 *
 * @since 1.2.0
 */
public final class Migrations {

    /** The table recording which migrations have run. */
    public static final String TABLE = "moonlib_migrations";

    private final Database database;
    private final String owner;
    private final Map<Integer, Step> steps = new TreeMap<>();

    Migrations(Database database, String owner) {
        this.database = database;
        this.owner = owner;
    }

    private static final class Step {
        final int version;
        final String description;
        final SqlConsumer<Transaction> action;

        Step(int version, String description, SqlConsumer<Transaction> action) {
            this.version = version;
            this.description = description;
            this.action = action;
        }
    }

    /**
     * Adds a migration made of one or more SQL statements. A single string may hold several
     * statements separated by {@code ;}.
     *
     * @param version     the version, starting at 1; must be unique
     * @param description a short description, shown in the log
     * @param sql         the statements
     * @return this builder
     */
    public Migrations add(int version, String description, String... sql) {
        List<String> statements = new ArrayList<>();
        for (String chunk : sql) {
            statements.addAll(splitStatements(chunk));
        }
        return add(version, description, tx -> {
            for (String statement : statements) {
                tx.update(statement);
            }
        });
    }

    /**
     * Adds a migration written in Java, for data changes SQL alone cannot express.
     *
     * @param version     the version; must be unique
     * @param description a short description
     * @param action      the migration
     * @return this builder
     */
    public Migrations add(int version, String description, SqlConsumer<Transaction> action) {
        if (version < 1) {
            throw new IllegalArgumentException("Migration versions start at 1, got " + version);
        }
        if (steps.containsKey(version)) {
            throw new IllegalArgumentException("Migration " + version + " is declared twice ('"
                    + steps.get(version).description + "' and '" + description + "')");
        }
        steps.put(version, new Step(version, description, action));
        return this;
    }

    /**
     * Adds a migration from a {@code .sql} file inside the plugin jar. {@code --} and
     * {@code /* *}{@code /} comments are allowed.
     *
     * @param version      the version; must be unique
     * @param description  a short description
     * @param resourcePath the path inside the jar, e.g. {@code "migrations/003_gangs.sql"}
     * @return this builder
     * @throws IllegalArgumentException if the resource does not exist
     */
    public Migrations addResource(int version, String description, String resourcePath) {
        String sql = readResource(resourcePath);
        return add(version, description, sql);
    }

    /**
     * Applies every migration that has not run yet, in version order. Stops at the first failure,
     * leaving later migrations for the next start.
     *
     * @return the number of migrations applied
     * @throws DatabaseException if a migration fails
     */
    public int run() {
        database.update("CREATE TABLE IF NOT EXISTS " + TABLE + " (owner VARCHAR(64) NOT NULL, version INTEGER NOT NULL, "
                + "description VARCHAR(255) NOT NULL, applied_at BIGINT NOT NULL, PRIMARY KEY (owner, version))");
        Set<Integer> applied = new HashSet<>(database.query("SELECT version FROM " + TABLE + " WHERE owner = ?",
                row -> row.getInt("version"), owner));
        int highestKnown = steps.isEmpty() ? 0 : ((TreeMap<Integer, Step>) steps).lastKey();
        for (int version : applied) {
            if (version > highestKnown) {
                database.logger().warning("[" + database.name() + "] The database has migration " + version + " for "
                        + owner + ", newer than this plugin knows (" + highestKnown + "). Was the plugin downgraded?");
                break;
            }
        }
        int count = 0;
        for (Step step : steps.values()) {
            if (applied.contains(step.version)) {
                continue;
            }
            try {
                database.transaction(tx -> {
                    step.action.accept(tx);
                    tx.update("INSERT INTO " + TABLE + " (owner, version, description, applied_at) VALUES (?, ?, ?, ?)",
                            owner, step.version, step.description, System.currentTimeMillis());
                });
            } catch (DatabaseException e) {
                database.logger().log(Level.SEVERE, "[" + database.name() + "] Migration " + step.version + " ("
                        + step.description + ") failed; later migrations were not applied", e);
                throw e;
            }
            database.logger().info("[" + database.name() + "] Applied migration " + step.version + ": " + step.description);
            count++;
        }
        return count;
    }

    /**
     * Returns the highest migration version applied for this plugin.
     *
     * @return the version, or {@code 0} if none
     */
    public int currentVersion() {
        return database.value("SELECT MAX(version) FROM " + TABLE + " WHERE owner = ?", Integer.class, owner).orElse(0);
    }

    private String readResource(String path) {
        ClassLoader loader = database.resourceLoader();
        try (InputStream in = loader.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalArgumentException("Migration file '" + path + "' is not in the plugin jar");
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not read migration file '" + path + "'", e);
        }
    }

    /**
     * Splits a SQL script into statements on {@code ;}, ignoring semicolons inside quotes and
     * comments, and dropping the comments.
     *
     * @param script the script
     * @return the non-empty statements
     */
    static List<String> splitStatements(String script) {
        List<String> out = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int n = script.length();
        char quote = 0;
        for (int i = 0; i < n; i++) {
            char c = script.charAt(i);
            char next = i + 1 < n ? script.charAt(i + 1) : 0;
            if (quote != 0) {
                current.append(c);
                if (c == quote) {
                    if (next == quote) {
                        current.append(next);
                        i++;
                    } else {
                        quote = 0;
                    }
                }
                continue;
            }
            if (c == '-' && next == '-') {
                while (i < n && script.charAt(i) != '\n') {
                    i++;
                }
                current.append('\n');
                continue;
            }
            if (c == '/' && next == '*') {
                int end = script.indexOf("*/", i + 2);
                i = end < 0 ? n : end + 1;
                current.append(' ');
                continue;
            }
            if (c == '\'' || c == '"' || c == '`') {
                quote = c;
                current.append(c);
                continue;
            }
            if (c == ';') {
                addStatement(out, current);
                continue;
            }
            current.append(c);
        }
        addStatement(out, current);
        return out;
    }

    private static void addStatement(List<String> out, StringBuilder current) {
        String statement = current.toString().trim();
        if (!statement.isEmpty()) {
            out.add(statement);
        }
        current.setLength(0);
    }
}
