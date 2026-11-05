package dev.lunamc.moonlib.command.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Makes a {@code String} parameter take the rest of the input, spaces included. Must be the last
 * argument.
 *
 * <pre>{@code
 * @Subcommand("broadcast")
 * public void broadcast(CommandSender sender, @Greedy @Arg("message") String message) { ... }
 * }</pre>
 *
 * @since 1.4.0
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface Greedy {
}
