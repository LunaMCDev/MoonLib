package dev.lunamc.moonlib.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NumbersTest {

    @Test
    void compactsLargeNumbers() {
        assertEquals("999", Numbers.compact(999));
        assertEquals("1.5K", Numbers.compact(1500));
        assertEquals("1.25M", Numbers.compact(1_250_000));
        assertEquals("3.2Q", Numbers.compact(3.2e15));
        assertEquals("-2B", Numbers.compact(-2e9));
    }

    @Test
    void compactRoundingRollsOverToNextSuffix() {
        assertEquals("1M", Numbers.compact(999_999));
    }

    @Test
    void formatsWithCommas() {
        assertEquals("1,234,567.89", Numbers.commas(1234567.891));
        assertEquals("12", Numbers.commas(12));
    }

    @Test
    void romanNumerals() {
        assertEquals("XIV", Numbers.roman(14));
        assertEquals("MMXXVI", Numbers.roman(2026));
        assertEquals("0", Numbers.roman(0));
    }

    @Test
    void ordinals() {
        assertEquals("1st", Numbers.ordinal(1));
        assertEquals("2nd", Numbers.ordinal(2));
        assertEquals("3rd", Numbers.ordinal(3));
        assertEquals("11th", Numbers.ordinal(11));
        assertEquals("22nd", Numbers.ordinal(22));
        assertEquals("113th", Numbers.ordinal(113));
    }

    @Test
    void safeParsing() {
        assertEquals(5, Numbers.parseInt("5", 0));
        assertEquals(0, Numbers.parseInt("five", 0));
        assertEquals(2.5, Numbers.parseDouble("2.5", 0), 1e-9);
        assertEquals(-1, Numbers.parseDouble("NaN", -1), 1e-9);
        assertTrue(Numbers.isInteger(" 42 "));
        assertFalse(Numbers.isInteger("4.2"));
    }

    @Test
    void randomStaysInRange() {
        for (int i = 0; i < 1000; i++) {
            int value = Numbers.random(3, 5);
            assertTrue(value >= 3 && value <= 5);
        }
        assertTrue(Numbers.chance(100));
        assertFalse(Numbers.chance(0));
    }
}
