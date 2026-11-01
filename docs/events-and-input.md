# Events & chat input

## Functional events

Package: `dev.lunamc.moonlib.event`

```java
// simplest form
Events.listen(PlayerJoinEvent.class, e -> e.setJoinMessage(null));

// with priority
Events.listen(PlayerDeathEvent.class, EventPriority.HIGH, e -> e.setKeepInventory(true));

// fully configured
EventSubscription<BlockBreakEvent> sub = Events.subscribe(BlockBreakEvent.class)
        .priority(EventPriority.HIGH)
        .ignoreCancelled()
        .filter(e -> e.getPlayer().getGameMode() == GameMode.SURVIVAL)
        .filter(e -> mines.contains(e.getBlock().getLocation()))
        .handler(mines::handleBreak);

sub.unregister();      // any time, even from inside the handler
sub.callCount();
```

### One-shot listeners

```java
Events.subscribe(PlayerInteractEvent.class)
        .filter(e -> e.getPlayer().equals(player) && e.getClickedBlock() != null)
        .expireAfter(1)
        .handler(e -> {
            setMineCorner(player, e.getClickedBlock().getLocation());
            e.setCancelled(true);
        });
```

### Firing custom events

```java
RankupEvent event = Events.call(new RankupEvent(player, from, to));
if (event.isCancelled()) return;
```

Handler exceptions are caught and logged with the event name, so one bad handler never affects
other plugins' listeners.

Classic `Listener` classes still work as normal; register them with
`registerListeners(...)` in your `MoonPlugin`.

## Chat input

Package: `dev.lunamc.moonlib.input`

```java
ChatInput.ask(player)
        .prompt("&eType the amount to withdraw, or &ccancel&e:")
        .validate(text -> Numbers.parseDouble(text, -1) > 0, "&cThat's not a positive number.")
        .timeout(30)                                   // seconds, 0 = no timeout
        .cancelWord("cancel")                          // null disables
        .timeoutMessage("&cToo slow!")
        .cancelledMessage("&7Cancelled.")
        .onInput(text -> bank.withdraw(player, Double.parseDouble(text)))
        .onCancel(() -> new BankMenu(player).open())
        .start();
```

- The typed message is hidden from chat.
- `onInput` and `onCancel` run on the player's thread, so you can open menus and touch the world.
- Invalid input re-prompts with the invalid message, without ending the prompt.
- Starting a new prompt replaces any pending prompt for the same player.
- `ChatInput.isWaiting(player)` and `ChatInput.cancel(player)` are available.

### From a menu

```java
set(13, Button.of(renameItem, click -> {
    click.close();
    ChatInput.ask(click.player())
            .prompt("&eNew name:")
            .onInput(name -> { pets.rename(click.player(), name); new PetMenu(click.player()).open(); })
            .onCancel(() -> new PetMenu(click.player()).open())
            .start();
}));
```
