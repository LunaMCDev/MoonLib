package dev.lunamc.moonlib.database;

import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseTest {

    enum Rank { A, B }

    private static Database db(FakeJdbc fake, int pool) {
        return Database.builder(null).url("jdbc:mysql://fake/db").connectionFactory(fake::open)
                .poolSize(pool).acquireTimeout(150).slowQueryThreshold(0).warnOnMainThread(false).build();
    }

    private static Map<String, Object> row(Object... kv) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            map.put((String) kv[i], kv[i + 1]);
        }
        return map;
    }

    @Test
    void poolReusesConnectionsAndLimitsThem() throws Exception {
        FakeJdbc fake = new FakeJdbc();
        Database db = db(fake, 2);
        for (int i = 0; i < 5; i++) {
            db.update("UPDATE t SET x = 1");
        }
        assertEquals(1, fake.opened.get());

        Connection a = db.connection();
        Connection b = db.connection();
        assertEquals(2, fake.opened.get());
        assertThrows(DatabaseException.class, db::connection);   // pool exhausted -> timeout
        a.close();
        a.close();                                               // double close is harmless
        assertTrue(a.isClosed());
        assertThrows(java.sql.SQLException.class, () -> a.prepareStatement("SELECT 1"));
        Connection c = db.connection();                          // reuses a's physical connection
        assertEquals(2, fake.opened.get());
        c.close();
        b.close();
        db.close();
        assertEquals(2, fake.physicallyClosed.get());
        assertTrue(db.isClosed());
        assertThrows(DatabaseException.class, () -> db.update("UPDATE t SET x = 1"));
    }

    @Test
    void bindsPluginFriendlyTypes() {
        FakeJdbc fake = new FakeJdbc();
        Database db = db(fake, 1);
        UUID id = UUID.fromString("00000000-0000-0000-0000-000000000001");
        db.update("INSERT INTO p VALUES (?, ?, ?, ?)", id, Rank.B, Instant.ofEpochMilli(0), null);
        String line = fake.log.get(fake.log.size() - 1);
        assertTrue(line.contains("String:" + id), line);
        assertTrue(line.contains("String:B"), line);
        assertTrue(line.contains("Timestamp:"), line);
        assertTrue(line.endsWith("null]"), line);
        assertEquals(Optional.of(42L), db.insert("INSERT INTO gangs (name) VALUES (?)", "Moon"));
    }

    @Test
    void queriesMapRowsAndValues() {
        FakeJdbc fake = new FakeJdbc();
        fake.answers = sql -> sql.contains("COUNT")
                ? Collections.singletonList(row("c", 7L))
                : Arrays.asList(row("name", "a", "tokens", 5L, "uuid", "0000000000000000000000000000000a"),
                row("name", "b", "tokens", 9L, "uuid", null));
        Database db = db(fake, 1);
        List<String> names = db.query("SELECT * FROM p", r -> r.getString("name") + r.getLong("tokens"));
        assertEquals(Arrays.asList("a5", "b9"), names);
        assertEquals(Optional.of(new UUID(0, 10)), db.queryOne("SELECT * FROM p", r -> r.getUuid("uuid")));
        assertEquals(Optional.of(7), db.value("SELECT COUNT(*) FROM p", Integer.class));
        assertEquals(Optional.of("7"), db.value("SELECT COUNT(*) FROM p", String.class));
        assertTrue(db.exists("SELECT * FROM p"));
        fake.answers = sql -> Collections.emptyList();
        assertFalse(db.exists("SELECT * FROM p"));
        assertEquals(Optional.empty(), db.value("SELECT COUNT(*) FROM p", Long.class));
    }

    @Test
    void transactionsCommitOrRollBack() {
        FakeJdbc fake = new FakeJdbc();
        Database db = db(fake, 1);
        db.transaction(tx -> {
            tx.update("UPDATE a SET x = 1");
            tx.update("UPDATE b SET x = 2");
        });
        assertEquals(Arrays.asList("autoCommit=false", "UPDATE a SET x = 1", "UPDATE b SET x = 2", "commit", "autoCommit=true"),
                fake.log);

        fake.log.clear();
        DatabaseException e = assertThrows(DatabaseException.class, () -> db.transaction(tx -> {
            tx.update("UPDATE a SET x = 1");
            throw new IllegalStateException("not enough tokens");
        }));
        assertTrue(e.getMessage().contains("not enough tokens"), e.getMessage());
        assertTrue(fake.log.contains("rollback"), fake.log.toString());
        assertFalse(fake.log.contains("commit"));

        fake.log.clear();
        int result = db.transactionResult(tx -> {
            try {
                tx.nested(inner -> {
                    inner.update("UPDATE c SET x = 3");
                    throw new IllegalStateException("undo just this");
                });
            } catch (DatabaseException expected) {
                // the outer transaction continues
            }
            return 5;
        });
        assertEquals(5, result);
        assertTrue(fake.log.containsAll(Arrays.asList("savepoint", "rollback-savepoint", "commit")), fake.log.toString());
    }

    @Test
    void migrationsRunOnceInOrder() {
        FakeJdbc fake = new FakeJdbc();
        Database db = db(fake, 1);
        int applied = db.migrations()
                .add(2, "second", "UPDATE two SET x = 1; UPDATE two SET y = 'a;b'")
                .add(1, "first", "CREATE TABLE one (id INT)")
                .run();
        assertEquals(2, applied);
        int first = indexOf(fake.log, "CREATE TABLE one");
        int second = indexOf(fake.log, "UPDATE two SET x = 1");
        assertTrue(first >= 0 && first < second, fake.log.toString());
        assertTrue(indexOf(fake.log, "UPDATE two SET y = 'a;b'") > second, "quoted ; is not a separator");

        int again = db.migrations()
                .add(1, "first", "CREATE TABLE one (id INT)")
                .add(2, "second", "UPDATE two SET x = 1")
                .add(3, "third", tx -> tx.update("UPDATE three SET z = ?", 3))
                .run();
        assertEquals(1, again);

        fake.failOn = "broken";
        assertThrows(DatabaseException.class, () -> db.migrations().add(4, "bad", "UPDATE broken SET x = 1").run());
        assertEquals(3, fake.migrationRows.size(), "a failed migration is not recorded");
        assertThrows(IllegalArgumentException.class, () -> db.migrations().add(1, "a", "x").add(1, "b", "y"));
    }

    @Test
    void scriptSplittingHandlesCommentsAndQuotes() {
        List<String> parts = Migrations.splitStatements(
                "-- header; comment\nCREATE TABLE a (x TEXT DEFAULT 'it''s;fine'); /* block; */ INSERT INTO a VALUES (\"q;\");\n\n");
        assertEquals(Arrays.asList("CREATE TABLE a (x TEXT DEFAULT 'it''s;fine')", "INSERT INTO a VALUES (\"q;\")"), parts);
    }

    @Test
    void asyncWorkFinishesBeforeClose() throws Exception {
        FakeJdbc fake = new FakeJdbc();
        Database db = db(fake, 2);
        for (int i = 0; i < 20; i++) {
            db.updateAsync("UPDATE t SET n = ?", i);
        }
        assertEquals(Integer.valueOf(1), db.updateAsync("UPDATE t SET n = 99").get(5, TimeUnit.SECONDS));
        db.close();
        assertEquals(21, count(fake.log, "UPDATE t SET n"));
        assertTrue(db.updateAsync("UPDATE t SET n = 1").isCompletedExceptionally());
    }

    @Test
    void dialectHelpers() {
        assertEquals(Dialect.SQLITE, Dialect.fromUrl("jdbc:sqlite:/tmp/x.db"));
        assertEquals("INSERT INTO bal (uuid, tokens) VALUES (?, ?) ON DUPLICATE KEY UPDATE tokens = VALUES(tokens)",
                Dialect.MYSQL.upsert("bal", "uuid", "tokens"));
        assertEquals("INSERT INTO bal (uuid, tokens) VALUES (?, ?) ON CONFLICT (uuid) DO UPDATE SET tokens = excluded.tokens",
                Dialect.SQLITE.upsert("bal", "uuid", "tokens"));
        assertEquals("INSERT OR IGNORE INTO t (a) VALUES (?)", Dialect.SQLITE.insertIgnore("t", Collections.singletonList("a")));
        assertEquals("?, ?, ?", Dialect.placeholders(3));
    }

    private static int indexOf(List<String> log, String prefix) {
        for (int i = 0; i < log.size(); i++) {
            if (log.get(i).startsWith(prefix)) {
                return i;
            }
        }
        return -1;
    }

    private static int count(List<String> log, String prefix) {
        int n = 0;
        for (String line : log) {
            if (line.startsWith(prefix)) {
                n++;
            }
        }
        return n;
    }
}
