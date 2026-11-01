package dev.lunamc.moonlib.command;

import dev.lunamc.moonlib.MoonContext;
import dev.lunamc.moonlib.MoonLib;
import org.bukkit.configuration.ConfigurationSection;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * The built-in messages used by the command framework. Override them per plugin with
 * {@link #load(ConfigurationSection)} or {@link #set(String, String)} so server owners can translate
 * them. The public fields are the global defaults shared by every plugin using this copy of MoonLib.
 *
 * <pre>{@code
 * CommandMessages.load(messages.getConfigurationSection("commands"));   // this plugin only
 * CommandMessages.set("no-permission", "&cNope. You need &e{permission}&c.");  // this plugin only
 * CommandMessages.NO_PERMISSION = "&cNope.";                                   // global default
 * }</pre>
 *
 * <p>Available placeholders are listed on each field.</p>
 */
public final class CommandMessages {

    /** Sent when the sender lacks permission. Placeholders: {@code {permission}}. */
    public static String NO_PERMISSION = "&cYou don't have permission to do that.";
    /** Sent when the console runs a player-only command. */
    public static String PLAYER_ONLY = "&cOnly players can use this command.";
    /** Usage line. Placeholders: {@code {usage}}. */
    public static String USAGE = "&cUsage: &7{usage}";
    /** Sent when a command is on cooldown. Placeholders: {@code {time}}. */
    public static String COOLDOWN = "&cPlease wait &e{time} &cbefore using this again.";
    /** Sent when a player argument does not match an online player. Placeholders: {@code {input}}. */
    public static String PLAYER_NOT_FOUND = "&cPlayer &e{input} &cis not online.";
    /** Sent when an offline player has never joined. Placeholders: {@code {input}}. */
    public static String PLAYER_NEVER_JOINED = "&cPlayer &e{input} &chas never joined.";
    /** Sent when a number is invalid. Placeholders: {@code {input}}. */
    public static String INVALID_NUMBER = "&e{input} &cis not a valid number.";
    /** Sent when a number is out of range. Placeholders: {@code {input}}, {@code {min}}, {@code {max}}. */
    public static String NUMBER_OUT_OF_RANGE = "&cNumber must be between &e{min} &cand &e{max}&c.";
    /** Sent when a boolean is invalid. Placeholders: {@code {input}}. */
    public static String INVALID_BOOLEAN = "&e{input} &cmust be true or false.";
    /** Sent when a duration is invalid. Placeholders: {@code {input}}. */
    public static String INVALID_DURATION = "&e{input} &cis not a valid duration (e.g. 1h30m).";
    /** Sent when a world does not exist. Placeholders: {@code {input}}. */
    public static String WORLD_NOT_FOUND = "&cWorld &e{input} &cdoes not exist.";
    /** Sent when a material does not exist. Placeholders: {@code {input}}. */
    public static String INVALID_MATERIAL = "&e{input} &cis not a valid material.";
    /** Sent when a value is not one of the allowed choices. Placeholders: {@code {input}}, {@code {options}}. */
    public static String INVALID_CHOICE = "&e{input} &cis not valid. Options: &7{options}";
    /** Sent when an unexpected exception occurs while running a command. */
    public static String INTERNAL_ERROR = "&cAn internal error occurred while running this command.";
    /** Header of the automatic help menu. Placeholders: {@code {command}}. */
    public static String HELP_HEADER = "&8&m------&r &d/{command} &7help &8&m------";
    /** One line of the automatic help menu. Placeholders: {@code {usage}}, {@code {description}}. */
    public static String HELP_LINE = "&d{usage} &8- &7{description}";

    private CommandMessages() {
        throw new UnsupportedOperationException("Utility class");
    }

    /** Config key to the global default (the public field). */
    private static final Map<String, Supplier<String>> DEFAULTS = new LinkedHashMap<>();

    static {
        DEFAULTS.put("no-permission", () -> NO_PERMISSION);
        DEFAULTS.put("player-only", () -> PLAYER_ONLY);
        DEFAULTS.put("usage", () -> USAGE);
        DEFAULTS.put("cooldown", () -> COOLDOWN);
        DEFAULTS.put("player-not-found", () -> PLAYER_NOT_FOUND);
        DEFAULTS.put("player-never-joined", () -> PLAYER_NEVER_JOINED);
        DEFAULTS.put("invalid-number", () -> INVALID_NUMBER);
        DEFAULTS.put("number-out-of-range", () -> NUMBER_OUT_OF_RANGE);
        DEFAULTS.put("invalid-boolean", () -> INVALID_BOOLEAN);
        DEFAULTS.put("invalid-duration", () -> INVALID_DURATION);
        DEFAULTS.put("world-not-found", () -> WORLD_NOT_FOUND);
        DEFAULTS.put("invalid-material", () -> INVALID_MATERIAL);
        DEFAULTS.put("invalid-choice", () -> INVALID_CHOICE);
        DEFAULTS.put("internal-error", () -> INTERNAL_ERROR);
        DEFAULTS.put("help-header", () -> HELP_HEADER);
        DEFAULTS.put("help-line", () -> HELP_LINE);
    }

    /**
     * Loads overrides from a config section for the <b>calling plugin only</b>. Keys are the field names
     * in lower-kebab-case, e.g. {@code no-permission}, {@code player-only}, {@code help-line}. Missing
     * keys keep their default. When several plugins share MoonLib, each plugin's overrides apply only
     * to its own commands.
     *
     * @param section the section, {@code null} is ignored
     */
    public static void load(ConfigurationSection section) {
        if (section == null) {
            return;
        }
        for (String key : DEFAULTS.keySet()) {
            String value = section.getString(key);
            if (value != null) {
                set(key, value);
            }
        }
    }

    /**
     * Overrides one message for the calling plugin only. To change a message for every plugin that shares
     * this copy of MoonLib, assign the public field instead.
     *
     * @param key   the config key, e.g. {@code "no-permission"}
     * @param value the message
     * @throws IllegalArgumentException if the key is unknown
     */
    public static void set(String key, String value) {
        if (!DEFAULTS.containsKey(key)) {
            throw new IllegalArgumentException("Unknown command message key '" + key + "'. Valid keys: " + DEFAULTS.keySet());
        }
        MoonContext context = MoonLib.context();
        if (context != null) {
            context.commandMessages().put(key, value);
        } else {
            setDefault(key, value);
        }
    }

    /**
     * Returns a message as seen by the calling plugin: its own override if it set one, otherwise the
     * public field's value.
     *
     * @param key the config key, e.g. {@code "no-permission"}
     * @return the message
     * @throws IllegalArgumentException if the key is unknown
     */
    public static String get(String key) {
        Supplier<String> fallback = DEFAULTS.get(key);
        if (fallback == null) {
            throw new IllegalArgumentException("Unknown command message key '" + key + "'");
        }
        MoonContext context = MoonLib.context();
        if (context != null) {
            String override = context.commandMessages().get(key);
            if (override != null) {
                return override;
            }
        }
        return fallback.get();
    }

    /**
     * Returns every valid message key.
     *
     * @return the keys in declaration order
     */
    public static Set<String> keys() {
        return Collections.unmodifiableSet(DEFAULTS.keySet());
    }

    private static void setDefault(String key, String value) {
        switch (key) {
            case "no-permission":
                NO_PERMISSION = value;
                break;
            case "player-only":
                PLAYER_ONLY = value;
                break;
            case "usage":
                USAGE = value;
                break;
            case "cooldown":
                COOLDOWN = value;
                break;
            case "player-not-found":
                PLAYER_NOT_FOUND = value;
                break;
            case "player-never-joined":
                PLAYER_NEVER_JOINED = value;
                break;
            case "invalid-number":
                INVALID_NUMBER = value;
                break;
            case "number-out-of-range":
                NUMBER_OUT_OF_RANGE = value;
                break;
            case "invalid-boolean":
                INVALID_BOOLEAN = value;
                break;
            case "invalid-duration":
                INVALID_DURATION = value;
                break;
            case "world-not-found":
                WORLD_NOT_FOUND = value;
                break;
            case "invalid-material":
                INVALID_MATERIAL = value;
                break;
            case "invalid-choice":
                INVALID_CHOICE = value;
                break;
            case "internal-error":
                INTERNAL_ERROR = value;
                break;
            case "help-header":
                HELP_HEADER = value;
                break;
            case "help-line":
                HELP_LINE = value;
                break;
            default:
                throw new IllegalArgumentException(key);
        }
    }
}
