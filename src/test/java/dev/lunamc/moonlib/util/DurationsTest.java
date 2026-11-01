package dev.lunamc.moonlib.util;

import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DurationsTest {

    @Test
    void parsesCompactDurations() {
        assertEquals(TimeUnit.MINUTES.toMillis(90), Durations.parse("1h30m"));
        assertEquals(TimeUnit.DAYS.toMillis(7) + TimeUnit.HOURS.toMillis(12), Durations.parse("7d12h"));
        assertEquals(TimeUnit.SECONDS.toMillis(45), Durations.parse("45s"));
        assertEquals(500, Durations.parse("500ms"));
    }

    @Test
    void parsesWordsAndSpaces() {
        assertEquals(TimeUnit.MINUTES.toMillis(5), Durations.parse("5 minutes"));
        assertEquals(TimeUnit.HOURS.toMillis(2) + TimeUnit.MINUTES.toMillis(3), Durations.parse("2 hours 3 mins"));
        assertEquals(TimeUnit.DAYS.toMillis(60), Durations.parse("2mo"));
    }

    @Test
    void bareNumberIsSeconds() {
        assertEquals(30_000, Durations.parse("30"));
    }

    @Test
    void rejectsInvalidInput() {
        assertThrows(IllegalArgumentException.class, () -> Durations.parse("soon"));
        assertThrows(IllegalArgumentException.class, () -> Durations.parse("5x"));
        assertThrows(IllegalArgumentException.class, () -> Durations.parse(""));
        assertEquals(-1, Durations.parse("nope", -1));
    }

    @Test
    void formatsCompactly() {
        assertEquals("1m 30s", Durations.format(90_000));
        assertEquals("2d 4h", Durations.format(TimeUnit.DAYS.toMillis(2) + TimeUnit.HOURS.toMillis(4) + 59_000, 2));
        assertEquals("0s", Durations.format(400));
    }

    @Test
    void formatsInWords() {
        assertEquals("1 hour, 5 minutes", Durations.formatLong(TimeUnit.MINUTES.toMillis(65)));
        assertEquals("2 seconds", Durations.formatLong(2000));
    }

    @Test
    void formatsAsClock() {
        assertEquals("05:09", Durations.formatClock(309_000));
        assertEquals("01:02:05", Durations.formatClock(3_725_000));
    }

    @Test
    void convertsToTicks() {
        assertEquals(100, Durations.toTicks(5, TimeUnit.SECONDS));
    }
}
