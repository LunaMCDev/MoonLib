package dev.lunamc.moonlib.scheduler;

import dev.lunamc.moonlib.MoonLib;
import dev.lunamc.moonlib.version.Reflection;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Internal API. Folia's regionised schedulers, accessed through reflection so MoonLib compiles against
 * the plain Spigot API.
 */
final class FoliaSchedulerAdapter implements SchedulerAdapter {

    private final Plugin plugin;
    private final Object globalScheduler;
    private final Object asyncScheduler;
    private final Object regionScheduler;

    private final Method globalRun;
    private final Method globalDelayed;
    private final Method globalTimer;
    private final Method globalCancel;
    private final Method asyncNow;
    private final Method asyncDelayed;
    private final Method asyncTimer;
    private final Method asyncCancel;
    private final Method regionRun;
    private final Method regionDelayed;
    private final Method regionTimer;
    private final Method entityGetScheduler;
    private final Method ownedByRegion;
    private final Method taskCancel;

    private volatile Method entityRun;
    private volatile Method entityDelayed;
    private volatile Method entityTimer;

    FoliaSchedulerAdapter(Plugin plugin) throws ReflectiveOperationException {
        this.plugin = plugin;
        Server server = Bukkit.getServer();
        this.globalScheduler = Server.class.getMethod("getGlobalRegionScheduler").invoke(server);
        this.asyncScheduler = Server.class.getMethod("getAsyncScheduler").invoke(server);
        this.regionScheduler = Server.class.getMethod("getRegionScheduler").invoke(server);

        Class<?> global = Class.forName("io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler");
        Class<?> async = Class.forName("io.papermc.paper.threadedregions.scheduler.AsyncScheduler");
        Class<?> region = Class.forName("io.papermc.paper.threadedregions.scheduler.RegionScheduler");
        Class<?> task = Class.forName("io.papermc.paper.threadedregions.scheduler.ScheduledTask");

        this.globalRun = global.getMethod("run", Plugin.class, Consumer.class);
        this.globalDelayed = global.getMethod("runDelayed", Plugin.class, Consumer.class, long.class);
        this.globalTimer = global.getMethod("runAtFixedRate", Plugin.class, Consumer.class, long.class, long.class);
        this.globalCancel = global.getMethod("cancelTasks", Plugin.class);
        this.asyncNow = async.getMethod("runNow", Plugin.class, Consumer.class);
        this.asyncDelayed = async.getMethod("runDelayed", Plugin.class, Consumer.class, long.class, TimeUnit.class);
        this.asyncTimer = async.getMethod("runAtFixedRate", Plugin.class, Consumer.class, long.class, long.class,
                TimeUnit.class);
        this.asyncCancel = async.getMethod("cancelTasks", Plugin.class);
        this.regionRun = region.getMethod("run", Plugin.class, Location.class, Consumer.class);
        this.regionDelayed = region.getMethod("runDelayed", Plugin.class, Location.class, Consumer.class, long.class);
        this.regionTimer = region.getMethod("runAtFixedRate", Plugin.class, Location.class, Consumer.class,
                long.class, long.class);
        this.entityGetScheduler = Entity.class.getMethod("getScheduler");
        this.ownedByRegion = Server.class.getMethod("isOwnedByCurrentRegion", Entity.class);
        this.taskCancel = task.getMethod("cancel");
    }

    @Override
    public TaskHandle runGlobal(Runnable task, long delayTicks, long periodTicks) {
        FoliaHandle handle = new FoliaHandle();
        Consumer<Object> consumer = wrap(task, handle);
        if (periodTicks > 0) {
            handle.task = Reflection.invoke(globalTimer, globalScheduler, plugin, consumer, Math.max(1, delayTicks),
                    periodTicks);
        } else if (delayTicks > 0) {
            handle.task = Reflection.invoke(globalDelayed, globalScheduler, plugin, consumer, delayTicks);
        } else {
            handle.task = Reflection.invoke(globalRun, globalScheduler, plugin, consumer);
        }
        return handle;
    }

