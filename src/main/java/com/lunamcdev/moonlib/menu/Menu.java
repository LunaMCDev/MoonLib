package com.lunamcdev.moonlib.menu;

import com.lunamcdev.moonlib.MoonLib;
import com.lunamcdev.moonlib.item.ItemBuilder;
import com.lunamcdev.moonlib.scheduler.TaskHandle;
import com.lunamcdev.moonlib.scheduler.Tasks;
import com.lunamcdev.moonlib.util.Text;
import com.lunamcdev.moonlib.version.ServerVersion;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A chest-style GUI shown to one player.
 *
 * <p>Subclass it, place buttons in {@link #build()}, and call {@link #open()}:</p>
 *
 * <pre>{@code
 * public final class UpgradeMenu extends Menu {
 *
 *     public UpgradeMenu(Player player) {
 *         super(player, "&8Pickaxe Upgrades", 3);
 *     }
 *
 *     @Override
 *     protected void build() {
 *         fillBorder(ItemBuilder.filler("GRAY_STAINED_GLASS_PANE"));
 *         set(13, Button.of(ItemBuilder.of("DIAMOND_PICKAXE").name("&bEfficiency").build(), click -> {
 *             upgrades.buy(click.player(), "efficiency");
 *             click.refresh();
 *         }));
 *         backButton(slot(2, 0));
 *     }
 * }
 *
 * new UpgradeMenu(player).open();
 * new UpgradeMenu(player).open(parentMenu);   // enables back()
 * }</pre>
 *
 * <h2>Layouts</h2>
 * <p>For more complex screens describe the layout visually and bind characters to buttons:</p>
 * <pre>{@code
 * pattern(
 *     "#########",
 *     "#.A.B.C.#",
 *     "####X####");
 * bind('#', ItemBuilder.filler("BLACK_STAINED_GLASS_PANE"));
 * bind('A', minesButton);
 * bind('X', Button.of(closeItem, MenuClick::close));
 * }</pre>
 *
 * <h2>Behaviour</h2>
 * <ul>
 *     <li>All clicks in the menu are cancelled; items can never be stolen.</li>
 *     <li>Shift-clicking and dragging from the player inventory into the menu is blocked.</li>
 *     <li>Clicks in the player's own inventory are blocked unless {@link #allowPlayerInventoryClicks(boolean)}.</li>
 *     <li>Button clicks are rate limited ({@link #clickCooldown(long)}) to stop auto-clicker double purchases.</li>
 *     <li>Exceptions thrown by click handlers are logged and never leave the menu in a broken state.</li>
 *     <li>All open menus are closed when the plugin disables.</li>
 * </ul>
 */
public abstract class Menu implements InventoryHolder {

    private final Player viewer;
    private final Plugin owner;
    private final Map<Integer, Button> buttons = new HashMap<>();
    private String title;
    private int rows;
    private Inventory inventory;
    private boolean dirtyInventory = true;
    private Menu previous;
    private String[] pattern;
    private long autoUpdateTicks;
    private TaskHandle updateTask = TaskHandle.NONE;
    private boolean allowPlayerInventoryClicks;
    private long clickCooldownMillis = 150;
    private long lastClickAt;
    private boolean open;

    /**
     * Creates a menu.
     *
     * @param viewer the player who will see the menu
     * @param title  the title, colour codes allowed
     * @param rows   the number of rows, 1 to 6
     */
    protected Menu(Player viewer, String title, int rows) {
        if (rows < 1 || rows > 6) {
            throw new IllegalArgumentException("Menus must have 1 to 6 rows, got " + rows);
        }
        this.viewer = viewer;
        this.owner = MoonLib.plugin();
        this.title = title == null ? "" : title;
        this.rows = rows;
    }

    /**
     * Places the menu's buttons. Called on every {@link #open()} and {@link #refresh()}, after the
     * contents were cleared.
     */
    protected abstract void build();

    /** Called after the menu was opened. */
    protected void onOpen() {
    }

    /** Called after the menu was closed, for any reason. */
    protected void onClose() {
    }

    /**
     * Called for clicks in the player's own inventory while this menu is open, if
     * {@link #allowPlayerInventoryClicks(boolean)} is enabled. Shift-clicks never reach here.
     *
     * @param event the click event (not cancelled)
     */
    protected void onPlayerInventoryClick(InventoryClickEvent event) {
    }

    // ------------------------------------------------------------------ lifecycle

    /** Opens the menu (or re-opens it, rebuilding the contents). */
    public final void open() {
        MoonLib.runAs(owner, this::openAsOwner);
    }

    private void openAsOwner() {
        MenuManager.ensureInitialised();
        if (inventory == null || dirtyInventory) {
            inventory = createInventory();
            dirtyInventory = false;
        }
        render();
        viewer.openInventory(inventory);
        MenuManager.track(viewer, this);
        open = true;
        onOpen();
        startUpdates();
    }

    /**
     * Opens the menu, remembering the menu it was opened from so {@link #back()} and
     * {@link #backButton(int)} return there.
     *
     * @param from the parent menu
     */
    public final void open(Menu from) {
        this.previous = from;
        open();
    }

    /** Clears and rebuilds the contents without re-opening the inventory. */
    public void refresh() {
        if (inventory == null) {
            return;
        }
        MoonLib.runAs(owner, this::render);
    }

    /** Closes the menu on the next tick. Safe to call from click handlers. */
    public void close() {
        MoonLib.runAs(owner, () -> Tasks.entity(viewer, () -> {
            if (MenuManager.openMenu(viewer) == this) {
                viewer.closeInventory();
            }
        }));
    }

    /** Re-opens the parent menu if there is one, otherwise closes this menu. */
    public void back() {
        if (previous == null) {
            close();
            return;
        }
        Menu parent = previous;
        MoonLib.runAs(owner, () -> Tasks.entity(viewer, parent::open));
    }

    /**
     * Changes the title. If the menu is open it is re-opened so the new title shows.
     *
     * @param newTitle the new title
     */
    public void setTitle(String newTitle) {
        this.title = newTitle == null ? "" : newTitle;
        this.dirtyInventory = true;
        if (open) {
            open();
        }
    }

    /**
     * Changes the number of rows. If the menu is open it is re-opened.
     *
     * @param newRows 1 to 6
     */
    public void setRows(int newRows) {
        if (newRows < 1 || newRows > 6) {
            throw new IllegalArgumentException("Menus must have 1 to 6 rows, got " + newRows);
        }
        this.rows = newRows;
        this.dirtyInventory = true;
        if (open) {
            open();
        }
    }

    // ------------------------------------------------------------------ placing items

    /**
     * Places a button.
     *
     * @param slot   the slot, 0 to {@link #size()} - 1
     * @param button the button, {@code null} clears the slot
     */
    public void set(int slot, Button button) {
        checkSlot(slot);
        if (button == null) {
            remove(slot);
            return;
        }
        buttons.put(slot, button);
        if (inventory != null) {
            inventory.setItem(slot, button.item());
        }
    }

    /**
     * Places a decorative item.
     *
     * @param slot the slot
     * @param item the item
     */
    public void set(int slot, ItemStack item) {
        set(slot, Button.display(item));
    }

    /**
     * Places a button by row and column.
     *
     * @param row    the row, from 0
     * @param column the column, 0 to 8
     * @param button the button
     */
    public void set(int row, int column, Button button) {
        set(slot(row, column), button);
    }

    /**
     * Places a button in several slots.
     *
     * @param button the button
     * @param slots  the slots
     */
    public void setAll(Button button, int... slots) {
        for (int slot : slots) {
            set(slot, button);
        }
    }

    /**
     * Clears a slot.
     *
     * @param slot the slot
     */
    public void remove(int slot) {
        buttons.remove(slot);
        if (inventory != null) {
            inventory.setItem(slot, null);
        }
    }

    /**
     * Returns the button in a slot.
     *
     * @param slot the slot
     * @return the button, or {@code null}
     */
    public Button button(int slot) {
        return buttons.get(slot);
    }

    /**
     * Fills every slot.
     *
     * @param item the item
     */
    public void fill(ItemStack item) {
        Button button = Button.display(item);
        for (int slot = 0; slot < size(); slot++) {
            set(slot, button);
        }
    }

    /**
     * Fills every slot that has no button yet.
     *
     * @param item the item
     */
    public void fillEmpty(ItemStack item) {
        Button button = Button.display(item);
        for (int slot = 0; slot < size(); slot++) {
            if (!buttons.containsKey(slot)) {
                set(slot, button);
            }
        }
    }

    /**
     * Fills the outer border.
     *
     * @param item the item
     */
    public void fillBorder(ItemStack item) {
        Button button = Button.display(item);
        for (int slot = 0; slot < size(); slot++) {
            int row = slot / 9;
            int column = slot % 9;
            if (row == 0 || row == rows - 1 || column == 0 || column == 8) {
                set(slot, button);
            }
        }
    }

    /**
     * Fills a row.
     *
     * @param row  the row, from 0
     * @param item the item
     */
    public void fillRow(int row, ItemStack item) {
        Button button = Button.display(item);
        for (int column = 0; column < 9; column++) {
            set(slot(row, column), button);
        }
    }

    /**
     * Fills a column.
     *
     * @param column the column, 0 to 8
     * @param item   the item
     */
    public void fillColumn(int column, ItemStack item) {
        Button button = Button.display(item);
        for (int row = 0; row < rows; row++) {
            set(slot(row, column), button);
        }
    }

    /**
     * Defines a visual layout. Each string is one row of exactly 9 characters; use
     * {@link #bind(char, Button)} afterwards to place buttons on a character.
     *
     * @param rowPatterns one string per row
     */
    public void pattern(String... rowPatterns) {
        if (rowPatterns.length > rows) {
            throw new IllegalArgumentException("Pattern has " + rowPatterns.length + " rows but the menu has " + rows);
        }
        for (String row : rowPatterns) {
            if (row.length() != 9) {
                throw new IllegalArgumentException("Pattern rows must be 9 characters: '" + row + "'");
            }
        }
        this.pattern = rowPatterns.clone();
    }

    /**
     * Places a button in every slot marked with a character in the current {@link #pattern(String...)}.
     *
     * @param key    the character
     * @param button the button
     */
    public void bind(char key, Button button) {
        for (int slot : slotsOf(key)) {
            set(slot, button);
        }
    }

    /**
     * Places a decorative item in every slot marked with a character.
     *
     * @param key  the character
     * @param item the item
     */
    public void bind(char key, ItemStack item) {
        bind(key, Button.display(item));
    }

    /**
     * Returns the slots marked with a character in the current pattern.
     *
     * @param key the character
     * @return the slots, in order
     */
    public List<Integer> slotsOf(char key) {
        if (pattern == null) {
            throw new IllegalStateException("Call pattern(...) before bind(...) or slotsOf(...)");
        }
        List<Integer> slots = new ArrayList<>();
        for (int row = 0; row < pattern.length; row++) {
            for (int column = 0; column < 9; column++) {
                if (pattern[row].charAt(column) == key) {
                    slots.add(slot(row, column));
                }
            }
        }
        return slots;
    }

    /**
     * Places a "back" button that returns to the parent menu, or closes this menu if there is none.
     *
     * @param slot the slot
     */
    public void backButton(int slot) {
        ItemStack item = ItemBuilder.of("ARROW")
                .name(previous == null ? "&cClose" : "&eBack")
                .build();
        set(slot, Button.of(item, MenuClick::back));
    }

    /**
     * Places a button that closes the menu.
     *
     * @param slot the slot
     */
    public void closeButton(int slot) {
        set(slot, Button.of(ItemBuilder.of("BARRIER").name("&cClose").build(), MenuClick::close));
    }

    // ------------------------------------------------------------------ options

    /**
     * Rebuilds the menu automatically while it is open, e.g. for live timers or balances.
     *
     * @param ticks the interval in ticks, or 0 to disable
     */
    public void autoUpdate(long ticks) {
        this.autoUpdateTicks = Math.max(0, ticks);
        if (open) {
            startUpdates();
        }
    }

    /**
     * Allows the player to click and move items in their own inventory while the menu is open.
     * Shift-clicking into the menu is still blocked.
     *
     * @param allow {@code true} to allow
     */
    public void allowPlayerInventoryClicks(boolean allow) {
        this.allowPlayerInventoryClicks = allow;
    }

    /**
     * Sets the minimum time between handled button clicks. Defaults to 150 ms.
     *
     * @param millis the cooldown, 0 to disable
     */
    public void clickCooldown(long millis) {
        this.clickCooldownMillis = Math.max(0, millis);
    }

    // ------------------------------------------------------------------ accessors

    /**
     * Converts a row and column into a slot index.
     *
     * @param row    the row, from 0
     * @param column the column, 0 to 8
     * @return the slot index
     */
    public static int slot(int row, int column) {
        return row * 9 + column;
    }

    /**
     * Returns the plugin that created this menu. Its click handlers run as this plugin, and the menu
     * is closed when this plugin disables.
     *
     * @return the owning plugin
     */
    public Plugin owner() {
        return owner;
    }

    /**
     * Returns the player viewing this menu.
     *
     * @return the viewer
     */
    public Player viewer() {
        return viewer;
    }

    /**
     * Returns the raw (uncoloured) title.
     *
     * @return the title
     */
    public String title() {
        return title;
    }

    /**
     * Returns the number of rows.
     *
     * @return 1 to 6
     */
    public int rows() {
        return rows;
    }

    /**
     * Returns the number of slots.
     *
     * @return rows times 9
     */
    public int size() {
        return rows * 9;
    }

    /**
     * Returns whether the menu is currently open.
     *
     * @return {@code true} if open
     */
    public boolean isOpen() {
        return open;
    }

    /**
     * Returns the menu this one was opened from.
     *
     * @return the parent menu, or {@code null}
     */
    public Menu previous() {
        return previous;
    }

    /**
     * Returns the buttons currently placed, by slot.
     *
     * @return an unmodifiable view
     */
    public Map<Integer, Button> buttons() {
        return Collections.unmodifiableMap(buttons);
    }

    @Override
    public Inventory getInventory() {
        if (inventory == null) {
            inventory = createInventory();
            dirtyInventory = false;
        }
        return inventory;
    }

    // ------------------------------------------------------------------ internals

    void handleClick(InventoryClickEvent event) {
        int rawSlot = event.getRawSlot();
        if (rawSlot < 0) {
            return; // clicked outside the window
        }
        if (rawSlot >= size()) {
            // Player inventory
            boolean movesIntoMenu = event.isShiftClick() || event.getClick().name().equals("DOUBLE_CLICK");
            if (allowPlayerInventoryClicks && !movesIntoMenu) {
                event.setCancelled(false);
                onPlayerInventoryClick(event);
            }
            return;
        }
        Button button = buttons.get(rawSlot);
        if (button == null || event.getClick().name().equals("DOUBLE_CLICK")) {
            return;
        }
        long now = System.currentTimeMillis();
        if (button.isClickable() && now - lastClickAt < clickCooldownMillis) {
            return;
        }
        lastClickAt = now;
        MenuClick click = new MenuClick(this, viewer, rawSlot, event);
        button.click(click);
        if (!click.isCancelled()) {
            event.setCancelled(false);
        }
    }

    void handleClose() {
        open = false;
        updateTask.cancel();
        updateTask = TaskHandle.NONE;
        onClose();
    }

    private void render() {
        buttons.clear();
        inventory.clear();
        build();
    }

    private Inventory createInventory() {
        String colored = Text.color(title);
        if (ServerVersion.current().isBelow(ServerVersion.V1_9) && colored.length() > 32) {
            colored = colored.substring(0, 32);
        }
        return Bukkit.createInventory(this, size(), colored);
    }

    private void startUpdates() {
        updateTask.cancel();
        updateTask = TaskHandle.NONE;
        if (autoUpdateTicks > 0) {
            updateTask = MoonLib.callAs(owner, () -> Tasks.entityTimer(viewer, autoUpdateTicks, autoUpdateTicks, () -> {
                if (open) {
                    refresh();
                }
            }));
        }
    }

    private void checkSlot(int slot) {
        if (slot < 0 || slot >= size()) {
            throw new IndexOutOfBoundsException("Slot " + slot + " is outside this " + size() + " slot menu");
        }
    }
}
