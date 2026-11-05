package dev.lunamc.moonlib.command.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Turns a method of a {@link Command @Command} class into a subcommand. Use spaces for nested
 * subcommands: {@code @Subcommand("mine reset")} creates {@code /cmd mine reset}, adding the
 * {@code mine} level automatically if no method declares it.
 *
 * @since 1.1.1-alpha
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Subcommand {
    /**
     * The subcommand path, for example {@code "give"} or {@code "mine reset"}.
     *
     * @return the path
     */
    String value();

    /**
     * Aliases for the last part of the path.
     *
     * @return the aliases
     */
    String[] aliases() default {};

    /**
     * The permission needed. Empty for none (the parent's permission still applies).
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
     * Whether only players can use this subcommand. A {@link org.bukkit.entity.Player Player}
     * sender parameter implies this.
     *
     * @return {@code true} for player-only
     */
    boolean playerOnly() default false;

    /**
     * Whether the subcommand is hidden from help pages and tab completion.
     *
     * @return {@code true} to hide
     */
    boolean hidden() default false;
}
