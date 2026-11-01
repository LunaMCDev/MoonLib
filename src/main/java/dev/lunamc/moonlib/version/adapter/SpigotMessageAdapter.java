package dev.lunamc.moonlib.version.adapter;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.entity.Player;

/**
 * Internal API. Spigot 1.12+ implementation using the Bukkit title API and the BungeeCord chat API
 * for action bars.
 */
public final class SpigotMessageAdapter implements MessageAdapter {

    /** Creates the adapter. */
    public SpigotMessageAdapter() {
    }

    @Override
    public void title(Player player, String title, String subtitle, int fadeIn, int stay, int fadeOut) {
        // Spigot treats an empty title as "no title" and would not show the subtitle alone.
        String safeTitle = title == null || title.isEmpty() ? " " : title;
        player.sendTitle(safeTitle, subtitle == null ? "" : subtitle, fadeIn, stay, fadeOut);
    }

    @Override
    public void clearTitle(Player player) {
        player.resetTitle();
    }

    @Override
    public void actionBar(Player player, String message) {
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(message));
    }

    @Override
    public String name() {
        return "Spigot";
    }
}
