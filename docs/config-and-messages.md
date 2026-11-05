# Config & messages

Package: `dev.lunamc.moonlib.config`

> For general YAML **and JSON** files, storing your own classes, and per-player data, see
> [data-files.md](data-files.md). `ConfigFile` is a `YamlFile`, so everything there works on it too.

## `ConfigFile`

```java
ConfigFile mines = new ConfigFile(plugin, "mines.yml");      // or loadConfig("mines.yml") in MoonPlugin
ConfigFile ranks = new ConfigFile(plugin, "data/ranks.yml"); // sub-folders are created
```

What happens on load:

1. If the file is missing it is copied from your jar's resource with the same path, or created
   empty if there is none.
2. Keys present in the jar's copy but missing on disk are **added automatically**, so shipping an
   update that adds options needs no migration code. Existing values are never changed. Turn it off
   with `.mergeDefaults(false)` before `reload()`.
3. If the YAML is invalid, the file is backed up as `mines.broken-<timestamp>.yml`, an error explains
   what happened, and the defaults are used. A typo never stops your plugin from enabling.

### Reading

| Method | Notes |
|---|---|
| `getString(path[, default])`, `getInt`, `getLong`, `getDouble`, `getBoolean`, `getStringList` | Plain values |
| `getText(path)` | String, coloured |
| `getTextList(path)` | List, coloured; a single string becomes a one-line list |
| `getEnum(path, Type.class, default)` | Case-insensitive, warns on invalid values |
| `getLocation(path)` / `setLocation(path, loc)` | Compact `world;x;y;z;yaw;pitch` format |
| `getSection`, `getOrCreateSection`, `keys(path)`, `contains` | Structure |
| `config()` | The underlying `FileConfiguration` |

### Writing

```java
mines.set("mines.a.size", 50);
mines.save();          // synchronous
mines.saveAsync();     // snapshot taken now, written off the main thread (atomic temp-file swap)
mines.reload();
```

## `Messages`

```yaml
# messages.yml (in src/main/resources)
prefix: "&d&lMoonPrison &8» &7"
rankup:
  success: "{prefix}&aYou ranked up to &e{rank}&a!"
  max: "{prefix}&cYou are already max rank."
join-motd:
  - "&8&m--------------------"
  - "&dWelcome to &lMoonPrison"
  - "&8&m--------------------"
disabled-message: ""
```

```java
Messages messages = new Messages(plugin);                 // or new Messages(plugin, "lang/en.yml")

messages.send(player, "rankup.success", "rank", "B");     // {prefix} and {rank} replaced
messages.send(player, "join-motd");                       // lists send line by line
messages.send(player, "disabled-message");                // "" sends nothing at all
String text = messages.get("rankup.max");
List<String> lines = messages.getList("join-motd");

messages.broadcast("events.start", "event", "Double XP");
messages.broadcastWithPermission("staff.alert", "staff.report", "player", name);
messages.actionBar(player, "mine.blocks", "count", 120);
messages.title(player, "rankup.title", "rankup.subtitle", "rank", "B");

messages.reload();
```

- `{prefix}` is available in every message.
- Rich-text tags (`<gradient:#7F7FD5:#91EAE4>`, `<bold>`, `<#ff8800>`...) work after
  `messages.useRichText(true)` or with `rich-text: true` at the top of the file. Placeholder values
  are escaped, so a player name can never inject formatting. See [rich-text.md](rich-text.md).
- A missing key shows `Missing message: key` and logs one warning, rather than throwing.
- PlaceholderAPI placeholders are applied for player recipients when PAPI is installed
  (`usePlaceholderApi(false)` to disable).

## Reloading automatically

*Since 1.4.0.* Any MoonLib file can reload itself when it changes on disk, so server owners (and you,
while developing) see edits the moment they save:

```java
settings.watch(this, this::applySettings);                       // ConfigFile, YamlFile or JsonFile
messages.file().watch(this, () -> getLogger().info("messages.yml reloaded"));
```

Writes made by the plugin's own `save()` are ignored, edits are debounced (an editor saving three
times in a row causes one reload), and a file with a syntax error is backed up while the previous
values stay in use, as with any reload. Watching stops when the plugin shuts down.

## Translating MoonLib's own messages

```java
CommandMessages.load(messages.file().getSection("commands"));
```

See [commands.md](commands.md#messages) for the keys.
