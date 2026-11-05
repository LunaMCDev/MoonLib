package com.lunamcdev.moonlib.version.adapter;

import org.bukkit.entity.Player;

/**
 * Internal API. Sends titles and action bars using whichever mechanism the running server supports.
 * Use {@link com.lunamcdev.moonlib.util.Titles} instead of this interface.
 */
public interface MessageAdapter {

    /**
     * Shows a title.
     *
     * @param player   the player
     * @param title    the coloured title, may be empty
     * @param subtitle the coloured subtitle, may be empty
     * @param fadeIn   fade-in ticks
     * @param stay     stay ticks
     * @param fadeOut  fade-out ticks
     */
    void title(Player player, String title, String subtitle, int fadeIn, int stay, int fadeOut);

    /**
     * Clears the current title.
     *
     * @param player the player
     */
    void clearTitle(Player player);

    /**
     * Shows an action bar message.
     *
     * @param player  the player
     * @param message the coloured message
     */
    void actionBar(Player player, String message);

    /**
     * Returns the adapter's name for diagnostics.
     *
     * @return e.g. {@code "Adventure"}
     */
    String name();
}
