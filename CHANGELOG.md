# Changelog

All notable changes to MoonLib are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project uses
[Semantic Versioning](https://semver.org/).

## [Unreleased]

## [1.4.0] - 2026-11-05

### Changed
- Building MoonLib now needs **JDK 25** (the 26.x API is Java 25 bytecode). The jar is still Java 8
  bytecode and runs on every supported server. `jitpack.yml` installs JDK 25 automatically.

### Added
- **Annotated commands** (`command.annotation`): `@Command`, `@Subcommand` (nested paths such as
  `"admin reset"`), `@Execute`, `@Arg` (name, optional, default, named type), `@Greedy`, `@Range`,
  `@Suggest`, `@Cooldown`. Builds ordinary `MoonCommand`s; mistakes are reported at build time with the
  class and method named. `MoonPlugin.registerCommands(Object...)` accepts handlers and commands mixed.
- **Rich text** (`RichText`): MiniMessage-style tags (named and hex colours, decorations and negation,
  multi-stop gradients, rainbow, reset, newline, placeholders) on every version without Adventure;
  downsampled to legacy colours below 1.16; unknown tags printed literally; placeholder values cannot
  inject formatting; legacy `&` codes still work. `Messages.useRichText(true)` / `rich-text: true`.
- **Database** (`database`): `Database` for SQLite, MySQL, MariaDB, PostgreSQL and H2 with a built-in
  connection pool (validation, max lifetime, acquire timeout), prepared statements, `Row` mapping
  (UUIDs, enums, instants), `query`/`queryOne`/`value`/`exists`/`insert`/`batch`, transactions with
  savepoints, async variants on dedicated worker threads, slow-query logging, a one-time warning for
  blocking calls on the server thread, `fromConfig` for owner-selectable storage, `fromDataSource` for
  external pools, `Dialect` helpers (`upsert`, `insertIgnore`, `autoIncrementKey`) and versioned
  `Migrations` (inline SQL, jar resources or Java).
- **Player data** (`player.PlayerCache`): loads during async pre-login, turns players away instead of
  letting them join with empty data when loading fails, ordered background saves, autosave, safe quick
  rejoin handling, final save on shutdown.
- **Modules** (`module`): `MoonModule` with `load`/`enable`/`disable`/`reload`, automatic cleanup of
  listeners, lambda events, commands, tasks and services; `ModuleManager` with dependency and soft-dependency
  ordering, cycle detection, config toggles, isolated failures and status reporting; `Services` registry.
- **Displays** (`display`): `Bar` boss bars with `countdown`, `TabList` header/footer/names with an
  auto-refresh provider, `Hologram` (text displays on 1.19.4+, marker armor stands before, non-persistent
  entities).
- `FileWatcher` (`watch`, `watchFolder`, `watchTree`) with debouncing; `DataFile.watch(plugin, afterReload)`
  reloads files edited on disk and ignores the plugin's own saves.
- `UpdateChecker` for Modrinth, SpigotMC, GitHub releases and Hangar, with staff notifications and
  pre-release-aware version comparison.
- `MoonContext.onShutdown` / `cancelShutdown`: resources close in reverse order before tasks are
  cancelled, so modules, player caches and databases shut down in the right order.
- **Template plugin** (`template-plugin/`): starter project with IntelliJ run configurations, a Paper
  download tool (Fill v3 API, SHA-256 verified), a dev build for hot-swapping with the JetBrains Runtime,
  automatic module reload after builds, self-reloading config and messages, and example modules.
- Built and tested against the latest Minecraft release: the Spigot **26.3** API (library, example,
  standalone and template plugins), a 26.3 dev server in the template, and test harnesses that run
  as a 26.3 server. `ServerVersion.V26_1`, `V26_2`, `V26_3` constants.
- Documentation: database, modules, player data, displays, rich text, development workflow, architecture,
  compatibility and API stability policy, cookbook and FAQ guides; MkDocs site configuration;
  CONTRIBUTING, SECURITY and code of conduct; CI and docs-publishing workflows; issue and PR templates.

## [1.3.0] - 2026-11-02

### Added
- **Plugins that work together.** `Integrations` / `Integration`: detect another plugin and switch a link on when it
  enables (whatever the load order), off when it disables, and on again after a reload; minimum versions; failures
  are logged and never crash the plugin.
- `MoonLink`: plugins built on MoonLib offer capabilities (`provide`), call each other's (`call`, `available`,
  `providers`) and send events (`publish`, `subscribe`) with no API jar or compile-time dependency. It works between
  relocated copies, shared copies and the standalone plugin, through Bukkit's `ServicesManager`.
- The standalone plugin's `/moonlib` lists every plugin's MoonLink capabilities.

## [1.2.0] - 2026-11-01

