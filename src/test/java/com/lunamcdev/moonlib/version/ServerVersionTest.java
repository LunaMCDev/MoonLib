package com.lunamcdev.moonlib.version;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerVersionTest {

    @Test
    void parsesBukkitVersionStrings() {
        assertEquals(ServerVersion.of(1, 20, 4), ServerVersion.parse("1.20.4-R0.1-SNAPSHOT"));
        assertEquals(ServerVersion.of(1, 8, 8), ServerVersion.parse("1.8.8-R0.1-SNAPSHOT"));
        assertEquals(ServerVersion.of(1, 21), ServerVersion.parse("1.21-R0.1-SNAPSHOT"));
    }

    @Test
    void parsesYearBasedVersions() {
        assertEquals(ServerVersion.of(26, 1), ServerVersion.parse("26.1-R0.1-SNAPSHOT"));
        assertEquals(ServerVersion.of(26, 2, 1), ServerVersion.parse("26.2.1"));
    }

    @Test
    void yearBasedVersionsAreNewerThanClassicOnes() {
        ServerVersion modern = ServerVersion.parse("26.1");
        assertTrue(modern.isAtLeast(ServerVersion.V1_21));
        assertTrue(modern.isAtLeast(1, 20, 5));
        assertFalse(modern.isBelow(ServerVersion.V1_13));
    }

    @Test
    void comparesPatchVersions() {
        ServerVersion v = ServerVersion.of(1, 20, 4);
        assertTrue(v.isAtLeast(1, 20, 4));
        assertTrue(v.isAtLeast(1, 20));
        assertFalse(v.isAtLeast(ServerVersion.V1_20_5));
        assertTrue(v.isBetween(ServerVersion.V1_20, ServerVersion.V1_21));
    }

    @Test
    void formatsWithoutZeroPatch() {
        assertEquals("1.21", ServerVersion.of(1, 21, 0).toString());
        assertEquals("1.8.8", ServerVersion.of(1, 8, 8).toString());
    }

    @Test
    void rejectsGarbage() {
        assertThrows(IllegalArgumentException.class, () -> ServerVersion.parse("not a version"));
    }

    @Test
    void parsesTheLatestRelease() {
        assertEquals(ServerVersion.V26_3, ServerVersion.parse("26.3-R0.1-SNAPSHOT"));
        assertEquals(ServerVersion.V26_3, ServerVersion.parse("26.3"));
        assertTrue(ServerVersion.V26_3.isAtLeast(ServerVersion.V26_1));
        assertTrue(ServerVersion.V26_3.isAtLeast(ServerVersion.V1_21));
        assertFalse(ServerVersion.V26_2.isAtLeast(ServerVersion.V26_3));
    }

    @Test
    void assumesLatestWithoutAServer() {
        // No Bukkit server in unit tests: modern code paths are used.
        assertFalse(ServerVersion.isLegacy());
        assertTrue(ServerVersion.supportsHexColors());
    }
}
