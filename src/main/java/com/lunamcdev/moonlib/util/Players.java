package com.lunamcdev.moonlib.util;

import com.lunamcdev.moonlib.item.Items;
import com.lunamcdev.moonlib.version.Sounds;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.potion.PotionEffect;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Common player operations.
 *
 * <pre>{@code
 * Players.give(player, reward);                    // drops leftovers at their feet
 * Players.heal(player);
 * Players.reset(player);                           // full heal, clear effects, clear inventory
 * Players.broadcast("&d[Staff] &f" + msg, "moonprison.staff");
 * Optional<Player> target = Players.find("Lun");    // exact name, then unique prefix
 * if (Players.isVanished(player)) { ... }
 * }</pre>
 */
public final class Players {

    private Players() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Gives items, dropping anything that does not fit at the player's feet.
     *
     * @param player the player
     * @param items  the items
     * @return {@code true} if everything fit in the inventory
     */
    public static boolean give(Player player, ItemStack... items) {
        boolean allFit = true;
        for (ItemStack item : items) {
            if (Items.isEmpty(item)) {
                continue;
            }
            Map<Integer, ItemStack> leftovers = player.getInventory().addItem(item.clone());
            for (ItemStack leftover : leftovers.values()) {
                allFit = false;
                player.getWorld().dropItemNaturally(player.getLocation(), leftover);
            }
        }
        return allFit;
    }

    /**
     * Gives items only if they all fit.
     *
     * @param player the player
     * @param items  the items
     * @return {@code true} if given, {@code false} if there was not enough room (nothing given)
     */
    public static boolean giveIfFits(Player player, ItemStack... items) {
        // Simulate on a copy so partially fitting multi-item rewards are never split.
        org.bukkit.inventory.Inventory copy = Bukkit.createInventory(null, 36);
        ItemStack[] storage = Items.storage(player.getInventory());
        ItemStack[] cloned = new ItemStack[Math.min(36, storage.length)];
        for (int i = 0; i < cloned.length; i++) {
            cloned[i] = storage[i] == null ? null : storage[i].clone();
        }
        copy.setContents(cloned);
        for (ItemStack item : items) {
            if (!Items.isEmpty(item) && !copy.addItem(item.clone()).isEmpty()) {
                return false;
            }
        }
        for (ItemStack item : items) {
            if (!Items.isEmpty(item)) {
                player.getInventory().addItem(item.clone());
            }
        }
        return true;
    }

    /**
     * Restores health, hunger, saturation and fire.
     *
     * @param player the player
     */
    @SuppressWarnings("deprecation")
    public static void heal(Player player) {
        double max;
        try {
            max = player.getMaxHealth();
        } catch (Throwable t) {
            max = 20;
        }
        player.setHealth(max);
        feed(player);
        player.setFireTicks(0);
    }

    /**
     * Restores hunger and saturation.
     *
     * @param player the player
     */
    public static void feed(Player player) {
        player.setFoodLevel(20);
        player.setSaturation(20F);
        player.setExhaustion(0F);
    }

    /**
     * Removes every active potion effect.
     *
     * @param player the player
     */
    public static void clearEffects(Player player) {
        for (PotionEffect effect : new ArrayList<>(player.getActivePotionEffects())) {
            player.removePotionEffect(effect.getType());
        }
    }

    /**
     * Clears the inventory, armour and cursor.
     *
     * @param player the player
     */
    public static void clearInventory(Player player) {
        player.getInventory().clear();
        player.getInventory().setArmorContents(new ItemStack[4]);
        player.setItemOnCursor(null);
    }

    /**
     * Fully resets a player: heal, clear effects, clear inventory, zero experience, survival mode,
     * no flight.
     *
     * @param player the player
     */
    public static void reset(Player player) {
        heal(player);
        clearEffects(player);
        clearInventory(player);
        player.setExp(0F);
        player.setLevel(0);
        player.setGameMode(GameMode.SURVIVAL);
        player.setAllowFlight(false);
        player.setFlying(false);
    }

    /**
     * Sends a coloured message to every player with a permission, and the console.
     *
     * @param message    the message
     * @param permission the permission, or {@code null} for everyone
     */
    public static void broadcast(String message, String permission) {
        String coloured = Text.color(message);
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (permission == null || player.hasPermission(permission)) {
                player.sendMessage(coloured);
            }
        }
        Bukkit.getConsoleSender().sendMessage(coloured);
    }

    /**
     * Sends coloured lines to a player.
     *
     * @param player the player
     * @param lines  the lines
     */
    public static void message(Player player, Collection<String> lines) {
        for (String line : lines) {
            player.sendMessage(Text.color(line));
        }
    }

    /**
     * Plays a sound to every online player (see {@link Sounds}).
     *
     * @param sound the sound name
     */
    public static void playSoundToAll(String sound) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            Sounds.play(player, sound);
        }
    }

    /**
     * Finds an online player by exact name, falling back to a unique, case-insensitive prefix.
     *
     * @param name the name or prefix
     * @return the player, or empty if none or several match
     */
    public static Optional<Player> find(String name) {
        Player exact = Bukkit.getPlayerExact(name);
        if (exact != null) {
            return Optional.of(exact);
        }
        String lower = name.toLowerCase(Locale.ROOT);
        Player match = null;
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getName().toLowerCase(Locale.ROOT).startsWith(lower)) {
                if (match != null) {
                    return Optional.empty();
                }
                match = online;
            }
        }
        return Optional.ofNullable(match);
    }

    /**
     * Returns a snapshot of online players.
     *
     * @return a new mutable list
     */
    public static List<Player> online() {
        return new ArrayList<>(Bukkit.getOnlinePlayers());
    }

    /**
     * Returns whether a player is vanished, using the {@code "vanished"} metadata convention shared by
     * EssentialsX, SuperVanish, PremiumVanish and most other vanish plugins.
     *
     * @param player the player
     * @return {@code true} if vanished
     */
    public static boolean isVanished(Player player) {
        for (MetadataValue value : player.getMetadata("vanished")) {
            if (value.asBoolean()) {
                return true;
            }
        }
        return false;
    }
}
