package com.lunamcdev.moonlib.hook;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Soft integration with PlaceholderAPI. Works whether or not PlaceholderAPI is installed: without it,
 * text is returned unchanged. No compile-time dependency is needed.
 *
 * <pre>{@code
 * String line = Placeholders.apply(player, "&fBalance: &a%vault_eco_balance_formatted%");
 * }</pre>
 *
 * <p>Remember to add {@code softdepend: [PlaceholderAPI]} to your {@code plugin.yml}.</p>
 */
public final class Placeholders {

    private static volatile Method setPlaceholders;
    private static volatile boolean resolved;

    private Placeholders() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Returns whether PlaceholderAPI is installed and enabled.
     *
     * @return {@code true} if placeholders will be replaced
     */
    public static boolean isEnabled() {
        Plugin papi = Bukkit.getPluginManager().getPlugin("PlaceholderAPI");
        return papi != null && papi.isEnabled() && method() != null;
    }

    /**
     * Replaces PlaceholderAPI placeholders.
     *
     * @param player the player to resolve placeholders for, may be {@code null}
     * @param text   the text
     * @return the text with placeholders replaced, or unchanged if PlaceholderAPI is missing
     */
    public static String apply(OfflinePlayer player, String text) {
        if (text == null || text.indexOf('%') < 0 || !isEnabled()) {
            return text;
        }
        try {
            return (String) method().invoke(null, player, text);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return text;
        }
    }

    /**
     * Replaces placeholders in every line.
     *
     * @param player the player
     * @param lines  the lines
     * @return a new list with placeholders replaced
     */
    public static List<String> apply(OfflinePlayer player, List<String> lines) {
        List<String> out = new ArrayList<>(lines.size());
        for (String line : lines) {
            out.add(apply(player, line));
        }
        return out;
    }

    private static Method method() {
        if (!resolved) {
            synchronized (Placeholders.class) {
                if (!resolved) {
                    try {
                        Class<?> api = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
                        setPlaceholders = api.getMethod("setPlaceholders", OfflinePlayer.class, String.class);
                    } catch (ReflectiveOperationException | LinkageError e) {
                        setPlaceholders = null;
                    }
                    // Only cache a miss once the server has finished starting, in case PAPI loads later.
                    resolved = setPlaceholders != null || Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null;
                }
            }
        }
        return setPlaceholders;
    }
}
