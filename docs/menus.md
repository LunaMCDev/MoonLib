# Menus

Package: `dev.lunamc.moonlib.menu`

## The basics

```java
public final class KitsMenu extends Menu {

    public KitsMenu(Player player) {
        super(player, "&8Kits", 3);   // viewer, title (colours/hex ok), rows 1-6
    }

    @Override
    protected void build() {
        fill(ItemBuilder.filler("BLACK_STAINED_GLASS_PANE"));
        set(11, Button.of(ItemBuilder.of("IRON_SWORD").name("&fStarter").build(), click -> kits.give(click.player(), "starter")));
        set(15, Button.of(ItemBuilder.of("DIAMOND_SWORD").name("&bVIP").build(), click -> kits.give(click.player(), "vip")));
        closeButton(slot(2, 4));
    }
}

new KitsMenu(player).open();
```

`build()` runs on every `open()` and `refresh()`, after the contents were cleared, so it always
reflects current data.

## Buttons

| Factory | Use |
|---|---|
| `Button.of(item, handler)` | Clickable |
| `Button.display(item)` | Decoration; clicks are blocked and ignored |
| `Button.dynamic(supplier[, handler])` | Item rebuilt on every refresh (live balances, timers) |

Or subclass `Button` and override `click(MenuClick)` for complex reusable buttons.

### `MenuClick`

| Method | Description |
|---|---|
| `player()`, `menu()`, `slot()`, `item()` | Context |
| `isLeftClick()`, `isRightClick()`, `isShiftClick()`, `isMiddleClick()`, `hotbarButton()`, `type()` | Click details |
| `refresh()` | Rebuild the menu in place (no flicker, cursor kept) |
| `close()` | Close next tick (safe inside click events) |
| `back()` | Re-open the parent menu, or close |
| `allowItemMovement()` | Un-cancel this click, e.g. for an input slot |
| `event()` | The raw `InventoryClickEvent` |

## Placing items

| Method | Description |
|---|---|
| `set(slot, Button/ItemStack)`, `set(row, column, Button)` | One slot |
| `setAll(button, slots...)` | Several slots |
| `fill`, `fillEmpty`, `fillBorder`, `fillRow`, `fillColumn` | Bulk |
| `remove(slot)`, `button(slot)` | Clear / inspect |
| `slot(row, column)` | Static helper: row × 9 + column |
| `backButton(slot)`, `closeButton(slot)` | Ready-made navigation |

### Visual layouts

```java
pattern(
    "#########",
    "#A.B.C.D#",
    "#########",
    "<   X   >");
bind('#', ItemBuilder.filler("GRAY_STAINED_GLASS_PANE"));
bind('A', minesButton);
bind('X', Button.of(close, MenuClick::close));
List<Integer> free = slotsOf('.');
```

Each row is exactly 9 characters; spaces are just characters you don't bind.

## Navigation between menus

```java
new SettingsMenu(player).open(this);   // remembers "this" as the parent
// in SettingsMenu: backButton(slot(3, 0));   or   click.back();
```

## Live updating

```java
autoUpdate(20);   // rebuild every second while open; stops automatically when closed
```

## Lifecycle hooks

```java
@Override protected void onOpen()  { ... }
@Override protected void onClose() { ... }   // any reason: Esc, another menu, quit, plugin disable
```

`setTitle(String)` and `setRows(int)` re-open the menu with the new title/size.

## Paginated menus

```java
public final class WarpsMenu extends PaginatedMenu<Warp> {

    public WarpsMenu(Player player) {
        super(player, "&8Warps", 6);
        setItemSlots(innerSlots(6));   // 28 slots inside a border. Default: every row except the last
    }

    @Override protected List<Warp> entries()          { return warps.all(); }
    @Override protected Button entryButton(Warp warp) { return Button.of(warp.icon(), c -> warp.teleport(c.player())); }
    @Override protected void decorate()               { fillBorder(ItemBuilder.filler("GRAY_STAINED_GLASS_PANE")); }
}
```

Previous/next arrows appear only when there is a previous/next page. Customise them by overriding
`previousPageItem()`, `nextPageItem()`, `previousPageSlot()`, `nextPageSlot()` and `emptyItem()`.
Navigate in code with `nextPage()`, `previousPage()`, `setPage(int)`.

## Confirm dialogs

```java
ConfirmMenu.open(player, "&8Prestige? Your rank resets!",
        p -> prestige.perform(p),
        p -> p.sendMessage("Cancelled"));

new ConfirmMenu(player, "&8Buy for $500?", previewItem, onYes, onNo).open(this);
```

Closing the dialog counts as cancelling. Each callback runs at most once. Override
`confirmItem()`/`cancelItem()` to restyle.

## Safety guarantees

MoonLib's menu listener ensures that:

- **Items can't be taken** from a menu: every click in it is cancelled unless a handler opts out
  with `allowItemMovement()`.
- **Shift-click, double-click and drag** from the player's inventory into the menu are blocked.
- Clicks in the player's own inventory are blocked unless `allowPlayerInventoryClicks(true)`.
- Double-click events never trigger a button twice, and button clicks are **rate limited**
  (150 ms default, `clickCooldown(ms)`) so auto-clickers can't double-buy.
- An exception in a click handler is logged with the menu class name and the click stays cancelled.
- Menus are identified by `InventoryHolder`, never by title, so renamed chests can't impersonate them.
- All menus are closed on plugin disable, so `/reload` can't leave a "dead" GUI open.

## Version notes

- Titles longer than 32 characters are trimmed on 1.8 (a client limit).
- MoonLib never touches `InventoryView`, whose class-to-interface change in 1.21 breaks many older
  plugins. Menus compiled once work on 1.8 through 1.21+.
