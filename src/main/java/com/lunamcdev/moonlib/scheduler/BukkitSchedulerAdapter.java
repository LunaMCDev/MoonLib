package com.lunamcdev.moonlib.scheduler;

import com.lunamcdev.moonlib.MoonLib;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;

/**
 * Internal API. Classic single-threaded Bukkit scheduler. Entity and location tasks simply run on the
 * main thread.
 */
final class BukkitSchedulerAdapter implements SchedulerAdapter {

    private final Plugin plugin;

    BukkitSchedulerAdapter(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public TaskHandle runGlobal(Runnable original, long delayTicks, long periodTicks) {
        Runnable task = bind(original);
        BukkitScheduler scheduler = Bukkit.getScheduler();
        BukkitTask bukkitTask;
        if (periodTicks > 0) {
            bukkitTask = scheduler.runTaskTimer(plugin, task, Math.max(0, delayTicks), periodTicks);
        } else if (delayTicks > 0) {
            bukkitTask = scheduler.runTaskLater(plugin, task, delayTicks);
        } else {
            bukkitTask = scheduler.runTask(plugin, task);
        }
        return new BukkitHandle(bukkitTask);
    }

    @Override
    public TaskHandle runAsync(Runnable original, long delayTicks, long periodTicks) {
        Runnable task = bind(original);
        BukkitScheduler scheduler = Bukkit.getScheduler();
        BukkitTask bukkitTask;
        if (periodTicks > 0) {
            bukkitTask = scheduler.runTaskTimerAsynchronously(plugin, task, Math.max(0, delayTicks), periodTicks);
        } else if (delayTicks > 0) {
            bukkitTask = scheduler.runTaskLaterAsynchronously(plugin, task, delayTicks);
        } else {
            bukkitTask = scheduler.runTaskAsynchronously(plugin, task);
        }
        return new BukkitHandle(bukkitTask);
    }

    @Override
    public TaskHandle runAtEntity(Entity entity, Runnable task, long delayTicks, long periodTicks) {
        return runGlobal(task, delayTicks, periodTicks);
    }

    @Override
    public TaskHandle runAtLocation(Location location, Runnable task, long delayTicks, long periodTicks) {
        return runGlobal(task, delayTicks, periodTicks);
    }

    @Override
    public boolean isOwnedByCurrentThread(Entity entity) {
        return Bukkit.isPrimaryThread();
    }

    /** Runs the task as this adapter's plugin, so anything it creates belongs to that plugin. */
    private Runnable bind(Runnable task) {
        return () -> MoonLib.runAs(plugin, task);
    }

    @Override
    public void cancelAll() {
        Bukkit.getScheduler().cancelTasks(plugin);
    }

    private static final class BukkitHandle implements TaskHandle {
        private final BukkitTask task;
        private volatile boolean cancelled;

        BukkitHandle(BukkitTask task) {
            this.task = task;
        }

        @Override
        public void cancel() {
            if (!cancelled) {
                cancelled = true;
                task.cancel();
            }
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }
    }
}