    @Override
    public TaskHandle runAsync(Runnable task, long delayTicks, long periodTicks) {
        FoliaHandle handle = new FoliaHandle();
        Consumer<Object> consumer = wrap(task, handle);
        if (periodTicks > 0) {
            handle.task = Reflection.invoke(asyncTimer, asyncScheduler, plugin, consumer,
                    Math.max(1, delayTicks * 50L), periodTicks * 50L, TimeUnit.MILLISECONDS);
        } else if (delayTicks > 0) {
            handle.task = Reflection.invoke(asyncDelayed, asyncScheduler, plugin, consumer, delayTicks * 50L,
                    TimeUnit.MILLISECONDS);
        } else {
            handle.task = Reflection.invoke(asyncNow, asyncScheduler, plugin, consumer);
        }
        return handle;
    }

    @Override
    public TaskHandle runAtEntity(Entity entity, Runnable task, long delayTicks, long periodTicks) {
        Object scheduler = Reflection.invoke(entityGetScheduler, entity);
        resolveEntityMethods(scheduler.getClass());
        FoliaHandle handle = new FoliaHandle();
        Consumer<Object> consumer = wrap(task, handle);
        Runnable retired = () -> handle.cancelled = true;
        if (periodTicks > 0) {
            handle.task = Reflection.invoke(entityTimer, scheduler, plugin, consumer, retired, Math.max(1, delayTicks),
                    periodTicks);
        } else if (delayTicks > 0) {
            handle.task = Reflection.invoke(entityDelayed, scheduler, plugin, consumer, retired, delayTicks);
        } else {
            handle.task = Reflection.invoke(entityRun, scheduler, plugin, consumer, retired);
        }
        if (handle.task == null) {
            // The entity was removed before the task could be scheduled.
            handle.cancelled = true;
        }
        return handle;
    }

    @Override
    public TaskHandle runAtLocation(Location location, Runnable task, long delayTicks, long periodTicks) {
        FoliaHandle handle = new FoliaHandle();
        Consumer<Object> consumer = wrap(task, handle);
        if (periodTicks > 0) {
            handle.task = Reflection.invoke(regionTimer, regionScheduler, plugin, location, consumer,
                    Math.max(1, delayTicks), periodTicks);
        } else if (delayTicks > 0) {
            handle.task = Reflection.invoke(regionDelayed, regionScheduler, plugin, location, consumer, delayTicks);
        } else {
            handle.task = Reflection.invoke(regionRun, regionScheduler, plugin, location, consumer);
        }
        return handle;
    }

    @Override
    public boolean isOwnedByCurrentThread(Entity entity) {
        return (Boolean) Reflection.invoke(ownedByRegion, Bukkit.getServer(), entity);
    }

    @Override
    public void cancelAll() {
        Reflection.invoke(globalCancel, globalScheduler, plugin);
        Reflection.invoke(asyncCancel, asyncScheduler, plugin);
    }

    private void resolveEntityMethods(Class<?> schedulerImpl) {
        if (entityRun != null) {
            return;
        }
        try {
            Class<?> api = Class.forName("io.papermc.paper.threadedregions.scheduler.EntityScheduler");
            entityDelayed = api.getMethod("runDelayed", Plugin.class, Consumer.class, Runnable.class, long.class);
            entityTimer = api.getMethod("runAtFixedRate", Plugin.class, Consumer.class, Runnable.class, long.class,
                    long.class);
            entityRun = api.getMethod("run", Plugin.class, Consumer.class, Runnable.class);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unsupported Folia EntityScheduler: " + schedulerImpl.getName(), e);
        }
    }

    private Consumer<Object> wrap(Runnable task, FoliaHandle handle) {
        return scheduledTask -> {
            if (handle.task == null) {
                handle.task = scheduledTask;
            }
            if (!handle.cancelled) {
                MoonLib.runAs(plugin, task);
            }
        };
    }

    private final class FoliaHandle implements TaskHandle {
        private volatile Object task;
        private volatile boolean cancelled;

        @Override
        public void cancel() {
            cancelled = true;
            Object current = task;
            if (current != null) {
                Reflection.invoke(taskCancel, current);
            }
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }
    }
}
