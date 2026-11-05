# Getting started

This guide takes you from an empty Maven project to a working plugin with a command, a menu and a
scoreboard, all built on MoonLib.

## 1. Install MoonLib

MoonLib is consumed from your local Maven repository:

```bash
cd MoonLib
mvn clean install
```

Re-run this whenever you change or update MoonLib.

## 2. Create your plugin project

A minimal `pom.xml` (the complete version is in [`example-plugin/pom.xml`](../example-plugin/pom.xml)):

```xml
<properties>
    <maven.compiler.release>8</maven.compiler.release>   <!-- or 17 / 21 if you only target modern servers -->
</properties>

<repositories>
    <repository>
        <id>spigotmc-repo</id>
        <url>https://hub.spigotmc.org/nexus/content/repositories/snapshots/</url>
    </repository>
</repositories>

<dependencies>
    <dependency>
        <groupId>org.spigotmc</groupId>
        <artifactId>spigot-api</artifactId>
        <version>26.3-R0.1-SNAPSHOT</version>
        <scope>provided</scope>
    </dependency>
    <dependency>
        <groupId>dev.lunamc</groupId>
        <artifactId>moonlib</artifactId>
        <version>1.4.0</version>
    </dependency>
</dependencies>
```

Then add the shade plugin with a relocation, exactly as shown in [shading.md](shading.md). (Relocating
is recommended but optional: several plugins can also share one copy, or depend on the standalone
MoonLib plugin. The same page explains all three setups.)

> **Paper API instead of Spigot API?** That works too: depend on `io.papermc.paper:paper-api` as
> `provided`. MoonLib itself only needs the Spigot API surface.

## 3. `plugin.yml`

```yaml
name: MyPlugin
version: ${project.version}
main: me.you.myplugin.MyPlugin
api-version: '1.13'
folia-supported: true          # only if you use Tasks.entity/at for world access
softdepend: [PlaceholderAPI, Vault]
```

No `commands:` section is needed. MoonLib registers commands directly.

## 4. The main class

```java
package me.you.myplugin;

import dev.lunamc.moonlib.MoonPlugin;

public final class MyPlugin extends MoonPlugin {

    @Override
    protected void enable() {
        registerCommands(new HelloCommand());
    }
}
```

`MoonPlugin` makes `onLoad`, `onEnable` and `onDisable` final and gives you `load()`, `enable()`
and `disable()` instead. This guarantees that:

1. MoonLib is initialised before your `enable()` runs.
2. If `enable()` throws, the error is logged clearly and the plugin disables itself, instead of
   staying half-enabled.
3. MoonLib shuts down after your `disable()`: *your* menus close, *your* boards are removed, *your*
   commands are unregistered and *your* tasks are cancelled. Other plugins using MoonLib are untouched.
4. A startup line such as `Enabled MyPlugin v1.0.0 on Paper 26.3 in 41ms` is logged.

### Can't extend `MoonPlugin`?

Call the lifecycle yourself:

```java
public final class MyPlugin extends JavaPlugin {
    @Override public void onEnable()  { MoonLib.init(this); /* ... */ }
    @Override public void onDisable() { MoonLib.shutdown(this); }
}
```

## 5. Your first command

```java
public final class HelloCommand extends MoonCommand {

    public HelloCommand() {
        super("hello", "hi");
        description("Say hello");
        optionalArgument("target", ArgumentTypes.PLAYER);
    }

    @Override
    protected void execute(CommandContext ctx) {
        if (ctx.has("target")) {
            Player target = ctx.get("target");
            target.sendMessage(Text.color("&d" + ctx.sender().getName() + " says hello!"));
            ctx.reply("&aSaid hello to &f{name}", "name", target.getName());
        } else {
            ctx.reply("&dHello, {name}!", "name", ctx.sender().getName());
        }
    }
}
```

Tab completion of player names, the usage line (`/hello [target]`) and the "player not online"
error all come for free. See [commands.md](commands.md).

## 6. Your first menu

```java
public final class WelcomeMenu extends Menu {

    public WelcomeMenu(Player player) {
        super(player, "&8Welcome", 3);
    }

    @Override
    protected void build() {
        fillBorder(ItemBuilder.filler("PURPLE_STAINED_GLASS_PANE"));
        set(13, Button.of(ItemBuilder.of("NETHER_STAR").name("&dClaim reward").glow().build(), click -> {
            Players.give(click.player(), new ItemStack(Material.DIAMOND, 3));
            Sounds.play(click.player(), "ENTITY_PLAYER_LEVELUP");
            click.close();
        }));
    }
}
```

Open it from anywhere with `new WelcomeMenu(player).open();`. See [menus.md](menus.md).

## 7. A scoreboard

```java
new BoardManager(new BoardProvider() {
    @Override public String title(Player p) { return "&d&lMY SERVER"; }
    @Override public List<String> lines(Player p) {
        return Arrays.asList("", "&fName: &d" + p.getName(), "&fPing: &a" + ping(p), "", "&7my.server.net");
    }
}, 20).start();
```

See [scoreboards.md](scoreboards.md).

## 8. Build and run

```bash
mvn clean package
```

Copy `target/MyPlugin-1.0.0.jar` into `plugins/` and start the server.

## Where next?

- A test server with hot reload, so you rarely restart: [dev-workflow.md](dev-workflow.md)
- Commands as annotated methods: [commands.md](commands.md#annotated-commands)
- Storage: [database.md](database.md) and [player-data.md](player-data.md)
- Structuring a big plugin: [modules.md](modules.md)
- Gradients and formatting: [rich-text.md](rich-text.md)
- Messages that server owners can translate: [config-and-messages.md](config-and-messages.md)
- Writing code that works on 1.8 and 1.21: [multi-version.md](multi-version.md)
- Async database work and Folia: [scheduler.md](scheduler.md)
