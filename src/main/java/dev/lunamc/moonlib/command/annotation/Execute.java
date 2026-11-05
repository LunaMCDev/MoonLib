package dev.lunamc.moonlib.command.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks the method of a {@link Command @Command} class that runs when the command is used with no
 * subcommand. Without one, the bare command shows the help page.
 *
 * @since 1.1.1-alpha
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Execute {
}
