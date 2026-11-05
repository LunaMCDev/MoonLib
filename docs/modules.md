# Modules & services

*Since 1.2.0.* Large plugins grow into tangles: one `onEnable` that sets up everything, features that
reach into each other, and one bug that stops the whole plugin from loading. Modules split a plugin
into independent features that:

- start in dependency order and stop in reverse;
- can be turned off by server owners in config;
- fail alone: a module that throws is switched off, its dependants are skipped, the rest runs;
- clean up after themselves: listeners, commands, tasks and services registered through the module
  are undone when it stops;
- can be reloaded one at a time, which is also what makes [hot reload](dev-workflow.md) work.

## A module

```java
public final class RanksModule extends MoonModule {

    private final MyPlugin plugin;

    public RanksModule(MyPlugin plugin) {
        super("ranks", EconomyModule.class);       // id, then required modules
        softDepend(StatsModule.class);             // enabled first if present, not required
        this.plugin = plugin;
    }

    @Override
    protected void enable() {
        RankService ranks = new RankService(service(Economy.class), plugin.database());
        provide(RankService.class, ranks);                         // for other modules
        on(PlayerJoinEvent.class, e -> ranks.load(e.getPlayer()));   // lambda listener
        listen(new RankupListener(ranks));                         // Listener class
        command(new RankupCommand(ranks));                         // MoonCommand or annotated handler
        track(Tasks.timer(0, 20 * 60, ranks::saveDirty));          // repeating task
        onDisable(ranks::flush);                                   // anything else
    }

    @Override
    protected void disable() {
        // save state; everything registered above is undone automatically afterwards
    }
}
```

| Lifecycle method | When |
|---|---|
| `load()` | Once per module, before any module is enabled (optional) |
| `enable()` | In dependency order; throw to fail the module |
| `disable()` | In reverse order; dependants are always stopped first |
| `reload()` | `ModuleManager.reload(id)`; default: disable then enable |

## The manager

```java
modules = new ModuleManager(this)
        .toggles(loadConfig("config.yml"), "modules")     // modules.<id>: false switches a module off
        .register(new EconomyModule(), new RanksModule(this), new MinesModule(this));
modules.enableAll();
```

`toggles` adds every module to the config as `true` the first time, so the file always lists what
can be switched off:

```yaml
modules:
  economy: true
  ranks: true
  mines: false
```

| Method | Use |
|---|---|
| `enableAll()` | load + enable everything (call once in `enable()`) |
| `reload(id)`, `reloadAll()` | for `/plugin reload [module]` |
| `state(module)`, `problem(module)` | `ENABLED`, `TURNED_OFF`, `SKIPPED`, `FAILED`, `DISABLED`, and why |
| `get(Class)`, `byId(id)`, `modules()` | lookups |
| `services()` | the shared service registry |
| `disableAll()` | called automatically on plugin shutdown |

Dependency cycles are reported at `enableAll()`: `Module dependency cycle: a -> b -> a`.

## Services

Modules should depend on **interfaces**, not on each other's internals:

```java
public interface Economy {
    long balance(UUID player);
    boolean withdraw(UUID player, long amount);
}

// EconomyModule.enable():
provide(Economy.class, new TokenEconomy(db));

// any other module:
Economy economy = service(Economy.class);                    // throws a clear error if missing
modules.services().find(Economy.class).ifPresent(...);       // optional lookup
```

A service disappears when the module that provided it stops. Services are private to the plugin; to
offer features to *other* plugins, use [MoonLink](integrations.md).

## Status command example

```java
@Subcommand(value = "modules", permission = "myplugin.admin")
public void modules(CommandSender sender) {
    for (MoonModule module : modules.modules()) {
        String problem = modules.problem(module);
        sender.sendMessage(Text.color("&f" + module.id() + " &7" + modules.state(module)
                + (problem == null ? "" : " &c(" + problem + ")")));
    }
}
```
