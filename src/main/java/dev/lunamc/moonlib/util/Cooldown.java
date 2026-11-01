package dev.lunamc.moonlib.util;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * A thread-safe cooldown tracker keyed by any type, usually a player's {@link java.util.UUID}.
 *
 * <pre>{@code
 * private final Cooldown<UUID> kitCooldown = Cooldown.of(10, TimeUnit.MINUTES);
 *
 * if (!kitCooldown.tryUse(player.getUniqueId())) {
 *     player.sendMessage("Wait " + kitCooldown.formatRemaining(player.getUniqueId()));
 *     return;
 * }
 * giveKit(player);
 * }</pre>
 *
 * <p>Expired entries are removed lazily when checked, and {@link #cleanup()} can be called from a
 * repeating task to purge entries for keys that are never checked again.</p>
 *
 * @param <K> the key type
 */
public final class Cooldown<K> {

    private final Map<K, Long> expiries = new ConcurrentHashMap<>();
    private final long durationMillis;

    /**
     * Creates a cooldown with a default duration.
     *
     * @param duration the duration
     * @param unit     the unit of {@code duration}
     */
    public Cooldown(long duration, TimeUnit unit) {
        if (duration < 0) {
            throw new IllegalArgumentException("Duration must not be negative");
        }
        this.durationMillis = unit.toMillis(duration);
    }

    /**
     * Creates a cooldown with a default duration.
     *
     * @param duration the duration
     * @param unit     the unit of {@code duration}
     * @param <K>      the key type
     * @return the new cooldown
     */
    public static <K> Cooldown<K> of(long duration, TimeUnit unit) {
        return new Cooldown<>(duration, unit);
    }

    /**
     * Starts the cooldown for a key if it is not already active.
     *
     * @param key the key
     * @return {@code true} if the key was <b>not</b> on cooldown (and now is), {@code false} if it still is
     */
    public boolean tryUse(K key) {
        long now = System.currentTimeMillis();
        boolean[] allowed = {false};
        expiries.compute(key, (k, expiry) -> {
            if (expiry == null || expiry <= now) {
                allowed[0] = true;
                return now + durationMillis;
            }
            return expiry;
        });
        return allowed[0];
    }

    /**
     * Returns whether the key is currently on cooldown.
     *
     * @param key the key
     * @return {@code true} if the cooldown is active
     */
    public boolean isActive(K key) {
        return remainingMillis(key) > 0;
    }

    /**
     * Starts (or restarts) the cooldown for a key using the default duration.
     *
     * @param key the key
     */
    public void put(K key) {
        put(key, durationMillis);
    }

    /**
     * Starts (or restarts) the cooldown for a key using a custom duration.
     *
     * @param key    the key
     * @param millis the duration in milliseconds
     */
    public void put(K key, long millis) {
        expiries.put(key, System.currentTimeMillis() + millis);
    }

    /**
     * Returns how long is left on a key's cooldown.
     *
     * @param key the key
     * @return the remaining milliseconds, or {@code 0} if not on cooldown
     */
    public long remainingMillis(K key) {
        Long expiry = expiries.get(key);
        if (expiry == null) {
            return 0;
        }
        long remaining = expiry - System.currentTimeMillis();
        if (remaining <= 0) {
            expiries.remove(key, expiry);
            return 0;
        }
        return remaining;
    }

    /**
     * Returns the remaining time formatted with {@link Durations#format(long)}, rounding up to the
     * next second so players never see "0s".
     *
     * @param key the key
     * @return e.g. {@code "4m 12s"}
     */
    public String formatRemaining(K key) {
        long remaining = remainingMillis(key);
        return Durations.format(((remaining + 999) / 1000) * 1000);
    }

    /**
     * Removes a key's cooldown.
     *
     * @param key the key
     */
    public void reset(K key) {
        expiries.remove(key);
    }

    /** Removes every cooldown. */
    public void clear() {
        expiries.clear();
    }

    /** Removes expired entries. */
    public void cleanup() {
        long now = System.currentTimeMillis();
        expiries.values().removeIf(expiry -> expiry <= now);
    }

    /**
     * Returns the default duration of this cooldown.
     *
     * @return the duration in milliseconds
     */
    public long durationMillis() {
        return durationMillis;
    }
}
