package com.lunamcdev.moonlib.command;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArgumentTypesTest {

    @Test
    void parsesCompactNumbers() {
        assertEquals(5000.0, ArgumentTypes.parseCompact("5k"), 1e-9);
        assertEquals(2_500_000.0, ArgumentTypes.parseCompact("2.5m"), 1e-9);
        assertEquals(1e9, ArgumentTypes.parseCompact("1B"), 1e-9);
        assertEquals(1234.0, ArgumentTypes.parseCompact("1,234"), 1e-9);
        assertEquals(null, ArgumentTypes.parseCompact("abc"));
    }

    @Test
    void integerRangeIsEnforced() {
        ArgumentType<Integer> type = ArgumentTypes.integer(1, 64);
        assertEquals(32, (int) type.parse(null, "32"));
        assertThrows(CommandError.class, () -> type.parse(null, "65"));
        assertThrows(CommandError.class, () -> type.parse(null, "lots"));
    }

    @Test
    void booleanAcceptsCommonWords() {
        assertTrue(ArgumentTypes.BOOLEAN.parse(null, "yes"));
        assertFalse(ArgumentTypes.BOOLEAN.parse(null, "off"));
        assertThrows(CommandError.class, () -> ArgumentTypes.BOOLEAN.parse(null, "maybe"));
    }

    @Test
    void choiceIsCaseInsensitive() {
        ArgumentType<String> type = ArgumentTypes.choice("Easy", "Hard");
        assertEquals("Hard", type.parse(null, "hard"));
        CommandError error = assertThrows(CommandError.class, () -> type.parse(null, "medium"));
        assertTrue(error.getMessage().contains("Easy, Hard"));
    }

    enum Mode { CREATIVE, SURVIVAL }

    @Test
    void enumTypeAcceptsAnyCase() {
        assertEquals(Mode.SURVIVAL, ArgumentTypes.enumType(Mode.class).parse(null, "survival"));
    }

    @Test
    void durationParsesToMillis() {
        assertEquals(90_000L, (long) ArgumentTypes.DURATION.parse(null, "1m30s"));
        assertThrows(CommandError.class, () -> ArgumentTypes.DURATION.parse(null, "later"));
    }
}
