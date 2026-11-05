package dev.lunamc.moonlib.standalone;

import dev.lunamc.moonlib.MoonContext;
import dev.lunamc.moonlib.MoonLib;
import dev.lunamc.moonlib.command.CommandBuilder;
import dev.lunamc.moonlib.command.CommandRegistry;
import dev.lunamc.moonlib.integration.MoonLink;
import dev.lunamc.moonlib.util.Durations;
import dev.lunamc.moonlib.util.Titles;
import dev.lunamc.moonlib.version.ServerSoftware;
import dev.lunamc.moonlib.version.ServerVersion;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.Set;

/**
 * The standalone MoonLib plugin. It initialises MoonLib so the library's shared listeners have a
 * permanent host, and adds a {@code /moonlib} diagnostics command.
 *
 * <p>Plugins using it declare {@code depend: [MoonLib]}, use MoonLib with {@code provided} scope, and
 * still call {@code MoonLib.init(this)} (or extend {@code MoonPlugin}) like any other plugin.</p>
 */
public final class MoonLibStandalone extends JavaPlugin {

    @Override
    public void onEnable() {
        MoonLib.init(this);
        CommandRegistry.register(this, CommandBuilder.create("moonlib")
                .permission("moonlib.admin")
                .description("Show which plugins use MoonLib")
                .executes(ctx -> {
                    ctx.reply("&d&lMoonLib &f{version} &7on {software} {mc} &8(titles: {titles})",
                            "version", MoonLib.version(),
                            "software", ServerSoftware.current().displayName(),
                            "mc", ServerVersion.current(),
                            "titles", Titles.implementationName());
                    ctx.reply("&7Listeners hosted by &f{host}", "host", MoonLib.host() == null ? "-" : MoonLib.host().getName());
                    for (Plugin plugin : MoonLib.plugins()) {
                        if (plugin == this) {
                            continue;
                        }
                        MoonContext context = MoonLib.context(plugin);
                        long uptime = System.currentTimeMillis() - context.initialisedAt();
                        ctx.reply(" &8- &f{name} &7{bundled} &8(for {uptime}, {commands} commands)",
                                "name", plugin.getName(),
                                "bundled", context.bundledVersion() == null ? "" : "bundles " + context.bundledVersion(),
                                "uptime", Durations.format(uptime, 2),
                                "commands", CommandRegistry.registered(plugin).size());
                    }
                    for (Map.Entry<String, Set<String>> entry : MoonLink.capabilities().entrySet()) {
                        if (!entry.getValue().isEmpty()) {
                            ctx.reply(" &8- &d{name} &7offers: &f{caps}", "name", entry.getKey(),
                                    "caps", String.join(", ", entry.getValue()));
                        }
                    }
                })
                .build());
        getLogger().info("MoonLib " + MoonLib.version() + " is ready for dependent plugins.");
    }

    @Override
    public void onDisable() {
        MoonLib.shutdown(this);
    }
}
