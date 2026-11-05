package com.lunamcdev.moonlib.player;

import com.lunamcdev.moonlib.config.DataFile;
import com.lunamcdev.moonlib.config.DataStore;
import com.lunamcdev.moonlib.database.Database;
import com.lunamcdev.moonlib.database.Dialect;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.io.File;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerStorageTest {

    public static final class Stats {
        public int kills;
        public List<String> titles = new ArrayList<>();
    }

    // ------------------------------------------------------------------ files

    @Test
    void filesRoundTripAndSkipUnchangedSaves() throws Exception {
        // YAML goes through Bukkit's YAML engine (needs a server); it shares this code path with JSON.
        for (DataFile.Format format : new DataFile.Format[]{DataFile.Format.JSON}) {
            File folder = Files.createTempDirectory("storage-" + format).toFile();
            FileStorage<Stats> storage = new FileStorage<>(DataStore.of(folder, Stats.class, format), "test");
            UUID id = UUID.randomUUID();
            assertEquals(null, storage.load(id, "Luna"));

            Stats stats = new Stats();
            stats.kills = 3;
            stats.titles.add("Miner");
            storage.save(id, stats);
            File file = folder.listFiles()[0];
            long written = file.lastModified();

            Stats loaded = storage.load(id, "Luna");
            assertEquals(3, loaded.kills);
            assertEquals("Miner", loaded.titles.get(0));

            Thread.sleep(20);
            file.setLastModified(written - 10_000);
            storage.save(id, loaded);                         // identical: nothing written
            assertEquals(written - 10_000, file.lastModified(), format + " rewrote unchanged data");
            loaded.kills++;
            storage.save(id, loaded);                         // changed: written
            assertTrue(file.lastModified() > written - 10_000, format + " did not write changed data");

            storage.delete(id);
            assertEquals(null, storage.load(id, "Luna"));
        }
    }

    // ------------------------------------------------------------------ SQL

    /** A tiny in-memory "database" that understands the four statements SqlStorage uses. */
    static final class FakeTable {
        final Map<String, String> rows = new ConcurrentHashMap<>();
        final List<String> writes = new CopyOnWriteArrayList<>();

        Database database() {
            DataSource source = (DataSource) Proxy.newProxyInstance(getClass().getClassLoader(),
                    new Class<?>[]{DataSource.class}, (p, m, a) -> m.getName().equals("getConnection") ? connection() : null);
            return Database.fromDataSource(null, source, Dialect.SQLITE, 1);
        }

        Connection connection() {
            boolean[] auto = {true};
            return (Connection) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Connection.class},
                    (p, m, a) -> {
                        switch (m.getName()) {
                            case "prepareStatement":
                                return statement((String) a[0]);
                            case "getAutoCommit":
                                return auto[0];
                            case "setAutoCommit":
                                auto[0] = (Boolean) a[0];
                                return null;
                            default:
                                return null;
                        }
                    });
        }

        PreparedStatement statement(String sql) {
            Map<Integer, Object> params = new HashMap<>();
            List<Map<Integer, Object>> batch = new ArrayList<>();
            return (PreparedStatement) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{PreparedStatement.class},
                    (p, m, a) -> {
                        switch (m.getName()) {
                            case "setObject":
                                params.put((Integer) a[0], a[1]);
                                return null;
                            case "addBatch":
                                batch.add(new HashMap<>(params));
                                return null;
                            case "executeBatch":
                                for (Map<Integer, Object> row : batch) {
                                    run(sql, row);
                                }
                                return new int[batch.size()];
                            case "executeUpdate":
                                run(sql, params);
                                return 1;
                            case "executeQuery":
                                String value = rows.get(params.get(1) + "|" + params.get(2));
                                List<Object> result = new ArrayList<>();
                                if (value != null) {
                                    result.add(value);
                                }
                                return results(result);
                            default:
                                return null;
                        }
                    });
        }

        void run(String sql, Map<Integer, Object> params) {
            if (sql.startsWith("INSERT")) {
                rows.put(params.get(1) + "|" + params.get(2), (String) params.get(3));
                writes.add(params.get(1) + "|" + params.get(2));
            } else if (sql.startsWith("DELETE")) {
                rows.remove(params.get(1) + "|" + params.get(2));
            }
        }

        static ResultSet results(List<Object> values) {
            int[] i = {-1};
            return (ResultSet) Proxy.newProxyInstance(PlayerStorageTest.class.getClassLoader(), new Class<?>[]{ResultSet.class},
                    (p, m, a) -> m.getName().equals("next") ? ++i[0] < values.size()
                            : m.getName().equals("getObject") ? values.get(i[0]) : null);
        }
    }

    @Test
    void sqlRowsAreKeptApartPerPlugin() throws Exception {
        FakeTable table = new FakeTable();
        Database db = table.database();
        SqlStorage<Stats> prison = new SqlStorage<>("MoonPrison", db, Stats.class, false);
        SqlStorage<Stats> crates = new SqlStorage<>("MoonCrates", db, Stats.class, false);
        UUID id = UUID.randomUUID();

        Stats a = new Stats();
        a.kills = 10;
        Stats b = new Stats();
        b.kills = 99;
        prison.save(id, a);
        crates.save(id, b);

        assertEquals(10, prison.load(id, "Luna").kills);
        assertEquals(99, crates.load(id, "Luna").kills);
        assertEquals(2, table.rows.size(), "one row per plugin for the same player");

        crates.delete(id);
        assertEquals(null, crates.load(id, "Luna"));
        assertEquals(10, prison.load(id, "Luna").kills, "deleting in one plugin leaves the other alone");
        db.close();
    }

    @Test
    void sqlBatchWritesOnlyChangedPlayers() throws Exception {
        FakeTable table = new FakeTable();
        Database db = table.database();
        SqlStorage<Stats> storage = new SqlStorage<>("MoonPrison", db, Stats.class, false);
        Map<UUID, Stats> online = new LinkedHashMap<>();
        for (int i = 0; i < 5; i++) {
            online.put(UUID.randomUUID(), new Stats());
        }
        storage.saveAll(online);
        assertEquals(5, table.writes.size());

        storage.saveAll(online);                               // autosave with nobody changed
        assertEquals(5, table.writes.size(), "idle players are not rewritten");

        online.values().iterator().next().kills = 1;
        storage.saveAll(online);
        assertEquals(6, table.writes.size(), "only the changed player is written");
        db.close();
    }
}
