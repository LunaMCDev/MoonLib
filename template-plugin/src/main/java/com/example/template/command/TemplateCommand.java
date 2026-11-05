package com.example.template.command;

import com.example.template.TemplatePlugin;
import com.example.template.data.PlayerData;
import com.example.template.menu.ModulesMenu;
import dev.lunamc.moonlib.command.CommandError;
import dev.lunamc.moonlib.command.annotation.Arg;
import dev.lunamc.moonlib.command.annotation.Command;
import dev.lunamc.moonlib.command.annotation.Execute;
import dev.lunamc.moonlib.command.annotation.Greedy;
import dev.lunamc.moonlib.command.annotation.Subcommand;
import dev.lunamc.moonlib.command.annotation.Suggest;
import dev.lunamc.moonlib.display.Hologram;
import dev.lunamc.moonlib.module.MoonModule;
import dev.lunamc.moonlib.scheduler.Tasks;
import dev.lunamc.moonlib.util.Durations;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code /template}: one class, one method per subcommand. MoonLib builds usage lines, help pages,
 * permission checks and tab completion from the annotations.
 */
@Command(value = "template", aliases = "tpl", permission = "template.use", description = "MoonTemplate commands")
public final class TemplateCommand {

    private final TemplatePlugin plugin;

    public TemplateCommand(TemplatePlugin plugin) {
        this.plugin = plugin;
    }

    @Execute
    public String info(CommandSender sender) {
        return "&d" + plugin.getName() + " &7v" + plugin.getDescription().getVersion()
                + " &8| &7try &f/template help";
    }

    @Subcommand(value = "stats", description = "Show your stats or another player's")
    public void stats(CommandSender sender, @Arg(value = "player", optional = true) Player target) {
        Player who = target != null ? target : asPlayer(sender);
        PlayerData data = plugin.players().get(who);
        plugin.messages().send(sender, "stats", "player", who.getName(), "joins", data.joins,
                "blocks", data.blocksBroken,
                "since", Durations.format(System.currentTimeMillis() - data.firstJoin));
    }

    @Subcommand(value = "reload", permission = "template.admin", description = "Reload config and modules")
    public String reload(CommandSender sender, @Arg(value = "module", optional = true) @Suggest("moduleIds") String module) {
        if (module == null) {
            plugin.reloadAll();
            return plugin.messages().get("reloaded");
        }
        if (plugin.modules().byId(module) == null) {
            throw new CommandError(plugin.messages().get("unknown-module", "module", module));
        }
        boolean enabled = plugin.modules().reload(module);
        return plugin.messages().get(enabled ? "module-reloaded" : "module-failed", "module", module);
    }

    @Subcommand(value = "modules", permission = "template.admin", description = "Manage modules in a menu")
    public void modules(Player player) {
        new ModulesMenu(player, plugin).open();
    }

    @Subcommand(value = "hologram", permission = "template.admin", description = "Spawn a test hologram for a minute")
    public String hologram(Player player, @Greedy @Arg("text") String text) {
        Hologram hologram = Hologram.create(player.getLocation().add(0, 2.5, 0), text.split("\\|"));
        Tasks.atLater(hologram.location(), 20 * 60, hologram::delete);
        return "&aHologram spawned. Use | for line breaks. It disappears in 60s.";
    }

    List<String> moduleIds(CommandSender sender) {
        List<String> ids = new ArrayList<>();
        for (MoonModule module : plugin.modules().modules()) {
            ids.add(module.id());
        }
        return ids;
    }

    private static Player asPlayer(CommandSender sender) {
        if (!(sender instanceof Player)) {
            throw new CommandError("&cConsole must name a player: /template stats <player>");
        }
        return (Player) sender;
    }
}
