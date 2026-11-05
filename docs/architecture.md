# Architecture

This page explains how MoonLib is put together and why. Read it before contributing, or when you
want to know what happens behind a call.

## Design goals

1. **Shade-first.** MoonLib is meant to live *inside* each plugin. It has no runtime dependencies,
   targets Java 8 bytecode, and never assumes it is the only copy on the server.
2. **One jar, every version.** Version differences are resolved at runtime (reflection, adapters,
   name tables), never at build time. A plugin built once runs from 1.8.8 to the newest release.
3. **Ownership everywhere.** Everything MoonLib creates belongs to a plugin and is cleaned up when
   that plugin stops, without touching other plugins.
4. **Fail loudly at build/startup, softly at runtime.** Mistakes in your code (an invalid command
   annotation, a module cycle, a duplicate migration) throw immediately with a precise message.
   Problems on a live server (a broken config, a database outage, a missing optional API) are
   logged and degrade gracefully.
5. **No surprises on the server thread.** Blocking work has async variants, and MoonLib warns when
   blocking work lands on the main thread.

## Layers

```
┌───────────────────────────────────────────────────────────────────────────┐
│ Your plugin                                                               │
├───────────────────────────────────────────────────────────────────────────┤
│ Features   command  menu  scoreboard  display  input  event  integration  │
│            module   player  database  config  item   hook                 │
├───────────────────────────────────────────────────────────────────────────┤
│ Platform   scheduler (Bukkit | Folia)   version (detection, adapters)      │
│            util (Text, RichText, Log, FileWatcher, ...)                    │
├───────────────────────────────────────────────────────────────────────────┤
│ Core       MoonLib (init/shutdown)  MoonContext (per-plugin state)         │
│            PluginResolver (who is calling?)                                │
└───────────────────────────────────────────────────────────────────────────┘
```

Features depend on the platform and core layers, never the other way round. Features may depend on
each other only through public API (for example `module` uses `command.annotation`).

## Core: contexts and ownership

`MoonLib.init(plugin)` creates a `MoonContext` for the plugin. A context holds the plugin's logger,
debug flag, command-message overrides and a stack of **shutdown resources**.

Every feature records its owner. When code asks "which plugin is this?" (`MoonLib.plugin()`), the
answer comes from, in order:

1. a binding set by `MoonLib.runAs(plugin, ...)`, which MoonLib itself uses around every task,
   menu click, command and event it dispatches, so callbacks always run as their owner;
2. the only registered plugin, if there is just one (the common, relocated case: no work at all);
3. a stack walk that finds the first class loaded by a registered plugin's class loader.

`MoonLib.shutdown(plugin)` then:

1. closes the context's shutdown resources in **reverse order of registration**: modules, player
   caches, databases, file watchers, boss bars, holograms, update checkers (whatever registered
   itself with `MoonContext.onShutdown`);
2. closes menus, stops scoreboards, chat prompts, commands, integrations and MoonLink endpoints;
3. cancels the plugin's tasks last, so resources closed in step 1 could still finish their work.

That order is what makes "save everything on shutdown" reliable: modules disable (and save) first,
the player cache saves every player through the database, and only then is the database closed.

## Multi-copy model

| Situation | What happens |
|---|---|
| Relocated (each plugin has its own copy) | Each copy is completely independent; contexts never meet |
| Shared (unrelocated) | One copy, many contexts; ownership resolution decides who is who |
| Cross-copy communication | `MoonLink` uses only JDK types (`BiFunction`, `Map`) through Bukkit's `ServicesManager`, so relocated copies understand each other |

Shared listeners (menus, chat input) are registered once under a *host* plugin and move to another
plugin if the host disables.

## Version handling

`ServerVersion` parses the running version once (including year-based `26.x`), and `ServerSoftware`
detects Paper, Purpur, Folia and others. Version-specific code lives in three patterns:

- **Name tables**: `Materials`, `Sounds`, `Enchants` map modern names to legacy ones and back.
- **Adapters**: an interface with per-version implementations chosen once, such as
  `MessageAdapter` (Adventure on Paper, BungeeCord chat on Spigot, packets on 1.8) and the scheduler.
- **Isolated classes**: code that references APIs missing on old servers sits in its own class that
  is only loaded after a version check (for example `ModernBossBar`, `ModernItemSupport`). The JVM
  never resolves those types on servers that lack them.

## Threading model

| Work | Runs on |
|---|---|
| Commands, menus, events, `Tasks.sync` | server thread (global region on Folia) |
| `Tasks.entity` / `Tasks.at` | the entity's / location's region thread on Folia; server thread otherwise |
| `Database` async methods | the database's worker threads (one per pooled connection) |
| `PlayerCache` loads and saves | one background thread per cache (keeps them ordered) |
| `FileWatcher` | one watcher thread per plugin; callbacks hop to the server thread |
| `UpdateChecker` | Bukkit async tasks |

## Error handling conventions

- Programming errors: `IllegalArgumentException` / `IllegalStateException` with the class,
  method or key named, thrown when the mistake is made.
- Operational errors: logged through the owning plugin's logger, with a sentence that says what
  stays working ("the module is off, the rest of the plugin keeps running").
- Database errors: `DatabaseException` (unchecked), with the failing statement but never its
  parameters, which may hold player data.
- Nothing MoonLib schedules lets an exception escape into the server's scheduler unlogged.

## Testing

The unit tests run without a server. Bukkit types are faked with small stubs or dynamic proxies,
JDBC with a scripted in-memory fake (`FakeJdbc`), and lifecycle behaviour (file watching, shutdown
order, multi-copy linking) with harnesses that load MoonLib in separate class loaders. See
[CONTRIBUTING.md](../CONTRIBUTING.md).