### Added
- **YAML and JSON files with one API.** `DataFile` (with `YamlFile` and `JsonFile`): create, read, edit and save
  by dot path, defaults from the jar merged on load, broken files backed up with line/column errors,
  atomic saves, `saveAsync`, `edit(...)`, `setIfAbsent`, `remove`, `keys`, `toMap`, `as(Class)` / `setRoot`.
- `DataMapper`: stores your own classes (fields, no-arg constructor), Java records, enums, UUIDs, `java.time`
  types, collections, arrays, maps (incl. `EnumMap`), Bukkit `ConfigurationSerializable` objects (also in JSON),
  with custom adapters via `DataMapper.register`.
- `DataStore<T>`: one file per key (e.g. per-player data) with sync and async load/save, `keys()`, `loadAll()`
  and safe key validation.
- `Json`: dependency-free JSON reader/writer (accepts comments and trailing commas, writes strict JSON).
- `MalformedDataException` with line and column.
- `MoonPlugin#loadYaml`, `#loadJson`, `#dataStore`.

### Changed
- `ConfigFile` is now a `YamlFile`. Its API is unchanged except that `set`, `setLocation` and `reload` return the
  file for chaining and `save()` returns whether it succeeded.

## [1.1.0] - 2026-11-01

### Added
- **Multi-plugin support.** Any number of plugins can use one copy of MoonLib (unrelocated shading or the
  new standalone plugin) without errors. Previously a second plugin on a shared copy threw an
  `IllegalStateException`.
- `MoonContext`: per-plugin state (debug flag, command message overrides, bundled MoonLib version).
- Automatic owner resolution from the call stack (StackWalker on Java 9+, `SecurityManager#getClassContext`
  on Java 8; `-Dmoonlib.stackwalker=false` forces the latter).
- `MoonLib.runAs` / `callAs` for explicit ownership; `MoonLib.plugins()`, `host()`, `mode()`, `context(...)`,
  `version()`, `isVersionAtLeast(...)`, `compareVersions(...)`.
- Shared listeners that move to another plugin when their host disables (`MoonLib.shareListener`).
- Version mismatch warnings between plugins sharing one copy (`moonlib.properties` in every jar).
- `standalone-plugin/`: MoonLib as an installable plugin with a `/moonlib` diagnostics command.
- `CommandMessages.set(key, value)`, `CommandMessages.get(key)`, `CommandMessages.keys()`.
- `CommandRegistry.registered(Plugin)`, `Menu#owner()`, `BoardManager#owner()`, `EventSubscription#owner()`.

### Changed
- `MoonLib.shutdown(Plugin)`, `Tasks.shutdown(Plugin)`, `MenuManager.shutdown(Plugin)`,
  `ChatInput.shutdown(Plugin)`, `BoardManager.stopAll(Plugin)` and `CommandRegistry.unregisterAll(Plugin)`
  clean up a single plugin. `MoonLib.shutdown()` without arguments shuts down the calling plugin.
- Tasks, menus, commands, chat prompts, event handlers and scoreboard updates run as the plugin that owns them.
- `Log` writes to the calling plugin's logger; `Log.setDebug` is per plugin. `Log.init(Logger)` was replaced
  by `Log.setFallbackLogger(Logger)`.
- `CommandMessages.load(...)` now applies to the calling plugin only. Assigning the public fields still
  changes the global defaults.

## [1.0.0] - 2026-11-01

### Added
- `MoonPlugin` base class and `MoonLib` lifecycle with relocation safety check.
- Command framework: `MoonCommand`, `CommandBuilder`, `CommandContext`, `CommandError`,
  `ArgumentTypes` (string, greedy, numbers with k/m/b suffixes, boolean, player, offline player,
  world, material, duration, enum, choice), `CommandRegistry` (command-map injection and
  `plugin.yml` binding), configurable `CommandMessages`, automatic help and usage.
- Menus: `Menu` with layouts and auto-update, `Button`, `MenuClick`, `PaginatedMenu`, `ConfirmMenu`,
  dupe-safe `MenuManager`.
- Scoreboards: flicker-free `Board`, `BoardManager` + `BoardProvider`.
- Multi-version: `ServerVersion` (classic and year-based versions), `ServerSoftware`, `Materials`,
  `Sounds`, `Enchants`, `Reflection`, title/action-bar adapters (Adventure, Spigot, legacy packets).
- Scheduler: `Tasks` with Bukkit and Folia implementations and `CompletableFuture` helpers.
- Items: `ItemBuilder`, `Skulls`, `ItemTags`, `Items`.
- Config: `ConfigFile` (default merging, broken-file backup, async save), `Messages`.
- Events: `Events`, `EventSubscription`.
- Chat input: `ChatInput`.
- Hooks: `Placeholders` (PlaceholderAPI), `VaultEconomy` (Vault).
- Utilities: `Text`, `Titles`, `Durations`, `Numbers`, `Cooldown`, `Locations`, `Cuboid`, `Players`, `Log`.
- Documentation in `docs/`, a complete `example-plugin`, and unit tests.
