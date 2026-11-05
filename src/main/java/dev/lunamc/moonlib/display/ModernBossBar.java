package dev.lunamc.moonlib.display;

import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;

/** The 1.9+ boss bar backend. Only loaded on servers that have the API. */
final class ModernBossBar implements Bar.Backend {

    private final BossBar bar;

    ModernBossBar(String title, Bar.Color color, Bar.Style style) {
        this.bar = Bukkit.createBossBar(title, BarColor.valueOf(color.name()), BarStyle.valueOf(style.name()));
    }

    @Override
    public void title(String title) {
        bar.setTitle(title);
    }

    @Override
    public void progress(double progress) {
        bar.setProgress(progress);
    }

    @Override
    public void color(Bar.Color color) {
        bar.setColor(BarColor.valueOf(color.name()));
    }

    @Override
    public void style(Bar.Style style) {
        bar.setStyle(BarStyle.valueOf(style.name()));
    }

    @Override
    public void show(Player player) {
        bar.addPlayer(player);
    }

    @Override
    public void hide(Player player) {
        bar.removePlayer(player);
    }

    @Override
    public void hideAll() {
        bar.removeAll();
    }
}
