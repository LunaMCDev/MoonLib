package dev.lunamc.moonlib.command.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class as a command handler for {@link AnnotatedCommands}. The class's
 * {@link Execute @Execute} method runs for the bare command, and each
 * {@link Subcommand @Subcommand} method becomes a subcommand.
 *
 * <pre>{@code
 * @Command(value = "tokens", aliases = "tk", permission = "prison.tokens")
 * public final class TokensCommand {
 *
 *     @Execute
 *     public String balance(Player player) {
 *         return "&eYou have &f" + tokens.of(player) + " &etokens";
 *     }
 *
 *     @Subcommand(value = "give", permission = "prison.tokens.give")
 *     public void give(CommandSender sender, @Arg("target") Player target, @Arg("amount") int amount) {
 *         tokens.add(target, amount);
 *     }
 * }
 * }</pre>
 *
 * @since 1.1.1-alpha
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Command {
    /**
     * The command name, without a slash.
     *
     * @return the name
     */
    String value();

    /**
     * Aliases for the command.
     *
     * @return the aliases
     */
    String[] aliases() default {};

    /**
     * The permission needed to use the command or any subcommand. Empty for none.
     *
     * @return the permission node
     */
    String permission() default "";

    /**
     * A short description shown in help pages.
     *
     * @return the description
     */
    String description() default "";

    /**
     * Whether only players can use the command.
     *
     * @return {@code true} for player-only
     */
    boolean playerOnly() default false;

    /**
     * Whether the command is hidden from help pages and tab completion of its parent.
     *
     * @return {@code true} to hide
     */
    boolean hidden() default false;
}
