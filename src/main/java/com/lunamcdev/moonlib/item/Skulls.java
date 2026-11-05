package com.lunamcdev.moonlib.item;

import com.lunamcdev.moonlib.version.Materials;
import com.lunamcdev.moonlib.version.Reflection;
import com.lunamcdev.moonlib.version.ServerVersion;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Player heads with custom textures, on every version.
 *
 * <p>Textures can be given in any of the three common forms: the base64 value from sites like
 * minecraft-heads.com, the full {@code textures.minecraft.net} URL, or just the texture hash.</p>
 *
 * <pre>{@code
 * ItemStack head = Skulls.fromTexture("eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3Jh...");
 * ItemStack hash = Skulls.fromTexture("e3b6b6e6a3...");
 * ItemStack own  = Skulls.ofPlayer(player);
 * }</pre>
 */
public final class Skulls {

    private static final Pattern URL_PATTERN = Pattern.compile("\"url\"\\s*:\\s*\"(https?://[^\"]+)\"");
    private static final String TEXTURE_BASE = "http://textures.minecraft.net/texture/";

    private Skulls() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Creates a blank player head.
     *
     * @return a new player head item
     */
    public static ItemStack blank() {
        return Materials.item("PLAYER_HEAD");
    }

    /**
     * Creates a head with a custom texture.
     *
     * @param texture a base64 texture value, a texture URL, or a texture hash
     * @return the head
     */
    public static ItemStack fromTexture(String texture) {
        ItemStack head = blank();
        ItemMeta meta = head.getItemMeta();
        if (meta instanceof SkullMeta) {
            applyTexture((SkullMeta) meta, texture);
            head.setItemMeta(meta);
        }
        return head;
    }

    /**
     * Creates the head of a player.
     *
     * @param player the player
     * @return the head
     */
    public static ItemStack ofPlayer(OfflinePlayer player) {
        ItemStack head = blank();
        ItemMeta meta = head.getItemMeta();
        if (meta instanceof SkullMeta) {
            applyOwner((SkullMeta) meta, player);
            head.setItemMeta(meta);
        }
        return head;
    }

    /**
     * Applies a texture to skull meta.
     *
     * @param meta    the meta
     * @param texture a base64 value, URL or hash
     */
    public static void applyTexture(SkullMeta meta, String texture) {
        String url = toUrl(texture);
        if (ServerVersion.current().isAtLeast(ServerVersion.V1_18_1)) {
            ModernItemSupport.setSkinUrl(meta, url);
        } else {
            applyLegacyTexture(meta, toBase64(url));
        }
    }

    /**
     * Sets the owner of skull meta.
     *
     * @param meta   the meta
     * @param player the owner
     */
    @SuppressWarnings("deprecation")
    public static void applyOwner(SkullMeta meta, OfflinePlayer player) {
        if (ServerVersion.current().isAtLeast(1, 12, 1)) {
            ModernItemSupport.setOwningPlayer(meta, player);
        } else {
            meta.setOwner(player.getName());
        }
    }

    /**
     * Normalises any texture form into a full texture URL.
     *
     * @param texture a base64 value, URL or hash
     * @return the URL
     */
    public static String toUrl(String texture) {
        String trimmed = texture.trim();
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return trimmed;
        }
        if (trimmed.matches("[0-9a-fA-F]{40,}")) {
            return TEXTURE_BASE + trimmed;
        }
        try {
            String json = new String(Base64.getDecoder().decode(trimmed), StandardCharsets.UTF_8);
            Matcher matcher = URL_PATTERN.matcher(json);
            if (matcher.find()) {
                return matcher.group(1);
            }
        } catch (IllegalArgumentException ignored) {
            // not base64
        }
        throw new IllegalArgumentException("Unrecognised skull texture: " + texture);
    }

    private static String toBase64(String url) {
        String json = "{\"textures\":{\"SKIN\":{\"url\":\"" + url + "\"}}}";
        return Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    private static void applyLegacyTexture(SkullMeta meta, String base64) {
        try {
            Class<?> profileClass = Class.forName("com.mojang.authlib.GameProfile");
            Class<?> propertyClass = Class.forName("com.mojang.authlib.properties.Property");
            Constructor<?> profileConstructor = profileClass.getConstructor(UUID.class, String.class);
            Constructor<?> propertyConstructor = propertyClass.getConstructor(String.class, String.class);

            UUID id = UUID.nameUUIDFromBytes(base64.getBytes(StandardCharsets.UTF_8));
            Object profile = profileConstructor.newInstance(id, null);
            Object properties = profileClass.getMethod("getProperties").invoke(profile);
            Method put = properties.getClass().getMethod("put", Object.class, Object.class);
            put.invoke(properties, "textures", propertyConstructor.newInstance("textures", base64));

            Method setProfile = Reflection.method(meta.getClass(), "setProfile", profileClass);
            if (setProfile != null) {
                setProfile.invoke(meta, profile);
            } else {
                Field field = Reflection.field(meta.getClass(), "profile");
                if (field == null) {
                    throw new IllegalStateException("CraftMetaSkull has no profile field");
                }
                field.set(meta, profile);
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            throw new IllegalStateException("Could not apply skull texture on this version", e);
        }
    }
}
