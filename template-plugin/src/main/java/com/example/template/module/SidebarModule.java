package com.example.template.module;

import com.example.template.TemplatePlugin;
import com.example.template.data.PlayerData;
import dev.lunamc.moonlib.module.MoonModule;
import dev.lunamc.moonlib.scoreboard.BoardManager;
import dev.lunamc.moonlib.scoreboard.BoardProvider;
import dev.lunamc.moonlib.util.Numbers;
import dev.lunamc.moonlib.util.RichText;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * A per-player sidebar driven by config.yml. Edit the lines while the server runs: the config
 * reloads itself and this module restarts with the new lines.
 */
public final class SidebarModule extends MoonModule implements BoardProvider {

    private final TemplatePlugin plugin;
    private BoardManager boards;

    public SidebarModule(TemplatePlugin plugin) {
        super("sidebar", StatsModule.class);
        this.plugin = plugin;
    }

    @Override
    protected void enable() {
        boards = new BoardManager(this, plugin.settings().getLong("sidebar.update-ticks", 20))
                .usePlaceholderApi(true)
                .start();
        onDisable(boards::stop);
    }

    @Override
    public String title(Player player) {
        return RichText.parseLegacy(plugin.settings().getString("sidebar.title", "<light_purple><bold>MOON"));
    }

    @Override
    public List<String> lines(Player player) {
        PlayerData data = plugin.players().find(player.getUniqueId()).orElse(null);
        List<String> out = new ArrayList<>();
        for (String line : plugin.settings().getStringList("sidebar.lines")) {
            out.add(RichText.parse(line,
                    "player", player.getName(),
                    "online", Bukkit.getOnlinePlayers().size(),
                    "joins", data == null ? "-" : Numbers.commas(data.joins),
                    "blocks", data == null ? "-" : Numbers.commas(data.blocksBroken)));
        }
        return out;
    }
}
