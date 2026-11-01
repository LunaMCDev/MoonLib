package dev.lunamc.moonlib.menu;

import dev.lunamc.moonlib.item.ItemBuilder;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * A menu that spreads a list of entries over multiple pages, with previous/next buttons.
 *
 * <pre>{@code
 * public final class MinesMenu extends PaginatedMenu<Mine> {
 *
 *     public MinesMenu(Player player) {
 *         super(player, "&8Mines", 6);
 *         setItemSlots(innerSlots(6));          // leave a border; default is every row but the last
 *     }
 *
 *     @Override
 *     protected List<Mine> entries() {
 *         return mineManager.all();
 *     }
 *
 *     @Override
 *     protected Button entryButton(Mine mine) {
 *         return Button.of(mine.icon(), click -> mine.teleport(click.player()));
 *     }
 *
 *     @Override
 *     protected void decorate() {
 *         fillBorder(ItemBuilder.filler("BLACK_STAINED_GLASS_PANE"));
 *     }
 * }
 * }</pre>
 *
 * <p>The page is kept in range automatically if the list shrinks between refreshes.</p>
 *
 * @param <T> the entry type
 */
public abstract class PaginatedMenu<T> extends Menu {

    private int page;
    private int pageCount = 1;
    private int[] itemSlots;

    /**
     * Creates a paginated menu.
     *
     * @param viewer the viewer
     * @param title  the title; {@code {page}} and {@code {pages}} placeholders are <i>not</i> replaced
     *               automatically, override {@link #decorate()} and call {@link #setTitle(String)} if needed
     * @param rows   2 to 6 rows (the last row holds the navigation by default)
     */
    protected PaginatedMenu(Player viewer, String title, int rows) {
        super(viewer, title, rows);
        if (rows < 2) {
            throw new IllegalArgumentException("Paginated menus need at least 2 rows");
        }
        this.itemSlots = new int[(rows - 1) * 9];
        for (int i = 0; i < itemSlots.length; i++) {
            itemSlots[i] = i;
        }
    }

    /**
     * Returns every entry to display, across all pages. Called on each render.
     *
     * @return the entries
     */
    protected abstract List<T> entries();

    /**
     * Creates the button for one entry.
     *
     * @param entry the entry
     * @return its button
     */
    protected abstract Button entryButton(T entry);

    /**
     * Places static decoration (borders, info items) before the entries are placed. Default does nothing.
     */
    protected void decorate() {
    }

    /**
     * Returns the item shown when there are no entries, placed in the centre of the item area.
     *
     * @return the item, or {@code null} for none
     */
    protected ItemStack emptyItem() {
        return ItemBuilder.of("BARRIER").name("&cNothing here yet").build();
    }

    /**
     * Returns the slot of the previous page button.
     *
     * @return the slot, by default the 4th slot of the last row
     */
    protected int previousPageSlot() {
        return slot(rows() - 1, 3);
    }

    /**
     * Returns the slot of the next page button.
     *
     * @return the slot, by default the 6th slot of the last row
     */
    protected int nextPageSlot() {
        return slot(rows() - 1, 5);
    }

    /**
     * Returns the item of the previous page button.
     *
     * @return the item
     */
    protected ItemStack previousPageItem() {
        return ItemBuilder.of("ARROW").name("&ePrevious Page")
                .lore("&7Page " + page + "/" + pageCount).build();
    }

    /**
     * Returns the item of the next page button.
     *
     * @return the item
     */
    protected ItemStack nextPageItem() {
        return ItemBuilder.of("ARROW").name("&eNext Page")
                .lore("&7Page " + (page + 2) + "/" + pageCount).build();
    }

    @Override
    protected final void build() {
        decorate();
        List<T> all = entries();
        int perPage = itemSlots.length;
        pageCount = Math.max(1, (all.size() + perPage - 1) / perPage);
        page = Math.max(0, Math.min(page, pageCount - 1));

        int start = page * perPage;
        for (int i = 0; i < perPage; i++) {
            int index = start + i;
            if (index >= all.size()) {
                break;
            }
            set(itemSlots[i], entryButton(all.get(index)));
        }
        if (all.isEmpty()) {
            ItemStack empty = emptyItem();
            if (empty != null && itemSlots.length > 0) {
                set(itemSlots[itemSlots.length / 2], empty);
            }
        }
        if (page > 0) {
            set(previousPageSlot(), Button.of(previousPageItem(), click -> previousPage()));
        }
        if (page < pageCount - 1) {
            set(nextPageSlot(), Button.of(nextPageItem(), click -> nextPage()));
        }
    }

    /** Shows the next page, if there is one. */
    public void nextPage() {
        if (page < pageCount - 1) {
            page++;
            refresh();
        }
    }

    /** Shows the previous page, if there is one. */
    public void previousPage() {
        if (page > 0) {
            page--;
            refresh();
        }
    }

    /**
     * Jumps to a page.
     *
     * @param newPage the zero-based page, clamped into range on the next render
     */
    public void setPage(int newPage) {
        this.page = Math.max(0, newPage);
        refresh();
    }

    /**
     * Returns the current page.
     *
     * @return the zero-based page
     */
    public int page() {
        return page;
    }

    /**
     * Returns the number of pages as of the last render.
     *
     * @return at least 1
     */
    public int pageCount() {
        return pageCount;
    }

    /**
     * Sets which slots hold entries, in display order.
     *
     * @param slots the slots
     */
    public void setItemSlots(int... slots) {
        if (slots.length == 0) {
            throw new IllegalArgumentException("At least one item slot is required");
        }
        this.itemSlots = slots.clone();
    }

    /**
     * Returns the slots inside a one-slot border, e.g. 28 slots for a 6 row menu.
     *
     * @param rows the menu's row count
     * @return the inner slots in reading order
     */
    public static int[] innerSlots(int rows) {
        if (rows < 3) {
            throw new IllegalArgumentException("A bordered layout needs at least 3 rows");
        }
        int[] slots = new int[(rows - 2) * 7];
        int i = 0;
        for (int row = 1; row < rows - 1; row++) {
            for (int column = 1; column < 8; column++) {
                slots[i++] = slot(row, column);
            }
        }
        return slots;
    }
}
