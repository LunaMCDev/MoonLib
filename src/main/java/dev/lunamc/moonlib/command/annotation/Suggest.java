package dev.lunamc.moonlib.command.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Supplies tab completions for a parameter from a method on the same handler. The method takes a
 * {@link org.bukkit.command.CommandSender CommandSender} (or nothing) and returns a
 * {@code Collection<String>}. MoonLib filters the results by what the player has typed.
 *
 * <pre>{@code
 * @Subcommand("reset")
 * public void reset(CommandSender sender, @Suggest("mineNames") @Arg("mine") String mine) { ... }
 *
 * List<String> mineNames(CommandSender sender) { return mines.names(); }
 * }</pre>
 *
 * @since 1.4.0
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface Suggest {
    /**
     * The name of the suggestion method.
     *
     * @return the method name
     */
    String value();
}
