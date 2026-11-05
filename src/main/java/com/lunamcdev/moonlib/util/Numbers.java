package com.lunamcdev.moonlib.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Number formatting, parsing and randomness helpers.
 *
 * <pre>{@code
 * Numbers.compact(1_250_000);        // "1.25M"
 * Numbers.compact(3.2e15);           // "3.2Q"
 * Numbers.commas(1234567.891);       // "1,234,567.89"
 * Numbers.roman(14);                 // "XIV"
 * Numbers.ordinal(22);               // "22nd"
 * Numbers.chance(12.5);              // true 12.5% of the time
 * Numbers.parseInt("abc", 0);        // 0
 * }</pre>
 */
public final class Numbers {

    private static final String[] COMPACT_SUFFIXES = {
            "", "K", "M", "B", "T", "Q", "Qi", "Sx", "Sp", "Oc", "No", "Dc",
            "UDc", "DDc", "TDc", "QaDc", "QiDc", "SxDc", "SpDc", "OcDc", "NoDc", "Vg"
    };
    private static final int[] ROMAN_VALUES = {1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1};
    private static final String[] ROMAN_SYMBOLS = {"M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I"};

    private static final ThreadLocal<DecimalFormat> COMMAS = ThreadLocal.withInitial(
            () -> new DecimalFormat("#,##0.##", DecimalFormatSymbols.getInstance(Locale.US)));
    private static final ThreadLocal<DecimalFormat> COMPACT = ThreadLocal.withInitial(
            () -> new DecimalFormat("0.##", DecimalFormatSymbols.getInstance(Locale.US)));

    private Numbers() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Formats large numbers with a suffix: {@code 1500 -> 1.5K}, {@code 2_000_000 -> 2M}. Supports
     * values up to 10^63 ({@code Vg}), which is useful for prison and idle-style economies.
     *
     * @param value the number
     * @return the compact representation
     */
    public static String compact(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return String.valueOf(value);
        }
        double abs = Math.abs(value);
        int index = 0;
        while (abs >= 1000 && index < COMPACT_SUFFIXES.length - 1) {
            abs /= 1000;
            index++;
        }
        // Rounding 999.996K would print "1000K"; bump it to the next suffix instead.
        if (index < COMPACT_SUFFIXES.length - 1 && round(abs, 2) >= 1000) {
            abs /= 1000;
            index++;
        }
        return (value < 0 ? "-" : "") + COMPACT.get().format(abs) + COMPACT_SUFFIXES[index];
    }

    /**
     * Formats a number with thousands separators and up to two decimal places.
     *
     * @param value the number
     * @return e.g. {@code "1,234,567.89"}
     */
    public static String commas(double value) {
        return COMMAS.get().format(value);
    }

    /**
     * Rounds to a number of decimal places using half-up rounding.
     *
     * @param value  the number
     * @param places decimal places, must be 0 or more
     * @return the rounded value
     */
    public static double round(double value, int places) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return value;
        }
        return new BigDecimal(Double.toString(value)).setScale(places, RoundingMode.HALF_UP).doubleValue();
    }

    /**
     * Converts a number from 1 to 3999 into roman numerals. Useful for enchant and rank levels.
     *
     * @param number the number
     * @return the roman numeral, or the plain number if it is outside 1..3999
     */
    public static String roman(int number) {
        if (number < 1 || number > 3999) {
            return String.valueOf(number);
        }
        StringBuilder sb = new StringBuilder();
        int remaining = number;
        for (int i = 0; i < ROMAN_VALUES.length; i++) {
            while (remaining >= ROMAN_VALUES[i]) {
                remaining -= ROMAN_VALUES[i];
                sb.append(ROMAN_SYMBOLS[i]);
            }
        }
        return sb.toString();
    }

    /**
     * Returns the number with its English ordinal suffix: 1st, 2nd, 3rd, 4th, 11th, 21st ...
     *
     * @param number the number
     * @return the ordinal string
     */
    public static String ordinal(int number) {
        int mod100 = Math.abs(number) % 100;
        int mod10 = Math.abs(number) % 10;
        String suffix;
        if (mod100 >= 11 && mod100 <= 13) {
            suffix = "th";
        } else if (mod10 == 1) {
            suffix = "st";
        } else if (mod10 == 2) {
            suffix = "nd";
        } else if (mod10 == 3) {
            suffix = "rd";
        } else {
            suffix = "th";
        }
        return number + suffix;
    }

    /**
     * Parses an integer, returning a fallback on failure.
     *
     * @param input    the text
     * @param fallback returned if {@code input} is not an integer
     * @return the parsed value or {@code fallback}
     */
    public static int parseInt(String input, int fallback) {
        try {
            return input == null ? fallback : Integer.parseInt(input.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /**
     * Parses a long, returning a fallback on failure.
     *
     * @param input    the text
     * @param fallback returned if {@code input} is not a long
     * @return the parsed value or {@code fallback}
     */
    public static long parseLong(String input, long fallback) {
        try {
            return input == null ? fallback : Long.parseLong(input.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /**
     * Parses a double, returning a fallback on failure. NaN and infinity are rejected.
     *
     * @param input    the text
     * @param fallback returned if {@code input} is not a finite number
     * @return the parsed value or {@code fallback}
     */
    public static double parseDouble(String input, double fallback) {
        try {
            if (input == null) {
                return fallback;
            }
            double value = Double.parseDouble(input.trim());
            return Double.isNaN(value) || Double.isInfinite(value) ? fallback : value;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /**
     * Returns whether the text is a valid integer.
     *
     * @param input the text
     * @return {@code true} if {@link Integer#parseInt(String)} would succeed
     */
    public static boolean isInteger(String input) {
        if (input == null) {
            return false;
        }
        try {
            Integer.parseInt(input.trim());
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * Returns whether the text is a valid finite decimal number.
     *
     * @param input the text
     * @return {@code true} if it parses as a finite double
     */
    public static boolean isDecimal(String input) {
        return !Double.isNaN(parseDouble(input, Double.NaN));
    }

    /**
     * Clamps a value into a range.
     *
     * @param value the value
     * @param min   the lower bound
     * @param max   the upper bound
     * @return {@code value} limited to {@code [min, max]}
     */
    public static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    /**
     * Clamps a value into a range.
     *
     * @param value the value
     * @param min   the lower bound
     * @param max   the upper bound
     * @return {@code value} limited to {@code [min, max]}
     */
    public static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    /**
     * Returns a random integer between {@code min} and {@code max}, both inclusive.
     *
     * @param min the smallest possible value
     * @param max the largest possible value
     * @return the random value
     */
    public static int random(int min, int max) {
        if (min > max) {
            throw new IllegalArgumentException("min (" + min + ") > max (" + max + ")");
        }
        return ThreadLocalRandom.current().nextInt(min, max + 1);
    }

    /**
     * Returns a random double between {@code min} (inclusive) and {@code max} (exclusive).
     *
     * @param min the lower bound
     * @param max the upper bound
     * @return the random value
     */
    public static double random(double min, double max) {
        if (min >= max) {
            return min;
        }
        return ThreadLocalRandom.current().nextDouble(min, max);
    }

    /**
     * Rolls a percentage chance.
     *
     * @param percent the chance from 0 to 100
     * @return {@code true} with the given probability
     */
    public static boolean chance(double percent) {
        return percent > 0 && (percent >= 100 || ThreadLocalRandom.current().nextDouble(100) < percent);
    }
}
