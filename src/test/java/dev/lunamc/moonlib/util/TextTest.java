package dev.lunamc.moonlib.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextTest {

    @Test
    void translatesLegacyCodes() {
        assertEquals("§aHello §lWorld", Text.color("&aHello &lWorld"));
        assertEquals("", Text.color((String) null));
    }

    @Test
    void translatesAllHexFormats() {
        String expected = "§x§f§f§5§5§a§aHi";
        assertEquals(expected, Text.color("&#FF55AAHi"));
        assertEquals(expected, Text.color("<#FF55AA>Hi"));
        assertEquals(expected, Text.color("{#FF55AA}Hi"));
    }

    @Test
    void hexFallsBackToNearestLegacyColour() {
        assertEquals("§cHi", Text.translateHex("&#FF5555Hi", false).replace('&', '§'));
        assertEquals("§6", Text.nearestLegacy(0xFFAA11).toString());
    }

    @Test
    void stripsColours() {
        assertEquals("Hello World", Text.strip("&a&lHello &#FF0000World"));
    }

    @Test
    void replacesPlaceholders() {
        assertEquals("Hi Luna, 5 coins", Text.replace("Hi {player}, {coins} coins", "player", "Luna", "coins", 5));
        assertThrows(IllegalArgumentException.class, () -> Text.replace("{a}", "a"));
    }

    @Test
    void buildsProgressBars() {
        assertEquals("§a|||||§7|||||", Text.progressBar(50, 100, 10, '|', "&a", "&7"));
        assertEquals("§a§7||||", Text.progressBar(0, 100, 4, '|', "&a", "&7"));
    }

    @Test
    void formatsEnumNames() {
        assertEquals("Diamond Pickaxe", Text.formatEnum("DIAMOND_PICKAXE"));
    }

    @Test
    void gradientColoursEveryCharacter() {
        String gradient = Text.gradient("ab", "#000000", "#FFFFFF");
        assertTrue(gradient.startsWith("§x§0§0§0§0§0§0a"));
        assertTrue(gradient.endsWith("§x§f§f§f§f§f§fb"));
    }

    @Test
    void centresMessages() {
        String centred = Text.center("Hi");
        assertTrue(centred.startsWith("    "));
        assertTrue(centred.endsWith("Hi"));
    }
}
