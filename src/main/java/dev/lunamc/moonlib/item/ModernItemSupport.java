package dev.lunamc.moonlib.item;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.profile.PlayerTextures;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.UUID;

/**
 * Internal API. Calls to Bukkit APIs newer than 1.8, isolated in their own class so the JVM never has
 * to resolve them on servers where they don't exist. Every method must only be called after the
 * corresponding {@link dev.lunamc.moonlib.version.ServerVersion} check.
 */
final class ModernItemSupport {

    private ModernItemSupport() {
    }

    /** 1.11+. */
    static void setUnbreakable(ItemMeta meta, boolean unbreakable) {
        meta.setUnbreakable(unbreakable);
    }

    /** 1.13+. */
    static void setDamage(ItemMeta meta, int damage) {
        if (meta instanceof Damageable) {
            ((Damageable) meta).setDamage(damage);
        }
    }

    /** 1.14+. */
    @SuppressWarnings("deprecation")
    static void setCustomModelData(ItemMeta meta, Integer data) {
        meta.setCustomModelData(data);
    }

    /** 1.20.5+. */
    static void setGlintOverride(ItemMeta meta, Boolean glint) {
        meta.setEnchantmentGlintOverride(glint);
    }

    /** 1.12.1+. */
    static void setOwningPlayer(SkullMeta meta, OfflinePlayer player) {
        meta.setOwningPlayer(player);
    }

    /** 1.18.1+. */
    static void setSkinUrl(SkullMeta meta, String url) {
        UUID id = UUID.nameUUIDFromBytes(url.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        PlayerProfile profile = Bukkit.createPlayerProfile(id);
        PlayerTextures textures = profile.getTextures();
        try {
            textures.setSkin(new URL(url));
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("Invalid skin URL: " + url, e);
        }
        profile.setTextures(textures);
        meta.setOwnerProfile(profile);
    }
}
