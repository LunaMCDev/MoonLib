package com.lunamcdev.moonlib.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpdateCheckerTest {

    @Test
    void comparesVersionsNumericallyWithPreReleases() {
        assertTrue(UpdateChecker.compare("1.10.0", "1.9.2") > 0);
        assertTrue(UpdateChecker.compare("v2.0.0", "2.0.0-beta") > 0);
        assertTrue(UpdateChecker.compare("2.0.0-beta", "2.0.0-alpha") > 0);
        assertEquals(0, UpdateChecker.compare("1.4.0", "v1.4.0"));
        assertEquals(0, UpdateChecker.compare("1.4.0+build.7", "1.4.0"));
        assertTrue(UpdateChecker.compare("1.4", "1.4.1") < 0);
    }

    @Test
    void parsesEachSource() {
        String modrinth = "[{\"version_number\":\"2.1.0-beta\",\"version_type\":\"beta\"},"
                + "{\"version_number\":\"2.0.3\",\"version_type\":\"release\"}]";
        assertEquals("2.0.3", UpdateChecker.parseLatest(UpdateChecker.Source.MODRINTH, modrinth));
        assertEquals("1.5.0", UpdateChecker.parseLatest(UpdateChecker.Source.GITHUB, "{\"tag_name\":\"v1.5.0\",\"name\":\"x\"}"));
        assertEquals("3.2", UpdateChecker.parseLatest(UpdateChecker.Source.SPIGOT, "3.2\n"));
        assertThrows(IllegalStateException.class, () -> UpdateChecker.parseLatest(UpdateChecker.Source.MODRINTH, "[]"));
        assertThrows(IllegalStateException.class, () -> UpdateChecker.parseLatest(UpdateChecker.Source.GITHUB, "<html>"));
        assertTrue(UpdateChecker.apiUrl(UpdateChecker.Source.HANGAR, "LunaMCDev/MoonPrison").endsWith("/projects/MoonPrison/latestrelease"));
    }
}
