package com.example.exampleplugin.command;

import com.example.exampleplugin.ExamplePlugin;
import com.example.exampleplugin.menu.PlayersMenu;
import com.example.exampleplugin.menu.ShopMenu;
import dev.lunamc.moonlib.command.ArgumentTypes;
import dev.lunamc.moonlib.command.CommandContext;
import dev.lunamc.moonlib.command.MoonCommand;
import dev.lunamc.moonlib.input.ChatInput;
import org.bukkit.entity.Player;

/**
 * {@code /example <shop|players|nick|reload|give>} - a class-based command with subcommands.
 */
public final class ExampleCommand extends MoonCommand {

    private final ExamplePlugin plugin;

    public ExampleCommand(ExamplePlugin plugin) {
        super("example", "ex");
        this.plugin = plugin;
        description("Example plugin commands");

        subcommand(new ShopSub());
        subcommand(new PlayersSub());
        subcommand(new NickSub());
        subcommand(new ReloadSub());
        subcommand(new GiveTokensSub());
    }

    @Override
    protected void execute(CommandContext ctx) {
        // Shown for "/example" with no or an unknown subcommand.
        plugin.messages().send(ctx.sender(), "help");
    }

    private final class ShopSub extends MoonCommand {
        ShopSub() {
            super("shop", "store");
            description("Open the shop");
            playerOnly();
        }

        @Override
        protected void execute(CommandContext ctx) {
            new ShopMenu(plugin, ctx.player()).open();
        }
    }

    private final class PlayersSub extends MoonCommand {
        PlayersSub() {
            super("players");
            description("Browse online players");
            playerOnly();
        }

        @Override
        protected void execute(CommandContext ctx) {
            new PlayersMenu(ctx.player()).open();
        }
    }

    private final class NickSub extends MoonCommand {
        NickSub() {
            super("nick", "nickname");
            description("Change your nickname");
            playerOnly();
        }

        @Override
        protected void execute(CommandContext ctx) {
            Player player = ctx.player();
            ChatInput.ask(player)
                    .prompt(plugin.messages().get("nickname.prompt"))
                    .validate(name -> name.matches("[A-Za-z0-9_]{3,16}"), "&cUse 3-16 letters, numbers or _.")
                    .timeout(30)
                    .onInput(name -> {
                        player.setDisplayName(name);
                        plugin.messages().send(player, "nickname.set", "name", name);
                    })
                    .start();
        }
    }

    private final class ReloadSub extends MoonCommand {
        ReloadSub() {
            super("reload");
            description("Reload the configuration");
            permission("example.admin");
        }

        @Override
        protected void execute(CommandContext ctx) {
            plugin.reloadEverything();
            plugin.messages().send(ctx.sender(), "reloaded");
        }
    }

    /** Demonstrates typed arguments: /example give <player> <amount> [reason...] */
    private final class GiveTokensSub extends MoonCommand {
        GiveTokensSub() {
            super("give");
            description("Give tokens to a player");
            permission("example.admin");
            argument("target", ArgumentTypes.PLAYER);
            argument("amount", ArgumentTypes.integer(1, 1_000_000));
            optionalArgument("reason", ArgumentTypes.GREEDY_STRING, "no reason given");
        }

        @Override
        protected void execute(CommandContext ctx) {
            Player target = ctx.get("target");
            int amount = ctx.get("amount");
            String reason = ctx.get("reason");
            ctx.check(!target.equals(ctx.sender()), "&cYou can't give tokens to yourself.");
            ctx.reply("&aGave &e{amount} &atokens to &f{player} &7({reason})",
                    "amount", amount, "player", target.getName(), "reason", reason);
        }
    }
}
