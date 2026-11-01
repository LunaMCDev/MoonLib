# Changelog

All notable changes to MoonLib are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project uses
[Semantic Versioning](https://semver.org/).

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
