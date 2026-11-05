package com.lunamcdev.moonlib.command;

/**
 * Thrown from command code to stop execution and show a message to the sender.
 *
 * <p>This is the idiomatic way to fail inside a command: no {@code return} chains, no
 * nested {@code if}s. The message is coloured with {@link com.lunamcdev.moonlib.util.Text#color(String)}.</p>
 *
 * <pre>{@code
 * if (balance < price) {
 *     throw new CommandError("&cYou need &e" + price + " &ccoins.");
 * }
 * // or, equivalently
 * ctx.check(balance >= price, "&cYou need &e" + price + " &ccoins.");
 * }</pre>
 *
 * <p>It is unchecked, so it can be thrown from helpers, lambdas and argument parsers.</p>
 */
public class CommandError extends RuntimeException {

    /** Serialisation version. */
    private static final long serialVersionUID = 1L;

    /** Whether the command's usage line is sent after the message. */
    private final boolean showUsage;

    /**
     * Creates an error with a message.
     *
     * @param message the message to send, colour codes allowed; {@code null} sends nothing
     */
    public CommandError(String message) {
        this(message, false);
    }

    /**
     * Creates an error with a message, optionally followed by the command's usage line.
     *
     * @param message   the message to send, colour codes allowed
     * @param showUsage {@code true} to also send the usage line
     */
    public CommandError(String message, boolean showUsage) {
        super(message, null, false, false);
        this.showUsage = showUsage;
    }

    /**
     * Returns whether the usage line should be sent after the message.
     *
     * @return {@code true} to show usage
     */
    public boolean showUsage() {
        return showUsage;
    }
}
