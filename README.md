# 🌙 MoonLib

**A shadeable foundation for Bukkit, Spigot, Paper, Purpur and Folia plugins.**
Commands, menus, scoreboards, multi-version support, a Folia-aware scheduler, configs, messages,
items and the dozens of small helpers every plugin ends up rewriting. You shade it into each
plugin and get straight to the parts that are actually yours.

| | |
|---|---|
| **Minecraft** | 1.8.8 → latest (including the year-based 26.x releases) |
| **Servers** | CraftBukkit, Spigot, Paper, Purpur, Pufferfish, Folia |
| **Java** | Builds with JDK 21+, ships Java 8 bytecode (so it also loads on 1.8 servers) |
| **Runtime dependencies** | None. PlaceholderAPI and Vault are optional soft hooks |
| **Multiple plugins** | Any number per server: relocated, shared, or via the standalone plugin |
| **Licence** | MIT |

---

## Contents

- [Features](#features)
- [Installation](#installation)
- [Quick start](#quick-start)
- [A taste of every module](#a-taste-of-every-module)
- [Documentation](#documentation)
- [Project layout](#project-layout)
- [Building](#building)

---

## Features

| Module | Package | What you get |
|---|---|---|
| **Core** | `dev.lunamc.moonlib` | `MoonPlugin` base class, multi-plugin support with per-plugin ownership, version checks |
| **Commands** | `.command` | Subcommands, typed arguments, tab completion, permissions, cooldowns, auto help/usage. No `plugin.yml` entries |
| **Menus** | `.menu` | Chest GUIs, visual layouts, pagination, confirm dialogs, auto-refresh, dupe-proof click handling |
| **Scoreboards** | `.scoreboard` | Flicker-free per-player sidebars, a manager with a provider and update timer |
| **Multi-version** | `.version` | Version/software detection, modern material, sound and enchantment names on every version, reflection helpers |
| **Scheduler** | `.scheduler` | One API for Bukkit and **Folia** (global, async, entity and region tasks), `CompletableFuture` helpers |
| **Items** | `.item` | `ItemBuilder`, custom-texture heads, persistent item tags, inventory counting/removal, base64 serialisation |
| **Files & config** | `.config` | Create, edit and save **YAML and JSON** files with one API, store your own classes directly, per-player `DataStore`, auto-merged defaults, broken-file recovery, atomic saves, translatable `Messages` |
| **Events** | `.event` | Lambda listeners with filters, priorities and auto-expiry |
| **Chat input** | `.input` | Ask a player to type something, with validation, timeout and cancel |
| **Hooks** | `.hook` | PlaceholderAPI and Vault economy, with no compile-time dependency |
| **Utilities** | `.util` | Colours (hex and gradients), titles/action bars, durations, numbers, cooldowns, locations, cuboids, players, logging |

---

## Installation

### 1. Install MoonLib to your local Maven repository

```bash
git clone https://github.com/LunaMCDev/MoonLib.git
cd MoonLib
mvn clean install
```

Or use it straight from GitHub through [JitPack](https://jitpack.io) (the repository's `jitpack.yml`
tells JitPack to build with JDK 21):

```xml
<repository>
    <id>jitpack.io</id>
    <url>https://jitpack.io</url>
</repository>

<dependency>
    <groupId>com.github.LunaMCDev</groupId>
    <artifactId>MoonLib</artifactId>
    <version>1.2.0</version>   <!-- a release tag, a commit hash, or main-SNAPSHOT -->
</dependency>
```

The JitPack artifact uses the `com.github.LunaMCDev` group, but the Java package is still
`dev.lunamc.moonlib`, so relocation configs stay the same.

### 2. Add it to your plugin

MoonLib can be relocated into your jar (recommended), shaded without relocating, or installed once as a
standalone plugin that your plugins `depend` on. Any number of plugins can use it at the same time in any
of these modes; see [docs/shading.md](docs/shading.md). The relocated setup:

```xml
<dependency>
    <groupId>dev.lunamc</groupId>
    <artifactId>moonlib</artifactId>
    <version>1.2.0</version>
    <scope>compile</scope>
</dependency>
```

### 3. Shade and relocate it (recommended)

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-shade-plugin</artifactId>
    <version>3.6.0</version>
    <executions>
        <execution>
            <phase>package</phase>
            <goals><goal>shade</goal></goals>
            <configuration>
                <createDependencyReducedPom>false</createDependencyReducedPom>
                <relocations>
                    <relocation>
                        <pattern>dev.lunamc.moonlib</pattern>
                        <!-- change this to a package inside YOUR plugin -->
                        <shadedPattern>me.you.yourplugin.libs.moonlib</shadedPattern>
                    </relocation>
                </relocations>
            </configuration>
        </execution>
    </executions>
</plugin>
```

> **Not relocating is fine too.** Plugins that shade MoonLib unrelocated, or that use the standalone
> MoonLib plugin, share one copy. MoonLib keeps each plugin's tasks, menus, commands, scoreboards
> and settings separate, works out which plugin is calling, and cleans up only the plugin that
> disables. Relocation is still the safest choice for plugins you publish, because each one gets a
> private copy and versions can never clash. Details, Gradle config and the standalone setup are in
> [docs/shading.md](docs/shading.md).

### 4. Set `api-version` in `plugin.yml`

```yaml
api-version: '1.13'
```

Without it, 1.13+ servers load your plugin in legacy-material mode. Older servers ignore the key, so
one jar still works everywhere.

---

## Quick start

```java
public final class MoonPrison extends MoonPlugin {

    private Messages messages;

    @Override
    protected void enable() {
        saveDefaultConfig();
        messages = new Messages(this);                         // messages.yml

        registerCommands(new RankupCommand(this));            // no plugin.yml needed
        registerListeners(new MineListener(this));

        new BoardManager(new PrisonBoard(this), 20).start();  // sidebar for everyone
    }

    @Override
    protected void disable() {
        // save your data. MoonLib then closes menus, removes boards,
        // unregisters commands and cancels tasks automatically.
    }
}
```

That is the whole setup. Read [docs/getting-started.md](docs/getting-started.md) for a guided tour,
or browse the complete, buildable [example plugin](example-plugin).

---

## A taste of every module

### Commands

```java
public final class PayCommand extends MoonCommand {

    public PayCommand() {
        super("pay");
        description("Send money to another player");
        playerOnly();
        argument("target", ArgumentTypes.PLAYER);
        argument("amount", ArgumentTypes.POSITIVE_DOUBLE);   // accepts 5k, 2.5m, 1b
        cooldown(3, TimeUnit.SECONDS);
    }

    @Override
    protected void execute(CommandContext ctx) {
        Player target = ctx.get("target");
        double amount = ctx.get("amount");
        ctx.check(!target.equals(ctx.player()), "&cYou can't pay yourself.");
        ctx.check(VaultEconomy.withdraw(ctx.player(), amount), "&cYou can't afford that.");
        VaultEconomy.deposit(target, amount);
        ctx.reply("&aSent &e{amount} &ato &f{name}", "amount", VaultEconomy.format(amount), "name", target.getName());
    }
}
```

Or inline:

```java
CommandBuilder.create("heal")
        .permission("myplugin.heal")
        .optionalArgument("target", ArgumentTypes.PLAYER)
        .executes(ctx -> Players.heal(ctx.has("target") ? ctx.<Player>get("target") : ctx.player()))
        .register();
```

### Menus

```java
public final class UpgradesMenu extends Menu {

    public UpgradesMenu(Player player) {
        super(player, "&8Pickaxe Upgrades", 3);
    }

    @Override
    protected void build() {
        pattern("#########",
                "#..E.F..#",
                "####X####");
        bind('#', ItemBuilder.filler("GRAY_STAINED_GLASS_PANE"));
        bind('E', Button.of(ItemBuilder.of("DIAMOND_PICKAXE").name("&bEfficiency").glow().build(),
                click -> { upgrades.buy(click.player(), "efficiency"); click.refresh(); }));
        bind('X', Button.of(ItemBuilder.of("BARRIER").name("&cClose").build(), MenuClick::close));
    }
}
```

### Scoreboards

```java
new BoardManager(new BoardProvider() {
    public String title(Player p) { return "&d&lMOON PRISON"; }
    public List<String> lines(Player p) {
        return Arrays.asList("", "&fRank: &d" + ranks.of(p), "&fBlocks: &e" + Numbers.commas(blocks.of(p)), "");
    }
}, 20).usePlaceholderApi(true).start();
```

### Multi-version

```java
ItemStack pane = Materials.item("LIME_STAINED_GLASS_PANE");   // STAINED_GLASS_PANE:5 on 1.8
Sounds.play(player, "ENTITY_EXPERIENCE_ORB_PICKUP");          // ORB_PICKUP on 1.8
Enchants.get("UNBREAKING");                                    // DURABILITY before 1.20.5
Titles.send(player, "&6RANK UP", "&7You are now &eB");        // packets on 1.8, Adventure on Paper
if (ServerVersion.atLeast(1, 14)) { /* custom model data, PDC... */ }
```

### Scheduler (Folia-ready)

```java
Tasks.supplyAsync(() -> database.load(uuid))
     .thenAccept(data -> Tasks.entity(player, () -> apply(player, data)));
TaskHandle reset = Tasks.timer(0, 20 * 60, mines::resetAll);
```

### Multiple plugins

```java
// Nothing to do: two, five or twenty plugins can call MoonLib.init(this) on one server.
MoonLib.mode();                                    // ISOLATED or SHARED
MoonLib.plugins();                                 // everyone using this copy
MoonLib.runAs(plugin, () -> Tasks.timer(0, 20, tick)); // explicit ownership, when you need it
```

### Items, config, events, input & utilities

```java
ItemStack crate = ItemBuilder.skull().texture("e3b6b6e6...").name("&dMoon Crate").tag("crate", "moon").build();

ConfigFile mines = new ConfigFile(this, "mines.yml");          // copied from the jar, new keys merged on update
JsonFile stats = DataFile.json(this, "data/stats.json");
stats.set("players." + uuid, playerData).save();             // your own classes, ItemStacks, UUIDs...
DataStore<PlayerData> players = DataStore.json(this, "players", PlayerData.class);
players.saveAsync(uuid, data);                                // plugins/YourPlugin/players/<uuid>.json
messages.send(player, "rankup.success", "rank", "B");

Events.subscribe(BlockBreakEvent.class).ignoreCancelled()
      .filter(e -> mine.contains(e.getBlock().getLocation()))
      .handler(this::onMineBreak);

ChatInput.ask(player).prompt("&eName your mine:").onInput(name -> rename(player, name)).start();

Text.color("&#FFD700&lGold &7text");   Text.gradient("MOON", "#7F7FD5", "#91EAE4");
Durations.parse("1h30m");              Numbers.compact(1_250_000);   // "1.25M"
Cooldown<UUID> kits = Cooldown.of(10, TimeUnit.MINUTES);
new Cuboid(pos1, pos2).fill(composition);
```

---

## Documentation

| Guide | Covers |
|---|---|
| [Getting started](docs/getting-started.md) | Setup, project structure, your first command and menu |
| [Deployment & multiple plugins](docs/shading.md) | Relocated, shared and standalone modes, how sharing works, Maven/Gradle configs, troubleshooting |
| [Commands](docs/commands.md) | `MoonCommand`, `CommandBuilder`, arguments, custom types, messages |
| [Menus](docs/menus.md) | `Menu`, layouts, `PaginatedMenu`, `ConfirmMenu`, safety guarantees |
| [Scoreboards](docs/scoreboards.md) | `Board`, `BoardManager`, limits per version |
| [Multi-version support](docs/multi-version.md) | How MoonLib stays compatible, and how to write compatible code yourself |
| [Scheduler](docs/scheduler.md) | `Tasks`, Folia, async patterns |
| [Items](docs/items.md) | `ItemBuilder`, `Skulls`, `ItemTags`, `Items` |
| [YAML & JSON files](docs/data-files.md) | `DataFile`, `YamlFile`, `JsonFile`, `DataStore`, storing your own classes |
| [Config & messages](docs/config-and-messages.md) | `ConfigFile`, `Messages`, translating built-in messages |
| [Events & chat input](docs/events-and-input.md) | `Events`, `ChatInput` |
| [Utilities & hooks](docs/utilities.md) | `Text`, `Titles`, `Durations`, `Numbers`, `Cooldown`, `Locations`, `Cuboid`, `Players`, `Log`, PlaceholderAPI, Vault |

Every public class and method also has full Javadoc. Generate the HTML with `mvn -P release package`
(output in `target/apidocs`) or just hover in your IDE.

---

## Project layout

```
MoonLib/
├── pom.xml                       Maven build (library)
├── src/main/java/dev/lunamc/moonlib/
│   ├── MoonLib.java              lifecycle (init / shutdown)
│   ├── MoonPlugin.java           JavaPlugin base class
│   ├── command/                  command framework
│   ├── menu/                     inventory GUIs
│   ├── scoreboard/               sidebars
│   ├── version/                  version detection + cross-version lookups
│   │   └── adapter/              internal per-version implementations
│   ├── scheduler/                Bukkit / Folia scheduler
│   ├── item/                     items, heads, tags
│   ├── config/                   YAML/JSON files, object mapping, data stores, messages
│   ├── event/                    functional listeners
│   ├── input/                    chat prompts
│   ├── hook/                     PlaceholderAPI, Vault
│   └── util/                     everything else
├── src/test/java/                unit tests (JUnit 5)
├── docs/                         guides
├── example-plugin/               a complete plugin that shades MoonLib
└── standalone-plugin/            MoonLib as an installable plugin (shared by dependants)
```

---

## Building

```bash
mvn clean install              # compile, test, install to ~/.m2
mvn clean install -P release   # also attach -sources.jar and -javadoc.jar
```

Requires **JDK 21 or newer** (the Spigot API it compiles against is Java 21 bytecode). The output
is Java 8 bytecode, so the same jar runs on every supported server.

To compile against a different API version, override the property:

```bash
mvn clean install -Dspigot.version=1.21.1-R0.1-SNAPSHOT
```

---

## Licence

MIT. See [LICENSE](LICENSE).
