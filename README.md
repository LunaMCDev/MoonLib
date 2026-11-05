# 🌙 MoonLib

**A shadeable foundation for Bukkit, Spigot, Paper, Purpur and Folia plugins.**
Commands (classic or annotated), menus, scoreboards, boss bars, holograms, rich text, SQL with
migrations, safe per-player data, modules, multi-version support, a Folia-aware scheduler, configs
that reload themselves, and the dozens of small helpers every plugin ends up rewriting. You shade
it into each plugin and get straight to the parts that are actually yours.

Starting a new plugin? Use the [**template plugin**](https://github.com/LunaMCDev/MoonLib-Template): a one-click IntelliJ test
server where code changes apply **without restarting**.

| | |
|---|---|
| **Minecraft** | 1.8.8 → latest (including the year-based 26.x releases) |
| **Servers** | CraftBukkit, Spigot, Paper, Purpur, Pufferfish, Folia |
| **Java** | Builds with JDK 25+ against the Minecraft 26.3 API, ships Java 8 bytecode (so it also loads on 1.8 servers) |
| **Runtime dependencies** | None. PlaceholderAPI and Vault are optional soft hooks |
| **Multiple plugins** | Any number per server, relocated or shared, without interfering with each other |
| **Tests** | 118 unit tests plus multi-copy, cross-plugin and lifecycle harnesses |
| **Licence** | MIT |

---

## Contents

- [Features](#features)
- [Installation](#installation)
- [Quick start](#quick-start)
- [A taste of every module](#a-taste-of-every-module)
- [Documentation](#documentation)
- [Example & template plugins, hot reload](#example--template-plugins-hot-reload)
- [Versioning](#versioning)
- [Project layout](#project-layout)
- [Building](#building)

---

## Features

| Module | Package | What you get |
|---|---|---|
| **Core** | `com.lunamcdev.moonlib` | `MoonPlugin` base class, multi-plugin support with per-plugin ownership, ordered shutdown of everything a plugin created |
| **Commands** | `.command` | Subcommands, typed arguments, tab completion, permissions, cooldowns, auto help/usage. No `plugin.yml` entries |
| **Annotated commands** | `.command.annotation` | A whole command tree as plain methods: `@Command`, `@Subcommand`, `@Arg`, `@Greedy`, `@Range`, `@Suggest`, `@Cooldown` |
| **Menus** | `.menu` | Chest GUIs, visual layouts, pagination, confirm dialogs, auto-refresh, dupe-proof click handling |
| **Scoreboards** | `.scoreboard` | Flicker-free per-player sidebars, a manager with a provider and update timer |
| **Displays** | `.display` | Boss bars with countdowns, tab header/footer and names, holograms (text displays on 1.19.4+, armor stands before) |
| **Rich text** | `.util.RichText` | MiniMessage-style `<gradient>`, `<rainbow>`, `<#hex>`, `<bold>`, safe placeholders, on every version without Adventure |
| **Database** | `.database` | SQLite / MySQL / MariaDB with a built-in pool, async queries, transactions, versioned migrations, dialect-aware upserts. No dependency |
| **Player data** | `.player` | `PlayerCache`: load before join, save on quit/autosave/shutdown, safe against failed loads and quick rejoins. Stored as **JSON or YAML files, or SQL**, chosen in code or in config; unchanged players are never rewritten; isolated per plugin |
| **Modules** | `.module` | Split a plugin into toggleable features with dependency order, isolated failures, automatic cleanup and a service registry |
| **Multi-version** | `.version` | Version/software detection, modern material, sound and enchantment names on every version, reflection helpers |
| **Scheduler** | `.scheduler` | One API for Bukkit and **Folia** (global, async, entity and region tasks), `CompletableFuture` helpers |
| **Items** | `.item` | `ItemBuilder`, custom-texture heads, persistent item tags, inventory counting/removal, base64 serialisation |
| **Files & config** | `.config` | Create, edit and save **YAML and JSON** files with one API, store your own classes directly, per-player `DataStore`, auto-merged defaults, broken-file recovery, atomic saves, translatable `Messages` |
| **Events** | `.event` | Lambda listeners with filters, priorities and auto-expiry |
| **Chat input** | `.input` | Ask a player to type something, with validation, timeout and cancel |
| **Hooks** | `.hook` | PlaceholderAPI and Vault economy, with no compile-time dependency |
| **Integrations** | `.integration` | Detect other plugins and link up automatically (`Integrations`), and call each other's features and events with no API jar (`MoonLink`) |
| **Utilities** | `.util` | Colours, titles/action bars, durations, numbers, cooldowns, locations, cuboids, players, logging, **file watching**, **update checks** (Modrinth, Spigot, GitHub, Hangar) |

---

## Installation

### 1. Install MoonLib to your local Maven repository

```bash
git clone https://github.com/LunaMCDev/MoonLib.git
cd MoonLib
mvn clean install
```

Or use it straight from GitHub through [JitPack](https://jitpack.io) (the repository's `jitpack.yml`
installs JDK 25 for the build):

```xml
<repository>
    <id>jitpack.io</id>
    <url>https://jitpack.io</url>
</repository>

<dependency>
    <groupId>com.github.LunaMCDev</groupId>
    <artifactId>MoonLib</artifactId>
    <version>1.2.0-beta</version>   <!-- a release tag, a commit hash, or main-SNAPSHOT -->
</dependency>
```

The JitPack artifact uses the `com.github.LunaMCDev` group, but the Java package is still
`com.lunamcdev.moonlib`, so relocation configs stay the same.

### 2. Add it to your plugin

MoonLib can be relocated into your jar (recommended) or shaded without relocating. Any number of plugins
can use it at the same time in either mode; see [docs/shading.md](docs/shading.md). The relocated setup:

```xml
<dependency>
    <groupId>com.lunamcdev</groupId>
    <artifactId>moonlib</artifactId>
    <version>1.2.0-beta</version>
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
                        <pattern>com.lunamcdev.moonlib</pattern>
                        <!-- change this to a package inside YOUR plugin -->
                        <shadedPattern>me.you.yourplugin.libs.moonlib</shadedPattern>
                    </relocation>
                </relocations>
            </configuration>
        </execution>
    </executions>
</plugin>
```

> **Not relocating is fine too.** Plugins that shade MoonLib unrelocated share one copy. MoonLib keeps each plugin's tasks, menus, commands, scoreboards
> and settings separate, works out which plugin is calling, and cleans up only the plugin that
> disables. Relocation is still the safest choice for plugins you publish, because each one gets a
> private copy and versions can never clash. Details and Gradle config are in
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
or browse the complete, buildable [example plugin](https://github.com/LunaMCDev/MoonLib-Example).

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

### Annotated commands

```java
@Command(value = "mine", permission = "prison.mine")
public final class MineCommand {
    @Subcommand("reset")
    public String reset(CommandSender sender, @Suggest("mines") @Arg("mine") String mine,
                        @Arg(value = "delay", def = "0") @Range(min = 0, max = 300) int delay) {
        mines.reset(mine, delay);
        return "&aResetting &f" + mine;
    }

    List<String> mines(CommandSender sender) { return mineManager.names(); }
}
```

### Rich text, boss bars & holograms

```java
player.sendMessage(RichText.parse("<gradient:#7F7FD5:#91EAE4><bold>MOON PRISON</bold></gradient> <gray>Hi <name>!",
        "name", player.getName()));                          // values can't inject tags
Bar.countdown(player, 30, s -> "&eEvent in &f" + s + "s", Bar.Color.YELLOW);
Hologram top = Hologram.create(spawn, "&d&lTOP MINERS", "&f1. Luna &7- &e1.2M");
```

### Database & player data

```java
Database db = Database.fromConfig(this, loadConfig("config.yml"), "database");   // sqlite or mysql, owner's choice
db.migrations().add(1, "balances", "CREATE TABLE balances (uuid CHAR(36) PRIMARY KEY, tokens BIGINT)").run();

PlayerCache<Balance> balances = PlayerCache.<Balance>builder(this)
        .loader((id, name) -> db.queryOne("SELECT * FROM balances WHERE uuid = ?", Balance::fromRow, id)
                .orElseGet(Balance::new))
        .saver((id, b) -> db.update(db.dialect().upsert("balances", "uuid", "tokens"), id, b.tokens))
        .build();

balances.get(player).tokens += 10;                          // loaded before join, saved automatically
```

### Modules

```java
modules = new ModuleManager(this)
        .toggles(loadConfig("config.yml"), "modules")                  // modules.<id>: false switches one off
        .register(new EconomyModule(), new MinesModule(), new RanksModule());
modules.enableAll();                                          // dependency order; one failing module never stops the rest
```

### Multiple plugins

```java
// Nothing to do: two, five or twenty plugins can call MoonLib.init(this) on one server.
MoonLib.mode();                                    // ISOLATED or SHARED
MoonLib.plugins();                                 // everyone using this copy
MoonLib.runAs(plugin, () -> Tasks.timer(0, 20, tick)); // explicit ownership, when you need it
```

### Plugins that work together

```java
// MoonCrates offers a feature...
MoonLink.provide("crates:give-key", payload -> { /* give the key */ return true; });

// ...MoonQuests uses it whenever MoonCrates is installed, without depending on it
Integrations.when("MoonCrates", crates -> rewards.register("crate-key", (player, crate) ->
        MoonLink.call("crates:give-key", MoonLink.payload("player", player, "crate", crate, "amount", 1))));
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
| [Development workflow](docs/dev-workflow.md) | Local test server, hot reload in IntelliJ, how it works |
| [Deployment & multiple plugins](docs/shading.md) | Relocated and shared modes, how sharing works, Maven/Gradle configs, troubleshooting |
| [Commands](docs/commands.md) | `MoonCommand`, annotated commands, `CommandBuilder`, arguments, custom types, messages |
| [Menus](docs/menus.md) | `Menu`, layouts, `PaginatedMenu`, `ConfirmMenu`, safety guarantees |
| [Scoreboards](docs/scoreboards.md) | `Board`, `BoardManager`, limits per version |
| [Boss bars, tab list & holograms](docs/displays.md) | `Bar`, `TabList`, `Hologram` |
| [Rich text](docs/rich-text.md) | `RichText` tags, placeholders, safety, version fallback |
| [Multi-version support](docs/multi-version.md) | How MoonLib stays compatible, and how to write compatible code yourself |
| [Scheduler](docs/scheduler.md) | `Tasks`, Folia, async patterns |
| [Items](docs/items.md) | `ItemBuilder`, `Skulls`, `ItemTags`, `Items` |
| [YAML & JSON files](docs/data-files.md) | `DataFile`, `YamlFile`, `JsonFile`, `DataStore`, storing your own classes |
| [Config & messages](docs/config-and-messages.md) | `ConfigFile`, `Messages`, auto-reload, translating built-in messages |
| [Database](docs/database.md) | `Database`, queries, async, transactions, migrations, dialects, tuning |
| [Player data](docs/player-data.md) | `PlayerCache` and its guarantees |
| [Modules & services](docs/modules.md) | `MoonModule`, `ModuleManager`, `Services` |
| [Events & chat input](docs/events-and-input.md) | `Events`, `ChatInput` |
| [Integrations](docs/integrations.md) | `Integrations`, `MoonLink`: detecting plugins and making them work together |
| [Utilities & hooks](docs/utilities.md) | `Text`, `Titles`, `Durations`, `Numbers`, `Cooldown`, `Locations`, `Cuboid`, `Players`, `Log`, `FileWatcher`, `UpdateChecker`, PlaceholderAPI, Vault |
| [Cookbook](docs/cookbook.md) | Complete recipes combining several modules |
| [Architecture](docs/architecture.md) | How MoonLib works inside: ownership, shutdown order, threads, versions |
| [Compatibility & stability](docs/compatibility.md) | Feature availability per version, semantic versioning, what counts as API |
| [FAQ](docs/faq.md) | Common questions |

Every public class and method also has full Javadoc, with `@since` on everything added after 1.0.
Generate the HTML with `mvn -P release package` (output in `target/apidocs`) or just hover in your
IDE. The guides also build into a searchable site with `mkdocs serve` (see `mkdocs.yml`); the
`docs` GitHub workflow publishes the site and Javadoc to GitHub Pages.

---

## Example & template plugins, hot reload

Two plugins live in their own repositories, both with the same test-server and hot-swap setup.
They are kept separate from the library on purpose: they use MoonLib exactly as your plugins do,
from JitPack, so they always build against a published version.

- [**MoonLib-Example**](https://github.com/LunaMCDev/MoonLib-Example): a tour of MoonLib's features (commands, menus,
  sidebar, messages, player data in JSON files). Read and poke at it.
- [**MoonLib-Template**](https://github.com/LunaMCDev/MoonLib-Template): a clean starting point for new plugins
  (*Use this template* on GitHub, or download it).

Both build with **Ant** (`build.xml`, driven by `pom.xml`) or Maven:

- **setup-server** (Ant target, or the *Setup Dev Server* run configuration): downloads Paper 26.3, the latest release (PaperMC Fill API,
  checksum-verified), into `run/`;
- **Dev Server**: builds the plugin into the server with Ant and starts it under the debugger
  (or `ant run` plus *Attach Debugger*, or *Dev Server (Maven)*);
- **edit → Build → done**: IntelliJ hot-swaps your classes (JetBrains Runtime allows adding
  methods and fields too) and the template re-enables its modules with the new code, so you
  never restart for most changes;
- config and messages that reload when you save them;
- the template adds example modules (stats, welcome titles and boss bar, sidebar), an annotated
  command, a modules menu and automatic module reload after each build.

Step-by-step setup: the [template's README](https://github.com/LunaMCDev/MoonLib-Template#readme). How it works:
[docs/dev-workflow.md](docs/dev-workflow.md).

---

## Versioning

The version is set **once**, in `pom.xml`, and you don't even edit that by hand:

```bash
java tools/SetVersion.java 1.3.0-beta    # or: ant set-version (asks), or: mvn validate -Pset-version -Dto=1.3.0-beta
```

That updates `pom.xml` and every other place in this repository that shows the version: the install
snippets in this README and the docs, `SECURITY.md` and the bug-report template. `MoonLib.VERSION` (and the shared-mode version check) is written into the
jar from `pom.xml` at build time. CI runs `java tools/SetVersion.java --check` and fails if anything
drifts. Then tag the commit with the same version for JitPack, and once JitPack has built it, bump
`<moonlib.version>` in your plugins (and in MoonLib-Example and MoonLib-Template).

---

## Project layout

```
MoonLib/
├── pom.xml                       Maven build (library)
├── src/main/java/com/lunamcdev/moonlib/
│   ├── MoonLib.java              lifecycle (init / shutdown)
│   ├── MoonPlugin.java           JavaPlugin base class
│   ├── command/                  command framework
│   │   └── annotation/           @Command, @Subcommand, @Arg ...
│   ├── menu/                     inventory GUIs
│   ├── scoreboard/               sidebars
│   ├── display/                  boss bars, tab list, holograms
│   ├── database/                 SQL, pool, transactions, migrations
│   ├── player/                   per-player data cache
│   ├── module/                   modules and services
│   ├── version/                  version detection + cross-version lookups
│   │   └── adapter/              internal per-version implementations
│   ├── scheduler/                Bukkit / Folia scheduler
│   ├── item/                     items, heads, tags
│   ├── config/                   YAML/JSON files, object mapping, data stores, messages
│   ├── event/                    functional listeners
│   ├── input/                    chat prompts
│   ├── hook/                     PlaceholderAPI, Vault
│   ├── integration/              plugin detection, MoonLink cross-plugin calls and events
│   └── util/                     everything else
├── src/test/java/                unit tests (JUnit 5)
├── docs/                         guides
├── tools/SetVersion.java         changes the version everywhere
├── build.xml                     Ant: set-version, check-version
├── .github/                      CI, docs publishing, issue/PR templates
└── mkdocs.yml                    documentation site
```

---

## Building

```bash
mvn clean install              # compile, test, install to ~/.m2
mvn clean install -P release   # also attach -sources.jar and -javadoc.jar
```

Requires **JDK 25 or newer**: MoonLib compiles against the newest API, Spigot **26.3**, which is Java 25
bytecode. The output
is Java 8 bytecode, so the same jar runs on every supported server.

To compile against a different API version, override the property:

```bash
mvn clean install -Dspigot.version=26.2-R0.1-SNAPSHOT
```

---

## Contributing

Bug reports, ideas and pull requests are welcome. See [CONTRIBUTING.md](CONTRIBUTING.md) for the
build, test and style rules, and [SECURITY.md](SECURITY.md) to report a vulnerability privately.

## Licence

MIT. See [LICENSE](LICENSE).
