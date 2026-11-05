package dev.lunamc.moonlib.command.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares a method parameter as a command argument. Parameters without {@code @Arg} are also
 * arguments (named after the parameter if the plugin is compiled with {@code -parameters}),
 * except a leading {@link org.bukkit.command.CommandSender CommandSender},
 * {@link org.bukkit.entity.Player Player} or {@link dev.lunamc.moonlib.command.CommandContext
 * CommandContext}, which receive the sender or context instead.
 *
 * <pre>{@code
 * public void pay(Player player,
 *                 @Arg("target") Player target,
 *                 @Arg(value = "amount", def = "100") double amount) { ... }
 * }</pre>
 *
 * @since 1.1.1-alpha
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface Arg {
    /**
     * The argument name, shown in usage lines. Empty to use the parameter name.
     *
     * @return the name
     */
    String value() default "";

    /**
     * Whether the argument may be left out. Implied when {@link #def()} is set. An optional
     * argument with no default receives {@code null}, so it must not be a primitive.
     *
     * @return {@code true} for optional
     */
    boolean optional() default false;

    /**
     * The default value, parsed by the argument's type when the argument is left out. Empty for
     * none.
     *
     * @return the default, as typed by a player
     */
    String def() default "";

    /**
     * The name of a type registered with {@link AnnotatedCommands#registerType(String,
     * dev.lunamc.moonlib.command.ArgumentType)}, used instead of picking a type from the parameter's
     * Java type. Empty to pick automatically.
     *
     * @return the registered type name
     */
    String type() default "";
}
