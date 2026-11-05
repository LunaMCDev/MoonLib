# Deploying MoonLib: relocated, shared or standalone

Any number of plugins can use MoonLib on the same server. There are three ways to ship it, and
all three work without errors. Pick per plugin; you can even mix them on one server.

| Mode | How | Each plugin gets | Best for |
|---|---|---|---|
| **Relocated** (recommended) | Shade + relocate into your own package | Its own private copy | Public plugins, anything you hand to other servers |
| **Shared shaded** | Shade without relocating | One copy shared with other unrelocated plugins | Quick setups, your own plugin suites |
| **Standalone** | Install `MoonLib.jar` in `plugins/`, `depend: [MoonLib]`, `provided` scope | The installed copy | Networks running many of your own plugins, small jars |

## How sharing works

When more than one plugin uses the same copy of MoonLib (shared shaded or standalone mode), MoonLib
keeps **separate state for every plugin**:

- **Ownership.** Every task, menu, command, scoreboard manager, chat prompt, event subscription,
  item-tag key and log line belongs to the plugin that created it. MoonLib works this out from the
  call stack: it finds the first class that belongs to a registered plugin, using each plugin's
  class loader. Code running inside a MoonLib task, menu click, command or event handler runs *as*
  its owner automatically.
- **Isolated shutdown.** When a plugin disables, `MoonLib.shutdown(plugin)` closes only *its*
  menus, stops only *its* boards, cancels only *its* tasks and unregisters only *its* commands.
  Everyone else keeps running.
- **Listener hosting.** MoonLib's two shared listeners (menus and chat input) are registered under
  a *host* plugin, the first one to initialise. If the host disables while others still use MoonLib,
  the listeners move to the next plugin automatically.
- **Per-plugin settings.** `Log.setDebug`, `CommandMessages.load` and `CommandMessages.set` affect
  only the calling plugin.
- **Version checks.** Each plugin's jar records which MoonLib version it was built with. If a plugin
  was built with a *newer* MoonLib than the copy that is loaded, a clear warning names both plugins
  and the fix. It doesn't throw.

Calling `MoonLib.init(plugin)` twice, calling it from several plugins, or calling `shutdown` for a
plugin that never initialised are all harmless.

### Attributing code explicitly

MoonLib can't attribute code that isn't in any plugin's jar, such as a utility library that two of
your plugins share and that is itself loaded by only one of them. Wrap such calls:

```java
MoonLib.runAs(plugin, () -> Tasks.timer(0, 20, this::tick));
Menu menu = MoonLib.callAs(plugin, () -> new ShopMenu(player));
```

Unattributed calls fall back to the host plugin, so they still work. They are just owned by the
host and cleaned up with it.

### Inspecting at runtime

```java
MoonLib.mode();            // ISOLATED or SHARED
MoonLib.plugins();         // every plugin using this copy
MoonLib.host();            // who hosts the shared listeners
MoonLib.context(plugin);   // per-plugin state, including bundledVersion()
MoonLib.version();         // the copy actually running
MoonLib.isVersionAtLeast("1.1.0");
```

The standalone plugin adds `/moonlib` (permission `moonlib.admin`), which lists all of this in game.

---

## Mode 1: relocated (recommended)

Relocation renames MoonLib's package inside your jar, so your plugin has a completely private copy.
There is nothing to coordinate, no version can ever clash, and caller detection is never needed,
because each copy has exactly one plugin.

### Maven

```xml
<dependency>
    <groupId>dev.lunamc</groupId>
    <artifactId>moonlib</artifactId>
    <version>1.1.1-alpha</version>
</dependency>
```

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
                        <shadedPattern>me.you.myplugin.libs.moonlib</shadedPattern>
                    </relocation>
                </relocations>
            </configuration>
        </execution>
    </executions>
</plugin>
```

### Gradle (Kotlin DSL)

```kotlin
plugins {
    java
    id("com.gradleup.shadow") version "8.3.5"
}

repositories {
    mavenLocal()
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
}

dependencies {
    compileOnly("org.spigotmc:spigot-api:26.3-R0.1-SNAPSHOT")
    implementation("dev.lunamc:moonlib:1.1.1-alpha")
}

tasks.shadowJar {
    relocate("dev.lunamc.moonlib", "me.you.myplugin.libs.moonlib")
    archiveClassifier.set("")
}

tasks.build { dependsOn(tasks.shadowJar) }
```

Check the result: every MoonLib path in the jar should start with your package.

```bash
unzip -l target/MyPlugin-1.0.0.jar | grep moonlib | head
```

---

## Mode 2: shared shaded

Same as mode 1 without the `<relocations>` block. Every plugin that does this shares one copy at
runtime, using the sharing system described above.

Things to know:

- **The first-loaded copy wins.** If plugins bundle different MoonLib versions, only one version is
  actually used and you get a warning. Keep your plugins on the same MoonLib version, or relocate.
- **Plugin managers.** If you hot-unload (e.g. with PlugMan) the plugin whose jar MoonLib's classes
  happened to be loaded from, the others can lose access to MoonLib classes they haven't used yet.
  A normal restart or `/reload` is fine. If you hot-swap plugins often, relocate or use standalone mode.

---

## Mode 3: standalone plugin

Build the standalone plugin and install it once:

```bash
cd MoonLib && mvn clean install
cd standalone-plugin && mvn clean package
# copy standalone-plugin/target/MoonLib-1.1.1-alpha.jar into plugins/
```

In each plugin that uses it:

```xml
<dependency>
    <groupId>dev.lunamc</groupId>
    <artifactId>moonlib</artifactId>
    <version>1.1.1-alpha</version>
    <scope>provided</scope>   <!-- not shaded -->
</dependency>
```

```yaml
# plugin.yml
depend: [MoonLib]
```

Your plugin still calls `MoonLib.init(this)` / extends `MoonPlugin` as normal. The jar stays tiny,
and updating MoonLib means replacing one file. If you need a feature from a newer MoonLib,
check for it:

```java
if (!MoonLib.isVersionAtLeast("1.1.0")) {
    getLogger().warning("Please update MoonLib to 1.1.0 or newer");
}
```

---

## `minimizeJar`

`<minimizeJar>true</minimizeJar>` is safe in modes 1 and 2: MoonLib's version-specific classes are
all referenced directly. If you ever see a `NoClassDefFoundError` for a MoonLib class, turn it off
first.

## Troubleshooting

| Symptom | Cause | Fix |
|---|---|---|
| Warning: "X was built with MoonLib 1.2.0, but MoonLib 1.1.0 (from Y) is the copy in use" | Shared copy older than what X needs | Update Y (or the standalone plugin), or relocate MoonLib in X |
| `NoClassDefFoundError: dev/lunamc/moonlib/...` | Not shaded (`provided` scope without the standalone plugin installed), or the shade plugin is missing | Shade it, or install the standalone plugin and add `depend: [MoonLib]` |
| `IllegalStateException: MoonLib has not been initialised` | Using MoonLib before `MoonLib.init` | Extend `MoonPlugin`, or call `MoonLib.init(this)` first in `onEnable` |
| A task or menu disappears when a *different* plugin disables | It was created from unattributed code and owned by the host | Wrap that code in `MoonLib.runAs(yourPlugin, ...)` |
| `UnsupportedClassVersionError` on an old server | Your plugin targets a newer Java than the server runs | Compile with `<maven.compiler.release>8</maven.compiler.release>` for 1.8 to 1.16 servers |
