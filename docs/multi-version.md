# Multi-version support

Package: `com.lunamcdev.moonlib.version`

MoonLib supports **1.8.8 to the latest release**, including the year-based versions (26.1, 26.2 ...).
One compiled jar runs on all of them.

## How MoonLib does it

1. **Java 8 bytecode.** 1.8 to 1.16 servers usually run Java 8, so the library targets it. Modern
   servers run Java 8 bytecode without issue.
2. **Compile against a modern API, call only what exists.** Anything newer than 1.8.8 is behind a
   `ServerVersion` check. Calls into newer APIs live in small isolated classes (for example
   `item.ModernItemSupport`), so old servers never try to load them.
3. **Look up renamed constants by name.** `Sound` became an interface in 1.21.3 and `Enchantment`
   constants were renamed in 1.20.5. Referencing them directly crashes on half your versions, so
   `Sounds` and `Enchants` resolve them reflectively by name with fallbacks.
4. **Avoid known breakages.** MoonLib never uses `InventoryView` (class → interface in 1.21), never
   assumes `getStorageContents()` (1.9+), and identifies menus by holder rather than title.
5. **Pick the best implementation at runtime.** Titles use Adventure on Paper, the Bukkit API on
   Spigot 1.12+, and packets on 1.8 to 1.11. The scheduler uses Folia's region schedulers on Folia.

## Detecting the version

```java
ServerVersion version = ServerVersion.current();     // e.g. 1.20.4 or 26.1
ServerVersion.atLeast(1, 14);                         // static shortcut
version.isAtLeast(ServerVersion.V1_20_5);
version.isBelow(ServerVersion.V1_13);
version.isBetween(ServerVersion.V1_9, ServerVersion.V1_12);
ServerVersion.isLegacy();                             // below 1.13
ServerVersion.supportsHexColors();                    // 1.16+
ServerVersion.nmsPackage();                           // "v1_8_R3", or "" on unrelocated servers
```

Year-based versions compare as newer than every `1.x` version, so `atLeast(1, 20, 5)` is `true`
on 26.1.

## Detecting the server software

```java
ServerSoftware.current();                 // FOLIA, PURPUR, PUFFERFISH, PAPER, SPIGOT or BUKKIT
ServerSoftware.current().isPaperBased();  // Paper and its forks
ServerSoftware.isFolia();
```

## Materials

Always write **modern names**:

```java
ItemStack pane = Materials.item("LIGHT_BLUE_STAINED_GLASS_PANE");   // STAINED_GLASS_PANE:3 on 1.8
Optional<Material> clock = Materials.material("CLOCK");             // WATCH on 1.8
Materials.firstMaterial("NETHERITE_INGOT", "DIAMOND");              // first that exists
Materials.isAir(type);                                               // AIR, CAVE_AIR, VOID_AIR
Materials.item("WOOL:14");                                           // legacy syntax also works
```

Every colour variant of wool, glass, panes, terracotta, concrete, concrete powder, carpet, beds
and banners is mapped, as are dyes and around a hundred commonly renamed items (heads, wooden and
golden tools, signs, doors, food...). Unknown names fall back to `Material.matchMaterial`.
`ItemBuilder.of(String)` and `ArgumentTypes.MATERIAL` use this automatically.

## Sounds

```java
Sounds.play(player, "ENTITY_EXPERIENCE_ORB_PICKUP");
Sounds.play(player, "BLOCK_NOTE_BLOCK_PLING", 1F, 2F);
Sounds.playWorld(location, 1F, 1F, "ENTITY_GENERIC_EXPLODE");
Sounds.play(player, "minecraft:entity.villager.yes");                 // dotted keys work too

Sounds.SoundEffect click = Sounds.parse("UI_BUTTON_CLICK:0.8:1.2");   // name:volume:pitch from config
click.play(player);
```

Unknown sounds are skipped silently, so a typo in a config can't break a feature.

## Enchantments

```java
Enchants.get("EFFICIENCY");          // vanilla name: works on every version
Enchants.get("DIG_SPEED");           // old Bukkit name: also works on every version
Enchants.get("minecraft:fortune");
```

## Titles and action bars

```java
Titles.send(player, "&6&lRANK UP", "&7You are now &eB");
Titles.send(player, "&cWarning", "", 5, 40, 10);
Titles.actionBar(player, "&aBlocks: &f" + blocks);
Titles.clear(player);
Titles.implementationName();          // "Adventure", "Spigot" or "Legacy NMS"
```

## Hex colours

`Text.color("&#FF55AA...")` renders hex on 1.16+ and the nearest of the 16 classic colours on
older versions. Gradients do the same.

## Writing your own version-specific code

```java
if (ServerVersion.atLeast(1, 14)) {
    Modern.applyModelData(meta, 1001);   // separate class: only loaded on 1.14+
}
```

Rules of thumb:

- Put calls to APIs that may not exist in a **separate class**, and only call into it after a
  version check. The JVM only loads a class the first time it's used.
- Never reference a constant that has been renamed (`Sound.X`, `Enchantment.X`, `Particle.X`,
  `Material.X` for flattened names) directly in shared code. Look it up by name.
- Don't call methods on `InventoryView`.
- For NMS work, `Reflection.nmsClass`, `Reflection.craftClass`, `Reflection.method`,
  `Reflection.field` and `Reflection.sendPacket` are cached and null-safe.

## Testing matrix suggestion

Test at least on: **1.8.8** (legacy), **1.12.2** (last pre-flattening), **1.16.5** (hex, Java 8/11),
**1.20.4**, and the **latest** Paper release.
