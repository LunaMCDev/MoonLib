package dev.lunamc.moonlib.config;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonFileTest {

    static final class Stats {
        int resets;
        Set<String> owners = new HashSet<>();

        private Stats() {
        }
    }

    private static Path tempDir() throws Exception {
        Path dir = Files.createTempDirectory("moonlib-test");
        dir.toFile().deleteOnExit();
        return dir;
    }

    private static String read(File file) throws Exception {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }

    @Test
    void createsEditsAndSaves() throws Exception {
        File file = tempDir().resolve("sub/data.json").toFile();
        JsonFile json = DataFile.json(file);
        assertTrue(file.exists(), "file and parent folder are created");
        assertEquals("{}", read(file).trim());

        UUID id = UUID.randomUUID();
        json.set("players." + id + ".name", "Luna")
                .set("players." + id + ".tokens", 50)
                .set("settings.enabled", true)
                .set("settings.list", Arrays.asList("a", "b"));
        assertTrue(json.save());

        JsonFile reloaded = DataFile.json(file);
        assertEquals("Luna", reloaded.getString("players." + id + ".name"));
        assertEquals(50, reloaded.getInt("players." + id + ".tokens", 0));
        assertTrue(reloaded.getBoolean("settings.enabled", false));
        assertEquals(Arrays.asList("a", "b"), reloaded.getStringList("settings.list"));
        assertEquals(Collections.singleton(id.toString()), reloaded.keys("players"));
        assertTrue(reloaded.keys("", true).contains("players." + id + ".tokens"));
        assertTrue(reloaded.isSection("settings"));
        assertFalse(reloaded.contains("nope.missing"));
        assertEquals(7, reloaded.getInt("nope.missing", 7));
    }

    @Test
    void storesObjectsAtPathsAndAsRoot() throws Exception {
        File file = tempDir().resolve("stats.json").toFile();
        JsonFile json = DataFile.json(file);
        Stats stats = new Stats();
        stats.resets = 3;
        stats.owners.add("Luna");
        json.set("mines.a", stats).save();

        Stats back = DataFile.json(file).get("mines.a", Stats.class);
        assertEquals(3, back.resets);
        assertTrue(back.owners.contains("Luna"));
        assertEquals(1, DataFile.json(file).getMap("mines", Stats.class).size());

        json.setRoot(stats).save();
        assertEquals(3, DataFile.json(file).as(Stats.class).resets);
    }

    @Test
    void removesAndClears() throws Exception {
        JsonFile json = DataFile.json(tempDir().resolve("r.json").toFile());
        json.set("a.b", 1).set("a.c", 2).remove("a.b");
        assertFalse(json.contains("a.b"));
        assertTrue(json.contains("a.c"));
        json.set("a.c", null);
        assertEquals(0, json.keys("a").size());
        json.remove("x.y.z"); // removing missing paths is harmless
        json.clear();
        assertTrue(json.keys("").isEmpty());
    }

    @Test
    void returnedValuesAreCopies() throws Exception {
        JsonFile json = DataFile.json(tempDir().resolve("c.json").toFile());
        json.set("list", Arrays.asList("a"));
        json.getStringList("list").add("b");
        @SuppressWarnings("unchecked")
        java.util.List<Object> raw = (java.util.List<Object>) json.get("list");
        raw.add("c");
        assertEquals(1, json.getStringList("list").size());
    }

    @Test
    void editSavesImmediately() throws Exception {
        File file = tempDir().resolve("e.json").toFile();
        DataFile.json(file).edit(f -> f.set("count", f.getInt("count", 0) + 1));
        DataFile.json(file).edit(f -> f.set("count", f.getInt("count", 0) + 1));
        assertEquals(2, DataFile.json(file).getInt("count", 0));
    }

    @Test
    void saveAsyncWritesSnapshot() throws Exception {
        File file = tempDir().resolve("async.json").toFile();
        JsonFile json = DataFile.json(file);
        json.set("value", 1);
        java.util.concurrent.CompletableFuture<Void> saving = json.saveAsync();
        json.set("value", 2); // after the snapshot
        saving.get(5, TimeUnit.SECONDS);
        assertEquals(1, DataFile.json(file).getInt("value", 0));
    }

    @Test
    void backsUpBrokenFiles() throws Exception {
        Path dir = tempDir();
        File file = dir.resolve("broken.json").toFile();
        Files.write(file.toPath(), "{\"a\": 1,, }".getBytes(StandardCharsets.UTF_8));
        JsonFile json = DataFile.json(file);
        assertTrue(json.keys("").isEmpty(), "falls back to empty data");
        File[] backups = dir.toFile().listFiles((d, n) -> n.startsWith("broken.broken-") && n.endsWith(".json"));
        assertEquals(1, backups.length);
        assertEquals("{\"a\": 1,, }", read(backups[0]));
    }

    @Test
    void compactOutput() throws Exception {
        File file = tempDir().resolve("compact.json").toFile();
        DataFile.json(file).prettyPrint(false).set("a", 1).save();
        assertEquals("{\"a\":1}", read(file).trim());
    }

    @Test
    void explainsBadReads() throws Exception {
        JsonFile json = DataFile.json(tempDir().resolve("bad.json").toFile());
        json.set("id", "not-a-uuid");
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> json.get("id", UUID.class));
        assertTrue(error.getMessage().contains("'id'"));
        assertNull(json.getUuid("id"));
    }

    @Test
    void picksFormatFromExtension() throws Exception {
        assertEquals(DataFile.Format.JSON, DataFile.of(tempDir().resolve("x.json").toFile()).format());
        assertEquals(DataFile.Format.YAML, DataFile.Format.fromFileName("config.YML"));
        assertThrows(IllegalArgumentException.class, () -> DataFile.Format.fromFileName("notes.txt"));
    }

    @Test
    void rejectsInvalidPaths() throws Exception {
        JsonFile json = DataFile.json(tempDir().resolve("p.json").toFile());
        assertThrows(IllegalArgumentException.class, () -> json.set("a..b", 1));
        Map<String, Object> map = json.toMap();
        assertTrue(map.isEmpty());
    }
}
