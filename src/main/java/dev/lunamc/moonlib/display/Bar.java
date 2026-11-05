package dev.lunamc.moonlib.display;

import dev.lunamc.moonlib.MoonLib;
import dev.lunamc.moonlib.scheduler.TaskHandle;
import dev.lunamc.moonlib.scheduler.Tasks;
import dev.lunamc.moonlib.util.Log;
import dev.lunamc.moonlib.util.Text;
import dev.lunamc.moonlib.version.ServerVersion;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

/**
 * A boss bar at the top of the screen, for progress, countdowns and announcements.
 *
 * <pre>{@code
 * Bar reset = Bar.create("&dMine A resets in &f60s", Bar.Color.PURPLE, Bar.Style.SEGMENTED_10);
 * reset.show(player);
 * reset.progress(0.5).title("&dMine A resets in &f30s");
 *
 * // a countdown that updates itself and disappears at the end:
 * Bar.countdown(player, 30, seconds -> "&eEvent starts in &f" + seconds + "s", Bar.Color.YELLOW);
 * }</pre>
 *
 * <p>Titles accept {@code &} colour codes and hex colours. Bars are removed for everyone when the
 * plugin that created them shuts down MoonLib. Boss bars need 1.9 or newer; on 1.8 every method is
 * a harmless no-op (a warning is logged once).</p>
 *
 * @since 1.1.1-alpha
 */
public final class Bar implements AutoCloseable {

    /** Bar colours. */
    public enum Color {
        /** Pink. */
        PINK,
        /** Blue. */
        BLUE,
        /** Red. */
        RED,
        /** Green. */
        GREEN,
        /** Yellow. */
        YELLOW,
        /** Purple. */
        PURPLE,
        /** White. */
        WHITE
    }

    /** Bar styles: solid, or split into notches. */
    public enum Style {
        /** One continuous bar. */
        SOLID,
        /** 6 notches. */
        SEGMENTED_6,
        /** 10 notches. */
        SEGMENTED_10,
        /** 12 notches. */
        SEGMENTED_12,
        /** 20 notches. */
        SEGMENTED_20
    }

    /** What a bar implementation must do. Implemented per server version. */
    interface Backend {
        void title(String title);

        void progress(double progress);

        void color(Color color);

        void style(Style style);

        void show(Player player);

        void hide(Player player);

        void hideAll();
    }

    private static final AtomicBoolean WARNED = new AtomicBoolean();

    private final Backend backend;
    private final Set<UUID> viewers = Collections.synchronizedSet(new LinkedHashSet<UUID>());
    private volatile String title;
    private volatile double progress = 1.0;

    private Bar(Backend backend, String title) {
        this.backend = backend;
        this.title = title;
    }

    /**
     * Creates a bar, initially shown to nobody, owned by the calling plugin.
     *
     * @param title the title
     * @param color the colour
     * @param style the style
     * @return the bar
     */
    public static Bar create(String title, Color color, Style style) {
        return create(MoonLib.pluginOrNull(), title, color, style);
    }

    /**
     * Creates a bar owned by a plugin.
     *
     * @param plugin the owner, or {@code null} to manage it yourself
     * @param title  the title
     * @param color  the colour
     * @param style  the style
     * @return the bar
     */
    public static Bar create(Plugin plugin, String title, Color color, Style style) {
        Backend backend;
        if (ServerVersion.current().isAtLeast(ServerVersion.V1_9)) {
            backend = new ModernBossBar(Text.color(title), color, style);
        } else {
            if (WARNED.compareAndSet(false, true)) {
                Log.warn("Boss bars need Minecraft 1.9 or newer; they are hidden on this server");
            }
            backend = NoBackend.INSTANCE;
        }
        Bar bar = new Bar(backend, title);
        if (plugin != null && MoonLib.isInitialised(plugin)) {
            MoonLib.context(plugin).onShutdown(bar);
        }
        return bar;
    }

    /**
     * Shows a self-updating countdown to one player, removed when it reaches zero or the player
     * leaves.
     *
     * @param player  the player
     * @param seconds the length
     * @param title   builds the title from the seconds left
     * @param color   the colour
     * @return the bar, already shown (close it to stop early)
     */
    public static Bar countdown(Player player, int seconds, Function<Integer, String> title, Color color) {
        Bar bar = create(title.apply(seconds), color, Style.SOLID);
        bar.show(player);
        int[] left = {seconds};
        TaskHandle[] task = new TaskHandle[1];
        task[0] = Tasks.entityTimer(player, 20, 20, () -> {
            left[0]--;
            if (left[0] <= 0 || !player.isOnline()) {
                bar.close();
                task[0].cancel();
                return;
            }
            bar.title(title.apply(left[0])).progress((double) left[0] / seconds);
        });
        return bar;
    }

    /**
     * Changes the title.
     *
     * @param value the title, colour codes allowed
     * @return this bar
     */
    public Bar title(String value) {
        this.title = value;
        backend.title(Text.color(value));
        return this;
    }

    /**
     * Sets how full the bar is.
     *
     * @param value from 0 to 1; values outside are clamped
     * @return this bar
     */
    public Bar progress(double value) {
        this.progress = Math.max(0, Math.min(1, value));
        backend.progress(progress);
        return this;
    }

    /**
     * Changes the colour.
     *
     * @param value the colour
     * @return this bar
     */
    public Bar color(Color value) {
        backend.color(value);
        return this;
    }

    /**
     * Changes the style.
     *
     * @param value the style
     * @return this bar
     */
    public Bar style(Style value) {
        backend.style(value);
        return this;
    }

    /**
     * Shows the bar to a player.
     *
     * @param player the player
     * @return this bar
     */
    public Bar show(Player player) {
        viewers.add(player.getUniqueId());
        backend.show(player);
        return this;
    }

    /**
     * Shows the bar to several players.
     *
     * @param players the players
     * @return this bar
     */
    public Bar show(Collection<? extends Player> players) {
        for (Player player : players) {
            show(player);
        }
        return this;
    }

    /**
     * Hides the bar from a player.
     *
     * @param player the player
     * @return this bar
     */
    public Bar hide(Player player) {
        viewers.remove(player.getUniqueId());
        backend.hide(player);
        return this;
    }

    /**
     * Returns whether a player currently sees the bar.
     *
     * @param player the player
     * @return {@code true} if shown
     */
    public boolean isShownTo(Player player) {
        return viewers.contains(player.getUniqueId());
    }

    /**
     * Returns the current title, before colouring.
     *
     * @return the title
     */
    public String title() {
        return title;
    }

    /**
     * Returns the current progress.
     *
     * @return from 0 to 1
     */
    public double progress() {
        return progress;
    }

    /** Hides the bar from everyone. */
    @Override
    public void close() {
        viewers.clear();
        backend.hideAll();
    }

    private enum NoBackend implements Backend {
        INSTANCE;

        @Override
        public void title(String title) {
        }

        @Override
        public void progress(double progress) {
        }

        @Override
        public void color(Color color) {
        }

        @Override
        public void style(Style style) {
        }

        @Override
        public void show(Player player) {
        }

        @Override
        public void hide(Player player) {
        }

        @Override
        public void hideAll() {
        }
    }
}
