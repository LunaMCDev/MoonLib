package com.lunamcdev.moonlib.item;

import com.lunamcdev.moonlib.version.Materials;
import com.lunamcdev.moonlib.version.ServerVersion;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.function.Predicate;

/**
 * General item helpers: emptiness checks, counting and removing items, and serialisation.
 *
 * <pre>{@code
 * int coal = Items.count(player.getInventory(), item -> item.getType() == Material.COAL);
 * boolean paid = Items.remove(player.getInventory(), item -> ItemTags.has(item, "token"), 5);
 *
 * String saved = Items.toBase64(player.getInventory().getContents());   // store in a database
 * ItemStack[] restored = Items.fromBase64(saved);
 * }</pre>
 */
public final class Items {

    private Items() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Returns whether an item is {@code null}, air, or has an amount of zero.
     *
     * @param item the item
     * @return {@code true} if there is effectively no item
     */
    public static boolean isEmpty(ItemStack item) {
        return item == null || Materials.isAir(item.getType()) || item.getAmount() <= 0;
    }

    /**
     * Returns whether two items are the same apart from their amount.
     *
     * @param a the first item
     * @param b the second item
     * @return {@code true} if similar
     */
    public static boolean isSimilar(ItemStack a, ItemStack b) {
        if (isEmpty(a) || isEmpty(b)) {
            return isEmpty(a) && isEmpty(b);
        }
        return a.isSimilar(b);
    }

    /**
     * Counts matching items in an inventory's storage slots.
     *
     * @param inventory the inventory
     * @param filter    which items count
     * @return the total amount
     */
    public static int count(Inventory inventory, Predicate<ItemStack> filter) {
        int total = 0;
        for (ItemStack item : storage(inventory)) {
            if (!isEmpty(item) && filter.test(item)) {
                total += item.getAmount();
            }
        }
        return total;
    }

    /**
     * Counts items of a material.
     *
     * @param inventory the inventory
     * @param material  the material
     * @return the total amount
     */
    public static int count(Inventory inventory, Material material) {
        return count(inventory, item -> item.getType() == material);
    }

    /**
     * Removes an exact amount of matching items, or nothing at all if there are not enough.
     *
     * @param inventory the inventory
     * @param filter    which items to take
     * @param amount    how many to take
     * @return {@code true} if the items were removed, {@code false} if there were not enough
     */
    public static boolean remove(Inventory inventory, Predicate<ItemStack> filter, int amount) {
        if (amount <= 0) {
            return true;
        }
        if (count(inventory, filter) < amount) {
            return false;
        }
        int remaining = amount;
        ItemStack[] contents = storage(inventory);
        for (int slot = 0; slot < contents.length && remaining > 0; slot++) {
            ItemStack item = contents[slot];
            if (isEmpty(item) || !filter.test(item)) {
                continue;
            }
            int take = Math.min(remaining, item.getAmount());
            remaining -= take;
            if (take >= item.getAmount()) {
                inventory.setItem(slot, null);
            } else {
                item.setAmount(item.getAmount() - take);
                inventory.setItem(slot, item);
            }
        }
        return true;
    }

    /**
     * Returns whether an inventory can fit an item completely.
     *
     * @param inventory the inventory
     * @param item      the item
     * @return {@code true} if it fits
     */
    public static boolean canFit(Inventory inventory, ItemStack item) {
        int remaining = item.getAmount();
        int maxStack = Math.min(item.getMaxStackSize(), inventory.getMaxStackSize());
        for (ItemStack slot : storage(inventory)) {
            if (isEmpty(slot)) {
                remaining -= maxStack;
            } else if (slot.isSimilar(item)) {
                remaining -= Math.max(0, maxStack - slot.getAmount());
            }
            if (remaining <= 0) {
                return true;
            }
        }
        return remaining <= 0;
    }

    /**
     * Returns the storage slots of an inventory: for player inventories this excludes armour and the
     * off-hand. ({@code getStorageContents()} only exists from 1.9; on 1.8 {@code getContents()} is
     * already storage only.)
     *
     * @param inventory the inventory
     * @return the storage contents
     */
    public static ItemStack[] storage(Inventory inventory) {
        return ServerVersion.current().isAtLeast(ServerVersion.V1_9)
                ? inventory.getStorageContents()
                : inventory.getContents();
    }

    /**
     * Serialises items (including empty slots) to a base64 string, preserving all metadata.
     *
     * @param items the items
     * @return the base64 data
     */
    public static String toBase64(ItemStack[] items) {
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
             BukkitObjectOutputStream out = new BukkitObjectOutputStream(bytes)) {
            out.writeInt(items.length);
            for (ItemStack item : items) {
                out.writeObject(item);
            }
            out.flush();
            return Base64.getEncoder().encodeToString(bytes.toByteArray());
        } catch (IOException e) {
            throw new IllegalStateException("Unable to serialise items", e);
        }
    }

    /**
     * Restores items written by {@link #toBase64(ItemStack[])}.
     *
     * @param data the base64 data
     * @return the items, with {@code null} for empty slots
     * @throws IllegalArgumentException if the data is invalid
     */
    public static ItemStack[] fromBase64(String data) {
        try (BukkitObjectInputStream in = new BukkitObjectInputStream(
                new ByteArrayInputStream(Base64.getDecoder().decode(data)))) {
            ItemStack[] items = new ItemStack[in.readInt()];
            for (int i = 0; i < items.length; i++) {
                items[i] = (ItemStack) in.readObject();
            }
            return items;
        } catch (IOException | ClassNotFoundException | IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid item data", e);
        }
    }

    /**
     * Serialises a single item to base64.
     *
     * @param item the item
     * @return the base64 data
     */
    public static String toBase64(ItemStack item) {
        return toBase64(new ItemStack[]{item});
    }

    /**
     * Restores a single item written by {@link #toBase64(ItemStack)}.
     *
     * @param data the base64 data
     * @return the item, possibly {@code null}
     */
    public static ItemStack itemFromBase64(String data) {
        ItemStack[] items = fromBase64(data);
        return items.length == 0 ? null : items[0];
    }
}
