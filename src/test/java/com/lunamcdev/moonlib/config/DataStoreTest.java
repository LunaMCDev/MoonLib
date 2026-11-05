package com.lunamcdev.moonlib.config;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DataStoreTest {

    static final class PlayerData {
        int tokens;
        String rank = "A";
        List<String> mines = new ArrayList<>();

        PlayerData() {
        }
    }

    private static DataStore<PlayerData> store() throws Exception {
        File folder = Files.createTempDirectory("moonlib-store").resolve("players").toFile();
        return DataStore.of(folder, PlayerData.class, DataFile.Format.JSON);
    }

    @Test
    void savesLoadsAndDeletes() throws Exception {
        DataStore<PlayerData> players = store();
        UUID id = UUID.randomUUID();
        assertNull(players.load(id));
        assertFalse(players.exists(id));

        PlayerData data = players.load(id, PlayerData::new);
        data.tokens = 99;
        data.mines.add("a");
        assertTrue(players.save(id, data));
        assertTrue(new File(players.folder(), id + ".json").exists());

        PlayerData back = players.load(id);
        assertEquals(99, back.tokens);
        assertEquals("a", back.mines.get(0));
        assertEquals(1, players.keys().size());
        assertEquals(1, players.loadAll().size());

        assertTrue(players.delete(id));
        assertFalse(players.exists(id));
        assertTrue(players.keys().isEmpty());
    }

    @Test
    void saveReplacesTheWholeEntry() throws Exception {
        DataStore<PlayerData> players = store();
        players.file("luna").set("leftover", "old").save();
        PlayerData data = new PlayerData();
        data.rank = "B";
        players.save("luna", data);
        assertFalse(players.file("luna").contains("leftover"));
        assertEquals("B", players.load("luna").rank);
    }

    @Test
    void asyncRoundTrip() throws Exception {
        DataStore<PlayerData> players = store();
        PlayerData data = new PlayerData();
        data.tokens = 5;
        players.saveAsync("async", data).get(5, TimeUnit.SECONDS);
        assertEquals(5, players.loadAsync("async").get(5, TimeUnit.SECONDS).tokens);
        assertEquals("A", players.loadAsync("missing", PlayerData::new).get(5, TimeUnit.SECONDS).rank);
    }

    @Test
    void rejectsUnsafeKeys() throws Exception {
        DataStore<PlayerData> players = store();
        assertThrows(IllegalArgumentException.class, () -> players.load("../escape"));
        assertThrows(IllegalArgumentException.class, () -> players.load("a/b"));
        assertThrows(IllegalArgumentException.class, () -> players.load(".hidden"));
        assertThrows(IllegalArgumentException.class, () -> players.load(""));
    }
}
