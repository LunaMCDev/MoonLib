package com.example.template.module;

import com.example.template.TemplatePlugin;
import com.example.template.data.PlayerData;
import dev.lunamc.moonlib.display.Bar;
import dev.lunamc.moonlib.module.MoonModule;
import dev.lunamc.moonlib.scheduler.Tasks;
import dev.lunamc.moonlib.util.RichText;
import dev.lunamc.moonlib.util.Titles;
import dev.lunamc.moonlib.version.Sounds;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Greets players with a title, a sound and a short boss bar countdown, and announces first joins.
 * Depends on {@link StatsModule} so join counts are already updated when it runs.
 */
public final class WelcomeModule extends MoonModule {

    private final TemplatePlugin plugin;

    public WelcomeModule(TemplatePlugin plugin) {
        super("welcome", StatsModule.class);
        this.plugin = plugin;
    }

    @Override
    protected void enable() {
        on(PlayerJoinEvent.class, event -> {
            Player player = event.getPlayer();
            PlayerData data = plugin.players().get(player);
            // Wait a moment so the client has finished loading the world.
            Tasks.entityLater(player, 20, () -> greet(player, data));
            if (data.isNew()) {
                plugin.messages().broadcast("welcome.first-join", "player", player.getName());
            }
        });
    }

    private void greet(Player player, PlayerData data) {
        if (!player.isOnline()) {
            return;
        }
        Titles.send(player,
                RichText.parse(plugin.settings().getString("welcome.title", "<gradient:#7F7FD5:#91EAE4><bold>WELCOME</bold></gradient>")),
                RichText.parse(plugin.settings().getString("welcome.subtitle", "<gray>Good to see you, <white><player>"),
                        "player", player.getName()));
        Sounds.play(player, "ENTITY_PLAYER_LEVELUP");
        plugin.messages().send(player, "welcome.message", "player", player.getName(), "joins", data.joins);
        int seconds = plugin.settings().getInt("welcome.bossbar-seconds", 10);
        if (seconds > 0) {
            Bar.countdown(player, seconds, left -> RichText.parse("<light_purple>Welcome to the server <gray>(" + left + "s)"),
                    Bar.Color.PURPLE);
        }
    }
}
