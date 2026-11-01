# Utilities & hooks

Package: `dev.lunamc.moonlib.util` and `dev.lunamc.moonlib.hook`

## `Text`

```java
Text.color("&aGreen &#FF55AA hex <#00FFAA>also hex {#123456}this too");
Text.color(listOfLines);                       // -> new coloured list
Text.strip("&aNo &lcolours");                  // "No colours"
Text.replace("Hi {name}", "name", "Luna");     // key/value pairs
Text.gradient("MOON PRISON", "#7F7FD5", "#91EAE4");
Text.gradient("BOLD", "#FF0000", "#FFFF00", "&l");
Text.center("&d&lWelcome!");                   // centred in the chat box
Text.progressBar(mined, needed, 20, '|', "&a", "&7");
Text.formatEnum(Material.DIAMOND_PICKAXE);     // "Diamond Pickaxe"
Text.repeat("=", 10);
Text.nearestLegacy(0xFF8800);                  // ChatColor.GOLD
```

## `Titles`

See [multi-version.md](multi-version.md#titles-and-action-bars).

## `Durations`

```java
Durations.parse("1h30m");          // 5400000 ms
Durations.parse("2 days 4 hours");
Durations.parse("30");             // bare number = seconds
Durations.parse("oops", -1);       // fallback instead of exception
Durations.format(5_400_000);       // "1h 30m"
Durations.format(ms, 2);           // at most 2 units
Durations.formatLong(65_000);      // "1 minute, 5 seconds"
Durations.formatClock(3_725_000);  // "01:02:05"
Durations.toTicks(5, TimeUnit.SECONDS);   // 100
```

Units: `ms`, `s`, `m`, `h`, `d`, `w`, `mo` (30 days), `y` (365 days), plus long forms.

## `Numbers`

```java
Numbers.compact(1_250_000);     // "1.25M" (K M B T Q Qi Sx Sp Oc No Dc ... up to 10^63)
Numbers.commas(1234567.891);    // "1,234,567.89"
Numbers.round(3.14159, 2);      // 3.14
Numbers.roman(14);              // "XIV"
Numbers.ordinal(22);            // "22nd"
Numbers.parseInt("x", 0);  Numbers.parseLong(..);  Numbers.parseDouble(..);
Numbers.isInteger("42");   Numbers.isDecimal("4.2");
Numbers.clamp(v, 0, 100);
Numbers.random(1, 6);           // inclusive
Numbers.chance(12.5);           // 12.5% true
```

## `Cooldown<K>`

```java
Cooldown<UUID> kit = Cooldown.of(10, TimeUnit.MINUTES);
if (!kit.tryUse(uuid)) {
    player.sendMessage("Wait " + kit.formatRemaining(uuid));
    return;
}
kit.put(uuid, customMillis);  kit.remainingMillis(uuid);  kit.reset(uuid);  kit.cleanup();
```

Thread-safe; `tryUse` is atomic.

## `Locations`

```java
String s = Locations.serialize(loc);          // "world;1.50;64.00;-3.50;90.00;0.00"
Location l = Locations.deserialize(s);        // null if invalid or world unloaded
Locations.serializeBlock(loc);                // "world;1;64;-4"
Locations.center(loc);                        // middle of the block
Locations.isSameBlock(a, b);
Locations.distance(a, b);                     // never throws across worlds
Locations.isWithin(a, b, 10);
Locations.highestSafe(loc);
Locations.toFriendlyString(loc);              // "world (1, 64, -4)"
```

## `Cuboid`

```java
Cuboid mine = new Cuboid(corner1, corner2);
mine.contains(location);
mine.volume();  mine.sizeX();  mine.center();  mine.top();  mine.randomLocation();
mine.players();                                 // online players inside
mine.fill(Material.STONE);
mine.fill(composition);                         // Map<Material, Double> weights
double percentLeft = 100.0 * mine.countNonAir() / mine.volume();
for (Block block : mine) { ... }
String saved = mine.serialize();  Cuboid back = Cuboid.deserialize(saved);
mine.expand(2);
```

Filling is synchronous: for very large regions spread the work over several ticks or use FAWE.

## `Players`

```java
Players.give(player, items...);          // leftovers dropped at their feet
Players.giveIfFits(player, items...);    // all or nothing
Players.heal(player);  Players.feed(player);  Players.clearEffects(player);
Players.clearInventory(player);  Players.reset(player);
Players.broadcast("&cRestarting soon", null);           // null = everyone
Players.broadcast("&d[Staff] " + msg, "myplugin.staff");
Players.find("Lun");                     // exact, then unique prefix
Players.online();                        // snapshot list
Players.isVanished(player);              // EssentialsX / SuperVanish convention
Players.playSoundToAll("ENTITY_WITHER_SPAWN");
```

## `Log`

```java
Log.info("Loaded {} mines in {}ms", count, took);
Log.warn("Mine {} has no spawn", name);
Log.error("Database failed", exception);
Log.setDebug(config.getBoolean("debug"));
Log.debug("Break at {}", location);     // only printed in debug mode
```

Output goes through your plugin's logger, so lines are prefixed with `[YourPlugin]`.

## PlaceholderAPI (`Placeholders`)

```java
String line = Placeholders.apply(player, "&fRank: %luckperms_prefix%");
Placeholders.isEnabled();
```

Returns text unchanged if PAPI is missing. Add `softdepend: [PlaceholderAPI]`.

## Vault economy (`VaultEconomy`)

```java
VaultEconomy.isAvailable();
VaultEconomy.balance(player);
VaultEconomy.has(player, 100);
VaultEconomy.withdraw(player, 100);    // false (and nothing taken) if they can't afford it
VaultEconomy.deposit(player, 100);
VaultEconomy.format(1234.5);           // uses the economy plugin's currency format
```

No compile-time Vault dependency is needed. The provider is found lazily, so load order doesn't
matter. Add `softdepend: [Vault]` (or `depend`).
