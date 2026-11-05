package dev.lunamc.moonlib.version;

import org.bukkit.Bukkit;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The Minecraft version of the running server, with simple comparison helpers.
 *
 * <p>This is the foundation of MoonLib's multi-version support. Gate any API that does not exist on
 * every version you support behind a check:</p>
 *
 * <pre>{@code
 * if (ServerVersion.atLeast(1, 14)) {
 *     meta.setCustomModelData(1001);
 * }
 *
 * if (ServerVersion.current().isBelow(ServerVersion.V1_13)) {
 *     // legacy material names, 16 character scoreboard prefixes, etc.
 * }
 * }</pre>
 *
 * <p>Both version schemes are understood: classic {@code 1.x.y} releases and the year-based
 * {@code 26.x} releases introduced in 2026. Year-based versions always compare as newer than
 * any {@code 1.x} version, so a check such as {@code atLeast(1, 20, 5)} is {@code true} on 26.1.</p>
 */
public final class ServerVersion implements Comparable<ServerVersion> {

    /** Minecraft 1.8 (the oldest supported version). */
    public static final ServerVersion V1_8 = of(1, 8);
    /** Minecraft 1.9 (off-hand, new combat, boss bars). */
    public static final ServerVersion V1_9 = of(1, 9);
    /** Minecraft 1.11 (Spigot title API with timings, setUnbreakable on meta). */
    public static final ServerVersion V1_11 = of(1, 11);
    /** Minecraft 1.12 (Spigot action bar API). */
    public static final ServerVersion V1_12 = of(1, 12);
    /** Minecraft 1.13 (the "flattening": new material names, 64 character team prefixes). */
    public static final ServerVersion V1_13 = of(1, 13);
    /** Minecraft 1.14 (PersistentDataContainer, custom model data). */
    public static final ServerVersion V1_14 = of(1, 14);
    /** Minecraft 1.16 (hex colours). */
    public static final ServerVersion V1_16 = of(1, 16);
    /** Minecraft 1.17. */
    public static final ServerVersion V1_17 = of(1, 17);
    /** Minecraft 1.18.1 (PlayerProfile API for skull textures). */
    public static final ServerVersion V1_18_1 = of(1, 18, 1);
    /** Minecraft 1.19. */
    public static final ServerVersion V1_19 = of(1, 19);
    /** Minecraft 1.20. */
    public static final ServerVersion V1_20 = of(1, 20);
    /** Minecraft 1.20.5 (item components, enchantment glint override). */
    public static final ServerVersion V1_20_5 = of(1, 20, 5);
    /** Minecraft 1.21. */
    public static final ServerVersion V1_21 = of(1, 21);
    /**
     * Minecraft 26.1, the first year-based version (needs Java 25).
     *
     * @since 1.1.1-alpha
     */
    public static final ServerVersion V26_1 = of(26, 1);
    /**
     * Minecraft 26.2.
     *
     * @since 1.1.1-alpha
     */
    public static final ServerVersion V26_2 = of(26, 2);
    /**
     * Minecraft 26.3, the newest release MoonLib is compiled and tested against.
     *
     * @since 1.1.1-alpha
     */
    public static final ServerVersion V26_3 = of(26, 3);

    private static final Pattern VERSION_PATTERN = Pattern.compile("(\\d+)\\.(\\d+)(?:\\.(\\d+))?");
    private static final Pattern MC_PATTERN = Pattern.compile("\\(MC: (\\d+\\.\\d+(?:\\.\\d+)?)\\)");
    private static final ServerVersion UNKNOWN_LATEST = new ServerVersion(999, 0, 0);

    private static volatile ServerVersion current;
    private static volatile String nmsPackage;

    private final int major;
    private final int minor;
    private final int patch;

    private ServerVersion(int major, int minor, int patch) {
        this.major = major;
        this.minor = minor;
        this.patch = patch;
    }

    /**
     * Creates a version.
     *
     * @param major the major number ({@code 1} for 1.20.4, {@code 26} for 26.1)
     * @param minor the minor number
     * @param patch the patch number
     * @return the version
     */
    public static ServerVersion of(int major, int minor, int patch) {
        return new ServerVersion(major, minor, patch);
    }

    /**
     * Creates a version with a patch of {@code 0}.
     *
     * @param major the major number
     * @param minor the minor number
     * @return the version
     */
    public static ServerVersion of(int major, int minor) {
        return new ServerVersion(major, minor, 0);
    }

    /**
     * Parses a version from any string that contains one, e.g. {@code "1.20.4-R0.1-SNAPSHOT"} or {@code "26.1"}.
     *
     * @param text the text
     * @return the version
     * @throws IllegalArgumentException if no version could be found
     */
    public static ServerVersion parse(String text) {
        if (text == null) {
            throw new IllegalArgumentException("Version text is null");
        }
        Matcher matcher = VERSION_PATTERN.matcher(text);
        if (!matcher.find()) {
            throw new IllegalArgumentException("No version found in: " + text);
        }
        int major = Integer.parseInt(matcher.group(1));
        int minor = Integer.parseInt(matcher.group(2));
        int patch = matcher.group(3) == null ? 0 : Integer.parseInt(matcher.group(3));
        return new ServerVersion(major, minor, patch);
    }

    /**
     * Returns the version of the running server. Detected once and cached.
     *
     * <p>When no server is running (unit tests), or the version cannot be read, the newest possible
     * version is assumed so that modern code paths are used.</p>
     *
     * @return the current server version
     */
    public static ServerVersion current() {
        ServerVersion version = current;
        if (version == null) {
            version = detect();
            current = version;
        }
        return version;
    }

