package com.example.template.module;

import com.example.template.TemplatePlugin;
import com.example.template.data.PlayerData;
import dev.lunamc.moonlib.module.MoonModule;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Counts joins and broken blocks per player. A minimal example of a module that changes player
 * data: it just edits the cached {@link PlayerData}; saving is automatic.
 */
public final class StatsModule extends MoonModule {

    private final TemplatePlugin plugin;

    public StatsModule(TemplatePlugin plugin) {
        super("stats");
        this.plugin = plugin;
    }

    @Override
    protected void enable() {
        on(PlayerJoinEvent.class, event -> {
            PlayerData data = plugin.players().get(event.getPlayer());
            data.name = event.getPlayer().getName();
            data.joins++;
            data.lastSeen = System.currentTimeMillis();
        });
        on(PlayerQuitEvent.class, event -> plugin.players().find(event.getPlayer().getUniqueId())
                .ifPresent(data -> data.lastSeen = System.currentTimeMillis()));
        on(BlockBreakEvent.class, event -> {
            if (!event.isCancelled()) {
                plugin.players().get(event.getPlayer()).blocksBroken++;
            }
        });
    }
}
