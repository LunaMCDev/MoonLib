package com.example.exampleplugin.menu;

import dev.lunamc.moonlib.item.ItemBuilder;
import dev.lunamc.moonlib.menu.Button;
import dev.lunamc.moonlib.menu.PaginatedMenu;
import dev.lunamc.moonlib.util.Locations;
import dev.lunamc.moonlib.util.Players;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * A paginated list of online players with their heads; click to teleport.
 */
public final class PlayersMenu extends PaginatedMenu<Player> {

    public PlayersMenu(Player viewer) {
        super(viewer, "&8Online Players", 6);
        setItemSlots(innerSlots(6));
        autoUpdate(40); // refresh every 2 seconds so joins/leaves appear live
    }

    @Override
    protected List<Player> entries() {
        return Players.online();
    }

    @Override
    protected Button entryButton(Player target) {
        return Button.of(ItemBuilder.skull()
                .skullOwner(target)
                .name("&f" + target.getName())
                .lore("&7" + Locations.toFriendlyString(target.getLocation()), "", "&eClick to teleport")
                .build(), click -> {
            click.close();
            click.player().teleport(target.getLocation());
        });
    }

    @Override
    protected void decorate() {
        fillBorder(ItemBuilder.filler("BLACK_STAINED_GLASS_PANE"));
        closeButton(slot(5, 4));
    }
}
