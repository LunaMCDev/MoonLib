# Player data

*Since 1.4.0.* Almost every plugin keeps data per player: balances, stats, settings. Getting it right
is harder than it looks. Loading on the main thread causes lag spikes, saving on quit can race a
quick rejoin, and a failed load followed by a save can wipe someone's progress. `PlayerCache`
handles all of that.

```java
PlayerCache<PlayerData> players = PlayerCache.of(this,
        DataStore.json(this, "players", PlayerData.class), PlayerData::new);

// anywhere on the server thread, for an online player:
PlayerData data = players.get(player);
data.tokens += 10;        // just change it; saving is automatic
```

## Any storage

```java
PlayerCache<Stats> stats = PlayerCache.<Stats>builder(this)
        .loader((id, name) -> db.queryOne("SELECT * FROM stats WHERE uuid = ?", Stats::fromRow, id)
                .orElseGet(Stats::new))
        .saver((id, s) -> db.update(db.dialect().upsert("stats", "uuid", "kills", "deaths"), id, s.kills, s.deaths))
        .autosave(5, TimeUnit.MINUTES)     // 0 to disable; quit and shutdown saves always happen
        .failedLoadMessage("&cYour stats could not be loaded. Please rejoin.")
        .build();
```

The loader and saver run on the cache's background thread, so blocking calls (files, SQL) are
correct there.

## What it guarantees

| Situation | Behaviour |
|---|---|
| Player logs in | Data loads during `AsyncPlayerPreLoginEvent`, off the server thread, so it is ready at join |
| Load fails (database down) | The player is **turned away** with a "please rejoin" message, instead of joining with empty data that would later overwrite their real data |
| Player quits | Saved in the background, then removed from memory |
| Player rejoins before that save finished | Gets the in-memory copy, never the stale copy on disk, and is not evicted afterwards |
| Two saves for one player | Run in order on one thread; an older save can never overwrite a newer one |
| Another plugin cancels the login after we loaded | Data is dropped after a minute without saving |
| Server stops | Every loaded player is saved before the plugin finishes disabling |
| Cache created while players are online (`/reload`) | Online players are loaded at once |

## API

| Method | Use |
|---|---|
| `get(player)` | Data of an online player (waits, with a warning, in the rare case it is still loading) |
| `find(uuid)` | `Optional`, only if in memory |
| `whenLoaded(player, data -> ...)` | Run on the player's thread once loaded |
| `load(uuid, name)`, `unload(uuid)` | Offline players, or caches built with `manual()` |
| `save(uuid)`, `saveAll()` | Save now (futures) |
| `loaded()` | Snapshot of everything in memory |
| `evict(uuid)` | Drop without saving (after deleting their data) |

## Thread safety

Your data objects are read by the save thread while the server thread changes them. Keep them
simple: fields of primitives and strings, marked `volatile` if you change them often. Use
concurrent collections (`ConcurrentHashMap`) for maps and lists inside them.
