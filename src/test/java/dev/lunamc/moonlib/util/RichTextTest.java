package dev.lunamc.moonlib.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RichTextTest {

    private static final char S = '§';

    private static String legacy(String text, Object... placeholders) {
        return RichText.render(text, false, placeholders);
    }

    private static String hex(String text, Object... placeholders) {
        return RichText.render(text, true, placeholders);
    }

    @Test
    void namedColoursAndDecorations() {
        assertEquals(S + "cHi", legacy("<red>Hi"));
        assertEquals(S + "c" + S + "lHi " + S + "rthere", legacy("<red><bold>Hi </bold></red>there"));
        assertEquals(S + "cA" + S + "lB" + S + "cC", legacy("<red>A<b>B</b>C"));
        assertEquals(S + "7grey", legacy("<grey>grey"));
    }

    @Test
    void closingATagClosesTagsOpenedInsideIt() {
        assertEquals(S + "c" + S + "lA" + S + "rB", legacy("<red><bold>A</red>B"));
        assertEquals(S + "cA" + S + "rB", legacy("<red>A<reset>B"));
    }

    @Test
    void hexColoursUseSectionX() {
        assertEquals(S + "x" + S + "f" + S + "f" + S + "8" + S + "8" + S + "0" + S + "0" + "O", hex("<#ff8800>O"));
        assertEquals(S + "6O", legacy("<#ffaa00>O"));
        assertEquals(hex("<#ff8800>O"), hex("<color:#ff8800>O"));
        assertEquals(S + "eY", legacy("<c:yellow>Y"));
    }

    @Test
    void gradientRunsFromFirstToLastColour() {
        String out = hex("<gradient:#000000:#ffffff>abc</gradient>");
        assertTrue(out.startsWith(S + "x" + S + "0" + S + "0" + S + "0" + S + "0" + S + "0" + S + "0" + "a"), out);
        assertTrue(out.contains(S + "x" + S + "8" + S + "0" + S + "8" + S + "0" + S + "8" + S + "0" + "b"), out);
        assertTrue(out.endsWith(S + "x" + S + "f" + S + "f" + S + "f" + S + "f" + S + "f" + S + "f" + "c"), out);
        assertEquals("abc", Text.strip(out));
        String bold = hex("<gradient:#ff0000:#0000ff><bold>ab</bold></gradient>");
        assertTrue(count(bold, S + "l") == 2, "bold is re-applied after each colour change: " + bold);
    }

    @Test
    void gradientDownsamplesOnOldServers() {
        String out = legacy("<gradient:#ff5555:#ff5555>hey</gradient>");
        assertEquals(S + "chey", out);
    }

    @Test
    void rainbowColoursEveryCharacter() {
        String out = hex("<rainbow>abcd</rainbow>");
        assertEquals(4, count(out, S + "x"));
        assertEquals("abcd", Text.strip(out));
    }

    @Test
    void unknownTagsAreLeftAlone() {
        assertEquals("I <3 you <html> </b>", legacy("I <3 you <html> </b>"));
        assertEquals("a <b> c", legacy("a \\<b> c"));
        assertEquals("<gradient:#ff0000>x", legacy("<gradient:#ff0000>x"));
    }

    @Test
    void placeholdersAreInsertedLiterally() {
        assertEquals(S + "7Hi " + S + "f<red>&cBob", legacy("<gray>Hi <white><name>", "name", "<red>&cBob"));
        assertEquals(S + "7Hi " + S + "cBob", legacy("<gray>Hi <name>", "name", RichText.parsed("<red>Bob")));
    }

    @Test
    void legacyCodesStillWork() {
        assertEquals(S + "cOld " + S + "aNew", legacy("&cOld <green>New"));
        assertEquals(S + "cA" + S + "lB", legacy("&cA&lB"));
        assertEquals(hex("<#123456>x"), hex("&#123456x"));
    }

    @Test
    void newlinesResetFormattingPerLine() {
        assertEquals(S + "cA\n" + S + "cB", legacy("<red>A<br>B"));
    }

    @Test
    void stripEscapeAndDetection() {
        assertEquals("Hello Bob", RichText.strip("<red>Hello <b><n>", "n", "Bob"));
        assertEquals("x", legacy(RichText.escape("x")));
        assertEquals("<red>", legacy(RichText.escape("<red>")));
        assertTrue(RichText.hasTags("a <b> c"));
        assertFalse(RichText.hasTags("a > b < c"));
        assertEquals("", legacy(null));
    }

    private static int count(String haystack, String needle) {
        int n = 0;
        for (int i = haystack.indexOf(needle); i >= 0; i = haystack.indexOf(needle, i + 1)) {
            n++;
        }
        return n;
    }
}
