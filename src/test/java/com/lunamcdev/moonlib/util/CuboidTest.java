package com.lunamcdev.moonlib.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CuboidTest {

    @Test
    void normalisesCorners() {
        Cuboid cuboid = new Cuboid("mines", 10, 80, 10, 0, 60, 0);
        assertEquals("mines;0;60;0;10;80;10", cuboid.serialize());
        assertEquals(11 * 21 * 11, cuboid.volume());
    }

    @Test
    void containsIsInclusive() {
        Cuboid cuboid = new Cuboid("mines", 0, 0, 0, 4, 4, 4);
        assertTrue(cuboid.contains(0, 0, 0));
        assertTrue(cuboid.contains(4, 4, 4));
        assertFalse(cuboid.contains(5, 4, 4));
    }

    @Test
    void roundTripsThroughText() {
        Cuboid cuboid = new Cuboid("world", -5, 10, 3, 7, 40, -9);
        assertEquals(cuboid, Cuboid.deserialize(cuboid.serialize()));
        assertThrows(IllegalArgumentException.class, () -> Cuboid.deserialize("world;1;2"));
    }

    @Test
    void expands() {
        Cuboid cuboid = new Cuboid("w", 0, 0, 0, 0, 0, 0).expand(1);
        assertEquals(27, cuboid.volume());
    }
}
