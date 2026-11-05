# FAQ

### Should I relocate MoonLib?

Yes, for anything you publish. Relocation gives your plugin a private copy, so another plugin can
never load a different MoonLib version under you. Shared and standalone modes are there for your own
plugin suites. See [shading.md](shading.md).

### Can I write my plugin in Kotlin?

Yes. MoonLib is a normal Java library, so Kotlin plugins use it directly (lambdas, SAM interfaces
and builders all work naturally). MoonLib itself stays in Java so that Java plugins get a clean API
and no plugin has to ship the Kotlin standard library just to use it.

### Does MoonLib work on Folia?

Yes. Use `Tasks.entity(...)` for anything touching a player or entity and `Tasks.at(location, ...)`
for blocks; `Tasks.sync` runs on the global region. Menus, commands, chat input, scoreboards and
player caches are already Folia-aware. Add `folia-supported: true` to `plugin.yml`.

### Why is my database call logging "A blocking database call ran on the server thread"?

You called a blocking method (`query`, `update`...) from the server thread, which freezes the game
until the database answers. Use the `...Async` variant, or wrap the work in `db.async(...)`. The
warning is shown once per database.

### SQLite says "database is locked"

SQLite allows one writer at a time. Keep `pool-size` at `0` (automatic, which is 1 for SQLite), and
do not open the same file from two plugins. MoonLib also enables WAL mode and a 5-second busy
timeout on every SQLite connection.

### How do I store data per player?

Use [`PlayerCache`](player-data.md). It loads before join, saves on quit, autosave and shutdown, and
handles failed loads and quick rejoins safely.

### Can server owners switch between SQLite and MySQL?

Yes: build the database with `Database.fromConfig(plugin, config, "database")` and they change
`type:` in config.

### My hologram/boss bar is still there after `/reload`

MoonLib removes them when the plugin shuts down MoonLib. If you call `MoonLib.init` yourself instead
of extending `MoonPlugin`, make sure `onDisable` calls `MoonLib.shutdown(this)`.

### Are rich-text messages safe to use with player input?

Yes. Placeholder values are inserted literally (a name like `<red>Bob` cannot inject formatting),
and `RichText.escape(text)` escapes text you put into a template yourself.

### Where is the Javadoc?

Every public class and method is documented. Build the HTML with `mvn -P release package`
(`target/apidocs`), read it in your IDE, or see the published site if the repository has GitHub
Pages enabled (the `docs` workflow publishes it).

### How do I get changes to show up without restarting my test server?

Use the [template plugin](../template-plugin) and its *Dev Server* run configuration: build in
IntelliJ and the running server picks up your code. See [dev-workflow.md](dev-workflow.md).
