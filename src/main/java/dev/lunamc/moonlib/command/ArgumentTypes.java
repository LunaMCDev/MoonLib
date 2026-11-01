package dev.lunamc.moonlib.command;

import dev.lunamc.moonlib.util.Durations;
import dev.lunamc.moonlib.util.Text;
import dev.lunamc.moonlib.version.Materials;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Built-in {@link ArgumentType}s. Every type produces a friendly, configurable error message
 * (see {@link CommandMessages}) and sensible tab completions.
 *
 * <pre>{@code
 * argument("target", ArgumentTypes.PLAYER);
 * argument("amount", ArgumentTypes.integer(1, 64));
 * optionalArgument("duration", ArgumentTypes.DURATION);
 * argument("mode", ArgumentTypes.enumType(GameMode.class));
 * argument("reason", ArgumentTypes.GREEDY_STRING);
 * }</pre>
 */
public final class ArgumentTypes {

    /** A single word. */
    public static final ArgumentType<String> STRING = (sender, input) -> input;

    /** All remaining words joined with spaces. Must be the last argument. */
    public static final ArgumentType<String> GREEDY_STRING = new ArgumentType<String>() {
        @Override
        public String parse(CommandSender sender, String input) {
            return input;
        }

        @Override
        public boolean greedy() {
            return true;
        }
    };

    /** Any 32-bit integer. */
    public static final ArgumentType<Integer> INTEGER = integer(Integer.MIN_VALUE, Integer.MAX_VALUE);

    /** An integer of 1 or more. */
    public static final ArgumentType<Integer> POSITIVE_INTEGER = integer(1, Integer.MAX_VALUE);

    /** Any 64-bit integer. Accepts compact suffixes like {@code 5k}, {@code 2.5m}, {@code 1b}. */
    public static final ArgumentType<Long> LONG = (sender, input) -> {
        Double value = parseCompact(input);
        if (value == null || value > Long.MAX_VALUE || value < Long.MIN_VALUE) {
            throw error(CommandMessages.get("invalid-number"), input);
        }
        return value.longValue();
    };

    /** Any finite decimal number. Accepts compact suffixes like {@code 5k}, {@code 2.5m}, {@code 1b}. */
    public static final ArgumentType<Double> DOUBLE = doubleRange(-Double.MAX_VALUE, Double.MAX_VALUE);

    /** A decimal number greater than zero, ideal for money amounts. Accepts compact suffixes. */
    public static final ArgumentType<Double> POSITIVE_DOUBLE = doubleRange(Double.MIN_VALUE, Double.MAX_VALUE);

    /** {@code true/false}, {@code yes/no}, {@code on/off}, {@code enable/disable}. */
    public static final ArgumentType<Boolean> BOOLEAN = new ArgumentType<Boolean>() {
        @Override
        public Boolean parse(CommandSender sender, String input) {
            switch (input.toLowerCase(Locale.ROOT)) {
                case "true": case "yes": case "on": case "enable": case "enabled": case "y":
                    return true;
                case "false": case "no": case "off": case "disable": case "disabled": case "n":
                    return false;
                default:
                    throw error(CommandMessages.get("invalid-boolean"), input);
            }
        }

        @Override
        public List<String> suggest(CommandSender sender, String input) {
            return Arrays.asList("true", "false");
        }
    };

    /**
     * An online player matched by exact name, then by unique name prefix. Players hidden from the
     * sender (vanished) are neither suggested nor matched.
     */
    public static final ArgumentType<Player> PLAYER = new ArgumentType<Player>() {
        @Override
        public Player parse(CommandSender sender, String input) {
            Player exact = Bukkit.getPlayerExact(input);
            if (exact != null && canSee(sender, exact)) {
                return exact;
            }
            Player match = null;
            String lower = input.toLowerCase(Locale.ROOT);
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (online.getName().toLowerCase(Locale.ROOT).startsWith(lower) && canSee(sender, online)) {
                    if (match != null) {
                        match = null;
                        break;
                    }
                    match = online;
                }
            }
            if (match == null) {
                throw error(CommandMessages.get("player-not-found"), input);
            }
            return match;
        }

