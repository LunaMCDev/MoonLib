package dev.lunamc.moonlib.command.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Limits a numeric parameter ({@code int}, {@code long} or {@code double} and their wrappers) to a
 * range. Values outside it are rejected with the standard "number out of range" message.
 *
 * @since 1.1.1-alpha
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface Range {
    /**
     * The smallest accepted value.
     *
     * @return the minimum
     */
    double min() default -Double.MAX_VALUE;

    /**
     * The largest accepted value.
     *
     * @return the maximum
     */
    double max() default Double.MAX_VALUE;
}
