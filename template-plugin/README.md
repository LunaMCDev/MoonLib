# MoonTemplate

A ready-to-use starting point for a MoonLib plugin, with a development setup where **you edit code,
press Build, and the running test server picks it up straight away. No restart, no copying jars.**

| What you get | Where |
|---|---|
| One-click local Paper test server in IntelliJ | `.run/`, `tools/DevServer.java` |
| Code changes applied live (HotSwap + JetBrains Runtime) | `.run/Dev Server.run.xml` |
| Modules re-enabled automatically after each build | `dev/DevMode.java` |
| `config.yml` and `messages.yml` reload when you save them | `TemplatePlugin.java` |
| Feature modules you can toggle in config | `module/` |
| Annotated `/template` command with tab completion | `command/TemplateCommand.java` |
| SQLite storage (MySQL by changing config), migrations, player cache | `data/` |
| Sidebar, boss bar, titles, rich-text gradients, a menu, holograms | `module/`, `menu/` |
| Release build with MoonLib relocated | `mvn package` |

---

## 1. First-time setup (5 minutes)

1. **Open the project.** In IntelliJ: *File → Open…* and pick this folder's `pom.xml`, then
   *Open as Project*. Wait for Maven to finish importing.

2. **Install a JetBrains Runtime (JBR).** This is what makes hot-swapping powerful: a normal JDK can
   only swap changed *method bodies*; JBR also allows adding and removing methods and fields.
   *File → Project Structure → SDKs → + → Download JDK…*, then:
   - **Vendor:** JetBrains Runtime
   - **Version:** **25** (Minecraft 26.x needs Java 25; also set it as the project SDK, since building
     against the 26.3 API needs JDK 25)

