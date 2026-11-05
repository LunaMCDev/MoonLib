package dev.lunamc.moonlib.command.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

/**
 * Adds a per-player cooldown to a command class (the bare command) or a subcommand method.
 *
 * <pre>{@code
 * @Subcommand("daily")
 * @Cooldown(value = 24, unit = TimeUnit.HOURS, bypass = "kits.bypass")
 * public void daily(Player player) { ... }
 * }</pre>
 *
 * @since 1.4.0
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface Cooldown {
    /**
     * The cooldown length, in {@link #unit()}.
     *
     * @return the length
     */
    long value();

    /**
     * The unit of {@link #value()}.
     *
     * @return the unit
     */
    TimeUnit unit() default TimeUnit.SECONDS;

    /**
     * A permission that skips the cooldown. Empty for none.
     *
     * @return the bypass permission
     */
    String bypass() default "";
}
