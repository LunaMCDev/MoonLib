package com.lunamcdev.moonlib.menu;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Details of a click on a {@link Button}.
 *
 * <p>The underlying event is cancelled by default so items can't be taken out of the menu. Call
 * {@link #allowItemMovement()} to let it through, for example for an "input" slot.</p>
 */
public final class MenuClick {

    private final Menu menu;
    private final Player player;
    private final int slot;
    private final InventoryClickEvent event;
    private boolean cancelled = true;

    MenuClick(Menu menu, Player player, int slot, InventoryClickEvent event) {
        this.menu = menu;
        this.player = player;
        this.slot = slot;
        this.event = event;
    }

    /**
     * Returns the menu that was clicked.
     *
     * @return the menu
     */
    public Menu menu() {
        return menu;
    }

    /**
     * Returns the player who clicked.
     *
     * @return the player
     */
    public Player player() {
        return player;
    }

    /**
     * Returns the clicked slot in the menu.
     *
     * @return the slot, from 0 to size - 1
     */
    public int slot() {
        return slot;
    }

    /**
     * Returns the click type.
     *
     * @return the click type
     */
    public ClickType type() {
        return event.getClick();
    }

    /**
     * Returns whether it was a left click (including shift-left).
     *
     * @return {@code true} for left clicks
     */
    public boolean isLeftClick() {
        return event.getClick().isLeftClick();
    }

    /**
     * Returns whether it was a right click (including shift-right).
     *
     * @return {@code true} for right clicks
     */
    public boolean isRightClick() {
        return event.getClick().isRightClick();
    }

    /**
     * Returns whether shift was held.
     *
     * @return {@code true} for shift clicks
     */
    public boolean isShiftClick() {
        return event.getClick().isShiftClick();
    }

    /**
     * Returns whether it was a middle click.
     *
     * @return {@code true} for middle clicks
     */
    public boolean isMiddleClick() {
        return event.getClick() == ClickType.MIDDLE;
    }

    /**
     * Returns the hotbar key pressed for number-key clicks.
     *
     * @return 0 to 8, or -1 if this was not a number key click
     */
    public int hotbarButton() {
        return event.getClick() == ClickType.NUMBER_KEY ? event.getHotbarButton() : -1;
    }

    /**
     * Returns the item in the clicked slot.
     *
     * @return the item, possibly {@code null}
     */
    public ItemStack item() {
        return event.getCurrentItem();
    }

    /**
     * Returns the raw Bukkit event, for advanced use.
     *
     * @return the event
     */
    public InventoryClickEvent event() {
        return event;
    }

    /** Lets the click move items, i.e. un-cancels the event. */
    public void allowItemMovement() {
        cancelled = false;
    }

    /** Rebuilds the menu's contents in place. */
    public void refresh() {
        menu.refresh();
    }

    /** Closes the menu on the next tick (closing inside a click event is unsafe). */
    public void close() {
        menu.close();
    }

    /** Opens the menu this one was opened from, if any; otherwise closes it. */
    public void back() {
        menu.back();
    }

    boolean isCancelled() {
        return cancelled;
    }
}
