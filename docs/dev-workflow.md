# Development workflow: test server and hot reload

The fastest way to build a plugin is to never restart the server. The
[template plugin](https://github.com/LunaMCDev/MoonLib-Template) and the [example plugin](https://github.com/LunaMCDev/MoonLib-Example) both set this up in
IntelliJ IDEA:

1. **Debug** the *Dev Server* run configuration: the Ant `dev` target builds your plugin straight
   into a local Paper server's `plugins/` folder, and the server starts in IntelliJ's console.
2. Edit code and press **Build** (`Ctrl+F9`): IntelliJ hot-swaps the changed classes into the running
   server, and the template's `DevMode` re-enables your modules with the new code.
3. Edit `config.yml` or `messages.yml` in the server folder: they reload on save.

The template's [README](https://github.com/LunaMCDev/MoonLib-Template#readme) has the step-by-step setup. This page explains
how it works, so you can add it to an existing plugin.

## The Ant build

Each plugin has a `build.xml` that reads **everything from `pom.xml`**: the plugin name and version,
the Java level, the Paper version for the test server, and the dependencies (resolved from Maven
Central, the Spigot repository and JitPack by the Maven Resolver Ant Tasks, downloaded once into
`.ant/`). So the pom stays the single place to configure the plugin, and Maven and Ant always agree.

| Target | Does |
|---|---|
| `setup-server` | Downloads Paper into `run/` (runs `tools/DevServer.java`) |
| `dev` (default) | Compiles, filters `plugin.yml`, bundles MoonLib (unrelocated) and writes `run/plugins/<Name>.jar` |
| `run` | `dev`, then starts the server with JetBrains Runtime flags and a debug port (5005) |
| `clean` | Deletes `build/` |

Three ways to work, all with hot swap:

| Start with | Build | Hot swap via |
|---|---|---|
| **Dev Server** run configuration (Debug) | Ant `dev`, automatically | IntelliJ's debugger, directly |
| **Dev Server (Maven)** run configuration (Debug) | Maven `package -Pdev`, automatically | IntelliJ's debugger, directly |
| Ant `run` target, then **Attach Debugger** (Debug) | Ant `dev` | the attached debugger (port 5005) |

Release jars still come from Maven (`mvn clean package`), because relocating MoonLib into your
package needs the Maven shade plugin. Local overrides such as `debug.port=5006` or
`server.memory=4G` go in a git-ignored `build.local.properties`.

## How the pieces fit

```
IntelliJ "Build"
   │ compiles to target/classes
   ├──► debugger hot-swaps changed classes into the running JVM   (JetBrains Runtime)
   └──► DevMode sees target/classes change (FileWatcher.watchTree)
            └──► ModuleManager.reloadAll(): enable() runs again with the new code
```

### 1. Hot-swapping

The JVM can replace a loaded class's bytecode while it runs (JVMTI class redefinition), which
IntelliJ does automatically after a build when the program runs under the **debugger**. Standard
JDKs only allow changed method bodies. The **JetBrains Runtime** with
`-XX:+AllowEnhancedClassRedefinition` also allows adding, removing and renaming methods and
fields. The template also passes `-XX:+IgnoreUnrecognizedVMOptions`, so the same run configuration
still starts on a normal JDK (with body-only swapping).

### 2. Why the dev build does not relocate MoonLib

Relocation rewrites every reference to `com.lunamcdev.moonlib` inside *your* classes. IntelliJ compiles
the unrelocated versions, so swapping them into a relocated jar would point at classes that do not
exist. The Ant `dev` target and the `dev` Maven profile therefore bundle MoonLib without relocating. MoonLib's
[shared mode](shading.md) makes that safe. Release builds still relocate.

### 3. Re-running setup code

A hot-swap changes code that runs *from now on*: the next event, command or menu click. Code that
already ran, such as a module's `enable()`, is not repeated. That is the reason for
[modules](modules.md): `ModuleManager.reloadAll()` stops each module (undoing its listeners,
commands and tasks) and starts it again with the new code. DevMode does this after every build:

```java
if (Boolean.getBoolean("moonlib.dev")) {                       // only on the dev server
    FileWatcher.watchTree(this, new File(System.getProperty("moonlib.dev.classes")), changed -> {
        if (changed.getName().endsWith(".class")) {
            Tasks.later(30, modules::reloadAll);              // after IntelliJ has finished swapping
        }
    });
}
```

(The template's version also batches changes and spots new classes.)

### 4. Limits

| Needs a restart | Why |
|---|---|
| A brand-new class | It is not in the running plugin jar, so the plugin's class loader cannot find it |
| `plugin.yml`, dependencies | Read once when the plugin loads |
| The main class's `enable()` | It is not a module; keep it to wiring |
| Static initialisers, enum constants | Already run |

## Paper's watchdog

Paper kills a server whose main thread stops responding for too long, which is exactly what a
breakpoint does. The template's run configuration passes `-Ddisable.watchdog=true`. Never use that
flag in production.

## Downloading Paper

`tools/DevServer.java` (run by the *Setup Dev Server* configuration) uses PaperMC's Fill v3 API,
picks the newest **stable** build (or the version you pin), verifies its SHA-256, and writes a
development `server.properties` and `eula.txt`. It sends an identifying `User-Agent`, which the API
requires. Minecraft 26.1+ needs Java 25, 1.20.5–1.21.x need Java 21.
