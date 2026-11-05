# Integrations: plugins that work together

Package: `dev.lunamc.moonlib.integration`

MoonLib gives your plugins two tools for working with other plugins:

| Tool | Use it for |
|---|---|
| `Integrations` | Detecting another plugin and switching a feature on while it's enabled (and off when it isn't) |
| `MoonLink` | Calling another MoonLib plugin's features and sending events between plugins, **with no API jar and no compile-time dependency** |

Together they let a set of plugins (such as the Moon suite) link up automatically: install two plugins and
they find each other and start cooperating, with no setup by the server owner.

## Detecting other plugins: `Integrations`

```java
// inline
Integrations.when("PlaceholderAPI", papi -> new MoonPrisonExpansion().register());
Integrations.when("MoonGangs",
        gangs -> sellPrices.addBooster("gang", this::gangBoost),
        ()    -> sellPrices.removeBooster("gang"));

// a class, for anything bigger, with a minimum version
public final class CratesLink extends Integration {
    public CratesLink() { super("MoonCrates", "1.2.0"); }

    @Override protected void enable(Plugin crates) { rewards.register("crate-key", new CrateKeyReward()); }
    @Override protected void disable()             { rewards.unregister("crate-key"); }
}
Integrations.register(new CratesLink());

Integrations.isActive("MoonCrates");   // is the link on right now?
Integrations.all();                    // your integrations
```

How it behaves:

- **Load order doesn't matter.** If the other plugin is already enabled, the link switches on immediately.
  Otherwise it switches on the moment that plugin enables. Add it to `softdepend` if you want it enabled first.
- **It follows the other plugin.** If that plugin disables (shutdown, or a plugin manager unloading it), the link
  switches off; when it comes back, the link switches on again.
- **Nothing can crash your plugin.** A version below `minVersion` is skipped with a warning. Any exception, or a
  `NoClassDefFoundError` from an API that changed, is logged and leaves that one link off.
- **Cleanup is automatic** when your plugin disables.

If you use another plugin's API classes, use them only inside the `Integration` class (or the lambdas), never
in your main class. Then the classes are only loaded when that plugin is actually installed.

## Talking to other plugins: `MoonLink`

Every plugin usually has its **own relocated copy** of MoonLib, so plugins can't share MoonLib's Java classes
with each other. MoonLink works around this: each copy registers an endpoint with Bukkit's `ServicesManager`
using only standard Java types, and the copies find and call each other through it. This works between
relocated copies, shared copies and the standalone plugin, in any combination.

### Offering a feature

```java
// in MoonCrates
MoonLink.provide("crates:give-key", payload -> {
    Map<String, Object> args = MoonLink.args(payload);
    Player player = (Player) args.get("player");
    String crate = (String) args.get("crate");
    int amount = ((Number) args.getOrDefault("amount", 1)).intValue();
    keys.give(player, crate, amount);
    return keys.count(player, crate);          // results are optional
});
```

### Using a feature

```java
// in MoonQuests or MoonVote: nothing to compile against
if (MoonLink.available("crates:give-key")) {
    MoonLink.call("crates:give-key", MoonLink.payload("player", player, "crate", "vote", "amount", 1));
}

Optional<Integer> total = MoonLink.call("crates:give-key", payload, Integer.class);
```

`call` returns an empty `Optional` when nobody offers the capability, so the calling plugin works the same
whether or not the other one is installed. If the provider throws, `call` throws an `IllegalStateException`
naming the plugin that failed.

### Events between plugins

```java
// MoonPrison announces rank-ups...
MoonLink.publish("prison:rankup", MoonLink.payload("player", player, "rank", "B"));

// ...and any plugin can react
MoonLink.subscribe("prison:rankup", payload -> {
    Player player = (Player) MoonLink.args(payload).get("player");
    quests.progress(player, "rankup");
});
```

`publish` returns how many plugins received the event. A subscriber that throws is logged and skipped.

### Rules

- **Payloads and results:** only Java and Bukkit types (`String`, numbers, booleans, `UUID`, `Map`, `List`,
  `Player`, `ItemStack`, `Location`...). Never your own classes: the other plugin can't see them.
- **Names:** `plugin:action`, e.g. `crates:give-key`, `gangs:get-boost`, `prison:rankup`. Names starting with
  `__` are reserved.
- **Threads:** calls and events run synchronously on the caller's thread, as the providing plugin. Call from the
  main thread (or the player's region on Folia) when the handler touches the world.
- **Document your capabilities** (name, payload keys, result) in each plugin's README so other plugins can use
  them.

### Diagnostics

```java
MoonLink.capabilities();           // plugin -> what it offers
MoonLink.providers("crates:give-key");
```

The standalone MoonLib plugin's `/moonlib` command lists every plugin's capabilities in game.

## Putting it together

A typical suite integration uses both tools: `Integrations` to know when the partner plugin is around, and
`MoonLink` to talk to it.

```java
// MoonQuests: offer "crate key" rewards only while MoonCrates is installed
Integrations.when("MoonCrates",
        crates -> rewards.register("crate-key", (player, value) ->
                MoonLink.call("crates:give-key", MoonLink.payload("player", player, "crate", value, "amount", 1))),
        () -> rewards.unregister("crate-key"));
```
