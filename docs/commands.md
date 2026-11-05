# Commands

Package: `dev.lunamc.moonlib.command`

MoonLib commands are registered straight into the server's command map, so you **don't declare
them in `plugin.yml`**. They are removed cleanly on disable, so `/reload` and plugin managers work.

## Class-based commands

```java
public final class MineCommand extends MoonCommand {

    public MineCommand(MineManager mines) {
        super("mine", "mines", "m");                 // name + aliases
        description("Manage your mine");

        subcommand(new MineTeleport(mines));
        subcommand(new MineReset(mines));
        subcommand(new MineUpgrade(mines));
    }
    // No execute(): "/mine" alone shows an automatic help page of the subcommands
    // the sender has permission for.
}

final class MineReset extends MoonCommand {
    private final MineManager mines;

    MineReset(MineManager mines) {
        super("reset");
        this.mines = mines;
        description("Reset a mine");
        permission("moonprison.mine.reset");
        argument("mine", MineTypes.MINE);            // custom type, see below
        cooldown(30, TimeUnit.SECONDS, "moonprison.mine.reset.bypass");
    }

    @Override
    protected void execute(CommandContext ctx) {
        Mine mine = ctx.get("mine");
        mines.reset(mine);
        ctx.reply("&aReset &f{mine}&a.", "mine", mine.name());
    }
}
```

Register it from your plugin's `enable()`:

```java
registerCommands(new MineCommand(mines));
```

### Configuration methods

Call these in the constructor:

| Method | Effect |
|---|---|
| `aliases(String...)` | Extra names |
| `description(String)` | Shown in help pages |
| `permission(String)` | Required to run and to see in help/tab completion. Subcommands also inherit their parents' permissions |
| `playerOnly()` | Console gets `CommandMessages.PLAYER_ONLY` |
| `hidden()` | Hidden from help and tab completion, still runnable |
| `cooldown(long, TimeUnit[, bypassPermission])` | Per-player cooldown, applied after a successful run |
| `argument(name, type)` | Required positional argument |
| `optionalArgument(name, type[, default])` | Optional argument (must come after required ones) |
| `customUsage(String)` | Override the generated argument part of the usage line |
| `subcommand(MoonCommand)` | Add a subcommand |

### Execution order

1. If the first argument matches a subcommand name or alias, the rest is routed to it.
2. Permission check (this command and all parents).
3. Player-only check.
4. Enough required arguments? If not, the usage line is shown.
5. Cooldown check.
6. `execute(ctx)`. A thrown `CommandError` sends its message; any other exception sends
   `CommandMessages.INTERNAL_ERROR` and logs the stack trace with the command line that caused it.

## `CommandContext`

| Method | Returns |
|---|---|
| `sender()`, `player()`, `isPlayer()` | Who ran it. `player()` fails gracefully for console |
| `get("name")` | A declared argument, parsed and cached. Optional arguments return their default |
| `getOrDefault("name", fallback)`, `optional("name")`, `has("name")` | Optional argument helpers |
| `get(index, type)`, `get(index, type, fallback)` | Parse any raw argument without declaring it |
| `arg(i)`, `args()`, `size()`, `joined(from)` | Raw access |
| `reply(message, placeholders...)` | Send a coloured message with `{key}` placeholders |
| `check(condition, message)` | Abort with a message unless the condition holds |
| `checkPermission(node)` | Abort with the no-permission message |
| `fail(message)`, `usage()` | Abort with a message / the usage line |

### Failing

The cleanest way to stop a command is to throw:

```java
ctx.check(level < maxLevel, "&cAlready at max level.");
if (!VaultEconomy.withdraw(player, cost)) {
    throw new CommandError("&cYou need " + VaultEconomy.format(cost));
}
```

`CommandError` is unchecked, so helpers and argument parsers can throw it too.
`new CommandError(message, true)` also sends the usage line.

## Built-in argument types (`ArgumentTypes`)

