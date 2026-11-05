# Contributing to MoonLib

Thanks for helping! This page covers how to build, test and shape a change so it can be merged
quickly. For how MoonLib works inside, read [docs/architecture.md](docs/architecture.md) first.

## Building

Requirements: **JDK 25+** and **Maven 3.6.3+**. MoonLib compiles against the newest Spigot API (26.3),
which is Java 25 bytecode; the library itself is still compiled to Java 8.

```bash
mvn clean install              # compile, unit tests, install to ~/.m2
mvn -P release package         # also sources + Javadoc (Javadoc lint must pass)
```

The library compiles to **Java 8 bytecode** (`--release 8`) so it loads on 1.8 servers. That means
no `var`, records, switch expressions, `List.of`, `String#isBlank` and so on in `src/main`.


When a new Minecraft version comes out, bump `spigot.version` in `pom.xml` (and in the
MoonLib-Example and MoonLib-Template repositories, with their `dev.paper.version`), add a `ServerVersion` constant, and update the version the test
harnesses report.

## Tests

- Unit tests live in `src/test/java` (JUnit 5) and must run **without a Minecraft server**. Fake
  Bukkit types with `java.lang.reflect.Proxy` or small classes, and JDBC with
  `database/FakeJdbc`. Look at the existing tests for patterns.
- Test behaviour, not implementation: what a player, a server owner or a plugin author would see.
- Anything involving threads or ordering (saves, shutdown, file watching) needs a test that would
  fail if the order were wrong.

## Rules for library code

1. **Every public class, method, constructor and field has Javadoc.** New API gets
   `@since <next version>`. Javadoc is built with `-Xdoclint:all`, so missing `@param`/`@return`
   fails the build.
2. **Version-specific API is never referenced directly from shared code.** Use name tables,
   reflection (`version.Reflection`) or an isolated class that is only loaded after a version check.
   See "Version handling" in [architecture.md](docs/architecture.md).
3. **Ownership and cleanup.** Anything that registers a listener, task, entity, thread or connection
   records its owning plugin and is undone on `MoonLib.shutdown(plugin)`, usually via
   `MoonLib.context(plugin).onShutdown(resource)`. Never assume you are the only plugin using this
   copy of MoonLib.
4. **The server thread is sacred.** Blocking I/O gets an async variant; callbacks that touch the
   world hop back with `Tasks`.
5. **Errors explain themselves.** Programming mistakes throw immediately and name the class, method or
   key and the fix. Runtime problems are logged with what still works. Never log player data or
   SQL parameters in errors.
6. **No runtime dependencies.** Optional integrations (PlaceholderAPI, Vault) are reached by reflection
   or soft hooks.
7. **Folia.** Use `Tasks.entity` / `Tasks.at` for anything touching an entity or block.

## Style

- 4-space indentation, 120-column lines, UTF-8 (see `.editorconfig`).
- `final` classes unless designed for extension; package-private for internals.
- Names say what things are for: `PlayerCache.unload`, not `PlayerCache.handleQuitAsync2`.
- User-facing text is British/American neutral, plain and short.

## Documentation

- A new feature needs a section in the matching guide in `docs/` (or a new guide added to
  `mkdocs.yml` and the README documentation table), plus a row in the README feature table if
  it is a new module.
- Code samples in docs must compile against the real API.
- Add a line under `## [Unreleased]` in [CHANGELOG.md](CHANGELOG.md).

## Pull requests

- One topic per pull request; keep refactors separate from features.
- Fill in the checklist in the PR template.
- CI builds and tests on JDK 25 and
  lints the Javadoc.

## Releasing (maintainers)

1. Set the version once: `java tools/SetVersion.java 1.3.0-beta` (or `ant set-version`, which asks for
   it). This changes `pom.xml` and every other place in this repository that shows the version: the
   install snippets in the README and docs, `SECURITY.md` and the
   bug-report template. `MoonLib.VERSION` is filled in from `pom.xml` at build time. CI runs
   `java tools/SetVersion.java --check` and fails if anything drifts.
2. Move `Unreleased` entries in the changelog to a heading for the new version.
3. Tag the commit with the bare version (`1.2.0-beta`) and push the tag. The release workflow checks the
   tag matches the pom, builds the jars and creates the GitHub release; JitPack builds the tag on
   first request.
4. After JitPack has built the tag, bump `<moonlib.version>` in MoonLib-Example and MoonLib-Template.
