package com.lunamcdev.moonlib.scoreboard;

import com.lunamcdev.moonlib.MoonLib;
import com.lunamcdev.moonlib.hook.Placeholders;
import com.lunamcdev.moonlib.scheduler.TaskHandle;
import com.lunamcdev.moonlib.scheduler.Tasks;
import com.lunamcdev.moonlib.util.Log;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Gives every online player a sidebar driven by a {@link BoardProvider}, updated on a timer.
 *
 * <pre>{@code
 * private BoardManager boards;
 *
 * @Override
 * protected void enable() {
 *     boards = new BoardManager(new PrisonBoard(), 20);   // update every second
 *     boards.usePlaceholderApi(true);                     // optional, if PlaceholderAPI is installed
 *     boards.start();
 * }
 *
 * // a player toggles their board off
 * boards.setHidden(player, true);
 * }</pre>
 *
 * <p>Boards are created on join and removed on quit automatically. A manager belongs to the plugin that
 * created it and is stopped when that plugin shuts MoonLib down, so you don't need to call
 * {@link #stop()} in {@code onDisable}. Only one plugin should show a sidebar to a given player at a
 * time; if two do, the most recent one is visible.</p>
 *
 * <p><b>Folia:</b> Folia does not support the Bukkit scoreboard API, so the manager logs a warning and
 * does nothing there.</p>
 */
public final class BoardManager implements Listener {

    private static final Set<BoardManager> ACTIVE = new CopyOnWriteArraySet<>();

    private final BoardProvider provider;
    private final long updateTicks;
    private final Plugin owner;
    private final Map<UUID, Board> boards = new ConcurrentHashMap<>();
    private final Set<UUID> hidden = ConcurrentHashMap.newKeySet();
    private boolean placeholderApi;
    private TaskHandle task = TaskHandle.NONE;
    private boolean running;

    /**
     * Creates a manager. Call {@link #start()} to begin.
     *
     * @param provider    supplies titles and lines
     * @param updateTicks how often to refresh every board, in ticks (20 = one second)
     */
    public BoardManager(BoardProvider provider, long updateTicks) {
        if (updateTicks < 1) {
            throw new IllegalArgumentException("updateTicks must be at least 1");
        }
        this.provider = provider;
        this.updateTicks = updateTicks;
        this.owner = MoonLib.plugin();
    }

    /**
     * Stops every running manager created by a plugin. Managers of other plugins sharing MoonLib keep
     * running. Called by {@link MoonLib#shutdown(Plugin)}.
     *
     * @param plugin the plugin
     */
    public static void stopAll(Plugin plugin) {
        for (BoardManager manager : ACTIVE) {
            if (manager.owner == plugin) {
                manager.stop();
            }
        }
    }

    /**
     * Returns the plugin that created this manager.
     *
     * @return the owning plugin
     */
    public Plugin owner() {
        return owner;
    }

    /**
     * Runs every line and the title through PlaceholderAPI if it is installed.
     *
     * @param enabled {@code true} to apply PlaceholderAPI placeholders
     * @return this manager
     */
    public BoardManager usePlaceholderApi(boolean enabled) {
        this.placeholderApi = enabled;
        return this;
    }

    /**
     * Starts the manager: creates boards for online players, listens for joins and quits, and starts
     * the update timer.
     *
     * @return this manager
     */
    public synchronized BoardManager start() {
        if (running) {
            return this;
        }
        if (Tasks.isFolia()) {
            Log.warn("Scoreboards are not supported on Folia; BoardManager will not start.");
            return this;
        }
        running = true;
        ACTIVE.add(this);
        Bukkit.getPluginManager().registerEvents(this, owner);
        MoonLib.runAs(owner, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                create(player);
            }
            task = Tasks.timer(updateTicks, updateTicks, this::updateAll);
        });
        return this;
    }

    /** Stops the timer and removes every board. */
    public synchronized void stop() {
        if (!running) {
            return;
        }
        running = false;
        ACTIVE.remove(this);
        task.cancel();
        HandlerList.unregisterAll(this);
        for (Board board : boards.values()) {
            board.delete();
        }
        boards.clear();
    }

    /** Refreshes every board immediately. */
    public void updateAll() {
        for (Board board : boards.values()) {
            update(board);
        }
    }

    /**
     * Refreshes one player's board immediately, e.g. right after their balance changed.
     *
     * @param player the player
     */
    public void update(Player player) {
        Board board = boards.get(player.getUniqueId());
        if (board != null) {
            update(board);
        }
    }

    /**
     * Hides or shows a player's board.
     *
     * @param player the player
     * @param hide   {@code true} to hide
     */
    public void setHidden(Player player, boolean hide) {
        if (hide) {
            hidden.add(player.getUniqueId());
            remove(player);
        } else {
            hidden.remove(player.getUniqueId());
            if (running && player.isOnline()) {
                create(player);
            }
        }
    }

    /**
     * Returns whether a player has hidden their board.
     *
     * @param player the player
     * @return {@code true} if hidden
     */
    public boolean isHidden(Player player) {
        return hidden.contains(player.getUniqueId());
    }

    /**
     * Returns a player's board.
     *
     * @param player the player
     * @return the board, or empty if they have none
     */
    public Optional<Board> board(Player player) {
        return Optional.ofNullable(boards.get(player.getUniqueId()));
    }

    /**
     * Returns every active board.
     *
     * @return an unmodifiable snapshot
     */
    public List<Board> boards() {
        return Collections.unmodifiableList(new ArrayList<>(boards.values()));
    }

    /**
     * Returns whether the manager is running.
     *
     * @return {@code true} after {@link #start()} and before {@link #stop()}
     */
    public boolean isRunning() {
        return running;
    }

    /**
     * Creates the board when a player joins.
     *
     * @param event the event
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        // One tick later so other plugins that set scoreboards on join don't overwrite ours.
        MoonLib.runAs(owner, () -> Tasks.entityLater(player, 1, () -> {
            if (running && player.isOnline()) {
                create(player);
            }
        }));
    }

    /**
     * Removes the board when a player quits.
     *
     * @param event the event
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Board board = boards.remove(event.getPlayer().getUniqueId());
        if (board != null) {
            board.delete();
        }
    }

    private void create(Player player) {
        if (hidden.contains(player.getUniqueId()) || boards.containsKey(player.getUniqueId())) {
            return;
        }
        Board board = Board.create(player);
        boards.put(player.getUniqueId(), board);
        update(board);
    }

    private void remove(Player player) {
        Board board = boards.remove(player.getUniqueId());
        if (board != null) {
            board.delete();
        }
    }

    private void update(Board board) {
        Player player = board.player();
        if (!player.isOnline() || board.isDeleted()) {
            boards.remove(player.getUniqueId());
            return;
        }
        try {
            List<String> lines = provider.lines(player);
            if (lines == null) {
                board.lines(Collections.<String>emptyList());
                return;
            }
            String title = provider.title(player);
            if (placeholderApi) {
                title = Placeholders.apply(player, title);
                lines = Placeholders.apply(player, lines);
            }
            board.title(title);
            board.lines(lines);
        } catch (RuntimeException e) {
            Log.error("BoardProvider threw while updating " + player.getName() + "'s scoreboard", e);
        }
    }
}