| Type | Parses | Tab completes |
|---|---|---|
| `STRING` | one word | |
| `GREEDY_STRING` | the rest of the line (must be last) | |
| `INTEGER`, `POSITIVE_INTEGER`, `integer(min, max)` | whole numbers, `1,000` allowed | |
| `LONG`, `DOUBLE`, `POSITIVE_DOUBLE`, `doubleRange(min, max)` | numbers with `k/m/b/t` suffixes (`2.5m`) | |
| `BOOLEAN` | true/false, yes/no, on/off | true, false |
| `PLAYER` | online player (exact, then unique prefix); respects vanish (`canSee`) | visible players |
| `OFFLINE_PLAYER` | any player who has joined before | online players |
| `WORLD` | loaded world | world names |
| `MATERIAL` | material, modern names on every version | matching materials |
| `DURATION` | `30s`, `1h30m`, `7d` → milliseconds | common durations |
| `enumType(Class)` | any enum constant, case-insensitive | constants |
| `choice(String...)`, `choice(Supplier)` | one of a fixed or dynamic set | the options |

### Custom types

```java
public final class MineTypes {
    public static final ArgumentType<Mine> MINE = ArgumentType.of(
            input -> MineManager.get().find(input)
                    .orElseThrow(() -> new CommandError("&cNo mine called &e" + input)),
            () -> MineManager.get().names());
}
```

For full control implement `ArgumentType<T>` directly (`parse`, `suggest`, `greedy`).

### Custom tab completion

Override `suggest(ctx)` to replace the declared types' suggestions. Return `null` to fall back.
Results are filtered by what the player typed and sorted automatically.

```java
@Override
protected List<String> suggest(CommandContext ctx) {
    return ctx.size() == 2 ? Arrays.asList("10", "100", "1000") : null;
}
```

## Annotated commands

*Since 1.1.1-alpha.* Write a whole command tree as plain methods. MoonLib builds the same `MoonCommand`
objects you would write by hand, so help pages, usage lines, messages and tab completion are
identical, and annotated and hand-written commands can be mixed freely.

```java
@Command(value = "mine", aliases = "m", permission = "prison.mine", description = "Manage mines")
public final class MineCommand {

    private final MineManager mines;

    public MineCommand(MineManager mines) {
        this.mines = mines;
    }

    @Execute                                               // bare /mine
    public String list(CommandSender sender) {
        return "&7Mines: &f" + String.join(", ", mines.names());   // a returned String is sent
    }

    @Subcommand(value = "reset", aliases = "r", description = "Reset a mine")
    @Cooldown(value = 10, unit = TimeUnit.SECONDS, bypass = "prison.mine.nocooldown")
    public void reset(CommandSender sender,
                      @Suggest("mineNames") @Arg("mine") String mine,
                      @Arg(value = "delay", def = "0") @Range(min = 0, max = 300) int delay) {
        mines.reset(mine, delay);
    }

    @Subcommand("tp")                                      // a Player first parameter = player-only
    public void teleport(Player player, @Arg("mine") String mine) {
        player.teleport(mines.spawn(mine));
    }

    @Subcommand(value = "admin setblocks", permission = "prison.mine.admin")   // nested: /mine admin setblocks
    public boolean setBlocks(CommandSender sender, @Arg("mine") String mine,
                             @Greedy @Arg("composition") String composition) {
        return mines.setComposition(mine, composition);   // false shows the usage line
    }

    List<String> mineNames(CommandSender sender) {        // used by @Suggest("mineNames")
        return mines.names();
    }
}

// in enable():
registerCommands(new MineCommand(mines));                  // MoonPlugin accepts handlers too
// or: AnnotatedCommands.register(this, new MineCommand(mines));
```

### Annotations

| Annotation | On | Meaning |
|---|---|---|
| `@Command(value, aliases, permission, description, playerOnly, hidden)` | class | The root command |
| `@Execute` | method | Runs for the bare command (otherwise the help page is shown) |
| `@Subcommand(value, aliases, permission, description, playerOnly, hidden)` | method | A subcommand; spaces in `value` nest (`"admin reset"`) |
| `@Arg(value, optional, def, type)` | parameter | Argument name, optional flag, default (typed like a player would) and a named type |
| `@Greedy` | `String` parameter | Takes the rest of the input, spaces included (last argument only) |
| `@Range(min, max)` | number parameter | Bounds for `int`, `long`, `double` |
| `@Suggest("method")` | parameter | Tab completions from `method()` or `method(CommandSender)` returning a `Collection<String>` |
| `@Cooldown(value, unit, bypass)` | method or class | Per-player cooldown |

