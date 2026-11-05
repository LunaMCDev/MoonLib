# Scheduler

Package: `com.lunamcdev.moonlib.scheduler`

`Tasks` is a single scheduling API that works on Bukkit, Spigot, Paper **and Folia**. All delays
and periods are in ticks (20 ticks = 1 second). Every method returns a `TaskHandle` with `cancel()`
and `isCancelled()`.

## Methods

| Method | Runs |
|---|---|
| `sync(task)` | next tick, main thread (Folia: global region) |
| `ensureSync(task)` | immediately if already on the main thread, otherwise next tick |
| `later(delay, task)` | after a delay |
| `timer(delay, period, task)` | repeatedly |
| `async(task)`, `asyncLater(delay, task)`, `asyncTimer(delay, period, task)` | off the main thread |
| `entity(entity, task)`, `entityLater(...)`, `entityTimer(...)` | on the thread that owns the entity |
| `at(location, task)`, `atLater(...)`, `atTimer(...)` | on the thread that owns the location |
| `supplyAsync(supplier)` | async, returns a `CompletableFuture` |
| `supplySync(supplier)` | main thread, returns a `CompletableFuture` |

## Examples

```java
// Countdown
int[] seconds = {10};
TaskHandle countdown = Tasks.timer(0, 20, () -> {
    if (seconds[0] == 0) { startEvent(); return; }
    Titles.broadcast("&e" + seconds[0]--, "");
});
countdown.cancel();

// Load from a database, then use the result safely
Tasks.supplyAsync(() -> database.loadProfile(player.getUniqueId()))
     .thenAccept(profile -> Tasks.entity(player, () -> profiles.apply(player, profile)))
     .exceptionally(error -> { player.sendMessage("Failed to load your data"); return null; });

// Save every 5 minutes, off the main thread
Tasks.asyncTimer(20 * 300, 20 * 300, profiles::saveAll);
```

Exceptions thrown inside `supplyAsync`/`supplySync` are logged and complete the future
exceptionally.

## Folia

Folia has no single main thread: each region of the world ticks on its own thread. MoonLib detects
Folia and switches to its schedulers automatically. To make your plugin Folia-compatible:

1. Use `Tasks.entity(player, ...)` for anything touching a player or entity, and
   `Tasks.at(location, ...)` for blocks. On normal servers these just run on the main thread, so
   there is no downside.
2. Use `Tasks.sync`/`timer` only for world-independent logic (global timers, broadcasts).
3. Add `folia-supported: true` to `plugin.yml`.

MoonLib's own menus, chat input and command framework already follow these rules. Scoreboards are
not available on Folia (see [scoreboards.md](scoreboards.md)).

`Tasks.isOwnedByCurrentThread(entity)` tells you whether it is safe to touch an entity right now.

## Automatic cleanup

All tasks belonging to your plugin are cancelled when MoonLib shuts down.
