package dev.lunamc.moonlib.menu;

import org.bukkit.inventory.ItemStack;

import java.util.function.Supplier;

/**
 * An item in a {@link Menu} with optional click behaviour.
 *
 * <pre>{@code
 * Button.of(ItemBuilder.of("DIAMOND_PICKAXE").name("&bUpgrade").build(), click -> {
 *     if (click.isRightClick()) upgradeMax(click.player()); else upgradeOnce(click.player());
 *     click.refresh();
 * });
 *
 * Button.display(ItemBuilder.of("PAPER").name("&7Just information").build());
 *
 * // An item that is rebuilt every time the menu refreshes:
 * Button.dynamic(() -> ItemBuilder.of("GOLD_INGOT").name("&6Balance: " + economy.balance(player)).build());
 * }</pre>
 */
public class Button {

    /**
     * Reacts to a click on a button.
     */
    @FunctionalInterface
    public interface ClickHandler {
        /**
         * Handles a click.
         *
         * @param click details about the click
         */
        void onClick(MenuClick click);
    }

    private final Supplier<ItemStack> item;
    private final ClickHandler handler;

    /**
     * Creates a button with a supplier, so the item is re-evaluated every time the menu renders.
     *
     * @param item    supplies the displayed item
     * @param handler the click handler, or {@code null} for none
     */
    protected Button(Supplier<ItemStack> item, ClickHandler handler) {
        this.item = item;
        this.handler = handler;
    }

    /**
     * Creates a clickable button.
     *
     * @param item    the displayed item
     * @param handler the click handler
     * @return the button
     */
    public static Button of(ItemStack item, ClickHandler handler) {
        ItemStack copy = item == null ? null : item.clone();
        return new Button(() -> copy, handler);
    }

    /**
     * Creates a purely decorative button. Clicks are cancelled and ignored.
     *
     * @param item the displayed item
     * @return the button
     */
    public static Button display(ItemStack item) {
        return of(item, null);
    }

    /**
     * Creates a button whose item is rebuilt each time the menu renders or refreshes.
     *
     * @param item    supplies the item
     * @param handler the click handler, or {@code null}
     * @return the button
     */
    public static Button dynamic(Supplier<ItemStack> item, ClickHandler handler) {
        return new Button(item, handler);
    }

    /**
     * Creates a decorative button whose item is rebuilt each time the menu renders.
     *
     * @param item supplies the item
     * @return the button
     */
    public static Button dynamic(Supplier<ItemStack> item) {
        return new Button(item, null);
    }

    /**
     * Returns the item to display.
     *
     * @return the item, possibly {@code null} for an empty slot
     */
    public ItemStack item() {
        return item.get();
    }

    /**
     * Handles a click. Override for class-based buttons.
     *
     * @param click details about the click
     */
    public void click(MenuClick click) {
        if (handler != null) {
            handler.onClick(click);
        }
    }

    /**
     * Returns whether this button reacts to clicks.
     *
     * @return {@code true} if it has a handler
     */
    public boolean isClickable() {
        return handler != null;
    }
}