        @Override
        public List<String> suggest(CommandSender sender, String input) {
            return visiblePlayerNames(sender);
        }
    };

    /**
     * A player who is online or has joined before. Suggests online player names.
     */
    @SuppressWarnings("deprecation")
    public static final ArgumentType<OfflinePlayer> OFFLINE_PLAYER = new ArgumentType<OfflinePlayer>() {
        @Override
        public OfflinePlayer parse(CommandSender sender, String input) {
            Player online = Bukkit.getPlayerExact(input);
            if (online != null) {
                return online;
            }
            OfflinePlayer offline = Bukkit.getOfflinePlayer(input);
            if (!offline.hasPlayedBefore() && !offline.isOnline()) {
                throw error(CommandMessages.get("player-never-joined"), input);
            }
            return offline;
        }

        @Override
        public List<String> suggest(CommandSender sender, String input) {
            return visiblePlayerNames(sender);
        }
    };

    /** A loaded world by name. */
    public static final ArgumentType<World> WORLD = new ArgumentType<World>() {
        @Override
        public World parse(CommandSender sender, String input) {
            World world = Bukkit.getWorld(input);
            if (world == null) {
                throw error(CommandMessages.get("world-not-found"), input);
            }
            return world;
        }

        @Override
        public List<String> suggest(CommandSender sender, String input) {
            List<String> names = new ArrayList<>();
            for (World world : Bukkit.getWorlds()) {
                names.add(world.getName());
            }
            return names;
        }
    };

    /** A material, accepting modern names on every version (see {@link Materials}). */
    public static final ArgumentType<Material> MATERIAL = new ArgumentType<Material>() {
        @Override
        public Material parse(CommandSender sender, String input) {
            Optional<Material> material = Materials.material(input);
            if (!material.isPresent()) {
                throw error(CommandMessages.get("invalid-material"), input);
            }
            return material.get();
        }

        @Override
        public List<String> suggest(CommandSender sender, String input) {
            if (input.isEmpty()) {
                return Collections.emptyList(); // avoid sending ~1500 entries for an empty prefix
            }
            List<String> names = new ArrayList<>();
            String upper = input.toUpperCase(Locale.ROOT);
            for (Material material : Material.values()) {
                String name = material.name();
                if (!name.startsWith("LEGACY_") && name.startsWith(upper)) {
                    names.add(name.toLowerCase(Locale.ROOT));
                }
            }
            return names;
        }
    };

    /** A duration such as {@code 30s}, {@code 1h30m} or {@code 7d}, parsed to milliseconds. */
    public static final ArgumentType<Long> DURATION = new ArgumentType<Long>() {
        @Override
        public Long parse(CommandSender sender, String input) {
            try {
                return Durations.parse(input);
            } catch (IllegalArgumentException e) {
                throw error(CommandMessages.get("invalid-duration"), input);
            }
        }

        @Override
        public List<String> suggest(CommandSender sender, String input) {
            return Arrays.asList("30s", "5m", "1h", "1d", "7d", "30d");
        }
    };

    private ArgumentTypes() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * An integer within a range, inclusive.
     *
     * @param min the smallest accepted value
     * @param max the largest accepted value
     * @return the argument type
     */
    public static ArgumentType<Integer> integer(int min, int max) {
        return (sender, input) -> {
            int value;
            try {
                value = Integer.parseInt(input.replace(",", "").replace("_", ""));
            } catch (NumberFormatException e) {
                throw error(CommandMessages.get("invalid-number"), input);
            }
            if (value < min || value > max) {
                throw new CommandError(Text.replace(CommandMessages.get("number-out-of-range"),
                        "input", input, "min", min, "max", max));
            }
            return value;
        };
    }

    /**
     * A decimal number within a range, inclusive. Accepts compact suffixes ({@code 1.5k}).
     *
     * @param min the smallest accepted value
     * @param max the largest accepted value
     * @return the argument type
     */
    public static ArgumentType<Double> doubleRange(double min, double max) {
        return (sender, input) -> {
            Double value = parseCompact(input);
            if (value == null) {
                throw error(CommandMessages.get("invalid-number"), input);
            }
            if (value < min || value > max) {
                throw new CommandError(Text.replace(CommandMessages.get("number-out-of-range"),
                        "input", input, "min", min == -Double.MAX_VALUE ? "-∞" : trim(min),
                        "max", max == Double.MAX_VALUE ? "∞" : trim(max)));
            }
            return value;
        };
    }

    /**
     * Any constant of an enum, case-insensitive, suggested in lower case.
     *
     * @param type the enum class
     * @param <E>  the enum type
     * @return the argument type
     */
    public static <E extends Enum<E>> ArgumentType<E> enumType(Class<E> type) {
        return new ArgumentType<E>() {
            @Override
            public E parse(CommandSender sender, String input) {
                String normalised = input.toUpperCase(Locale.ROOT).replace('-', '_');
                for (E constant : type.getEnumConstants()) {
                    if (constant.name().equals(normalised)) {
                        return constant;
                    }
                }
                throw new CommandError(Text.replace(CommandMessages.get("invalid-choice"), "input", input,
                        "options", String.join(", ", names())));
            }

            @Override
            public List<String> suggest(CommandSender sender, String input) {
                return names();
            }

            private List<String> names() {
                List<String> names = new ArrayList<>();
                for (E constant : type.getEnumConstants()) {
                    names.add(constant.name().toLowerCase(Locale.ROOT));
                }
                return names;
            }
        };
    }

    /**
     * One of a fixed set of words, case-insensitive. Returns the matching option as written here.
     *
     * @param options the accepted values
     * @return the argument type
     */
    public static ArgumentType<String> choice(String... options) {
        List<String> list = Collections.unmodifiableList(Arrays.asList(options));
        return choice(() -> list);
    }

    /**
     * One of a dynamic set of words, e.g. the names of all mines.
     *
     * @param options supplies the accepted values each time the argument is parsed or completed
     * @return the argument type
     */
    public static ArgumentType<String> choice(Supplier<? extends Collection<String>> options) {
        return new ArgumentType<String>() {
            @Override
            public String parse(CommandSender sender, String input) {
                Collection<String> current = options.get();
                for (String option : current) {
                    if (option.equalsIgnoreCase(input)) {
                        return option;
                    }
                }
                throw new CommandError(Text.replace(CommandMessages.get("invalid-choice"), "input", input,
                        "options", String.join(", ", current)));
            }

            @Override
            public List<String> suggest(CommandSender sender, String input) {
                return new ArrayList<>(options.get());
            }
        };
    }

    private static List<String> visiblePlayerNames(CommandSender sender) {
        List<String> names = new ArrayList<>();
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (canSee(sender, online)) {
                names.add(online.getName());
            }
        }
        return names;
    }

    private static boolean canSee(CommandSender sender, Player target) {
        return !(sender instanceof Player) || ((Player) sender).canSee(target);
    }

    private static CommandError error(String template, String input) {
        return new CommandError(Text.replace(template, "input", input));
    }

    private static String trim(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(value);
    }

    /**
     * Parses a number that may use a compact suffix: k (thousand), m (million), b (billion), t (trillion).
     *
     * @param input the text
     * @return the value, or {@code null} if invalid
     */
    static Double parseCompact(String input) {
        if (input == null || input.isEmpty()) {
            return null;
        }
        String text = input.trim().toLowerCase(Locale.ROOT).replace(",", "").replace("_", "");
        double multiplier = 1;
        char last = text.charAt(text.length() - 1);
        switch (last) {
            case 'k':
                multiplier = 1e3;
                break;
            case 'm':
                multiplier = 1e6;
                break;
            case 'b':
                multiplier = 1e9;
                break;
            case 't':
                multiplier = 1e12;
                break;
            default:
                break;
        }
        if (multiplier != 1) {
            text = text.substring(0, text.length() - 1);
        }
        try {
            double value = Double.parseDouble(text) * multiplier;
            return Double.isNaN(value) || Double.isInfinite(value) ? null : value;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