### Parameters

- A **first** parameter of type `CommandSender` or `Player` receives the sender. `Player` makes the
  (sub)command player-only. A `CommandContext` parameter (anywhere) receives the context.
- Every other parameter is an argument. Built in: `String`, `int`/`Integer`, `long`/`Long`,
  `double`/`Double`, `boolean`/`Boolean`, `Player`, `OfflinePlayer`, `World`, `Material`, `UUID` and
  every `enum`. Add your own:

  ```java
  AnnotatedCommands.registerType(Mine.class, ArgumentType.of(mines::byName, mines::names));
  AnnotatedCommands.registerType("rank", ArgumentTypes.choice(ranks::names));   // @Arg(type = "rank")
  ```

- Without `@Arg`, an argument is named after the parameter, which needs the compiler flag
  `-parameters` (the template plugin enables it). Otherwise it is called `arg0`, `arg1`...
- An optional argument with no `def` receives `null`, so it must use a wrapper type (`Integer`,
  not `int`).

### Return values and errors

| Return type | Effect |
|---|---|
| `void` | nothing |
| `String` | sent to the sender (colour codes allowed) |
| `boolean` | `false` shows the usage line |

Throw `CommandError` to stop with a message. Any other exception is logged with the command line
and the sender sees `internal-error`.

Mistakes in the annotations are reported **when the command is built** (at startup), naming the
class and method, for example: `MineCommand.reset() has optional primitive argument 'delay' with no
default; use a wrapper type or set def`.

## Inline commands (`CommandBuilder`)

```java
CommandBuilder.create("spawn")
        .playerOnly()
        .cooldown(5, TimeUnit.SECONDS)
        .executes(ctx -> ctx.player().teleport(spawn))
        .register();

CommandBuilder.create("eco", "economy")
        .permission("myplugin.eco")
        .subcommand(CommandBuilder.create("give")
                .argument("target", ArgumentTypes.OFFLINE_PLAYER)
                .argument("amount", ArgumentTypes.POSITIVE_DOUBLE)
                .executes(ctx -> VaultEconomy.deposit(ctx.get("target"), ctx.<Double>get("amount"))))
        .register();
```

## `plugin.yml` commands

Prefer `plugin.yml`? Declare the command there and bind a `MoonCommand` to it:

```java
CommandRegistry.bind(getCommand("mine"), new MineCommand(mines));
```

## Messages

Load your own wording from a config section, or set single messages. Both apply to **your plugin
only**, even when other plugins share MoonLib:

```java
CommandMessages.load(messages.file().getSection("commands"));
CommandMessages.set("player-only", "&cConsole can't do that.");
```

The public static fields (`CommandMessages.NO_PERMISSION`, ...) are the global defaults used by
every plugin that hasn't overridden a message.

```yaml
commands:
  no-permission: "&cYou can't do that."
  player-only: "&cPlayers only."
  usage: "&cUsage: &7{usage}"
  cooldown: "&cWait &e{time}&c."
  player-not-found: "&e{input} &cis offline."
  invalid-number: "&e{input} &cisn't a number."
  number-out-of-range: "&cPick a number from &e{min} &cto &e{max}&c."
  help-header: "&8--- &d/{command} &8---"
  help-line: "&d{usage} &8- &7{description}"
```

See the Javadoc of `CommandMessages` for every key and its placeholders.

## Unregistering

Commands registered with `CommandRegistry.register` (which `registerCommands` uses) are removed
automatically when *their* plugin disables. Commands of other plugins are untouched. To remove one
earlier: `CommandRegistry.unregister(command)`. `CommandRegistry.registered(plugin)` lists a
plugin's commands.

If two plugins register the same name, Bukkit gives the first one `/name` and the second stays
available as `/pluginname:name`. MoonLib logs a warning; nothing throws.
