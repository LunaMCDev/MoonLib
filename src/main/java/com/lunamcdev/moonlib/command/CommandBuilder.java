package com.lunamcdev.moonlib.command;

import com.lunamcdev.moonlib.MoonLib;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * A fluent builder for commands, for when a whole class would be overkill.
 *
 * <pre>{@code
 * CommandBuilder.create("heal")
 *         .permission("moonprison.heal")
 *         .description("Heal yourself or another player")
 *         .optionalArgument("target", ArgumentTypes.PLAYER)
 *         .cooldown(30, TimeUnit.SECONDS, "moonprison.heal.bypass")
 *         .executes(ctx -> {
 *             Player target = ctx.has("target") ? ctx.<Player>get("target") : ctx.player();
 *             Players.heal(target);
 *             ctx.reply("&aHealed &e{name}", "name", target.getName());
 *         })
 *         .register();
 *
 * CommandBuilder.create("mine")
 *         .subcommand(CommandBuilder.create("reset").argument("mine", MINE).executes(...).build())
 *         .subcommand(CommandBuilder.create("tp").playerOnly().executes(...).build())
 *         .register();
 * }</pre>
 */
public final class CommandBuilder {

    /**
     * Command logic that may throw {@link CommandError}.
     */
    @FunctionalInterface
    public interface Executor {
        /**
         * Runs the command.
         *
         * @param ctx the context
         */
        void execute(CommandContext ctx);
    }

    private final String name;
    private final List<String> aliases = new ArrayList<>();
    private final List<Consumer<BuiltCommand>> steps = new ArrayList<>();
    private Executor executor;
    private Function<CommandContext, List<String>> suggester;

    private CommandBuilder(String name) {
        this.name = name;
    }

    /**
     * Starts building a command.
     *
     * @param name    the command name
     * @param aliases optional aliases
     * @return a new builder
     */
    public static CommandBuilder create(String name, String... aliases) {
        CommandBuilder builder = new CommandBuilder(name);
        for (String alias : aliases) {
            builder.aliases.add(alias);
        }
        return builder;
    }

    /**
     * Adds aliases.
     *
     * @param more the aliases
     * @return this builder
     */
    public CommandBuilder aliases(String... more) {
        for (String alias : more) {
            aliases.add(alias);
        }
        return this;
    }

    /**
     * Sets the required permission.
     *
     * @param permission the node
     * @return this builder
     */
    public CommandBuilder permission(String permission) {
        steps.add(cmd -> cmd.permission(permission));
        return this;
    }

    /**
     * Sets the description.
     *
     * @param description the description
     * @return this builder
     */
    public CommandBuilder description(String description) {
        steps.add(cmd -> cmd.description(description));
        return this;
    }

    /**
     * Overrides the argument part of the usage line.
     *
     * @param usage e.g. {@code "<player> [amount]"}
     * @return this builder
     */
    public CommandBuilder usage(String usage) {
        steps.add(cmd -> cmd.customUsage(usage));
        return this;
    }

    /**
     * Restricts the command to players.
     *
     * @return this builder
     */
    public CommandBuilder playerOnly() {
        steps.add(MoonCommand::playerOnly);
        return this;
    }

    /**
     * Hides the command from help and tab completion.
     *
     * @return this builder
     */
    public CommandBuilder hidden() {
        steps.add(MoonCommand::hidden);
        return this;
    }

    /**
     * Adds a per-player cooldown.
     *
     * @param duration the duration
     * @param unit     the unit
     * @return this builder
     */
    public CommandBuilder cooldown(long duration, TimeUnit unit) {
        steps.add(cmd -> cmd.cooldown(duration, unit));
        return this;
    }

    /**
     * Adds a per-player cooldown with a bypass permission.
     *
     * @param duration the duration
     * @param unit     the unit
     * @param bypass   the bypass permission
     * @return this builder
     */
    public CommandBuilder cooldown(long duration, TimeUnit unit, String bypass) {
        steps.add(cmd -> cmd.cooldown(duration, unit, bypass));
        return this;
    }

    /**
     * Declares a required argument.
     *
     * @param argName the name
     * @param type    the type
     * @param <T>     the parsed type
     * @return this builder
     */
    public <T> CommandBuilder argument(String argName, ArgumentType<T> type) {
        steps.add(cmd -> cmd.argument(argName, type));
        return this;
    }

    /**
     * Declares an optional argument.
     *
     * @param argName the name
     * @param type    the type
     * @param <T>     the parsed type
     * @return this builder
     */
    public <T> CommandBuilder optionalArgument(String argName, ArgumentType<T> type) {
        steps.add(cmd -> cmd.optionalArgument(argName, type));
        return this;
    }

    /**
     * Declares an optional argument with a default.
     *
     * @param argName      the name
     * @param type         the type
     * @param defaultValue the default
     * @param <T>          the parsed type
     * @return this builder
     */
    public <T> CommandBuilder optionalArgument(String argName, ArgumentType<T> type, T defaultValue) {
        steps.add(cmd -> cmd.optionalArgument(argName, type, defaultValue));
        return this;
    }

    /**
     * Adds a subcommand.
     *
     * @param sub the subcommand (another builder's {@link #build()} or any {@link MoonCommand})
     * @return this builder
     */
    public CommandBuilder subcommand(MoonCommand sub) {
        steps.add(cmd -> cmd.subcommand(sub));
        return this;
    }

    /**
     * Adds a subcommand from another builder.
     *
     * @param sub the subcommand builder
     * @return this builder
     */
    public CommandBuilder subcommand(CommandBuilder sub) {
        return subcommand(sub.build());
    }

    /**
     * Sets custom tab completion.
     *
     * @param suggester returns suggestions for the argument being typed, or {@code null} for the default
     * @return this builder
     */
    public CommandBuilder suggests(Function<CommandContext, List<String>> suggester) {
        this.suggester = suggester;
        return this;
    }

    /**
     * Sets the logic. If never called, the command shows its help (with subcommands) or usage.
     *
     * @param executor the logic
     * @return this builder
     */
    public CommandBuilder executes(Executor executor) {
        this.executor = executor;
        return this;
    }

    /**
     * Builds the command without registering it.
     *
     * @return the command
     */
    public MoonCommand build() {
        BuiltCommand command = new BuiltCommand(name, aliases.toArray(new String[0]), executor, suggester);
        for (Consumer<BuiltCommand> step : steps) {
            step.accept(command);
        }
        return command;
    }

    /**
     * Builds the command and registers it for the plugin that owns MoonLib.
     *
     * @return the registered command
     */
    public MoonCommand register() {
        MoonCommand command = build();
        CommandRegistry.register(MoonLib.plugin(), command);
        return command;
    }

    private static final class BuiltCommand extends MoonCommand {
        private final Executor executor;
        private final Function<CommandContext, List<String>> suggester;

        BuiltCommand(String name, String[] aliases, Executor executor,
                     Function<CommandContext, List<String>> suggester) {
            super(name, aliases);
            this.executor = executor;
            this.suggester = suggester;
        }

        @Override
        protected void execute(CommandContext ctx) {
            if (executor == null) {
                super.execute(ctx);
            } else {
                executor.execute(ctx);
            }
        }

        @Override
        protected List<String> suggest(CommandContext ctx) {
            return suggester == null ? null : suggester.apply(ctx);
        }
    }
}
