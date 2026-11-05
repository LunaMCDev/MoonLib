# Compatibility & API stability

## Supported platforms

| | Supported |
|---|---|
| Minecraft | 1.8.8 → newest, including year-based releases (26.x) |
| Server software | CraftBukkit, Spigot, Paper, Purpur, Pufferfish, Folia |
| Java at runtime | 8+ for MoonLib itself; your server's version decides the minimum (26.1+ needs Java 25, 1.20.5+ needs 21) |
| Java to build | JDK 21+ (the Spigot API it compiles against is Java 21 bytecode); output is Java 8 bytecode |

## Feature availability by version

Everything not listed works on every supported version.

| Feature | Needs | Older servers |
|---|---|---|
| Hex colours (`&#rrggbb`, `<#hex>`, gradients) | 1.16 | nearest of the 16 legacy colours |
| Boss bars (`Bar`) | 1.9 | no-op, one warning |
| Tab header/footer (`TabList.set`) | 1.13 | skipped, one warning |
| Text-display holograms | 1.19.4 | armor-stand holograms |
| Non-persistent hologram entities | 1.13 | entities persist; delete holograms on shutdown (automatic) |
| Persistent item tags (`ItemTags`) | 1.14 | not available; check `ItemTags.isSupported()` |
| Custom model data | 1.14 | ignored |
| Player profile textures for heads | 1.18.1 | reflection on `GameProfile` |
| Adventure messages | Paper | BungeeCord chat API / packets |
| Folia regions | Folia | regular Bukkit scheduler |

## Versioning policy

MoonLib follows [Semantic Versioning](https://semver.org/):

| Change | Version bump | Example |
|---|---|---|
| Bug fixes, docs, internal changes | patch (1.4.**1**) | fix a menu dupe |
| New classes, methods, options; deprecations | minor (1.**5**.0) | add a new display type |
| Removing or changing public API | major (**2**.0.0) | rename a method |

### What counts as public API

- Every `public` or `protected` class, method, field and constructor in `dev.lunamc.moonlib` and its
  subpackages, **except** `dev.lunamc.moonlib.version.adapter` (internal by contract) and anything
  annotated or documented as internal.
- The format of files MoonLib writes (YAML/JSON layout from `DataMapper`, the
  `moonlib_migrations` table).
- Config keys MoonLib reads (`CommandMessages` keys, `Database.fromConfig` keys).

Not covered: log message wording, package-private members, the exact text of default messages, and
behaviour that was a bug.

### Deprecation

Public API is deprecated for at least one minor release before removal in the next major release.
Deprecated members say what to use instead in their Javadoc, and the
[changelog](../CHANGELOG.md) lists them.

### Shading means no forced upgrades

Because each plugin shades (and should relocate) its own copy, upgrading MoonLib in one plugin
never affects another. In shared mode, MoonLib warns if a plugin was built against a newer copy
than the one loaded (see [shading.md](shading.md)).

## `@since` tags

Every class and member added after 1.0.0 carries `@since` in its Javadoc, so you can tell at a glance
which MoonLib version a feature needs.
