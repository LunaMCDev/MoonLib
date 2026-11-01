package dev.lunamc.moonlib.util;

import dev.lunamc.moonlib.MoonContext;
import dev.lunamc.moonlib.MoonLib;
import org.bukkit.plugin.Plugin;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Lightweight logging facade that writes through the calling plugin's {@link Logger}, so every line is
 * prefixed with {@code [YourPlugin]} in the console. When several plugins share MoonLib, each plugin's
 * messages go to its own logger and each plugin has its own debug flag.
 *
 * <p>Messages use SLF4J-style <code>{}</code> placeholders, which are filled in order:</p>
 * <pre>{@code
 * Log.info("Loaded {} mines in {}ms", mines.size(), took);
 * Log.debug("Player {} broke {} at {}", player.getName(), block.getType(), block.getLocation());
 * }</pre>
 *
 * <p>Debug messages are only printed while {@link #setDebug(boolean) debug mode} is on, which makes it
 * cheap to leave debug calls in production code and toggle them from a config option.</p>
 */
public final class Log {

    private static volatile Logger fallback = Logger.getLogger("MoonLib");

    private Log() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Sets the logger used when no plugin is using MoonLib (for example in unit tests).
     *
     * @param logger the fallback logger
     */
    public static void setFallbackLogger(Logger logger) {
        fallback = logger;
    }

    /**
     * Enables or disables debug output for the calling plugin.
     *
     * @param enabled {@code true} to print {@link #debug(String, Object...)} messages
     */
    public static void setDebug(boolean enabled) {
        MoonContext context = MoonLib.context();
        if (context != null) {
            context.setDebug(enabled);
        }
    }

    /**
     * Returns whether debug output is enabled for the calling plugin.
     *
     * @return {@code true} if debug messages are printed
     */
    public static boolean isDebug() {
        MoonContext context = MoonLib.context();
        return context != null && context.isDebug();
    }

    /**
     * Logs an informational message.
     *
     * @param message the message, with optional <code>{}</code> placeholders
     * @param args    values for the placeholders
     */
    public static void info(String message, Object... args) {
        logger().info(format(message, args));
    }

    /**
     * Logs a warning.
     *
     * @param message the message, with optional <code>{}</code> placeholders
     * @param args    values for the placeholders
     */
    public static void warn(String message, Object... args) {
        logger().warning(format(message, args));
    }

    /**
     * Logs an error.
     *
     * @param message the message, with optional <code>{}</code> placeholders
     * @param args    values for the placeholders
     */
    public static void error(String message, Object... args) {
        logger().severe(format(message, args));
    }

    /**
     * Logs an error together with the stack trace of its cause.
     *
     * @param message the message
     * @param cause   the exception that caused the error
     */
    public static void error(String message, Throwable cause) {
        logger().log(Level.SEVERE, message, cause);
    }

    /**
     * Logs a debug message, only when debug mode is enabled.
     *
     * @param message the message, with optional <code>{}</code> placeholders
     * @param args    values for the placeholders
     */
    public static void debug(String message, Object... args) {
        MoonContext context = MoonLib.context();
        if (context != null && context.isDebug()) {
            context.plugin().getLogger().info("[Debug] " + format(message, args));
        }
    }

    /**
     * Returns the logger of the calling plugin.
     *
     * @return the logger, or the fallback logger if no plugin is using MoonLib
     */
    public static Logger logger() {
        Plugin plugin = MoonLib.pluginOrNull();
        return plugin == null ? fallback : plugin.getLogger();
    }

    /**
     * Fills <code>{}</code> placeholders in order with the given arguments.
     *
     * @param message the template
     * @param args    the arguments
     * @return the formatted message
     */
    public static String format(String message, Object... args) {
        if (message == null) {
            return "null";
        }
        if (args == null || args.length == 0) {
            return message;
        }
        StringBuilder out = new StringBuilder(message.length() + 16 * args.length);
        int argIndex = 0;
        int i = 0;
        while (i < message.length()) {
            char c = message.charAt(i);
            if (c == '{' && i + 1 < message.length() && message.charAt(i + 1) == '}' && argIndex < args.length) {
                out.append(args[argIndex++]);
                i += 2;
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }
}
