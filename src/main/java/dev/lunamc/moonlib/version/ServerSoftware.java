package dev.lunamc.moonlib.version;

/**
 * The server implementation the plugin is running on.
 *
 * <p>Detection works by checking for classes that only exist in each implementation, from the most
 * specific fork to the most general, so a Purpur server reports {@link #PURPUR} while still answering
 * {@code true} to {@link #isPaperBased()}.</p>
 *
 * <pre>{@code
 * if (ServerSoftware.current().isPaperBased()) {
 *     // use a Paper-only optimisation
 * }
 * if (ServerSoftware.isFolia()) {
 *     // regionised threading: use Tasks.entity(...) / Tasks.at(...)
 * }
 * }</pre>
 */
public enum ServerSoftware {

    /** Folia: Paper with regionised multithreading. */
    FOLIA("Folia", true, "io.papermc.paper.threadedregions.RegionizedServer"),
    /** Purpur: a Paper fork. */
    PURPUR("Purpur", true, "org.purpurmc.purpur.PurpurConfig"),
    /** Pufferfish: a Paper fork. */
    PUFFERFISH("Pufferfish", true, "gg.pufferfish.pufferfish.PufferfishConfig"),
    /** Paper (and unknown Paper forks). */
    PAPER("Paper", true, "io.papermc.paper.configuration.Configuration", "com.destroystokyo.paper.PaperConfig"),
    /** Spigot. */
    SPIGOT("Spigot", false, "org.spigotmc.SpigotConfig"),
    /** CraftBukkit or another Bukkit implementation. */
    BUKKIT("Bukkit", false);

    private static volatile ServerSoftware current;

    private final String displayName;
    private final boolean paperBased;
    private final String[] markerClasses;

    ServerSoftware(String displayName, boolean paperBased, String... markerClasses) {
        this.displayName = displayName;
        this.paperBased = paperBased;
        this.markerClasses = markerClasses;
    }

    /**
     * Returns the detected server software. Detected once and cached.
     *
     * @return the current server software
     */
    public static ServerSoftware current() {
        ServerSoftware software = current;
        if (software == null) {
            software = BUKKIT;
            for (ServerSoftware candidate : values()) {
                if (candidate.isPresent()) {
                    software = candidate;
                    break;
                }
            }
            current = software;
        }
        return software;
    }

    /**
     * Returns whether the server is Folia, which requires region-aware scheduling.
     *
     * @return {@code true} on Folia
     */
    public static boolean isFolia() {
        return current() == FOLIA;
    }

    /**
     * Returns whether this software is Paper or a fork of Paper.
     *
     * @return {@code true} for Paper, Purpur, Pufferfish and Folia
     */
    public boolean isPaperBased() {
        return paperBased;
    }

    /**
     * Returns whether this software is Spigot or a fork of Spigot (which includes Paper).
     *
     * @return {@code true} for anything but plain CraftBukkit
     */
    public boolean isSpigotBased() {
        return this != BUKKIT;
    }

    /**
     * Returns a human readable name.
     *
     * @return e.g. {@code "Paper"}
     */
    public String displayName() {
        return displayName;
    }

    private boolean isPresent() {
        for (String marker : markerClasses) {
            if (Reflection.classExists(marker)) {
                return true;
            }
        }
        return false;
    }
}
