package com.lunamcdev.moonlib.command;

/**
 * A declared, named command argument. Created through {@link MoonCommand#argument(String, ArgumentType)}
 * and {@link MoonCommand#optionalArgument(String, ArgumentType)}.
 *
 * @param <T> the parsed type
 */
public final class Argument<T> {

    private final String name;
    private final ArgumentType<T> type;
    private final boolean optional;
    private final T defaultValue;

    Argument(String name, ArgumentType<T> type, boolean optional, T defaultValue) {
        this.name = name;
        this.type = type;
        this.optional = optional;
        this.defaultValue = defaultValue;
    }

    /**
     * Returns the argument name, used in the usage line and with {@link CommandContext#get(String)}.
     *
     * @return the name
     */
    public String name() {
        return name;
    }

    /**
     * Returns the argument type.
     *
     * @return the type
     */
    public ArgumentType<T> type() {
        return type;
    }

    /**
     * Returns whether the argument may be omitted.
     *
     * @return {@code true} if optional
     */
    public boolean optional() {
        return optional;
    }

    /**
     * Returns the value used when an optional argument is omitted.
     *
     * @return the default, possibly {@code null}
     */
    public T defaultValue() {
        return defaultValue;
    }

    /**
     * Returns how the argument appears in usage lines: {@code <name>} or {@code [name]}, with
     * {@code ...} appended for greedy arguments.
     *
     * @return the usage token
     */
    public String usageToken() {
        String inner = type.greedy() ? name + "..." : name;
        return optional ? "[" + inner + "]" : "<" + inner + ">";
    }
}