    /**
     * Shortcut for {@code ServerVersion.current().isAtLeast(major, minor)}.
     *
     * @param major the major number
     * @param minor the minor number
     * @return {@code true} if the server is running at least that version
     */
    public static boolean atLeast(int major, int minor) {
        return current().isAtLeast(major, minor, 0);
    }

    /**
     * Shortcut for {@code ServerVersion.current().isAtLeast(major, minor, patch)}.
     *
     * @param major the major number
     * @param minor the minor number
     * @param patch the patch number
     * @return {@code true} if the server is running at least that version
     */
    public static boolean atLeast(int major, int minor, int patch) {
        return current().isAtLeast(major, minor, patch);
    }

    /**
     * Returns whether the server predates the 1.13 "flattening" (legacy materials and limits).
     *
     * @return {@code true} on 1.8 to 1.12.2
     */
    public static boolean isLegacy() {
        return current().isBelow(V1_13);
    }

    /**
     * Returns whether hex colours are supported (1.16+).
     *
     * @return {@code true} if hex colours render
     */
    public static boolean supportsHexColors() {
        return current().isAtLeast(V1_16);
    }

    /**
     * Returns the CraftBukkit/NMS package version, e.g. {@code "v1_8_R3"}. Servers that no longer
     * relocate CraftBukkit (Paper 1.20.5+) return an empty string.
     *
     * @return the package version, or an empty string
     */
    public static String nmsPackage() {
        String pkg = nmsPackage;
        if (pkg == null) {
            pkg = "";
            try {
                String name = Bukkit.getServer().getClass().getPackage().getName();
                String last = name.substring(name.lastIndexOf('.') + 1);
                if (last.startsWith("v") && last.contains("_R")) {
                    pkg = last;
                }
            } catch (Throwable ignored) {
                // no server or unusual implementation
            }
            nmsPackage = pkg;
        }
        return pkg;
    }

    /**
     * Returns whether this version is the same as or newer than another.
     *
     * @param other the version to compare with
     * @return {@code true} if {@code this >= other}
     */
    public boolean isAtLeast(ServerVersion other) {
        return compareTo(other) >= 0;
    }

    /**
     * Returns whether this version is the same as or newer than the given numbers.
     *
     * @param major the major number
     * @param minor the minor number
     * @return {@code true} if {@code this >= major.minor}
     */
    public boolean isAtLeast(int major, int minor) {
        return isAtLeast(major, minor, 0);
    }

    /**
     * Returns whether this version is the same as or newer than the given numbers.
     *
     * @param major the major number
     * @param minor the minor number
     * @param patch the patch number
     * @return {@code true} if {@code this >= major.minor.patch}
     */
    public boolean isAtLeast(int major, int minor, int patch) {
        return compareTo(new ServerVersion(major, minor, patch)) >= 0;
    }

    /**
     * Returns whether this version is older than another.
     *
     * @param other the version to compare with
     * @return {@code true} if {@code this < other}
     */
    public boolean isBelow(ServerVersion other) {
        return compareTo(other) < 0;
    }

    /**
     * Returns whether this version is within a range, inclusive on both ends.
     *
     * @param min the oldest version
     * @param max the newest version
     * @return {@code true} if {@code min <= this <= max}
     */
    public boolean isBetween(ServerVersion min, ServerVersion max) {
        return compareTo(min) >= 0 && compareTo(max) <= 0;
    }

    /**
     * Returns the major number.
     *
     * @return {@code 1} for 1.x releases, the year number for year-based releases
     */
    public int major() {
        return major;
    }

    /**
     * Returns the minor number.
     *
     * @return the minor number, e.g. {@code 20} for 1.20.4
     */
    public int minor() {
        return minor;
    }

    /**
     * Returns the patch number.
     *
     * @return the patch number, e.g. {@code 4} for 1.20.4
     */
    public int patch() {
        return patch;
    }

    @Override
    public int compareTo(ServerVersion other) {
        if (major != other.major) {
            return Integer.compare(major, other.major);
        }
        if (minor != other.minor) {
            return Integer.compare(minor, other.minor);
        }
        return Integer.compare(patch, other.patch);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ServerVersion)) {
            return false;
        }
        ServerVersion that = (ServerVersion) o;
        return major == that.major && minor == that.minor && patch == that.patch;
    }

    @Override
    public int hashCode() {
        return (major * 31 + minor) * 31 + patch;
    }

    /**
     * Returns the version as text, omitting a zero patch: {@code "1.20.4"}, {@code "1.21"}, {@code "26.1"}.
     *
     * @return the version string
     */
    @Override
    public String toString() {
        return patch == 0 ? major + "." + minor : major + "." + minor + "." + patch;
    }

    private static ServerVersion detect() {
        try {
            if (Bukkit.getServer() == null) {
                return UNKNOWN_LATEST;
            }
            String bukkitVersion = Bukkit.getBukkitVersion();
            if (bukkitVersion != null && VERSION_PATTERN.matcher(bukkitVersion).find()) {
                return parse(bukkitVersion);
            }
            Matcher mc = MC_PATTERN.matcher(Bukkit.getVersion());
            if (mc.find()) {
                return parse(mc.group(1));
            }
        } catch (Throwable ignored) {
            // fall through to the default
        }
        return UNKNOWN_LATEST;
    }
}
