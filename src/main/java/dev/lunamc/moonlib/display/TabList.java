package dev.lunamc.moonlib.display;

import dev.lunamc.moonlib.scheduler.TaskHandle;
import dev.lunamc.moonlib.scheduler.Tasks;
import dev.lunamc.moonlib.util.Log;
import dev.lunamc.moonlib.util.Text;
import dev.lunamc.moonlib.version.Reflection;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The player list (tab) header, footer and names.
 *
 * <pre>{@code
 * TabList.set(player, "&d&lMOON PRISON\n&7play.moonprison.net", "&7Online: &f" + Bukkit.getOnlinePlayers().size());
 * TabList.name(player, "&8[&dB&8] &f" + player.getName());
 *
 * // keep it updated for everyone, every 2 seconds:
 * TabList.auto(new TabList.Provider() {
 *     public List<String> header(Player p) { return Arrays.asList("&d&lMOON PRISON", ""); }
 *     public List<String> footer(Player p) { return Arrays.asList("", "&7Rank: &f" + ranks.of(p)); }
 * }, 40);
 * }</pre>
 *
 * <p>Headers and footers need 1.13 or newer (on older servers they are skipped with a one-time
 * warning); names work on every version.</p>
 *
 * @since 1.1.1-alpha
 */
public final class TabList {

    /** Supplies per-player header and footer lines for {@link #auto(Provider, long)}. */
    public interface Provider {
        /**
         * Returns the header lines.
         *
         * @param player the viewer
         * @return the lines, colour codes allowed
         */
        List<String> header(Player player);

        /**
         * Returns the footer lines.
         *
         * @param player the viewer
         * @return the lines, colour codes allowed
         */
        List<String> footer(Player player);
    }

    private static final AtomicBoolean WARNED = new AtomicBoolean();
    private static volatile Method headerFooter;
    private static volatile boolean looked;

    private TabList() {
    }

    /**
     * Sets a player's tab header and footer. Use {@code \n} for several lines.
     *
     * @param player the viewer
     * @param header the header, colour codes allowed ({@code null} for none)
     * @param footer the footer, colour codes allowed ({@code null} for none)
     */
    public static void set(Player player, String header, String footer) {
        Method method = headerFooterMethod();
        if (method == null) {
            if (WARNED.compareAndSet(false, true)) {
                Log.warn("Tab list headers and footers need Minecraft 1.13 or newer; they are skipped on this server");
            }
            return;
        }
        Reflection.invoke(method, player, Text.color(header == null ? "" : header), Text.color(footer == null ? "" : footer));
    }

    /**
     * Sets a player's tab header and footer from lines.
     *
     * @param player the viewer
     * @param header the header lines
     * @param footer the footer lines
     */
    public static void set(Player player, List<String> header, List<String> footer) {
        set(player, String.join("\n", header), String.join("\n", footer));
    }

    /**
     * Clears a player's tab header and footer.
     *
     * @param player the viewer
     */
    public static void clear(Player player) {
        set(player, "", "");
    }

    /**
     * Sets the name shown for a player in everyone's tab list.
     *
     * @param player the player
     * @param name   the name, colour codes allowed, or {@code null} to reset
     */
    public static void name(Player player, String name) {
        Method method = Reflection.method(player.getClass(), "setPlayerListName", String.class);
        if (method != null) {
            Reflection.invoke(method, player, name == null ? null : Text.color(name));
        }
    }

    /**
     * Updates every online player's header and footer on a timer. Stops when the calling plugin
     * shuts down MoonLib, or when the returned handle is cancelled.
     *
     * @param provider    supplies the lines
     * @param periodTicks how often to refresh, in ticks (20 = one second)
     * @return the task
     */
    public static TaskHandle auto(Provider provider, long periodTicks) {
        return Tasks.timer(0, periodTicks, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                Tasks.entity(player, () -> {
                    try {
                        set(player, provider.header(player), provider.footer(player));
                    } catch (RuntimeException e) {
                        Log.error("Tab list provider failed for " + player.getName(), e);
                    }
                });
            }
        });
    }

    private static Method headerFooterMethod() {
        if (!looked) {
            Class<?> type = Reflection.findClass("org.bukkit.entity.Player");
            headerFooter = Reflection.method(type, "setPlayerListHeaderFooter", String.class, String.class);
            looked = true;
        }
        return headerFooter;
    }
}
