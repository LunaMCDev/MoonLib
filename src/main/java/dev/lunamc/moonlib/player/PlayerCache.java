package dev.lunamc.moonlib.player;

import dev.lunamc.moonlib.MoonLib;
import dev.lunamc.moonlib.config.DataStore;
import dev.lunamc.moonlib.scheduler.TaskHandle;
import dev.lunamc.moonlib.scheduler.Tasks;
import dev.lunamc.moonlib.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;

/**
 * Keeps each online player's data in memory: loaded before they join, saved when they leave, and
 * saved periodically and on shutdown. Works with any storage: a {@link DataStore}, a
 * {@link dev.lunamc.moonlib.database.Database}, or your own code.
 *
 * <pre>{@code
 * // JSON file per player:
 * PlayerCache<PlayerData> players = PlayerCache.of(this, DataStore.json(this, "players", PlayerData.class),
 *         PlayerData::new);
 *
 * // or any storage:
 * PlayerCache<Stats> stats = PlayerCache.<Stats>builder(this)
 *         .loader((id, name) -> db.queryOne("SELECT * FROM stats WHERE uuid = ?", Stats::fromRow, id)
 *                 .orElseGet(Stats::new))
 *         .saver((id, value) -> db.update(db.dialect().upsert("stats", "uuid", "kills", "deaths"),
 *                 id, value.kills, value.deaths))
 *         .autosave(5, TimeUnit.MINUTES)
 *         .build();
 *
 * PlayerData data = players.get(player);     // always loaded for online players
 * data.tokens += 10;                          // just change it; saving is automatic
 * }</pre>
 *
 * <h2>Guarantees</h2>
 * <ul>
 *     <li>Data loads during {@link AsyncPlayerPreLoginEvent}, off the server thread, so it is ready
 *     when the player joins. If loading fails the player is turned away with a "please rejoin"
 *     message instead of joining with empty data that would overwrite their real data.</li>
 *     <li>Loads and saves run in order on one background thread, so a save is never overtaken by
 *     an older one.</li>
 *     <li>A player who rejoins before their quit-save finished gets the in-memory data, never the
 *     stale copy on disk.</li>
 *     <li>On shutdown every loaded player is saved before the plugin finishes disabling.</li>
 * </ul>
 *
 * <p>Loaders and savers run on the background thread: they must not touch the world. Values are
 * shared with the server thread, so keep them simple (fields of primitives and strings) or make
 * them thread-safe.</p>
 *
 * @param <T> the data type
 * @since 1.1.1-alpha
 */
public final class PlayerCache<T> implements AutoCloseable {

    /**
     * Loads a player's data.
     *
     * @param <T> the data type
     */
    @FunctionalInterface
    public interface Loader<T> {
        /**
         * Loads, or creates, the data. Runs on a background thread.
         *
         * @param id   the player's UUID
         * @param name the player's name
         * @return the data; {@code null} is treated as a failure
         * @throws Exception if the data cannot be loaded (the player is turned away)
         */
        T load(UUID id, String name) throws Exception;
    }

    /**
     * Saves a player's data.
     *
     * @param <T> the data type
     */
    @FunctionalInterface
    public interface Saver<T> {
        /**
         * Saves the data. Runs on a background thread.
         *
         * @param id    the player's UUID
         * @param value the data
         * @throws Exception if saving fails (logged; the data stays in memory)
         */
        void save(UUID id, T value) throws Exception;
    }

    private final Plugin plugin;
    private final Loader<T> loader;
    private final Saver<T> saver;
    private final String failedLoadMessage;
    private final long unclaimedTimeoutMillis;
    private final Map<UUID, Entry<T>> entries = new ConcurrentHashMap<>();
    private final ExecutorService worker;
    private final Listener listener = new Events();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final TaskHandle autosave;

    private static final class Entry<T> {
        final T value;
        volatile boolean online;
        volatile long loadedAt = System.currentTimeMillis();
        volatile long quitGeneration;

        Entry(T value) {
            this.value = value;
        }
    }

