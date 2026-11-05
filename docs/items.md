# Items

Package: `com.lunamcdev.moonlib.item`

## `ItemBuilder`

```java
ItemStack pickaxe = ItemBuilder.of("DIAMOND_PICKAXE")
        .name("&b&lMoon Pickaxe")
        .lore("&7Efficiency: &f{eff}", "&7Fortune: &f{fortune}", "", "&eRight-click to upgrade")
        .placeholders("eff", Numbers.roman(5), "fortune", Numbers.roman(3))
        .enchant("EFFICIENCY", 5)
        .enchant("FORTUNE", 3)
        .unbreakable(true)
        .hideAll()
        .customModelData(1001)
        .tag("moon-pickaxe", "true")
        .build();
```

| Method | Notes |
|---|---|
| `of(Material)`, `of(Material, amount)`, `of(String)`, `of(ItemStack)`, `skull()` | `of(String)` uses modern names on every version; unknown names become stone |
| `filler(String)` | Nameless, flag-free item for menu backgrounds |
| `name`, `lore`, `addLore`, `clearLore` | Coloured at build time |
| `placeholder(key, value)`, `placeholders(pairs...)` | `{key}` in name/lore; a value containing `\n` expands to several lore lines |
| `amount(int)` | |
| `enchant(Enchantment, level)`, `enchant(String, level)` | Unsafe levels allowed; names via `Enchants` |
| `glow()`, `glow(boolean)` | Glint override on 1.20.5+, hidden enchant before |
| `flags(ItemFlag...)`, `hideAll()` | |
| `unbreakable(boolean)` | 1.8 to 1.10 handled via the Spigot meta |
| `damage(int)` | Damageable meta on 1.13+, durability before |
| `customModelData(int)` | 1.14+, ignored before |
| `color(Color)` | Leather armour |
| `skullOwner(OfflinePlayer)`, `texture(String)` | Player heads |
| `tag(key, value)` | Hidden persistent data, 1.14+ |
| `meta(Consumer<ItemMeta>)`, `meta(Class<M>, Consumer<M>)` | Anything else |

A builder can be reused: every `build()` returns a fresh copy.

## Heads (`Skulls`)

```java
ItemStack crate = Skulls.fromTexture("eyJ0ZXh0dXJlcyI6...");     // base64 value
ItemStack hash  = Skulls.fromTexture("e3b6b6e6a3f2...");         // texture hash
ItemStack url   = Skulls.fromTexture("http://textures.minecraft.net/texture/e3b6...");
ItemStack mine  = Skulls.ofPlayer(player);
```

Uses the `PlayerProfile` API on 1.18.1+, and the GameProfile via reflection before that.

## Persistent tags (`ItemTags`, 1.14+)

```java
ItemTags.setString(item, "voucher", "vip");
ItemTags.setInt(item, "level", 5);
if (ItemTags.has(item, "voucher")) { ... }
int level = ItemTags.getInt(item, "level", 0);
ItemTags.remove(item, "voucher");
```

Keys are namespaced under your plugin automatically. Tags can't be faked with anvils or
`/give` name tricks, so use them (not names or lore) to recognise your custom items.
`ItemTags.isSupported()` reports whether the server is 1.14+.

## Inventory helpers (`Items`)

```java
Items.isEmpty(item);                                         // null, air or amount 0
Items.count(inventory, Material.COAL);
Items.count(inventory, item -> ItemTags.has(item, "token"));
Items.remove(inventory, item -> item.getType() == Material.EMERALD, 10);   // all or nothing
Items.canFit(inventory, reward);
Items.storage(player.getInventory());                         // storage slots only, every version

String data = Items.toBase64(player.getInventory().getContents());   // store anywhere
ItemStack[] restored = Items.fromBase64(data);
```

Giving items with overflow handling lives in `Players.give(player, items...)` and
`Players.giveIfFits(player, items...)`.
