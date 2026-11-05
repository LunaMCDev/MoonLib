package com.example.template.menu;

import com.example.template.TemplatePlugin;
import dev.lunamc.moonlib.item.ItemBuilder;
import dev.lunamc.moonlib.menu.Button;
import dev.lunamc.moonlib.menu.Menu;
import dev.lunamc.moonlib.module.ModuleManager;
import dev.lunamc.moonlib.module.MoonModule;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Shows every module with its state; click one to reload it. Refreshes itself every second, so
 * a module reloaded by the dev watcher or a config edit updates live.
 */
public final class ModulesMenu extends Menu {

    private final TemplatePlugin plugin;

    public ModulesMenu(Player viewer, TemplatePlugin plugin) {
        super(viewer, "&8Modules", 3);
        this.plugin = plugin;
        autoUpdate(20);
    }

    @Override
    protected void build() {
        fill(ItemBuilder.filler("GRAY_STAINED_GLASS_PANE"));
        List<MoonModule> modules = plugin.modules().modules();
        for (int i = 0; i < modules.size() && i < 7; i++) {
            MoonModule module = modules.get(i);
            ModuleManager.State state = plugin.modules().state(module);
            boolean on = state == ModuleManager.State.ENABLED;
            String problem = plugin.modules().problem(module);
            set(slot(1, 1 + i), Button.of(ItemBuilder.of(on ? "LIME_DYE" : "GRAY_DYE")
                    .name((on ? "&a" : "&7") + module.id())
                    .lore("&7State: &f" + state.name().toLowerCase().replace('_', ' '),
                            problem == null ? "" : "&c" + problem,
                            "",
                            "&eClick to reload")
                    .build(), click -> {
                plugin.modules().reload(module.id());
                click.refresh();
            }));
        }
        closeButton(slot(2, 4));
    }
}
