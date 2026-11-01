package dev.lunamc.moonlib.scheduler;

import dev.lunamc.moonlib.MoonLib;
import dev.lunamc.moonlib.util.Log;
import dev.lunamc.moonlib.version.ServerSoftware;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * One scheduler API for Bukkit, Spigot, Paper <b>and Folia</b>.
 *
 * <p>All delays and periods are in server ticks (20 ticks = 1 second). Every method returns a
 * {@link TaskHandle} that can be cancelled.</p>
 *
 * <pre>{@code
 * Tasks.sync(() -> player.teleport(spawn));                 // next tick on the main thread
 * Tasks.async(() -> database.save(data));                    // off the main thread
 * Tasks.later(40, () -> player.sendMessage("2 seconds later"));
 * TaskHandle timer = Tasks.timer(0, 20, () -> tickMines());  // every second
 *
 * // Load from the database, then continue on the main thread with the result:
 * Tasks.supplyAsync(() -> database.load(uuid))
 *      .thenAccept(data -> Tasks.entity(player, () -> apply(player, data)));
 * }</pre>
 *
 * <p>Tasks belong to the plugin that scheduled them (see {@link MoonLib#plugin()}), so when several
 * plugins share MoonLib each plugin's tasks are cancelled only when that plugin disables.</p>
 *
 * <h2>Folia</h2>
 * <p>On Folia there is no single main thread. {@link #sync(Runnable)} and {@link #timer(long, long, Runnable)}
 * run on the global region (fine for world-independent logic), while {@link #entity(Entity, Runnable)}
 * and {@link #at(Location, Runnable)} run on the thread that owns that entity or location. Use
 * those for anything that touches a player, entity or block, and your plugin works on both Paper
 * and Folia unchanged. On non-Folia servers they simply run on the main thread.</p>
 */
public final class Tasks {

    private static final Map<Plugin, SchedulerAdapter> ADAPTERS = new ConcurrentHashMap<>();

    private Tasks() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Prepares the scheduler for a plugin. Called by {@link MoonLib#init(Plugin)}.
     *
     * @param plugin the owning plugin
     */
    public static void init(Plugin plugin) {
        ADAPTERS.computeIfAbsent(plugin, Tasks::createAdapter);
    }

    /**
     * Cancels every task owned by a plugin. Tasks of other plugins sharing MoonLib keep running.
     * Called by {@link MoonLib#shutdown(Plugin)}.
     *
     * @param plugin the plugin
     */
    public static void shutdown(Plugin plugin) {
        SchedulerAdapter removed = ADAPTERS.remove(plugin);
        if (removed != null) {
            removed.cancelAll();
        }
    }

    /**
     * Runs a task on the next tick on the main thread (global region on Folia).
     *
     * @param task the task
     * @return the task handle
     */
    public static TaskHandle sync(Runnable task) {
        return adapter().runGlobal(task, 0, 0);
    }

    /**
     * Runs a task immediately if already on the main thread, otherwise schedules it for the next tick.
     *
     * @param task the task
     * @return the task handle, or {@link TaskHandle#NONE} if it ran immediately
     */
    public static TaskHandle ensureSync(Runnable task) {
        if (!ServerSoftware.isFolia() && Bukkit.isPrimaryThread()) {
            task.run();
            return TaskHandle.NONE;
        }
        return sync(task);
    }

    /**
     * Runs a task after a delay on the main thread (global region on Folia).
     *
     * @param delayTicks the delay in ticks
     * @param task       the task
     * @return the task handle
     */
    public static TaskHandle later(long delayTicks, Runnable task) {
        return adapter().runGlobal(task, delayTicks, 0);
    }

    /**
     * Runs a task repeatedly on the main thread (global region on Folia).
     *
     * @param delayTicks  ticks before the first run
     * @param periodTicks ticks between runs, must be at least 1
     * @param task        the task
     * @return the task handle
     */
    public static TaskHandle timer(long delayTicks, long periodTicks, Runnable task) {
        return adapter().runGlobal(task, delayTicks, requirePeriod(periodTicks));
    }

    /**
     * Runs a task asynchronously. Never touch the Bukkit world from an async task.
     *
     * @param task the task
     * @return the task handle
     */
    public static TaskHandle async(Runnable task) {
        return adapter().runAsync(task, 0, 0);
    }

    /**
     * Runs a task asynchronously after a delay.
     *
     * @param delayTicks the delay in ticks
     * @param task       the task
     * @return the task handle
     */
    public static TaskHandle asyncLater(long delayTicks, Runnable task) {
        return adapter().runAsync(task, delayTicks, 0);
    }

    /**
     * Runs a task asynchronously and repeatedly.
     *
     * @param delayTicks  ticks before the first run
     * @param periodTicks ticks between runs, must be at least 1
     * @param task        the task
     * @return the task handle
     */
    public static TaskHandle asyncTimer(long delayTicks, long periodTicks, Runnable task) {
        return adapter().runAsync(task, delayTicks, requirePeriod(periodTicks));
    }

    /**
     * Runs a task on the thread that owns an entity (the main thread outside Folia).
     *
     * @param entity the entity, usually a player
     * @param task   the task
     * @return the task handle
     */
    public static TaskHandle entity(Entity entity, Runnable task) {
        return adapter().runAtEntity(entity, task, 0, 0);
    }

    /**
     * Runs a task on the entity's thread after a delay.
     *
     * @param entity     the entity
     * @param delayTicks the delay in ticks
     * @param task       the task
     * @return the task handle
     */
    public static TaskHandle entityLater(Entity entity, long delayTicks, Runnable task) {
        return adapter().runAtEntity(entity, task, delayTicks, 0);
    }

    /**
     * Runs a task repeatedly on the entity's thread. On Folia the task stops automatically when the
     * entity is removed.
     *
     * @param entity      the entity
     * @param delayTicks  ticks before the first run
     * @param periodTicks ticks between runs, must be at least 1
     * @param task        the task
     * @return the task handle
     */
    public static TaskHandle entityTimer(Entity entity, long delayTicks, long periodTicks, Runnable task) {
        return adapter().runAtEntity(entity, task, delayTicks, requirePeriod(periodTicks));
    }

    /**
     * Runs a task on the thread that owns a location (the main thread outside Folia).
     *
     * @param location the location
     * @param task     the task
     * @return the task handle
     */
    public static TaskHandle at(Location location, Runnable task) {
        return adapter().runAtLocation(location, task, 0, 0);
    }

    /**
     * Runs a task on the location's thread after a delay.
     *
     * @param location   the location
     * @param delayTicks the delay in ticks
     * @param task       the task
     * @return the task handle
     */
    public static TaskHandle atLater(Location location, long delayTicks, Runnable task) {
        return adapter().runAtLocation(location, task, delayTicks, 0);
    }

    /**
     * Runs a task repeatedly on the location's thread.
     *
     * @param location    the location
     * @param delayTicks  ticks before the first run
     * @param periodTicks ticks between runs, must be at least 1
     * @param task        the task
     * @return the task handle
     */
    public static TaskHandle atTimer(Location location, long delayTicks, long periodTicks, Runnable task) {
        return adapter().runAtLocation(location, task, delayTicks, requirePeriod(periodTicks));
    }

    /**
     * Computes a value asynchronously. Exceptions complete the future exceptionally and are logged.
     *
     * @param supplier the computation
     * @param <T>      the result type
     * @return a future completed off the main thread
     */
    public static <T> CompletableFuture<T> supplyAsync(Supplier<T> supplier) {
        CompletableFuture<T> future = new CompletableFuture<>();
        async(() -> complete(future, supplier));
        return future;
    }

    /**
     * Computes a value on the main thread (global region on Folia) and returns a future for it. Useful
     * from async code that needs to read the world.
     *
     * @param supplier the computation
     * @param <T>      the result type
     * @return a future completed on the main thread
     */
    public static <T> CompletableFuture<T> supplySync(Supplier<T> supplier) {
        CompletableFuture<T> future = new CompletableFuture<>();
        sync(() -> complete(future, supplier));
        return future;
    }

    /**
     * Returns whether the current thread may safely modify the given entity: the main thread
     * normally, or the owning region thread on Folia.
     *
     * @param entity the entity
     * @return {@code true} if it is safe to touch the entity right now
     */
    public static boolean isOwnedByCurrentThread(Entity entity) {
        return adapter().isOwnedByCurrentThread(entity);
    }

    /**
     * Returns whether the server is running Folia.
     *
     * @return {@code true} on Folia
     */
    public static boolean isFolia() {
        return ServerSoftware.isFolia();
    }

    private static <T> void complete(CompletableFuture<T> future, Supplier<T> supplier) {
        try {
            future.complete(supplier.get());
        } catch (Throwable t) {
            Log.error("Exception in scheduled computation", t);
            future.completeExceptionally(t);
        }
    }

    private static long requirePeriod(long periodTicks) {
        if (periodTicks < 1) {
            throw new IllegalArgumentException("Period must be at least 1 tick, got " + periodTicks);
        }
        return periodTicks;
    }

    /** The scheduler of the plugin currently using MoonLib, so every task belongs to its creator. */
    private static SchedulerAdapter adapter() {
        return ADAPTERS.computeIfAbsent(MoonLib.plugin(), Tasks::createAdapter);
    }

    private static SchedulerAdapter createAdapter(Plugin plugin) {
        if (ServerSoftware.isFolia()) {
            try {
                return new FoliaSchedulerAdapter(plugin);
            } catch (ReflectiveOperationException | RuntimeException e) {
                Log.error("Folia detected but its scheduler could not be accessed; falling back to Bukkit", e);
            }
        }
        return new BukkitSchedulerAdapter(plugin);
    }
}
