package com.example.template.data;

/**
 * Everything the plugin stores per player. Kept in memory by MoonLib's {@code PlayerCache} while
 * the player is online and saved by {@link PlayerRepository}.
 *
 * <p>Fields are {@code volatile} because saving happens on a background thread while the server
 * thread changes them.</p>
 */
public final class PlayerData {

    public volatile String name;
    public volatile int joins;
    public volatile long blocksBroken;
    public volatile long firstJoin;
    public volatile long lastSeen;

    public PlayerData(String name) {
        this.name = name;
        this.firstJoin = System.currentTimeMillis();
        this.lastSeen = firstJoin;
    }

    /** Returns whether this is the player's first time on the server. */
    public boolean isNew() {
        return joins <= 1;
    }
}
