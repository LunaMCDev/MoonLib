# Player data

*Since 1.2.0.* Almost every plugin keeps data per player: balances, stats, settings. Getting it right
is harder than it looks. Loading on the main thread causes lag spikes, saving on quit can race a
quick rejoin, and a failed load followed by a save can wipe someone's progress. `PlayerCache`
handles all of that.

```java
public final class PlayerData {          // plain fields and a no-argument constructor
    public volatile long tokens;
    public volatile String rank = "A";
}

PlayerCache<PlayerData> players = PlayerCache.json(this, PlayerData.class, PlayerData::new);

// anywhere on the server thread, for an online player:
PlayerData data = players.get(player);
data.tokens += 10;        // just change it; saving is automatic
```

## Files or a database: choose the storage

No database is needed. Pick where data lives in one line, or let server owners pick in config:

| Storage | Code | Where the data goes |
|---|---|---|
| JSON files | `PlayerCache.json(this, PlayerData.class, PlayerData::new)` | `plugins/<Plugin>/players/<uuid>.json` |
| YAML files | `PlayerCache.yaml(this, PlayerData.class, PlayerData::new)` | `plugins/<Plugin>/players/<uuid>.yml` |
| A database | `PlayerCache.sql(this, database, PlayerData.class, PlayerData::new)` | a row in `moonlib_player_data` |
| Owner's choice | `PlayerCache.fromConfig(this, config, "storage", PlayerData.class, PlayerData::new)` | see below |

```yaml
storage:
  type: json            # json, yaml, sqlite, mysql or mariadb
  folder: players       # json / yaml
  file: data.db         # sqlite
  host: localhost       # mysql / mariadb (same keys as Database.fromConfig)
  port: 3306
  name: prison
  username: root
  password: ''
```

Your class is saved with `DataMapper`, so it can hold anything `DataMapper` supports: numbers,
strings, enums, UUIDs, lists, sets, maps and nested objects. Adding a field needs no migration: old
files and rows simply get the field's default.

For more control, build the cache yourself:

```java
PlayerCache<PlayerData> players = PlayerCache.<PlayerData>builder(this)
        .storage(PlayerStorage.fromConfig(this, config, "storage", PlayerData.class), PlayerData::new)
        .autosave(2, TimeUnit.MINUTES)
        .failedLoadMessage("&cYour data could not be loaded. Please rejoin.")
        .build();
```

### Efficient saving

The built-in storages remember what they last wrote for each player and **skip saves when nothing
changed**, so an autosave with 200 idle players writes nothing. With a database, all changed players
of an autosave are written in **one batched transaction**.

### Several plugins, one server

- File storage lives in each plugin's own data folder.
- The SQL storage keys every row by **plugin name and UUID**, so any number of plugins can share one
  database without seeing or overwriting each other's data.
- Each cache has its own background thread, listeners and autosave, owned by its plugin. Shutting
  one plugin down saves and closes only its caches, even when several plugins share one copy of
  MoonLib.

### Your own storage

Implement `PlayerStorage<T>` (`load`, `save`, `delete`, `describe`; optionally `saveAll` and `close`)
to store data anywhere, for example in your own SQL table with real columns you can query.

## Custom loader and saver

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
| `evict(uuid)` | Drop without saving |
| `delete(uuid)` | Delete stored data and drop from memory (storage-backed caches) |
| `storage()` | The `PlayerStorage` in use |

## Thread safety

Your data objects are read by the save thread while the server thread changes them. Keep them
simple: fields of primitives and strings, marked `volatile` if you change them often. Use
concurrent collections (`ConcurrentHashMap`) for maps and lists inside them.
