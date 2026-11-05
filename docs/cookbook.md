# Cookbook

Short, complete recipes for things plugins do all the time. Each one combines several MoonLib
modules; follow the links for the details.

## A currency with SQL storage, caching and a `/balance` command

```java
public final class EconomyModule extends MoonModule {
    private final MyPlugin plugin;

    public EconomyModule(MyPlugin plugin) {
        super("economy");
        this.plugin = plugin;
    }

    @Override
    protected void enable() {
        Database db = plugin.database();
        String save = db.dialect().upsert("balances", "uuid", "tokens");
        PlayerCache<long[]> balances = PlayerCache.<long[]>builder(plugin)
                .loader((id, name) -> new long[]{db.value("SELECT tokens FROM balances WHERE uuid = ?", Long.class, id).orElse(0L)})
                .saver((id, value) -> db.update(save, id, value[0]))
                .name("balances")
                .build();
        onDisable(balances::close);
        command(new BalanceCommand(balances));
    }
}

@Command(value = "balance", aliases = "bal")
final class BalanceCommand {
    private final PlayerCache<long[]> balances;

    BalanceCommand(PlayerCache<long[]> balances) {
        this.balances = balances;
    }

    @Execute
    String self(Player player) {
        return RichText.parse("<gray>Balance: <gold><amount>", "amount", Numbers.commas(balances.get(player)[0]));
    }

    @Subcommand(value = "give", permission = "eco.admin")
    String give(CommandSender sender, @Arg("player") Player target, @Arg("amount") @Range(min = 1) long amount) {
        balances.get(target)[0] += amount;
        return "&aGave &f" + target.getName() + " &e" + Numbers.commas(amount);
    }
}
```

See [database.md](database.md), [player-data.md](player-data.md), [commands.md](commands.md).

## A leaderboard hologram that refreshes every minute

```java
Hologram board = Hologram.create(spawn, "&d&lTOP MINERS", "&7loading...");
Tasks.timer(0, 20 * 60, () -> db.queryAsync(
                "SELECT name, blocks FROM stats ORDER BY blocks DESC LIMIT 5",
                row -> row.getString("name") + " &7- &e" + Numbers.compact(row.getLong("blocks")))
        .thenAccept(rows -> Tasks.at(spawn, () -> {
            List<String> lines = new ArrayList<>();
            lines.add("&d&lTOP MINERS");
            for (int i = 0; i < rows.size(); i++) {
                lines.add("&f" + (i + 1) + ". " + rows.get(i));
            }
            board.lines(lines);
        })));
```

The query runs off the server thread; the hologram is updated back on the location's thread.

## Confirm before something destructive

```java
@Subcommand(value = "reset", permission = "prison.admin")
void reset(Player player, @Arg("mine") String mine) {
    ConfirmMenu.open(player, "&cReset " + mine + "?",
            confirmed -> mines.reset(mine),
            cancelled -> cancelled.sendMessage("&7Cancelled."));
}
```

## Ask a player to type something

```java
ChatInput.ask(player)
        .prompt("&eType a name for your gang, or &ccancel")
        .validate(name -> name.matches("[A-Za-z0-9]{3,16}"), "&c3-16 letters or numbers")
        .timeout(30)                                  // seconds
        .onInput(name -> db.insertAsync("INSERT INTO gangs (name, owner) VALUES (?, ?)", name, player.getUniqueId()))
        .start();
```

## Let server owners edit config without restarting

```java
settings = loadConfig("config.yml");
settings.watch(this, () -> modules.reloadAll());     // save the file and it applies
```

## Make two of your plugins work together, optionally

```java
// MoonCrates
MoonLink.provide("crates:give-key", payload -> {
    Map<String, Object> args = MoonLink.args(payload);
    crates.giveKey((Player) args.get("player"), (String) args.get("crate"), (int) args.get("amount"));
    return true;
});

// MoonQuests: works with or without MoonCrates installed
Integrations.when("MoonCrates", crates -> rewards.register("crate-key", (player, crate) ->
        MoonLink.call("crates:give-key", MoonLink.payload("player", player, "crate", crate, "amount", 1))));
```

See [integrations.md](integrations.md).

## A countdown event with a boss bar and titles

```java
void startEvent(String name, int seconds) {
    for (Player player : Bukkit.getOnlinePlayers()) {
        Bar.countdown(player, seconds, left -> RichText.parse("<yellow>" + name + " starts in <white>" + left + "s"),
                Bar.Color.YELLOW);
    }
    Tasks.later(seconds * 20L, () -> Bukkit.getOnlinePlayers().forEach(p ->
            Titles.send(p, RichText.parse("<gradient:gold:yellow><bold>" + name), "&7Good luck!")));
}
```

## Split a big plugin into toggleable features

```java
modules = new ModuleManager(this)
        .toggles(loadConfig("config.yml"), "modules")
        .register(new EconomyModule(this), new MinesModule(this), new RanksModule(this), new CratesModule(this));
modules.enableAll();
```

See [modules.md](modules.md).
