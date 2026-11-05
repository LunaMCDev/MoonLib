# Deploying MoonLib: relocated or shared

Any number of plugins can use MoonLib on the same server. There are two ways to ship it, and
both work without errors. Pick per plugin; you can even mix them on one server.

| Mode | How | Each plugin gets | Best for |
|---|---|---|---|
| **Relocated** (recommended) | Shade + relocate into your own package | Its own private copy | Public plugins, anything you hand to other servers |
| **Shared shaded** | Shade without relocating | One copy shared with other unrelocated plugins | Quick setups, your own plugin suites |

## How sharing works

When more than one plugin uses the same copy of MoonLib (shared mode), MoonLib
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


---

## Mode 1: relocated (recommended)

Relocation renames MoonLib's package inside your jar, so your plugin has a completely private copy.
There is nothing to coordinate, no version can ever clash, and caller detection is never needed,
because each copy has exactly one plugin.

### Maven

```xml
<dependency>
    <groupId>com.lunamcdev</groupId>
    <artifactId>moonlib</artifactId>
    <version>1.2.0-beta</version>
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
                        <pattern>com.lunamcdev.moonlib</pattern>
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
    implementation("com.lunamcdev:moonlib:1.2.0-beta")
}

tasks.shadowJar {
    relocate("com.lunamcdev.moonlib", "me.you.myplugin.libs.moonlib")
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
  A normal restart or `/reload` is fine. If you hot-swap plugins often, relocate.

---

## `minimizeJar`

`<minimizeJar>true</minimizeJar>` is safe in both modes: MoonLib's version-specific classes are
all referenced directly. If you ever see a `NoClassDefFoundError` for a MoonLib class, turn it off
first.

## Troubleshooting

| Symptom | Cause | Fix |
|---|---|---|
| Warning: "X was built with MoonLib 1.2.0, but MoonLib 1.1.0 (from Y) is the copy in use" | Shared copy older than what X needs | Update Y, or relocate MoonLib in X |
| `NoClassDefFoundError: com/lunamcdev/moonlib/...` | Not shaded (`provided` scope), or the shade plugin is missing | Use `compile` scope and the shade plugin |
| `IllegalStateException: MoonLib has not been initialised` | Using MoonLib before `MoonLib.init` | Extend `MoonPlugin`, or call `MoonLib.init(this)` first in `onEnable` |
| A task or menu disappears when a *different* plugin disables | It was created from unattributed code and owned by the host | Wrap that code in `MoonLib.runAs(yourPlugin, ...)` |
| `UnsupportedClassVersionError` on an old server | Your plugin targets a newer Java than the server runs | Compile with `<maven.compiler.release>8</maven.compiler.release>` for 1.8 to 1.16 servers |
