package dev.lunamc.moonlib.command;

import dev.lunamc.moonlib.util.Text;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Everything a command needs while it runs: who ran it, the label they used, the raw arguments,
 * and typed access to declared arguments.
 *
 * <pre>{@code
 * @Override
 * protected void execute(CommandContext ctx) {
 *     Player target = ctx.get("target");                // declared with argument("target", PLAYER)
 *     int amount = ctx.getOrDefault("amount", 1);        // declared with optionalArgument(...)
 *     ctx.check(amount <= 64, "&cYou can give at most 64.");
 *     ctx.reply("&aGave {amount} to {player}", "amount", amount, "player", target.getName());
 * }
 * }</pre>
 */
public final class CommandContext {

    private final MoonCommand command;
    private final CommandSender sender;
    private final String label;
    private final String[] args;
    private final Map<String, Object> parsed = new HashMap<>();

    CommandContext(MoonCommand command, CommandSender sender, String label, String[] args) {
        this.command = command;
        this.sender = sender;
        this.label = label;
        this.args = args;
    }

    /**
     * Returns the command being executed.
     *
     * @return the command
     */
    public MoonCommand command() {
        return command;
    }

    /**
     * Returns whoever ran the command.
     *
     * @return the sender
     */
    public CommandSender sender() {
        return sender;
    }

    /**
     * Returns the sender as a player.
     *
     * @return the player
     * @throws CommandError with {@link CommandMessages#PLAYER_ONLY} if the sender is not a player
     */
    public Player player() {
        if (!(sender instanceof Player)) {
            throw new CommandError(CommandMessages.get("player-only"));
        }
        return (Player) sender;
    }

    /**
     * Returns whether the sender is a player.
     *
     * @return {@code true} for players
     */
    public boolean isPlayer() {
        return sender instanceof Player;
    }

    /**
     * Returns the label used to run the command, including any parent labels for subcommands, e.g.
     * {@code "mine reset"}.
     *
     * @return the label
     */
    public String label() {
        return label;
    }

    /**
     * Returns a copy of the raw arguments (after subcommand names were removed).
     *
     * @return the arguments
     */
    public String[] args() {
        return args.clone();
    }

    /**
     * Returns the number of raw arguments.
     *
     * @return the argument count
     */
    public int size() {
        return args.length;
    }

    /**
     * Returns a raw argument.
     *
     * @param index the zero-based index
     * @return the argument, or {@code null} if absent
     */
    public String arg(int index) {
        return index >= 0 && index < args.length ? args[index] : null;
    }

    /**
     * Returns a raw argument or a default.
     *
     * @param index    the zero-based index
     * @param fallback returned if the argument is absent
     * @return the argument or {@code fallback}
     */
    public String arg(int index, String fallback) {
        String value = arg(index);
        return value == null ? fallback : value;
    }

    /**
     * Joins all raw arguments from an index onward with spaces.
     *
     * @param from the first index to include
     * @return the joined text, or an empty string if there are none
     */
    public String joined(int from) {
        if (from >= args.length) {
            return "";
        }
        return String.join(" ", Arrays.copyOfRange(args, Math.max(0, from), args.length));
    }

    /**
     * Returns a declared argument's parsed value. Parsing happens on first access and is cached.
     *
     * @param name the argument name
     * @param <T>  the expected type
     * @return the value, or the argument's default if an optional argument was omitted
     * @throws CommandError          if the input is invalid
     * @throws IllegalArgumentException if no argument with that name was declared
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String name) {
        if (parsed.containsKey(name)) {
            return (T) parsed.get(name);
        }
        List<Argument<?>> declared = command.arguments();
        for (int i = 0; i < declared.size(); i++) {
            Argument<?> argument = declared.get(i);
            if (!argument.name().equals(name)) {
                continue;
            }
            Object value;
            if (i >= args.length) {
                if (!argument.optional()) {
                    throw new CommandError(Text.replace(CommandMessages.get("usage"), "usage", command.usage(label)));
                }
                value = argument.defaultValue();
            } else if (argument.type().greedy()) {
                value = argument.type().parse(sender, joined(i));
            } else {
                value = argument.type().parse(sender, args[i]);
            }
            parsed.put(name, value);
            return (T) value;
        }
        throw new IllegalArgumentException("Command /" + command.name() + " has no argument named '" + name + "'");
    }

    /**
     * Returns a declared argument's value, or a fallback if it was omitted.
     *
     * @param name     the argument name
     * @param fallback returned if the argument was not supplied (or its default is {@code null})
     * @param <T>      the expected type
     * @return the value or {@code fallback}
     */
    public <T> T getOrDefault(String name, T fallback) {
        T value = get(name);
        return value == null ? fallback : value;
    }

