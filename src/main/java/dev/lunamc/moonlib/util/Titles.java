package dev.lunamc.moonlib.util;

import dev.lunamc.moonlib.version.ServerSoftware;
import dev.lunamc.moonlib.version.ServerVersion;
import dev.lunamc.moonlib.version.adapter.AdventureMessageAdapter;
import dev.lunamc.moonlib.version.adapter.LegacyMessageAdapter;
import dev.lunamc.moonlib.version.adapter.MessageAdapter;
import dev.lunamc.moonlib.version.adapter.SpigotMessageAdapter;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Titles and action bars that work on every supported version.
 *
 * <p>The best available implementation is chosen automatically:</p>
 * <ul>
 *     <li><b>Paper</b> 1.16.5+: native Adventure API</li>
 *     <li><b>Spigot</b> 1.12+: Bukkit title API and BungeeCord chat action bars</li>
 *     <li><b>1.8 to 1.11</b>: packets via reflection</li>
 * </ul>
 * <p>All text is run through {@link Text#color(String)} first.</p>
 *
 * <pre>{@code
 * Titles.send(player, "&6&lRANK UP!", "&7You are now rank &eB");
 * Titles.send(player, "&cWarning", "", 5, 40, 10);
 * Titles.actionBar(player, "&aBlocks mined: &f" + mined);
 * Titles.broadcastActionBar("&d&lDouble XP is active!");
 * }</pre>
 */
public final class Titles {

    /** Default fade-in time in ticks. */
    public static final int DEFAULT_FADE_IN = 10;
    /** Default stay time in ticks. */
    public static final int DEFAULT_STAY = 70;
    /** Default fade-out time in ticks. */
    public static final int DEFAULT_FADE_OUT = 20;

    private static volatile MessageAdapter adapter;
    private static volatile boolean warned;

    private Titles() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Shows a title using the default timings.
     *
     * @param player   the player
     * @param title    the title, may be empty
     * @param subtitle the subtitle, may be empty
     */
    public static void send(Player player, String title, String subtitle) {
        send(player, title, subtitle, DEFAULT_FADE_IN, DEFAULT_STAY, DEFAULT_FADE_OUT);
    }

    /**
     * Shows a title.
     *
     * @param player   the player
     * @param title    the title, may be empty
     * @param subtitle the subtitle, may be empty
     * @param fadeIn   fade-in time in ticks
     * @param stay     stay time in ticks
     * @param fadeOut  fade-out time in ticks
     */
    public static void send(Player player, String title, String subtitle, int fadeIn, int stay, int fadeOut) {
        MessageAdapter current = adapter();
        if (current == null || player == null) {
            return;
        }
        try {
            current.title(player, Text.color(title), Text.color(subtitle), fadeIn, stay, fadeOut);
        } catch (RuntimeException e) {
            fail(e);
        }
    }

    /**
     * Clears any title currently shown to the player.
     *
     * @param player the player
     */
    public static void clear(Player player) {
        MessageAdapter current = adapter();
        if (current == null || player == null) {
            return;
        }
        try {
            current.clearTitle(player);
        } catch (RuntimeException e) {
            fail(e);
        }
    }

    /**
     * Shows an action bar message (the text above the hotbar).
     *
     * @param player  the player
     * @param message the message
     */
    public static void actionBar(Player player, String message) {
        MessageAdapter current = adapter();
        if (current == null || player == null) {
            return;
        }
        try {
            current.actionBar(player, Text.color(message));
        } catch (RuntimeException e) {
            fail(e);
        }
    }

    /**
     * Shows a title to every online player.
     *
     * @param title    the title
     * @param subtitle the subtitle
     */
    public static void broadcast(String title, String subtitle) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            send(player, title, subtitle);
        }
    }

    /**
     * Shows an action bar message to every online player.
     *
     * @param message the message
     */
    public static void broadcastActionBar(String message) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            actionBar(player, message);
        }
    }

    /**
     * Returns the name of the implementation in use, useful in debug output.
     *
     * @return e.g. {@code "Adventure"}, {@code "Spigot"}, {@code "Legacy NMS"} or {@code "None"}
     */
    public static String implementationName() {
        MessageAdapter current = adapter();
        return current == null ? "None" : current.name();
    }

    private static MessageAdapter adapter() {
        MessageAdapter current = adapter;
        if (current == null) {
            synchronized (Titles.class) {
                current = adapter;
                if (current == null) {
                    current = select();
                    adapter = current;
                }
            }
        }
        return current;
    }

    private static MessageAdapter select() {
        if (ServerSoftware.current().isPaperBased() && ServerVersion.current().isAtLeast(1, 16, 5)) {
            MessageAdapter adventure = AdventureMessageAdapter.tryCreate();
            if (adventure != null) {
                return adventure;
            }
        }
        if (ServerVersion.current().isAtLeast(ServerVersion.V1_12)) {
            return new SpigotMessageAdapter();
        }
        MessageAdapter legacy = LegacyMessageAdapter.tryCreate();
        if (legacy != null) {
            return legacy;
        }
        Log.warn("Titles and action bars are not supported on this server ({} {})",
                ServerSoftware.current().displayName(), ServerVersion.current());
        return new MessageAdapter() {
            @Override
            public void title(Player player, String title, String subtitle, int fadeIn, int stay, int fadeOut) {
            }

            @Override
            public void clearTitle(Player player) {
            }

            @Override
            public void actionBar(Player player, String message) {
            }

            @Override
            public String name() {
                return "None";
            }
        };
    }

    private static void fail(RuntimeException e) {
        if (!warned) {
            warned = true;
            Log.error("Failed to send a title/action bar using the " + implementationName()
                    + " implementation. Further errors will be suppressed.", e);
        }
    }
}
