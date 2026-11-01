package dev.lunamc.moonlib.item;

import dev.lunamc.moonlib.MoonLib;
import dev.lunamc.moonlib.version.ServerVersion;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.Locale;

/**
 * Hidden, persistent data on items using Bukkit's {@code PersistentDataContainer}. <b>Requires 1.14+.</b>
 *
 * <p>Use tags to recognise your own items reliably instead of comparing names or lore, which players
 * can fake with anvils.</p>
 *
 * <pre>{@code
 * ItemStack pickaxe = ItemBuilder.of("DIAMOND_PICKAXE").tag("prison-pickaxe", "true").build();
 *
 * if (ItemTags.has(event.getItem(), "prison-pickaxe")) { ... }
 * ItemTags.setInt(item, "level", 5);
 * int level = ItemTags.getInt(item, "level", 0);
 * }</pre>
 *
 * <p>Keys are namespaced under the owning plugin, so two plugins using the key {@code "level"} never
 * clash.</p>
 */
public final class ItemTags {

    private ItemTags() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Returns whether tags are supported on this server.
     *
     * @return {@code true} on 1.14+
     */
    public static boolean isSupported() {
        return ServerVersion.current().isAtLeast(ServerVersion.V1_14);
    }

    /**
     * Stores a string.
     *
     * @param item  the item, modified in place
     * @param key   the key, lower case letters, digits, {@code _ - . /}
     * @param value the value
     */
    public static void setString(ItemStack item, String key, String value) {
        edit(item, meta -> meta.getPersistentDataContainer().set(key(key), PersistentDataType.STRING, value));
    }

    /**
     * Reads a string.
     *
     * @param item the item
     * @param key  the key
     * @return the value, or {@code null}
     */
    public static String getString(ItemStack item, String key) {
        PersistentDataContainer container = container(item);
        return container == null ? null : container.get(key(key), PersistentDataType.STRING);
    }

    /**
     * Stores an integer.
     *
     * @param item  the item, modified in place
     * @param key   the key
     * @param value the value
     */
    public static void setInt(ItemStack item, String key, int value) {
        edit(item, meta -> meta.getPersistentDataContainer().set(key(key), PersistentDataType.INTEGER, value));
    }

    /**
     * Reads an integer.
     *
     * @param item     the item
     * @param key      the key
     * @param fallback returned if absent
     * @return the value or {@code fallback}
     */
    public static int getInt(ItemStack item, String key, int fallback) {
        PersistentDataContainer container = container(item);
        Integer value = container == null ? null : container.get(key(key), PersistentDataType.INTEGER);
        return value == null ? fallback : value;
    }

    /**
     * Stores a double.
     *
     * @param item  the item, modified in place
     * @param key   the key
     * @param value the value
     */
    public static void setDouble(ItemStack item, String key, double value) {
        edit(item, meta -> meta.getPersistentDataContainer().set(key(key), PersistentDataType.DOUBLE, value));
    }

    /**
     * Reads a double.
     *
     * @param item     the item
     * @param key      the key
     * @param fallback returned if absent
     * @return the value or {@code fallback}
     */
    public static double getDouble(ItemStack item, String key, double fallback) {
        PersistentDataContainer container = container(item);
        Double value = container == null ? null : container.get(key(key), PersistentDataType.DOUBLE);
        return value == null ? fallback : value;
    }

    /**
     * Stores a long.
     *
     * @param item  the item, modified in place
     * @param key   the key
     * @param value the value
     */
    public static void setLong(ItemStack item, String key, long value) {
        edit(item, meta -> meta.getPersistentDataContainer().set(key(key), PersistentDataType.LONG, value));
    }

    /**
     * Reads a long.
     *
     * @param item     the item
     * @param key      the key
     * @param fallback returned if absent
     * @return the value or {@code fallback}
     */
    public static long getLong(ItemStack item, String key, long fallback) {
        PersistentDataContainer container = container(item);
        Long value = container == null ? null : container.get(key(key), PersistentDataType.LONG);
        return value == null ? fallback : value;
    }

    /**
     * Returns whether an item has any value under a key.
     *
     * @param item the item, may be {@code null}
     * @param key  the key
     * @return {@code true} if present
     */
    public static boolean has(ItemStack item, String key) {
        PersistentDataContainer container = container(item);
        if (container == null) {
            return false;
        }
        NamespacedKey namespaced = key(key);
        return container.has(namespaced, PersistentDataType.STRING)
                || container.has(namespaced, PersistentDataType.INTEGER)
                || container.has(namespaced, PersistentDataType.DOUBLE)
                || container.has(namespaced, PersistentDataType.LONG);
    }

    /**
     * Removes a key.
     *
     * @param item the item, modified in place
     * @param key  the key
     */
    public static void remove(ItemStack item, String key) {
        edit(item, meta -> meta.getPersistentDataContainer().remove(key(key)));
    }

    /**
     * Stores a string directly on item meta. Used by {@link ItemBuilder#tag(String, String)}.
     *
     * @param meta  the meta
     * @param key   the key
     * @param value the value
     */
    public static void setString(ItemMeta meta, String key, String value) {
        requireSupported();
        meta.getPersistentDataContainer().set(key(key), PersistentDataType.STRING, value);
    }

    /**
     * Creates a key namespaced under the owning plugin.
     *
     * @param key the key
     * @return the namespaced key
     */
    public static NamespacedKey key(String key) {
        return new NamespacedKey(MoonLib.plugin(), key.toLowerCase(Locale.ROOT));
    }

    private static PersistentDataContainer container(ItemStack item) {
        requireSupported();
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        return meta == null ? null : meta.getPersistentDataContainer();
    }

    private static void edit(ItemStack item, java.util.function.Consumer<ItemMeta> action) {
        requireSupported();
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            throw new IllegalArgumentException("Item " + item.getType() + " cannot hold data");
        }
        action.accept(meta);
        item.setItemMeta(meta);
    }

    private static void requireSupported() {
        if (!isSupported()) {
            throw new UnsupportedOperationException("Item tags require Minecraft 1.14 or newer (server is "
                    + ServerVersion.current() + ")");
        }
    }
}