3. **Download the test server.** Run the **Setup Dev Server** run configuration (top-right run menu).
   It downloads Paper **26.3** (the latest Minecraft release, matching the API the template compiles against) into `run/`, verifies its checksum, and writes
   `eula.txt` and a development `server.properties`. Running the server means you accept the
   [Minecraft EULA](https://aka.ms/MinecraftEULA).
   - Another version: edit the configuration's command line to `validate -Psetup-server -Ddev.paper.version=latest` (or `26.2`, `1.21.11`...)
   - Update Paper later: run it again (it only downloads when there is a newer build)

4. **Point the Dev Server at the JBR.** *Run → Edit Configurations… → Dev Server → JRE* and choose the
   JetBrains Runtime from step 2.

5. **Let IntelliJ swap classes without asking.** *Settings → Build, Execution, Deployment →
   Debugger → HotSwap* → **Reload classes after compilation: Always**.

## 2. Daily workflow

1. Start **Dev Server** with **Debug** (the bug icon, or `Shift+F9`), *not* Run. Hot-swapping only
   works under the debugger. Maven builds the plugin into `run/plugins/` first, then Paper starts.
2. Join `localhost` in Minecraft. Type `op <yourname>` in the IntelliJ console to get admin
   commands.
3. **Change code, then press Build (`Ctrl+F9`, `⌘F9` on macOS).** IntelliJ compiles and swaps the
   changed classes into the running server. A second later `DevMode` re-enables every module
   with the new code and tells you in chat:
   `[Dev] 2 class file(s) rebuilt: modules reloaded with the new code.`
4. **Edit config while it runs.** Open `run/plugins/MoonTemplate/config.yml`, change a sidebar line,
   save. It is live within a second. Same for `messages.yml`.
5. Breakpoints work too: click in the gutter, trigger the code in game, inspect variables.
   (`-Ddisable.watchdog=true` stops Paper from killing the server while you are paused.)

### What applies live, and what needs a restart

| Change | Live? |
|---|---|
| Code inside methods (commands, events, menus, module logic) | ✅ Build |
| New methods or fields in existing classes | ✅ Build, **with JBR** (plain JDK: restart) |
| `config.yml` / `messages.yml` in `run/plugins/MoonTemplate/` | ✅ Save the file |
| Code in a module's `enable()` | ✅ Build: DevMode re-enables modules |
| A brand-new class | ⟳ Restart Dev Server (`Ctrl+F5`). DevMode tells you when |
| `plugin.yml`, `pom.xml`, the main class's `enable()` | ⟳ Restart Dev Server |
| Default `config.yml` in `src/main/resources` | ⟳ Restart (or edit the copy in `run/plugins/`) |

That is why the template keeps features in **modules**: their `enable()` re-runs on every build,
so almost everything you write is hot-reloadable. The main class only wires things together.

## 3. Building for release

Run **Build Release Jar** (or `mvn clean package`). The jar is `target/MoonTemplate-1.0.0.jar`, with
MoonLib relocated into your own package so it can never clash with another plugin's copy.

| Build | Command | MoonLib | Output |
|---|---|---|---|
| Release | `mvn package` | relocated | `target/` |
| Dev | `mvn package -Pdev` | not relocated (needed for hot-swap) | `run/plugins/` |

Never ship the dev jar.

## 4. Make it yours

1. In `pom.xml`: change `groupId`, `artifactId`, `name` and `relocation.package`.
2. Rename the package `com.example.template` (IntelliJ: right-click → *Refactor → Rename*, rename the
   package and directory).
3. In `plugin.yml`: update `main`, `description`, `author` and the permission names.
4. Rename `TemplateCommand` and its `@Command("template")`.
5. Delete the example modules you do not need, and add your own:

```java
public final class MinesModule extends MoonModule {
    public MinesModule(MyPlugin plugin) { super("mines"); }

    @Override
    protected void enable() {
        on(BlockBreakEvent.class, event -> { /* ... */ });   // unregistered on disable/reload
        command(new MineCommand());                          // same
        track(Tasks.timer(0, 20 * 60, this::resetMines));    // cancelled on disable/reload
    }
}
```

Register it in `TemplatePlugin.enable()` and it gets a `modules.mines` toggle in config.yml
automatically.

## 5. Troubleshooting

| Problem | Fix |
|---|---|
| `Unable to access jarfile …/run/server.jar` | Run **Setup Dev Server** first |
| `UnsupportedClassVersionError` or "requires running the server with Java 25" | The Dev Server's JRE is too old: 26.x needs Java 25, 1.20.5+ needs 21 |
| IntelliJ: *Hot Swap failed … add method not implemented* | The Dev Server is not running on the JetBrains Runtime (step 4), or you started it with Run instead of Debug |
| Changes do nothing after Build | Started with Run instead of Debug, or HotSwap is set to *Never* (step 5) |
| `[Dev] New class … needs a restart` | Expected: restart the Dev Server once |
| `Could not find artifact com.github.LunaMCDev:MoonLib` | Push a `1.4.0` tag to the MoonLib repo so JitPack can build it, or run `mvn install` in MoonLib and set `moonlib.groupId` to `dev.lunamc` and `moonlib.artifactId` to `moonlib` in `pom.xml` |
| Port 25565 already in use | Change `server-port` in `run/server.properties` |
| Can't join with an offline/test account | Set `online-mode=false` in `run/server.properties` (local testing only) |

## Project layout

```
template-plugin/
├── pom.xml                         release / dev / setup-server profiles
├── .run/                           IntelliJ run configurations (shared, committed)
│   ├── Dev Server.run.xml
│   ├── Setup Dev Server.run.xml
│   └── Build Release Jar.run.xml
├── tools/DevServer.java            downloads Paper (Fill API), writes eula/server.properties
├── run/                            the test server (git-ignored, created by setup)
└── src/main/
    ├── java/com/example/template/
    │   ├── TemplatePlugin.java     wiring only
    │   ├── command/                /template (annotated)
    │   ├── data/                   PlayerData + SQL repository
    │   ├── module/                 stats, welcome, sidebar
    │   ├── menu/                   modules menu
    │   └── dev/DevMode.java        auto-reload after builds (dev server only)
    └── resources/                  plugin.yml, config.yml, messages.yml
```
