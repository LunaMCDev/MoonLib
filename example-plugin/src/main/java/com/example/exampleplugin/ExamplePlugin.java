package com.example.exampleplugin;

import com.example.exampleplugin.board.ExampleBoard;
import com.example.exampleplugin.command.ExampleCommand;
import dev.lunamc.moonlib.MoonPlugin;
import dev.lunamc.moonlib.command.ArgumentTypes;
import dev.lunamc.moonlib.command.CommandBuilder;
import dev.lunamc.moonlib.command.CommandMessages;
import dev.lunamc.moonlib.config.ConfigFile;
import dev.lunamc.moonlib.config.Messages;
import dev.lunamc.moonlib.event.Events;
import dev.lunamc.moonlib.scoreboard.BoardManager;
import dev.lunamc.moonlib.util.Log;
import dev.lunamc.moonlib.util.Players;
import dev.lunamc.moonlib.util.Titles;
import dev.lunamc.moonlib.version.Sounds;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.concurrent.TimeUnit;

/**
 * Example plugin showing the typical structure of a MoonLib plugin.
 */
public final class ExamplePlugin extends MoonPlugin {

    private ConfigFile settings;
    private Messages messages;
    private BoardManager boards;

    @Override
    protected void enable() {
        // ---- configuration
        settings = loadConfig("config.yml");
        messages = new Messages(this);
        CommandMessages.load(messages.file().getSection("commands"));
        Log.setDebug(settings.getBoolean("debug", false));

        // ---- commands (no plugin.yml entries needed)
        registerCommands(new ExampleCommand(this));

        CommandBuilder.create("heal")
                .description("Heal yourself or another player")
                .permission("example.heal")
                .optionalArgument("target", ArgumentTypes.PLAYER)
                .cooldown(30, TimeUnit.SECONDS, "example.heal.bypass")
                .executes(ctx -> {
                    Player target = ctx.has("target") ? ctx.<Player>get("target") : ctx.player();
                    Players.heal(target);
                    Sounds.play(target, "ENTITY_PLAYER_LEVELUP");
                    messages.send(ctx.sender(), "healed", "player", target.getName());
                })
                .register();

        // ---- events, without a listener class
        Events.listen(PlayerJoinEvent.class, event -> {
            Player player = event.getPlayer();
            Titles.send(player, "&#B388FF&lWelcome", "&7Enjoy your stay, &f" + player.getName());
        });

        // ---- scoreboard
        if (settings.getBoolean("scoreboard.enabled", true)) {
            boards = new BoardManager(new ExampleBoard(this), settings.getLong("scoreboard.update-ticks", 20))
                    .usePlaceholderApi(true)
                    .start();
        }
    }

    @Override
    protected void disable() {
        // MoonLib stops boards, closes menus and unregisters commands for us after this returns.
        Log.info("Goodbye!");
    }

    /** Reloads configuration and messages. */
    public void reloadEverything() {
        settings.reload();
        messages.reload();
        CommandMessages.load(messages.file().getSection("commands"));
        Log.setDebug(settings.getBoolean("debug", false));
    }

    /**
     * Returns the main settings file.
     *
     * @return config.yml
     */
    public ConfigFile settings() {
        return settings;
    }

    /**
     * Returns the messages.
     *
     * @return messages.yml
     */
    public Messages messages() {
        return messages;
    }

    /**
     * Returns the scoreboard manager.
     *
     * @return the manager, or {@code null} if scoreboards are disabled
     */
    public BoardManager boards() {
        return boards;
    }
}
