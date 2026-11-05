# Boss bars, tab list & holograms

*Since 1.1.1-alpha.* All three are removed automatically when the owning plugin shuts down MoonLib. Text
accepts `&` codes and hex colours; pass it through [`RichText.parse`](rich-text.md) for gradients.

## Boss bars (`Bar`)

```java
Bar reset = Bar.create("&dMine A resets in &f60s", Bar.Color.PURPLE, Bar.Style.SEGMENTED_10);
reset.show(player);                        // or show(Bukkit.getOnlinePlayers())
reset.title("&dMine A resets in &f30s").progress(0.5);
reset.hide(player);
reset.close();                             // hide from everyone

Bar.countdown(player, 30, seconds -> "&eEvent starts in &f" + seconds + "s", Bar.Color.YELLOW);
```

`countdown` updates itself every second and disappears at zero or when the player leaves. Boss bars
need 1.9+; on 1.8 every call is a harmless no-op and a warning is logged once.

## Tab list (`TabList`)

```java
TabList.set(player, "&d&lMOON PRISON\n&7play.example.net", "&7Online: &f" + Bukkit.getOnlinePlayers().size());
TabList.name(player, "&8[&dB&8] &f" + player.getName());   // the player's name in everyone's list
TabList.clear(player);

TabList.auto(new TabList.Provider() {                       // refreshed for everyone
    public List<String> header(Player p) { return Arrays.asList("&d&lMOON PRISON", ""); }
    public List<String> footer(Player p) { return Arrays.asList("", "&7Rank: &f" + ranks.of(p)); }
}, 40);
```

Headers and footers need 1.13+ (skipped with a one-time warning before that); names work everywhere.

## Holograms (`Hologram`)

```java
Hologram top = Hologram.create(location, "&d&lTOP MINERS", "&f1. Luna &7- &e1.2M", "&f2. Kai &7- &e900K");
top.line(1, "&f1. Luna &7- &e1.3M");
top.lines(newLines);
top.teleport(otherLocation);
top.delete();
```

| Server | Implementation |
|---|---|
| 1.19.4+ | one text display entity for all lines (crisp, always faces the player) |
| older | one invisible marker armor stand per line |

Hologram entities are marked non-persistent (1.13+), so a crash never leaves orphaned text in the
world, and tagged `moonlib-hologram`. Create and change holograms on the server thread (the
location's region thread on Folia: `Tasks.at(location, ...)`).

Sidebars are covered in [scoreboards.md](scoreboards.md).
