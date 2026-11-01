package com.example.exampleplugin.menu;

import com.example.exampleplugin.ExamplePlugin;
import dev.lunamc.moonlib.config.ConfigFile;
import dev.lunamc.moonlib.hook.VaultEconomy;
import dev.lunamc.moonlib.item.ItemBuilder;
import dev.lunamc.moonlib.menu.Button;
import dev.lunamc.moonlib.menu.ConfirmMenu;
import dev.lunamc.moonlib.menu.Menu;
import dev.lunamc.moonlib.menu.MenuClick;
import dev.lunamc.moonlib.util.Players;
import dev.lunamc.moonlib.util.Text;
import dev.lunamc.moonlib.version.Materials;
import dev.lunamc.moonlib.version.Sounds;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * A config-driven shop using a visual pattern, Vault and a confirm dialog.
 */
public final class ShopMenu extends Menu {

    private final ExamplePlugin plugin;

    public ShopMenu(ExamplePlugin plugin, Player viewer) {
        super(viewer, "&8Shop", 3);
        this.plugin = plugin;
    }

    @Override
    protected void build() {
        pattern(
                "#########",
                "#.......#",
                "####X####");
        bind('#', ItemBuilder.filler("PURPLE_STAINED_GLASS_PANE"));
        bind('X', Button.of(ItemBuilder.of("BARRIER").name("&cClose").build(), MenuClick::close));

        // Balance display that updates whenever the menu refreshes.
        set(4, Button.dynamic(() -> ItemBuilder.of("GOLD_INGOT")
                .name("&6Balance: &e" + VaultEconomy.format(VaultEconomy.balance(viewer())))
                .build()));

        ConfigFile settings = plugin.settings();
        List<Integer> slots = slotsOf('.');
        int index = 0;
        for (String key : settings.keys("shop.items")) {
            if (index >= slots.size()) {
                break;
            }
            String path = "shop.items." + key;
            String material = settings.getString(path + ".material", "STONE");
            double price = settings.getDouble(path + ".price", 0);
            ItemStack icon = ItemBuilder.of(material)
                    .name("&f" + Text.formatEnum(material))
                    .lore("", "&7Price: &e{price}", "", "&aClick to buy")
                    .placeholder("price", VaultEconomy.format(price))
                    .build();
            set(slots.get(index++), Button.of(icon, click -> buy(click, material, price)));
        }
    }

    private void buy(MenuClick click, String material, double price) {
        Player player = click.player();
        if (!VaultEconomy.isAvailable()) {
            plugin.messages().send(player, "shop.no-economy");
            return;
        }
        ItemStack reward = Materials.item(material);
        new ConfirmMenu(player, "&8Buy for " + VaultEconomy.format(price) + "?", reward,
                p -> {
                    if (VaultEconomy.withdraw(p, price)) {
                        Players.give(p, reward);
                        Sounds.play(p, "ENTITY_EXPERIENCE_ORB_PICKUP");
                        plugin.messages().send(p, "shop.bought", "item", Text.formatEnum(material),
                                "price", VaultEconomy.format(price));
                    } else {
                        Sounds.play(p, "ENTITY_VILLAGER_NO");
                        plugin.messages().send(p, "shop.too-poor", "price", VaultEconomy.format(price));
                    }
                },
                null).open(this);
    }
}