    private PlayerCache(Builder<T> builder) {
        this.plugin = builder.plugin;
        this.loader = Objects.requireNonNull(builder.loader, "loader");
        this.saver = Objects.requireNonNull(builder.saver, "saver");
        this.failedLoadMessage = builder.failedLoadMessage;
        this.unclaimedTimeoutMillis = builder.unclaimedTimeoutMillis;
        String threadName = (plugin == null ? "MoonLib" : plugin.getName()) + "-" + builder.name;
        this.worker = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, threadName);
            thread.setDaemon(true);
            return thread;
        });
        if (builder.registerEvents && plugin != null) {
            Bukkit.getPluginManager().registerEvents(listener, plugin);
            for (Player online : Bukkit.getOnlinePlayers()) {
                load(online.getUniqueId(), online.getName()).thenRun(() -> markOnline(online.getUniqueId()));
            }
        }
        if (builder.autosaveTicks > 0 && plugin != null) {
            this.autosave = MoonLib.callAs(plugin, () -> Tasks.timer(builder.autosaveTicks, builder.autosaveTicks,
                    () -> saveAll()));
        } else {
            this.autosave = null;
        }
        if (plugin != null && MoonLib.isInitialised(plugin)) {
            MoonLib.context(plugin).onShutdown(this);
        }
    }

    /**
     * Creates a cache backed by a {@link DataStore}, with a 5 minute autosave.
     *
     * @param plugin  the owning plugin
     * @param store   the store
     * @param creator makes data for new players
     * @param <T>     the data type
     * @return the cache, already listening for players
     */
    public static <T> PlayerCache<T> of(Plugin plugin, DataStore<T> store, Supplier<T> creator) {
        return PlayerCache.<T>builder(plugin)
                .loader((id, name) -> store.load(id, creator))
                .saver((id, value) -> {
                    if (!store.save(id, value)) {
                        throw new IllegalStateException("DataStore could not save " + id);
                    }
                })
                .build();
    }

    /**
     * Starts a builder.
     *
     * @param plugin the owning plugin
     * @param <T>    the data type
     * @return a builder
     */
    public static <T> Builder<T> builder(Plugin plugin) {
        return new Builder<>(plugin);
    }

    // ------------------------------------------------------------------ access

    /**
     * Returns an online player's data.
     *
     * <p>Data is normally loaded before the player joins. If it is not (the cache was created
     * after they logged in and the load has not finished), this waits for it, logging a warning,
     * rather than returning nothing.</p>
     *
     * @param player the player
     * @return the data
     * @throws IllegalStateException if the data cannot be loaded
     */
    public T get(Player player) {
        return get(player.getUniqueId(), player.getName());
    }

    /**
     * Returns a player's data by UUID, loading it (blocking) if it is not in memory.
     *
     * @param id   the UUID
     * @param name the name, passed to the loader
     * @return the data
     * @throws IllegalStateException if the data cannot be loaded
     */
    public T get(UUID id, String name) {
        Entry<T> entry = entries.get(id);
        if (entry != null) {
            return entry.value;
        }
        log(Level.WARNING, "Data for " + name + " was not loaded in advance; loading it now and waiting", null);
        try {
            return load(id, name).get(30, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            throw new IllegalStateException("Timed out loading data for " + name, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while loading data for " + name, e);
        } catch (java.util.concurrent.ExecutionException e) {
            throw new IllegalStateException("Could not load data for " + name, e.getCause());
        }
    }

    /**
     * Returns a player's data if it is in memory.
     *
     * @param id the UUID
     * @return the data, or empty
     */
    public Optional<T> find(UUID id) {
        Entry<T> entry = entries.get(id);
        return entry == null ? Optional.<T>empty() : Optional.of(entry.value);
    }

    /**
     * Returns whether a player's data is in memory.
     *
     * @param id the UUID
     * @return {@code true} if loaded
     */
    public boolean isLoaded(UUID id) {
        return entries.containsKey(id);
    }

    /**
     * Runs an action with an online player's data on the server thread, once the data is ready.
     *
     * @param player the player
     * @param action the action
     */
    public void whenLoaded(Player player, Consumer<T> action) {
        Entry<T> entry = entries.get(player.getUniqueId());
        if (entry != null) {
            action.accept(entry.value);
            return;
        }
        load(player.getUniqueId(), player.getName()).thenAccept(value ->
                MoonLib.runAs(plugin, () -> Tasks.entity(player, () -> action.accept(value))));
    }

    /**
     * Returns a snapshot of every player in memory.
     *
     * @return UUID to data
     */
    public Map<UUID, T> loaded() {
        Map<UUID, T> out = new HashMap<>();
        for (Map.Entry<UUID, Entry<T>> e : entries.entrySet()) {
            out.put(e.getKey(), e.getValue().value);
        }
        return Collections.unmodifiableMap(out);
    }

    // ------------------------------------------------------------------ load / save

    /**
     * Loads a player's data into memory if it is not already there.
     *
     * @param id   the UUID
     * @param name the name, passed to the loader
     * @return a future completed with the data
     */
    public CompletableFuture<T> load(UUID id, String name) {
        CompletableFuture<T> future = new CompletableFuture<>();
        Entry<T> existing = entries.get(id);
        if (existing != null) {
            future.complete(existing.value);
            return future;
        }
        submit(future, () -> {
            Entry<T> again = entries.get(id);
            if (again != null) {
                future.complete(again.value);
                return;
            }
            T value = loader.load(id, name);
            if (value == null) {
                throw new IllegalStateException("The loader returned null for " + name);
            }
            entries.put(id, new Entry<>(value));
            future.complete(value);
        });
        return future;
    }

    /**
     * Saves one player's data in the background.
     *
     * @param id the UUID
     * @return a future completed when saved (immediately if the player is not loaded)
     */
    public CompletableFuture<Void> save(UUID id) {
        Entry<T> entry = entries.get(id);
        CompletableFuture<Void> future = new CompletableFuture<>();
        if (entry == null) {
            future.complete(null);
            return future;
        }
        submit(future, () -> {
            saver.save(id, entry.value);
            future.complete(null);
        });
        future.whenComplete((ok, error) -> {
            if (error != null) {
                log(Level.SEVERE, "Could not save data for " + id + "; it stays in memory and is retried on the next save",
                        error);
            }
        });
        return future;
    }

    /**
     * Saves every loaded player in the background.
     *
     * @return a future completed when all are saved
     */
    public CompletableFuture<Void> saveAll() {
        List<CompletableFuture<Void>> all = new ArrayList<>();
        for (UUID id : new ArrayList<>(entries.keySet())) {
            all.add(save(id).exceptionally(error -> null));
        }
        return CompletableFuture.allOf(all.toArray(new CompletableFuture<?>[0]));
    }

    /**
     * Saves a player and removes them from memory, unless they have come back online meanwhile.
     *
     * @param id the UUID
     * @return a future completed once saved
     */
    public CompletableFuture<Void> unload(UUID id) {
        Entry<T> entry = entries.get(id);
        if (entry == null) {
            return CompletableFuture.completedFuture(null);
        }
        entry.online = false;
        long generation = ++entry.quitGeneration;
        return save(id).whenComplete((ok, error) -> {
            if (error == null && !entry.online && entry.quitGeneration == generation) {
                entries.remove(id, entry);
            }
        });
    }

    /**
     * Removes a player from memory without saving, for example after deleting their data.
     *
     * @param id the UUID
     */
    public void evict(UUID id) {
        entries.remove(id);
    }

    /**
     * Saves everyone (waiting up to 30 seconds), stops autosaving and stops listening. Called
     * automatically when the plugin shuts down MoonLib.
     */
    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        if (autosave != null) {
            autosave.cancel();
        }
        HandlerList.unregisterAll(listener);
        if (plugin != null && MoonLib.isInitialised(plugin)) {
            MoonLib.context(plugin).cancelShutdown(this);
        }
        try {
            saveAll().get(30, TimeUnit.SECONDS);
        } catch (Exception e) {
            log(Level.SEVERE, "Not every player could be saved while shutting down", e);
        }
        worker.shutdown();
        try {
            worker.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        entries.clear();
    }

    // ------------------------------------------------------------------ events (package-private for tests)

    void handlePreLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            return;
        }
        UUID id = event.getUniqueId();
        Entry<T> rejoining = entries.get(id);
        if (rejoining != null) {
            rejoining.quitGeneration++;      // cancels the pending removal from a recent quit
            rejoining.loadedAt = System.currentTimeMillis();
            return;
        }
        try {
            load(id, event.getName()).get(30, TimeUnit.SECONDS);
            Entry<T> entry = entries.get(id);
            if (entry != null) {
                entry.loadedAt = System.currentTimeMillis();
            }
            scheduleUnclaimedCheck(id);
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log(Level.SEVERE, "Could not load data for " + event.getName() + " (" + id + "); they were asked to rejoin",
                    e instanceof java.util.concurrent.ExecutionException ? e.getCause() : e);
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, Text.color(failedLoadMessage));
        }
    }

    void handleJoin(Player player) {
        Entry<T> entry = entries.get(player.getUniqueId());
        if (entry != null) {
            entry.online = true;
            entry.quitGeneration++;
        } else {
            load(player.getUniqueId(), player.getName()).whenComplete((value, error) -> {
                if (error != null) {
                    log(Level.SEVERE, "Could not load data for " + player.getName(), error);
                } else {
                    markOnline(player.getUniqueId());
                }
            });
        }
    }

    void handleQuit(Player player) {
        unload(player.getUniqueId());
    }

    private void markOnline(UUID id) {
        Entry<T> entry = entries.get(id);
        if (entry != null) {
            entry.online = true;
        }
    }

    /** Drops data loaded for a login that never turned into a join (another plugin kicked them). */
    private void scheduleUnclaimedCheck(UUID id) {
        if (plugin == null || unclaimedTimeoutMillis <= 0) {
            return;
        }
        long ticks = Math.max(1, unclaimedTimeoutMillis / 50);
        MoonLib.runAs(plugin, () -> Tasks.later(ticks, () -> {
            Entry<T> entry = entries.get(id);
            if (entry != null && !entry.online
                    && System.currentTimeMillis() - entry.loadedAt >= unclaimedTimeoutMillis) {
                entries.remove(id, entry);
            }
        }));
    }

    @FunctionalInterface
    private interface Work {
        void run() throws Exception;
    }

    private void submit(CompletableFuture<?> future, Work work) {
        try {
            worker.execute(() -> {
                try {
                    work.run();
                } catch (Throwable t) {
                    future.completeExceptionally(t);
                }
            });
        } catch (java.util.concurrent.RejectedExecutionException e) {
            future.completeExceptionally(new IllegalStateException("The player cache is closed", e));
        }
    }

    private void log(Level level, String message, Throwable error) {
        java.util.logging.Logger logger = plugin != null ? plugin.getLogger() : java.util.logging.Logger.getLogger("MoonLib");
        logger.log(level, message, error);
    }

    private final class Events implements Listener {
        @EventHandler(priority = EventPriority.MONITOR)
        public void onPreLogin(AsyncPlayerPreLoginEvent event) {
            handlePreLogin(event);
        }

        @EventHandler(priority = EventPriority.LOWEST)
        public void onJoin(PlayerJoinEvent event) {
            handleJoin(event.getPlayer());
        }

        @EventHandler(priority = EventPriority.MONITOR)
        public void onQuit(PlayerQuitEvent event) {
            handleQuit(event.getPlayer());
        }
    }

    /**
     * Configures a {@link PlayerCache}.
     *
     * @param <T> the data type
     */
    public static final class Builder<T> {
        private final Plugin plugin;
        private Loader<T> loader;
        private Saver<T> saver;
        private long autosaveTicks = 20L * 60 * 5;
        private String failedLoadMessage = "&cYour data could not be loaded. Please rejoin in a moment.";
        private long unclaimedTimeoutMillis = 60_000;
        private boolean registerEvents = true;
        private String name = "players";

        private Builder(Plugin plugin) {
            this.plugin = plugin;
        }

        /**
         * Sets how data is loaded (required).
         *
         * @param value the loader
         * @return this builder
         */
        public Builder<T> loader(Loader<T> value) {
            this.loader = value;
            return this;
        }

        /**
         * Sets how data is saved (required).
         *
         * @param value the saver
         * @return this builder
         */
        public Builder<T> saver(Saver<T> value) {
            this.saver = value;
            return this;
        }

        /**
         * Sets the autosave interval. Default 5 minutes; {@code 0} disables autosaving (data is
         * still saved on quit and shutdown).
         *
         * @param interval the interval
         * @param unit     its unit
         * @return this builder
         */
        public Builder<T> autosave(long interval, TimeUnit unit) {
            this.autosaveTicks = unit.toMillis(interval) / 50;
            return this;
        }

        /**
         * Sets the kick message shown when a player's data cannot be loaded.
         *
         * @param message the message, colour codes allowed
         * @return this builder
         */
        public Builder<T> failedLoadMessage(String message) {
            this.failedLoadMessage = message;
            return this;
        }

        /**
         * Names the cache in thread names, for plugins with several.
         *
         * @param value the name
         * @return this builder
         */
        public Builder<T> name(String value) {
            this.name = value;
            return this;
        }

        /**
         * Turns automatic loading on login and saving on quit off, for caches you drive yourself
         * with {@link PlayerCache#load} and {@link PlayerCache#unload}.
         *
         * @return this builder
         */
        public Builder<T> manual() {
            this.registerEvents = false;
            return this;
        }

        /**
         * Creates the cache and, unless {@link #manual()}, starts listening and loads players who
         * are already online.
         *
         * @return the cache
         */
        public PlayerCache<T> build() {
            return new PlayerCache<>(this);
        }
    }
}
