package dev.lunamc.moonlib.menu;

import dev.lunamc.moonlib.item.ItemBuilder;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.function.Consumer;

/**
 * A ready-made "Are you sure?" menu with confirm and cancel buttons.
 *
 * <pre>{@code
 * ConfirmMenu.open(player, "&8Prestige? Your rank resets.",
 *         p -> prestige.perform(p),
 *         p -> p.sendMessage("Cancelled."));
 *
 * // With a custom info item in the middle:
 * new ConfirmMenu(player, "&8Buy for $5,000?", info, p -> shop.buy(p, item), null).open();
 * }</pre>
 *
 * <p>The callback runs once; the menu closes itself afterwards. Closing the menu without choosing
 * counts as cancelling.</p>
 */
public class ConfirmMenu extends Menu {

    private final ItemStack info;
    private final Consumer<Player> onConfirm;
    private final Consumer<Player> onCancel;
    private boolean decided;

    /**
     * Creates a confirm menu.
     *
     * @param viewer    the player
     * @param title     the title, usually the question
     * @param info      an item shown between the buttons, or {@code null}
     * @param onConfirm runs when the player confirms
     * @param onCancel  runs when the player cancels or closes the menu, may be {@code null}
     */
    public ConfirmMenu(Player viewer, String title, ItemStack info, Consumer<Player> onConfirm,
                       Consumer<Player> onCancel) {
        super(viewer, title, 3);
        this.info = info;
        this.onConfirm = onConfirm;
        this.onCancel = onCancel;
    }

    /**
     * Creates and opens a confirm menu.
     *
     * @param viewer    the player
     * @param title     the title
     * @param onConfirm runs on confirm
     * @param onCancel  runs on cancel or close, may be {@code null}
     * @return the opened menu
     */
    public static ConfirmMenu open(Player viewer, String title, Consumer<Player> onConfirm, Consumer<Player> onCancel) {
        ConfirmMenu menu = new ConfirmMenu(viewer, title, null, onConfirm, onCancel);
        menu.open();
        return menu;
    }

    /**
     * Returns the confirm button item.
     *
     * @return the item
     */
    protected ItemStack confirmItem() {
        return ItemBuilder.of("LIME_WOOL").name("&a&lConfirm").build();
    }

    /**
     * Returns the cancel button item.
     *
     * @return the item
     */
    protected ItemStack cancelItem() {
        return ItemBuilder.of("RED_WOOL").name("&c&lCancel").build();
    }

    @Override
    protected void build() {
        fill(ItemBuilder.filler("GRAY_STAINED_GLASS_PANE"));
        Button confirm = Button.of(confirmItem(), click -> decide(true));
        Button cancel = Button.of(cancelItem(), click -> decide(false));
        setAll(confirm, 10, 11, 12);
        setAll(cancel, 14, 15, 16);
        if (info != null) {
            set(13, info);
        }
    }

    @Override
    protected void onClose() {
        if (!decided) {
            decided = true;
            if (onCancel != null) {
                onCancel.accept(viewer());
            }
        }
    }

    private void decide(boolean confirmed) {
        if (decided) {
            return;
        }
        decided = true;
        close();
        if (confirmed) {
            onConfirm.accept(viewer());
        } else if (onCancel != null) {
            onCancel.accept(viewer());
        }
    }
}
