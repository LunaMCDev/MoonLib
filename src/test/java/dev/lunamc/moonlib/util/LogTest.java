package dev.lunamc.moonlib.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LogTest {

    @Test
    void fillsPlaceholdersInOrder() {
        assertEquals("Loaded 3 mines in 12ms", Log.format("Loaded {} mines in {}ms", 3, 12));
    }

    @Test
    void leavesExtraPlaceholdersAlone() {
        assertEquals("a {} b", Log.format("{} {} b", "a"));
    }
}
