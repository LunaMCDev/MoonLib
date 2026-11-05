package com.lunamcdev.moonlib.menu;

import com.lunamcdev.moonlib.MoonLib;
import com.lunamcdev.moonlib.util.Log;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Internal listener that routes inventory events to {@link Menu}s. Registered automatically by
 * {@link MoonLib#init(Plugin)}; you never need to touch it, apart from {@link #openMenu(Player)}.
 *
 * <p>One listener serves every plugin sharing this copy of MoonLib. Click and close handlers run as
 * the plugin that created the menu.
 *
 * <p>Menus are identified by their {@link InventoryHolder}, which is reliable on every version and
 * immune to players renaming items or other plugins using the same title.</p>
 */
public final class MenuManager implements Listener {

    private static final Map<UUID, Menu> OPEN = new ConcurrentHashMap<>();
    private static final MenuManager INSTANCE = new MenuManager();

    private MenuManager() {
    }

    /**
     * Makes sure the shared menu listener is registered. Called by {@link MoonLib#init(Plugin)}.
     *
     * @param plugin the plugin being initialised
     */
    public static void init(Plugin plugin) {
        MoonLib.shareListener(INSTANCE);
    }

    /**
     * Closes every open menu that belongs to a plugin. Menus of other plugins sharing MoonLib stay open.
     * Called by {@link MoonLib#shutdown(Plugin)}.
     *
     * @param plugin the plugin
     */
    public static void shutdown(Plugin plugin) {
        for (Menu menu : new ArrayList<>(OPEN.values())) {
            if (menu.owner() != plugin) {
                continue;
            }
            OPEN.remove(menu.viewer().getUniqueId(), menu);
            try {
                Player viewer = menu.viewer();
                if (viewer.isOnline()) {
                    viewer.closeInventory();
                }
            } catch (RuntimeException e) {
                Log.error("Failed to close a menu during shutdown", e);
            }
        }
    }

    /**
     * Returns the MoonLib menu a player currently has open.
     *
     * @param player the player
     * @return the menu, or {@code null}
     */
    public static Menu openMenu(Player player) {
        return OPEN.get(player.getUniqueId());
    }

    static void ensureInitialised() {
        if (!MoonLib.isShared(INSTANCE)) {
            MoonLib.shareListener(INSTANCE);
        }
    }

    static void track(Player player, Menu menu) {
        OPEN.put(player.getUniqueId(), menu);
    }

    private static Menu menuOf(InventoryHolder holder) {
        return holder instanceof Menu ? (Menu) holder : null;
    }

    /**
     * Handles clicks.
     *
     * @param event the event
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent event) {
        Menu menu = menuOf(event.getInventory().getHolder());
        if (menu == null) {
            return;
        }
        event.setCancelled(true);
        HumanEntity clicker = event.getWhoClicked();
        if (!(clicker instanceof Player) || !clicker.getUniqueId().equals(menu.viewer().getUniqueId())) {
            return;
        }
        try {
            MoonLib.runAs(menu.owner(), () -> menu.handleClick(event));
        } catch (Throwable t) {
            event.setCancelled(true);
            Log.error("Error handling a click in menu " + menu.getClass().getName(), t);
        }
    }

    /**
     * Blocks dragging items into menus.
     *
     * @param event the event
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onDrag(InventoryDragEvent event) {
        Menu menu = menuOf(event.getInventory().getHolder());
        if (menu == null) {
            return;
        }
        int topSize = event.getInventory().getSize();
        for (int raw : event.getRawSlots()) {
            if (raw < topSize) {
                event.setCancelled(true);
                return;
            }
        }
    }

    /**
     * Handles closing.
     *
     * @param event the event
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        Menu menu = menuOf(event.getInventory().getHolder());
        if (menu == null) {
            return;
        }
        OPEN.remove(event.getPlayer().getUniqueId(), menu);
        try {
            MoonLib.runAs(menu.owner(), menu::handleClose);
        } catch (Throwable t) {
            Log.error("Error in onClose of menu " + menu.getClass().getName(), t);
        }
    }

    /**
     * Cleans up when a player leaves.
     *
     * @param event the event
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        OPEN.remove(event.getPlayer().getUniqueId());
    }
}
