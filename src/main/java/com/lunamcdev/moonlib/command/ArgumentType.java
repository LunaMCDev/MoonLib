package com.lunamcdev.moonlib.command;

import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

/**
 * Parses one command argument into a typed value and provides tab completions for it.
 *
 * <p>Built-in types live in {@link ArgumentTypes}. Custom types are one lambda away:</p>
 *
 * <pre>{@code
 * public static final ArgumentType<Mine> MINE = ArgumentType.of(
 *         input -> mines.find(input).orElseThrow(() -> new CommandError("&cUnknown mine: " + input)),
 *         () -> mines.names());
 *
 * // then in a command
 * argument("mine", MINE);
 * Mine mine = ctx.get("mine");
 * }</pre>
 *
 * @param <T> the parsed type
 */
public interface ArgumentType<T> {

    /**
     * Parses the raw input.
     *
     * @param sender the command sender, for permission or context aware parsing
     * @param input  the raw argument (for greedy types, all remaining arguments joined by spaces)
     * @return the parsed value
     * @throws CommandError if the input is invalid; its message is shown to the sender
     */
    T parse(CommandSender sender, String input) throws CommandError;

    /**
     * Returns tab completions. The framework filters them by the typed prefix, so returning every
     * possible value is fine.
     *
     * @param sender the sender
     * @param input  what has been typed so far for this argument
     * @return the suggestions, never {@code null}
     */
    default List<String> suggest(CommandSender sender, String input) {
        return Collections.emptyList();
    }

    /**
     * Returns whether this type consumes all remaining arguments (like a reason or a message).
     * A greedy argument must be the last declared argument.
     *
     * @return {@code true} if greedy
     */
    default boolean greedy() {
        return false;
    }

    /**
     * Creates an argument type from a parser and a suggestion supplier.
     *
     * @param parser      converts input to a value, throwing {@link CommandError} on failure
     * @param suggestions supplies all possible values for tab completion
     * @param <T>         the parsed type
     * @return the argument type
     */
    static <T> ArgumentType<T> of(Function<String, T> parser, java.util.function.Supplier<? extends Collection<String>> suggestions) {
        return new ArgumentType<T>() {
            @Override
            public T parse(CommandSender sender, String input) {
                return parser.apply(input);
            }

            @Override
            public List<String> suggest(CommandSender sender, String input) {
                Collection<String> values = suggestions.get();
                return values == null ? Collections.<String>emptyList() : new ArrayList<>(values);
            }
        };
    }

    /**
     * Creates an argument type without tab completions.
     *
     * @param parser converts input to a value, throwing {@link CommandError} on failure
     * @param <T>    the parsed type
     * @return the argument type
     */
    static <T> ArgumentType<T> of(Function<String, T> parser) {
        return (sender, input) -> parser.apply(input);
    }

    /**
     * Returns a copy of this type that suggests the given values instead.
     *
     * @param suggestions the suggestions to offer
     * @return a new argument type
     */
    default ArgumentType<T> withSuggestions(java.util.function.Supplier<? extends Collection<String>> suggestions) {
        ArgumentType<T> self = this;
        return new ArgumentType<T>() {
            @Override
            public T parse(CommandSender sender, String input) {
                return self.parse(sender, input);
            }

            @Override
            public List<String> suggest(CommandSender sender, String input) {
                Collection<String> values = suggestions.get();
                return values == null ? Collections.<String>emptyList() : new ArrayList<>(values);
            }

            @Override
            public boolean greedy() {
                return self.greedy();
            }
        };
    }
}
