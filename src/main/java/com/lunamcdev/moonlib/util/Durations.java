package com.lunamcdev.moonlib.util;

import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parsing and formatting of human readable durations.
 *
 * <pre>{@code
 * long ban = Durations.parse("7d12h");              // 648000000
 * Durations.format(90_000);                          // "1m 30s"
 * Durations.formatLong(90_000);                      // "1 minute, 30 seconds"
 * Durations.formatClock(3_725_000);                  // "01:02:05"
 * long ticks = Durations.toTicks(5, TimeUnit.SECONDS); // 100
 * }</pre>
 *
 * <p>Supported units when parsing: {@code ms}, {@code s}, {@code m}, {@code h}, {@code d}, {@code w},
 * {@code mo} (30 days) and {@code y} (365 days). Long names such as {@code "5 minutes"} also work.</p>
 */
public final class Durations {

    private static final Pattern PART = Pattern.compile(
            // Longest alternatives first: regex alternation takes the first one that matches.
            "(\\d+(?:\\.\\d+)?)\\s*(milliseconds?|millis|ms|months?|mo|minutes?|mins?|m|seconds?|secs?|s|"
                    + "hours?|hrs?|h|days?|d|weeks?|w|years?|yrs?|y)",
            Pattern.CASE_INSENSITIVE);

    private static final long SECOND = 1000L;
    private static final long MINUTE = 60 * SECOND;
    private static final long HOUR = 60 * MINUTE;
    private static final long DAY = 24 * HOUR;
    private static final long WEEK = 7 * DAY;
    private static final long MONTH = 30 * DAY;
    private static final long YEAR = 365 * DAY;

    private static final long[] UNIT_MILLIS = {YEAR, MONTH, WEEK, DAY, HOUR, MINUTE, SECOND};
    private static final String[] SHORT_NAMES = {"y", "mo", "w", "d", "h", "m", "s"};
    private static final String[] LONG_NAMES = {"year", "month", "week", "day", "hour", "minute", "second"};

    private Durations() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Parses a duration such as {@code "1h30m"}, {@code "2d 6h"} or {@code "45 seconds"}.
     * A bare number is interpreted as seconds.
     *
     * @param input the text to parse
     * @return the duration in milliseconds
     * @throws IllegalArgumentException if the text is not a valid duration
     */
    public static long parse(String input) {
        if (input == null || input.trim().isEmpty()) {
            throw new IllegalArgumentException("Duration is empty");
        }
        String text = input.trim().toLowerCase(Locale.ROOT);
        if (text.matches("\\d+")) {
            return Long.parseLong(text) * SECOND;
        }
        Matcher matcher = PART.matcher(text);
        long total = 0;
        int consumed = 0;
        while (matcher.find()) {
            if (!text.substring(consumed, matcher.start()).trim().replace(",", "").isEmpty()) {
                throw new IllegalArgumentException("Invalid duration: " + input);
            }
            double amount = Double.parseDouble(matcher.group(1));
            total += (long) (amount * unitMillis(matcher.group(2)));
            consumed = matcher.end();
        }
        if (consumed == 0 || !text.substring(consumed).trim().isEmpty()) {
            throw new IllegalArgumentException("Invalid duration: " + input);
        }
        return total;
    }

    /**
     * Parses a duration, returning a fallback instead of throwing.
     *
     * @param input    the text to parse
     * @param fallback the value to return when parsing fails
     * @return the duration in milliseconds, or {@code fallback}
     */
    public static long parse(String input, long fallback) {
        try {
            return parse(input);
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    /**
     * Formats milliseconds compactly, e.g. {@code "2d 4h 1m 9s"}. Zero units are omitted.
     *
     * @param millis the duration
     * @return the formatted duration, {@code "0s"} for anything under one second
     */
    public static String format(long millis) {
        return format(millis, Integer.MAX_VALUE);
    }

    /**
     * Formats milliseconds compactly, showing at most {@code maxUnits} units: {@code format(t, 2)} gives
     * {@code "2d 4h"} rather than {@code "2d 4h 1m 9s"}.
     *
     * @param millis   the duration
     * @param maxUnits the maximum number of units to show
     * @return the formatted duration
     */
    public static String format(long millis, int maxUnits) {
        return build(millis, maxUnits, false);
    }

    /**
     * Formats milliseconds in words, e.g. {@code "1 hour, 5 minutes"}.
     *
     * @param millis the duration
     * @return the formatted duration
     */
    public static String formatLong(long millis) {
        return build(millis, Integer.MAX_VALUE, true);
    }

    /**
     * Formats milliseconds as a clock: {@code "05:09"} below an hour, {@code "01:05:09"} otherwise.
     *
     * @param millis the duration
     * @return the clock string
     */
    public static String formatClock(long millis) {
        long totalSeconds = Math.max(0, millis) / 1000;
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        return hours > 0
                ? String.format("%02d:%02d:%02d", hours, minutes, seconds)
                : String.format("%02d:%02d", minutes, seconds);
    }

    /**
     * Converts a duration to server ticks (20 per second).
     *
     * @param amount the amount
     * @param unit   the unit of {@code amount}
     * @return the duration in ticks
     */
    public static long toTicks(long amount, TimeUnit unit) {
        return unit.toMillis(amount) / 50L;
    }

    /**
     * Converts milliseconds to ticks.
     *
     * @param millis the duration
     * @return the duration in ticks
     */
    public static long millisToTicks(long millis) {
        return millis / 50L;
    }

    private static String build(long millis, int maxUnits, boolean words) {
        long remaining = Math.max(0, millis);
        StringBuilder sb = new StringBuilder();
        int used = 0;
        for (int i = 0; i < UNIT_MILLIS.length && used < maxUnits; i++) {
            long amount = remaining / UNIT_MILLIS[i];
            if (amount <= 0) {
                continue;
            }
            remaining -= amount * UNIT_MILLIS[i];
            if (sb.length() > 0) {
                sb.append(words ? ", " : " ");
            }
            sb.append(amount);
            if (words) {
                sb.append(' ').append(LONG_NAMES[i]).append(amount == 1 ? "" : "s");
            } else {
                sb.append(SHORT_NAMES[i]);
            }
            used++;
        }
        if (sb.length() == 0) {
            return words ? "0 seconds" : "0s";
        }
        return sb.toString();
    }

    private static long unitMillis(String unit) {
        String u = unit.toLowerCase(Locale.ROOT);
        if (u.equals("ms") || u.startsWith("milli")) {
            return 1;
        }
        if (u.startsWith("mo")) {
            return MONTH;
        }
        if (u.startsWith("mi") || u.equals("m")) {
            return MINUTE;
        }
        switch (u.charAt(0)) {
            case 's':
                return SECOND;
            case 'h':
                return HOUR;
            case 'd':
                return DAY;
            case 'w':
                return WEEK;
            case 'y':
                return YEAR;
            default:
                throw new IllegalArgumentException("Unknown time unit: " + unit);
        }
    }
}
