# YAML & JSON files

Package: `com.lunamcdev.moonlib.config`

MoonLib gives YAML and JSON files the same API: create a file, read and edit values by dot path, store
your own classes, and save. It also includes a `DataStore` for "one file per player" data.

| Class | Use it for |
|---|---|
| `DataFile` | The shared API, plus the factory methods below |
| `YamlFile` | YAML files (Bukkit's `YamlConfiguration` underneath; comments are kept on 1.18.1+) |
| `JsonFile` | JSON files (MoonLib's own reader/writer, identical on every server version) |
| `DataStore<T>` | A folder with one file per key, each holding one object (player data, islands, mines...) |
| `DataMapper` | Converts your objects to file data and back; used automatically, but extensible |
| `Json` | Parse/write JSON text yourself (`Json.parse`, `Json.write`) |
| `ConfigFile` | A `YamlFile` for configs, kept for compatibility |

## Creating and loading

```java
YamlFile mines = DataFile.yaml(plugin, "mines.yml");
JsonFile stats = DataFile.json(plugin, "data/stats.json");     // sub-folders are created
DataFile warps = DataFile.of(plugin, "warps.json");            // format from the extension

JsonFile anywhere = DataFile.json(new File("/some/other/place.json"));   // no plugin needed

// inside a MoonPlugin
YamlFile ranks = loadYaml("ranks.yml");
JsonFile cache = loadJson("cache.json");
```

When a file is loaded:

1. If it doesn't exist, it is created: copied from your jar's resource with the same path (comments
   and formatting kept), or empty (`{}` for JSON).
2. Keys present in the jar's copy but missing on disk are **added automatically**, so shipping an update
   with new options needs no migration code. Existing values are never changed. Disable with
   `.mergeDefaults(false)` followed by `.reload()`.
3. If the file can't be parsed, it is backed up as `name.broken-<timestamp>.ext`, the error is logged
   with its line and column, and the defaults are used. A typo never stops your plugin from enabling,
   and nobody's data is lost.

## Reading

```java
int size          = mines.getInt("mines.a.size", 50);
long total        = stats.getLong("totals.blocks", 0);
double tax        = mines.getDouble("tax", 0.05);
boolean enabled   = mines.getBoolean("enabled", true);
String name       = mines.getString("mines.a.name", "Mine A");
String prefix     = mines.getText("prefix");                      // coloured
List<String> lore = mines.getTextList("lore");                    // coloured
List<String> ids  = mines.getStringList("mines.a.owners");
Material block    = mines.getEnum("mines.a.block", Material.class, Material.STONE);
UUID owner        = mines.getUuid("mines.a.owner");
Location spawn    = mines.getLocation("mines.a.spawn");

Set<String> mineIds = mines.keys("mines");             // direct children
Set<String> all     = mines.keys("", true);            // every path in the file
boolean exists      = mines.contains("mines.a");
boolean section     = mines.isSection("mines.a");
```

Getters are forgiving: `getInt` accepts `"12"`, `getBoolean` accepts `yes`/`no`, and anything missing
or invalid returns your fallback.

## Editing and saving

```java
stats.set("totals.blocks", total + mined)       // missing parents are created
     .set("lastReset", Instant.now())
     .set("topPlayer", player.getUniqueId())
     .remove("legacy.option");                   // or set(path, null)
stats.save();                                    // returns false (and logs) on failure
stats.saveAsync();                               // snapshot now, write off the main thread

stats.edit(f -> f.set("resets", f.getInt("resets", 0) + 1));   // change + save in one call
stats.setIfAbsent("created", Instant.now());
mines.setLocation("mines.a.spawn", player.getLocation());

stats.reload();      // re-read from disk
stats.clear();       // empty it (in memory)
stats.delete();      // remove the file
String text = stats.saveToString();
```

Saves are **atomic**: data is written to a temporary file and then moved into place, so a crash or
power cut mid-save can't corrupt the file. All methods are thread-safe.

## Storing your own classes

Any class with a no-argument constructor (any visibility) can be stored. Every non-static,
non-transient field is saved, including inherited ones.

```java
public final class Mine {
    private String name;
    private int size = 25;
    private Material block = Material.STONE;
    private UUID owner;
    private Location spawn;                          // Bukkit types work
    private Map<Material, Double> composition = new EnumMap<>(Material.class);
    private List<UUID> members = new ArrayList<>();
    private transient boolean resetting;             // not saved

    private Mine() { }                               // used when loading
    public Mine(String name, UUID owner) { this.name = name; this.owner = owner; }
}

mines.set("mines.a", new Mine("A", player.getUniqueId())).save();
Mine a = mines.get("mines.a", Mine.class);
Map<String, Mine> everyMine = mines.getMap("mines", Mine.class);
List<Mine> list = mines.getList("list", Mine.class);

// the whole file as one object (great for settings classes)
Settings settings = DataFile.yaml(plugin, "settings.yml").as(Settings.class);
file.setRoot(settings).save();
```

**Adding fields later is safe:** fields missing from an old file keep the value your constructor gave
them. Java records are supported too, through their canonical constructor.

### Supported types

Strings, primitives and wrappers, `BigInteger`, `BigDecimal`, enums (read case-insensitively), `UUID`,
`Instant`, `LocalDate`, `LocalDateTime`, `Duration`, lists, sets, any collection, arrays, `EnumSet`,
maps with string/enum/UUID/number keys (`EnumMap` and sorted maps included), Bukkit
`ConfigurationSerializable` types (`ItemStack`, `Location`, `Vector`, `Color`, ...), your own classes
and records.

In YAML, Bukkit objects use Bukkit's normal YAML form. In JSON they are stored as maps with Bukkit's
`"=="` type key and restored exactly, so even `ItemStack`s with custom names, lore and enchantments
round-trip through JSON.

### Custom types

```java
DataMapper.register(Mine.class, DataMapper.adapter(
        mine -> mine.id(),                          // write: store only the id
        stored -> mineManager.byId(stored.toString())));   // read: look it up again
```

### Generic types

```java
List<UUID> ids = (List<UUID>) file.get("ids", DataMapper.listOf(UUID.class));
Map<Material, Integer> counts = (Map<Material, Integer>) file.get("counts", DataMapper.mapOf(Material.class, Integer.class));
```

### Errors

Reading data that doesn't fit throws an `IllegalArgumentException` that names the path and the
problem, e.g. `Could not read 'mines.a' in mines.yml as Mine: Field 'size': 'big' is not a number`.
The `get(path, type, fallback)` form logs that message and returns the fallback instead.

## Per-player data with `DataStore`

```java
public final class PlayerData {
    private long tokens;
    private String rank = "A";
    private Set<String> unlockedMines = new HashSet<>();
}

DataStore<PlayerData> players = DataStore.json(plugin, "players", PlayerData.class);
// or, in a MoonPlugin:  dataStore("players", PlayerData.class)

// join: load off the main thread, then use it on the player's thread
players.loadAsync(player.getUniqueId(), PlayerData::new)
       .thenAccept(data -> Tasks.entity(player, () -> cache.put(player.getUniqueId(), data)));

// quit
players.saveAsync(player.getUniqueId(), cache.remove(player.getUniqueId()));

// everything else
players.load(uuid);                 // null if none
players.load(uuid, PlayerData::new);
players.save(uuid, data);
players.exists(uuid);
players.delete(uuid);
players.keys();                     // every stored key
players.loadAll();                  // key -> object
players.file(key);                  // open one entry as a DataFile for path edits
```

Files go to `plugins/YourPlugin/players/<uuid>.json` (`DataStore.yaml(...)` for `.yml`). `saveAsync`
converts the object immediately on the calling thread, so you can keep modifying it while the write
happens. Keys may only contain letters, digits, `_`, `-` and `.`, so a key like `../../server` can
never escape the folder.

## Working with JSON text directly

```java
Map<String, Object> data = Json.parseObject(text);  // comments and trailing commas allowed
Object anything = Json.parse("[1, 2, 3]");
String pretty = Json.write(data);                   // 2-space indented
String compact = Json.write(data, false);
JsonFile file = DataFile.json(plugin, "x.json").prettyPrint(false);   // compact files
```

Parse errors are `MalformedDataException`s with `line()` and `column()`.

## YAML specifics

`YamlFile` also exposes Bukkit's API for anything else:

```java
FileConfiguration config = mines.config();
ConfigurationSection section = mines.getSection("mines.a");
ConfigurationSection created = mines.getOrCreateSection("mines.b");
```

Dots always separate path segments, so keys containing dots (such as IP addresses) can't be addressed
by path. Store them as list entries or replace the dots.
