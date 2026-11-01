package dev.lunamc.moonlib.command;

import dev.lunamc.moonlib.util.Cooldown;
import dev.lunamc.moonlib.util.Log;
import dev.lunamc.moonlib.util.Text;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Base class for commands, with subcommands, typed arguments, permissions, cooldowns, automatic
 * usage/help messages and tab completion.
 *
 * <p>Configure the command in its constructor and implement {@link #execute(CommandContext)}:</p>
 *
 * <pre>{@code
 * public final class GiveTokensCommand extends MoonCommand {
 *
 *     public GiveTokensCommand() {
 *         super("givetokens", "gt");
 *         description("Give tokens to a player");
 *         permission("moonprison.admin.tokens");
 *         argument("target", ArgumentTypes.PLAYER);
 *         argument("amount", ArgumentTypes.POSITIVE_INTEGER);
 *         optionalArgument("reason", ArgumentTypes.GREEDY_STRING);
 *     }
 *
 *     @Override
 *     protected void execute(CommandContext ctx) {
 *         Player target = ctx.get("target");
 *         int amount = ctx.get("amount");
 *         String reason = ctx.getOrDefault("reason", "no reason");
 *         tokens.add(target, amount);
 *         ctx.reply("&aGave &e{amount} &atokens to &e{player} &7({reason})",
 *                 "amount", amount, "player", target.getName(), "reason", reason);
 *     }
 * }
 * }</pre>
 *
 * <h2>Subcommands</h2>
 * <p>Add other {@code MoonCommand}s with {@link #subcommand(MoonCommand)}. A command with subcommands
 * that is run with no (or an unknown) subcommand calls {@link #execute(CommandContext)}, which by default
 * prints an automatically generated, permission-filtered help page.</p>
 *
 * <h2>Execution order</h2>
 * <ol>
 *     <li>Subcommand routing (if the first argument names a subcommand)</li>
 *     <li>Permission check</li>
 *     <li>Player-only check</li>
 *     <li>Required argument count check (shows usage)</li>
 *     <li>Cooldown check</li>
 *     <li>{@link #execute(CommandContext)}; a thrown {@link CommandError} sends its message</li>
 * </ol>
 *
 * <p>Register with {@link dev.lunamc.moonlib.MoonPlugin#registerCommands(MoonCommand...)} or
 * {@link CommandRegistry#register(org.bukkit.plugin.Plugin, MoonCommand)}. No {@code plugin.yml}
 * entry is needed.</p>
 */
public abstract class MoonCommand {

    private final String name;
    private final List<String> aliases = new ArrayList<>();
    private final Map<String, MoonCommand> subcommands = new LinkedHashMap<>();
    private final List<MoonCommand> subcommandList = new ArrayList<>();
    private final List<Argument<?>> arguments = new ArrayList<>();

    private MoonCommand parent;
    private String permission;
    private String description = "";
    private String customUsage;
    private boolean playerOnly;
    private boolean hidden;
    private Cooldown<UUID> cooldown;
    private String cooldownBypassPermission;

    /**
     * Creates a command.
     *
     * @param name    the primary name, without a slash
     * @param aliases alternative names
     */
    protected MoonCommand(String name, String... aliases) {
        if (name == null || name.trim().isEmpty() || name.contains(" ")) {
            throw new IllegalArgumentException("Invalid command name: '" + name + "'");
        }
        this.name = name.toLowerCase(Locale.ROOT);
        for (String alias : aliases) {
            this.aliases.add(alias.toLowerCase(Locale.ROOT));
        }
    }

    // ------------------------------------------------------------------ configuration

    /**
     * Adds aliases.
     *
     * @param more the aliases
     * @return this command
     */
    protected final MoonCommand aliases(String... more) {
        for (String alias : more) {
            aliases.add(alias.toLowerCase(Locale.ROOT));
        }
        return this;
    }

    /**
     * Requires a permission to run (and see) the command. Subcommands are additionally checked against
     * their own permission.
     *
     * @param node the permission node, or {@code null} for none
     * @return this command
     */
    protected final MoonCommand permission(String node) {
        this.permission = node;
        return this;
    }

    /**
     * Sets the description shown in help pages.
     *
     * @param text the description
     * @return this command
     */
    protected final MoonCommand description(String text) {
        this.description = text == null ? "" : text;
        return this;
    }

    /**
     * Overrides the automatically generated argument part of the usage line.
     *
     * @param usage e.g. {@code "<player> [amount]"}
     * @return this command
     */
    protected final MoonCommand customUsage(String usage) {
        this.customUsage = usage;
        return this;
    }

    /**
     * Restricts the command to players. The console receives {@link CommandMessages#PLAYER_ONLY}.
     *
     * @return this command
     */
    protected final MoonCommand playerOnly() {
        this.playerOnly = true;
        return this;
    }

    /**
     * Hides the command from help pages and tab completion (it still works when typed).
     *
     * @return this command
     */
    protected final MoonCommand hidden() {
        this.hidden = true;
        return this;
    }

    /**
     * Adds a per-player cooldown, applied after a successful execution.
     *
     * @param duration the cooldown length
     * @param unit     the unit of {@code duration}
     * @return this command
     */
    protected final MoonCommand cooldown(long duration, TimeUnit unit) {
        this.cooldown = Cooldown.of(duration, unit);
        return this;
    }

    /**
     * Adds a per-player cooldown that players with a bypass permission ignore.
     *
     * @param duration         the cooldown length
     * @param unit             the unit of {@code duration}
     * @param bypassPermission the permission that skips the cooldown
     * @return this command
     */
    protected final MoonCommand cooldown(long duration, TimeUnit unit, String bypassPermission) {
        cooldown(duration, unit);
        this.cooldownBypassPermission = bypassPermission;
        return this;
    }

    /**
     * Declares a required argument. Arguments are positional in declaration order.
     *
     * @param argName the name, used in usage lines and {@link CommandContext#get(String)}
     * @param type    the type
     * @param <T>     the parsed type
     * @return this command
     */
    protected final <T> MoonCommand argument(String argName, ArgumentType<T> type) {
        return addArgument(new Argument<>(argName, type, false, null));
    }

    /**
     * Declares an optional argument whose value is {@code null} when omitted.
     *
     * @param argName the name
     * @param type    the type
     * @param <T>     the parsed type
     * @return this command
     */
    protected final <T> MoonCommand optionalArgument(String argName, ArgumentType<T> type) {
        return addArgument(new Argument<>(argName, type, true, null));
    }

    /**
     * Declares an optional argument with a default value.
     *
     * @param argName      the name
     * @param type         the type
     * @param defaultValue the value used when omitted
     * @param <T>          the parsed type
     * @return this command
     */
    protected final <T> MoonCommand optionalArgument(String argName, ArgumentType<T> type, T defaultValue) {
        return addArgument(new Argument<>(argName, type, true, defaultValue));
    }

    /**
     * Adds a subcommand. Its name and aliases are matched case-insensitively against the first argument.
     *
     * @param sub the subcommand
     * @return this command
     */
    protected final MoonCommand subcommand(MoonCommand sub) {
        if (sub.parent != null) {
            throw new IllegalStateException("/" + sub.name + " is already a subcommand of /" + sub.parent.name);
        }
        sub.parent = this;
        subcommandList.add(sub);
        subcommands.put(sub.name, sub);
        for (String alias : sub.aliases) {
            subcommands.putIfAbsent(alias, sub);
        }
        return this;
    }

    private MoonCommand addArgument(Argument<?> argument) {
        if (!arguments.isEmpty()) {
            Argument<?> last = arguments.get(arguments.size() - 1);
            if (last.type().greedy()) {
                throw new IllegalStateException("A greedy argument must be the last argument of /" + name);
            }
            if (last.optional() && !argument.optional()) {
                throw new IllegalStateException("Required argument '" + argument.name()
                        + "' cannot follow an optional argument in /" + name);
            }
        }
        for (Argument<?> existing : arguments) {
            if (existing.name().equals(argument.name())) {
                throw new IllegalStateException("Duplicate argument '" + argument.name() + "' in /" + name);
            }
        }
        arguments.add(argument);
        return this;
    }

    // ------------------------------------------------------------------ behaviour

    /**
     * Runs the command. All checks (permission, player-only, argument count, cooldown) have passed.
     *
     * <p>The default implementation sends the help page if the command has subcommands, or the usage
     * line otherwise.</p>
     *
     * @param ctx the context
     * @throws CommandError to abort with a message
     */
    protected void execute(CommandContext ctx) {
        if (!subcommandList.isEmpty()) {
            sendHelp(ctx.sender(), ctx.label());
        } else {
            ctx.usage();
        }
    }

    /**
     * Provides custom tab completions for the argument currently being typed. Return {@code null} to
     * use the declared argument types' suggestions (the default).
     *
     * @param ctx the context; the last raw argument is the one being completed
     * @return suggestions, or {@code null} for the default behaviour
     */
    protected List<String> suggest(CommandContext ctx) {
        return null;
    }

    /**
     * Sends the automatic help page listing visible subcommands the sender may use.
     *
     * @param sender the recipient
     * @param label  the label used
     */
    public void sendHelp(CommandSender sender, String label) {
        sender.sendMessage(Text.color(Text.replace(CommandMessages.get("help-header"), "command", label)));
        if (subcommandList.isEmpty()) {
            sender.sendMessage(Text.color(Text.replace(CommandMessages.get("help-line"),
                    "usage", usage(label), "description", description)));
            return;
        }
        for (MoonCommand sub : subcommandList) {
            if (!sub.hidden && sub.hasPermission(sender)) {
                sender.sendMessage(Text.color(Text.replace(CommandMessages.get("help-line"),
                        "usage", sub.usage(label + " " + sub.name), "description", sub.description)));
            }
        }
    }

    // ------------------------------------------------------------------ dispatching (package-private)

    final void dispatch(CommandSender sender, String label, String[] args) {
        if (!subcommandList.isEmpty() && args.length > 0) {
            MoonCommand sub = subcommands.get(args[0].toLowerCase(Locale.ROOT));
            if (sub != null) {
                if (!hasPermission(sender)) {
                    sendNoPermission(sender);
                    return;
                }
                sub.dispatch(sender, label + " " + args[0].toLowerCase(Locale.ROOT), Arrays.copyOfRange(args, 1, args.length));
                return;
            }
        }

        if (!hasPermission(sender)) {
            sendNoPermission(sender);
            return;
        }
        if (playerOnly && !(sender instanceof Player)) {
            sender.sendMessage(Text.color(CommandMessages.get("player-only")));
            return;
        }
        if (args.length < requiredArgumentCount()) {
            sender.sendMessage(Text.color(Text.replace(CommandMessages.get("usage"), "usage", usage(label))));
            return;
        }
        UUID cooldownKey = null;
        if (cooldown != null && sender instanceof Player
                && (cooldownBypassPermission == null || !sender.hasPermission(cooldownBypassPermission))) {
            cooldownKey = ((Player) sender).getUniqueId();
            if (cooldown.isActive(cooldownKey)) {
                sender.sendMessage(Text.color(Text.replace(CommandMessages.get("cooldown"),
                        "time", cooldown.formatRemaining(cooldownKey))));
                return;
            }
        }

        CommandContext ctx = new CommandContext(this, sender, label, args);
        try {
            execute(ctx);
            if (cooldownKey != null) {
                cooldown.put(cooldownKey);
            }
        } catch (CommandError error) {
            if (error.getMessage() != null && !error.getMessage().isEmpty()) {
                sender.sendMessage(Text.color(error.getMessage()));
            }
            if (error.showUsage()) {
                sender.sendMessage(Text.color(Text.replace(CommandMessages.get("usage"), "usage", usage(label))));
            }
        } catch (RuntimeException e) {
            sender.sendMessage(Text.color(CommandMessages.get("internal-error")));
            Log.error("Unhandled exception while " + sender.getName() + " ran /" + label + " "
                    + String.join(" ", args), e);
        }
    }

    final List<String> complete(CommandSender sender, String label, String[] args) {
        if (!hasPermission(sender) || args.length == 0) {
            return Collections.emptyList();
        }
        String current = args[args.length - 1];

        if (!subcommandList.isEmpty()) {
            if (args.length == 1) {
                Set<String> names = new LinkedHashSet<>();
                for (MoonCommand sub : subcommandList) {
                    if (!sub.hidden && sub.hasPermission(sender)) {
                        names.add(sub.name);
                    }
                }
                List<String> matches = filter(new ArrayList<>(names), current);
                if (!matches.isEmpty() || arguments.isEmpty()) {
                    return matches;
                }
            } else {
                MoonCommand sub = subcommands.get(args[0].toLowerCase(Locale.ROOT));
                if (sub != null) {
                    return sub.complete(sender, label + " " + args[0], Arrays.copyOfRange(args, 1, args.length));
                }
            }
        }

        List<String> custom;
        try {
            custom = suggest(new CommandContext(this, sender, label, args));
        } catch (RuntimeException e) {
            custom = null;
        }
        if (custom != null) {
            return filter(custom, current);
        }

        int index = args.length - 1;
        Argument<?> argument = null;
        if (index < arguments.size()) {
            argument = arguments.get(index);
        } else if (!arguments.isEmpty() && arguments.get(arguments.size() - 1).type().greedy()) {
            argument = arguments.get(arguments.size() - 1);
        }
        if (argument == null) {
            return Collections.emptyList();
        }
        try {
            return filter(argument.type().suggest(sender, current), current);
        } catch (RuntimeException e) {
            return Collections.emptyList();
        }
    }

    // ------------------------------------------------------------------ accessors

    /**
     * Returns the full usage line, e.g. {@code /mine reset <mine>}.
     *
     * @param label the label to show (the alias the sender typed, including parents)
     * @return the usage line
     */
    public String usage(String label) {
        StringBuilder sb = new StringBuilder("/").append(label);
        if (customUsage != null) {
            if (!customUsage.isEmpty()) {
                sb.append(' ').append(customUsage);
            }
            return sb.toString();
        }
        if (!subcommandList.isEmpty() && arguments.isEmpty()) {
            sb.append(" <").append(subcommandNamesForUsage()).append('>');
        }
        for (Argument<?> argument : arguments) {
            sb.append(' ').append(argument.usageToken());
        }
        return sb.toString();
    }

    /**
     * Returns whether the sender has this command's permission (and every parent's).
     *
     * @param sender the sender
     * @return {@code true} if allowed
     */
    public boolean hasPermission(CommandSender sender) {
        if (parent != null && !parent.hasPermission(sender)) {
            return false;
        }
        return permission == null || permission.isEmpty() || sender.hasPermission(permission);
    }

    /**
     * Returns the primary name.
     *
     * @return the name
     */
    public final String name() {
        return name;
    }

    /**
     * Returns the aliases.
     *
     * @return an unmodifiable list of aliases
     */
    public final List<String> aliases() {
        return Collections.unmodifiableList(aliases);
    }

    /**
     * Returns the permission node.
     *
     * @return the permission, or {@code null}
     */
    public final String permission() {
        return permission;
    }

    /**
     * Returns the description.
     *
     * @return the description, never {@code null}
     */
    public final String description() {
        return description;
    }

    /**
     * Returns whether the command is player-only.
     *
     * @return {@code true} if only players may run it
     */
    public final boolean isPlayerOnly() {
        return playerOnly;
    }

    /**
     * Returns whether the command is hidden from help and tab completion.
     *
     * @return {@code true} if hidden
     */
    public final boolean isHidden() {
        return hidden;
    }

    /**
     * Returns the parent command for subcommands.
     *
     * @return the parent, or {@code null} for root commands
     */
    public final MoonCommand parent() {
        return parent;
    }

    /**
     * Returns the subcommands in registration order.
     *
     * @return an unmodifiable list
     */
    public final List<MoonCommand> subcommands() {
        return Collections.unmodifiableList(subcommandList);
    }

    /**
     * Returns the declared arguments in order.
     *
     * @return an unmodifiable list
     */
    public final List<Argument<?>> arguments() {
        return Collections.unmodifiableList(arguments);
    }

    /**
     * Returns the command's cooldown tracker, for example to reset a player's cooldown.
     *
     * @return the cooldown, or {@code null} if the command has none
     */
    public final Cooldown<UUID> cooldownTracker() {
        return cooldown;
    }

    private int requiredArgumentCount() {
        int count = 0;
        for (Argument<?> argument : arguments) {
            if (!argument.optional()) {
                count++;
            }
        }
        return count;
    }

    private String subcommandNamesForUsage() {
        List<String> names = new ArrayList<>();
        for (MoonCommand sub : subcommandList) {
            if (!sub.hidden) {
                names.add(sub.name);
            }
        }
        return String.join("|", names);
    }

    private void sendNoPermission(CommandSender sender) {
        String node = permission;
        for (MoonCommand cmd = this; node == null && cmd != null; cmd = cmd.parent) {
            node = cmd.permission;
        }
        sender.sendMessage(Text.color(Text.replace(CommandMessages.get("no-permission"), "permission",
                node == null ? "" : node)));
    }

    private static List<String> filter(List<String> options, String prefix) {
        if (options == null || options.isEmpty()) {
            return Collections.emptyList();
        }
        String lower = prefix.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (String option : options) {
            if (option != null && option.toLowerCase(Locale.ROOT).startsWith(lower)) {
                result.add(option);
            }
        }
        Collections.sort(result, String.CASE_INSENSITIVE_ORDER);
        return result;
    }
}
