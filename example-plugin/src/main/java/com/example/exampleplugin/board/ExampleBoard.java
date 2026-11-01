package com.example.exampleplugin.board;

import com.example.exampleplugin.ExamplePlugin;
import dev.lunamc.moonlib.hook.VaultEconomy;
import dev.lunamc.moonlib.scoreboard.BoardProvider;
import dev.lunamc.moonlib.util.Numbers;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

/**
 * The sidebar shown to every player.
 */
public final class ExampleBoard implements BoardProvider {

    private final ExamplePlugin plugin;

    public ExampleBoard(ExamplePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String title(Player player) {
        return plugin.settings().getString("scoreboard.title", "&d&lEXAMPLE");
    }

    @Override
    public List<String> lines(Player player) {
        return Arrays.asList(
                "&7" + new SimpleDateFormat("dd/MM/yy").format(new Date()),
                "",
                "&fName: &d" + player.getName(),
                "&fBalance: &a$" + Numbers.compact(VaultEconomy.balance(player)),
                "&fOnline: &e" + Bukkit.getOnlinePlayers().size(),
                "",
                "&#B388FFplay.example.net");
    }
}
