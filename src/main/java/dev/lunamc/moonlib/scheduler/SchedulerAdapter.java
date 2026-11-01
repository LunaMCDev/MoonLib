package dev.lunamc.moonlib.scheduler;

import org.bukkit.Location;
import org.bukkit.entity.Entity;

/**
 * Internal API. The platform specific scheduler behind {@link Tasks}.
 */
interface SchedulerAdapter {

    TaskHandle runGlobal(Runnable task, long delayTicks, long periodTicks);

    TaskHandle runAsync(Runnable task, long delayTicks, long periodTicks);

    TaskHandle runAtEntity(Entity entity, Runnable task, long delayTicks, long periodTicks);

    TaskHandle runAtLocation(Location location, Runnable task, long delayTicks, long periodTicks);

    boolean isOwnedByCurrentThread(Entity entity);

    void cancelAll();
}