    /**
     * Returns a declared argument's value as an optional.
     *
     * @param name the argument name
     * @param <T>  the expected type
     * @return the value, or empty if it was omitted
     */
    public <T> Optional<T> optional(String name) {
        return Optional.ofNullable(this.<T>get(name));
    }

    /**
     * Returns whether a declared argument was supplied.
     *
     * @param name the argument name
     * @return {@code true} if the sender typed a value for it
     */
    public boolean has(String name) {
        List<Argument<?>> declared = command.arguments();
        for (int i = 0; i < declared.size(); i++) {
            if (declared.get(i).name().equals(name)) {
                return i < args.length;
            }
        }
        return false;
    }

    /**
     * Parses a raw argument by index with any argument type, without declaring it.
     *
     * @param index the zero-based index
     * @param type  the type
     * @param <T>   the parsed type
     * @return the value
     * @throws CommandError if the argument is missing or invalid
     */
    public <T> T get(int index, ArgumentType<T> type) {
        if (index >= args.length) {
            throw new CommandError(Text.replace(CommandMessages.get("usage"), "usage", command.usage(label)));
        }
        return type.parse(sender, type.greedy() ? joined(index) : args[index]);
    }

    /**
     * Parses a raw argument by index, returning a fallback if it is missing.
     *
     * @param index    the zero-based index
     * @param type     the type
     * @param fallback returned if the argument is absent
     * @param <T>      the parsed type
     * @return the value or {@code fallback}
     * @throws CommandError if the argument is present but invalid
     */
    public <T> T get(int index, ArgumentType<T> type, T fallback) {
        return index >= args.length ? fallback : get(index, type);
    }

    /**
     * Sends a coloured message to the sender, with optional {@code {key}} placeholder pairs.
     *
     * @param message      the message
     * @param placeholders alternating keys and values
     */
    public void reply(String message, Object... placeholders) {
        sender.sendMessage(Text.color(Text.replace(message, placeholders)));
    }

    /**
     * Sends several coloured lines to the sender.
     *
     * @param lines the lines
     */
    public void reply(List<String> lines) {
        for (String line : lines) {
            sender.sendMessage(Text.color(line));
        }
    }

    /**
     * Stops the command with a message.
     *
     * @param message the message, colour codes allowed
     * @return never returns normally; declared so you can write {@code throw ctx.fail(...)} in expressions
     * @throws CommandError always
     */
    public CommandError fail(String message) {
        throw new CommandError(message);
    }

    /**
     * Stops the command with a message unless a condition holds.
     *
     * @param condition the condition that must be true to continue
     * @param message   the message sent if it is false
     * @throws CommandError if {@code condition} is false
     */
    public void check(boolean condition, String message) {
        if (!condition) {
            throw new CommandError(message);
        }
    }

    /**
     * Stops the command with {@link CommandMessages#NO_PERMISSION} unless the sender has a permission.
     *
     * @param permission the permission node
     * @throws CommandError if the sender lacks the permission
     */
    public void checkPermission(String permission) {
        if (!sender.hasPermission(permission)) {
            throw new CommandError(Text.replace(CommandMessages.get("no-permission"), "permission", permission));
        }
    }

    /**
     * Stops the command and shows its usage line.
     *
     * @throws CommandError always
     */
    public void usage() {
        throw new CommandError(Text.replace(CommandMessages.get("usage"), "usage", command.usage(label)));
    }
}
