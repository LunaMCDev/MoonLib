package com.lunamcdev.moonlib.config;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DataMapperTest {

    enum Rank { A, B, C }

    static class Base {
        int level = 1;
    }

    static final class Profile extends Base {
        String name = "default";
        long tokens;
        double multiplier = 1.0;
        boolean vip;
        Rank rank = Rank.A;
        UUID id;
        Instant joined;
        Duration playtime = Duration.ZERO;
        List<String> mines = new ArrayList<>();
        Set<Rank> unlocked = EnumSet.noneOf(Rank.class);
        Map<Rank, Integer> blocks = new EnumMap<>(Rank.class);
        Map<UUID, Long> debts = new HashMap<>();
        int[] slots = new int[0];
        Home home;
        transient String cached = "skip me";
        static String constant = "never stored";

        private Profile() {
        }
    }

    static final class Home {
        String world;
        double x;
        double y;

        private Home() {
        }

        Home(String world, double x, double y) {
            this.world = world;
            this.x = x;
            this.y = y;
        }
    }

    static final class NoDefaultConstructor {
        final int value;

        NoDefaultConstructor(int value) {
            this.value = value;
        }
    }

    private static Profile sample() {
        Profile p = new Profile();
        p.level = 7;
        p.name = "Luna";
        p.tokens = 12_345_678_901L;
        p.multiplier = 2.5;
        p.vip = true;
        p.rank = Rank.C;
        p.id = UUID.fromString("5f2b6e2c-1e8a-4c1e-9a55-0f9e3b6e7a11");
        p.joined = Instant.parse("2026-11-01T12:00:00Z");
        p.playtime = Duration.ofMinutes(95);
        p.mines.addAll(Arrays.asList("a", "b"));
        p.unlocked.add(Rank.B);
        p.blocks.put(Rank.A, 500);
        p.debts.put(p.id, 10L);
        p.slots = new int[]{1, 2, 3};
        p.home = new Home("world", 10.5, 64);
        p.cached = "changed";
        return p;
    }

    @Test
    void roundTripsThroughJsonText() throws Exception {
        Object tree = DataMapper.toTree(sample());
        Object reparsed = Json.parse(Json.write(tree));
        Profile back = DataMapper.fromTree(reparsed, Profile.class);
        assertEquals(7, back.level);
        assertEquals("Luna", back.name);
        assertEquals(12_345_678_901L, back.tokens);
        assertEquals(2.5, back.multiplier, 1e-9);
        assertTrue(back.vip);
        assertEquals(Rank.C, back.rank);
        assertEquals(sample().id, back.id);
        assertEquals(Instant.parse("2026-11-01T12:00:00Z"), back.joined);
        assertEquals(Duration.ofMinutes(95), back.playtime);
        assertEquals(Arrays.asList("a", "b"), back.mines);
        assertEquals(EnumSet.of(Rank.B), back.unlocked);
        assertTrue(back.blocks instanceof EnumMap);
        assertEquals(500, (int) back.blocks.get(Rank.A));
        assertEquals(10L, (long) back.debts.get(sample().id));
        assertEquals(3, back.slots.length);
        assertEquals("world", back.home.world);
        assertEquals(10.5, back.home.x, 1e-9);
        assertEquals("skip me", back.cached);
    }

    @Test
    void skipsTransientAndStaticFields() {
        Map<?, ?> tree = (Map<?, ?>) DataMapper.toTree(sample());
        assertFalse(tree.containsKey("cached"));
        assertFalse(tree.containsKey("constant"));
        assertTrue(tree.containsKey("level"));
    }

    @Test
    void missingFieldsKeepConstructorDefaults() {
        Map<String, Object> partial = new HashMap<>();
        partial.put("name", "Old save");
        Profile back = DataMapper.fromTree(partial, Profile.class);
        assertEquals("Old save", back.name);
        assertEquals(1.0, back.multiplier, 1e-9);
        assertEquals(Rank.A, back.rank);
        assertTrue(back.mines.isEmpty());
    }

    @Test
    void convertsLooselyTypedValues() {
        assertEquals(12, (int) DataMapper.fromTree("12", Integer.class));
        assertEquals(3L, (long) DataMapper.fromTree(3.0, long.class));
        assertEquals(Boolean.TRUE, DataMapper.fromTree("yes", Boolean.class));
        assertEquals(Rank.B, DataMapper.fromTree("b", Rank.class));
        assertEquals("5", DataMapper.fromTree(5, String.class));
        assertEquals(0, (int) DataMapper.fromTree(null, int.class));
        assertEquals(Arrays.asList("solo"), DataMapper.fromTree("solo", DataMapper.listOf(String.class)));
    }

    @Test
    void genericHelpers() {
        Object uuids = DataMapper.fromTree(Arrays.asList(sample().id.toString()), DataMapper.setOf(UUID.class));
        assertTrue(uuids instanceof LinkedHashSet);
        assertTrue(((Set<?>) uuids).contains(sample().id));
        Map<String, Object> tree = new HashMap<>();
        tree.put("A", "3");
        Object ranks = DataMapper.fromTree(tree, DataMapper.mapOf(Rank.class, Integer.class));
        assertEquals(3, ((Map<?, ?>) ranks).get(Rank.A));
    }

    @Test
    void customAdapters() {
        DataMapper.register(Home.class, DataMapper.adapter(h -> h.world + "@" + h.x, s -> {
            String[] parts = s.toString().split("@");
            return new Home(parts[0], Double.parseDouble(parts[1]), 0);
        }));
        try {
            assertEquals("nether@4.0", DataMapper.toTree(new Home("nether", 4, 0)));
            assertEquals("nether", DataMapper.fromTree("nether@4.0", Home.class).world);
        } finally {
            DataMapper.unregister(Home.class);
        }
        assertEquals("nether", ((Map<?, ?>) DataMapper.toTree(new Home("nether", 4, 0))).get("world"));
    }

    @Test
    void explainsUnsupportedTypes() {
        IllegalArgumentException noConstructor = assertThrows(IllegalArgumentException.class,
                () -> DataMapper.fromTree(new HashMap<String, Object>(), NoDefaultConstructor.class));
        assertTrue(noConstructor.getMessage().contains("no-argument constructor"));
        IllegalArgumentException wrongShape = assertThrows(IllegalArgumentException.class,
                () -> DataMapper.fromTree("text", Profile.class));
        assertTrue(wrongShape.getMessage().contains("Expected Profile"));
        assertThrows(IllegalArgumentException.class, () -> DataMapper.fromTree("nope", UUID.class));
        assertThrows(IllegalArgumentException.class, () -> DataMapper.fromTree("X", Rank.class));
    }

    @Test
    void rejectsSelfReferences() {
        List<Object> list = new ArrayList<>();
        list.add(list);
        assertThrows(IllegalArgumentException.class, () -> DataMapper.toTree(list));
    }
}
