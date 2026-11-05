package com.example.template.dev;

import com.example.template.TemplatePlugin;
import dev.lunamc.moonlib.scheduler.Tasks;
import dev.lunamc.moonlib.util.FileWatcher;
import dev.lunamc.moonlib.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Development helper, active only on the dev server (the "Dev Server" run configuration passes
 * {@code -Dmoonlib.dev=true}). Never runs on a real server.
 *
 * <p>When you press Build in IntelliJ, the debugger hot-swaps your changed classes into the running
 * server. Code that runs on every event or command uses the new version straight away, but code
 * that already ran (a module's {@code enable()}) would not. So after each build this re-enables
 * every module with the new code and tells you in chat.</p>
 *
 * <p>Brand-new classes cannot be hot-swapped into a plugin jar; for those it tells you to restart
 * the dev server instead.</p>
 */
public final class DevMode {

    private static final long SETTLE_TICKS = 30;   // let IntelliJ finish hot-swapping first

    private final TemplatePlugin plugin;
    private final Path classes;
    private final Set<String> knownClasses = new HashSet<>();
    private final Set<String> changed = new LinkedHashSet<>();
    private boolean scheduled;

    private DevMode(TemplatePlugin plugin, Path classes) {
        this.plugin = plugin;
        this.classes = classes;
    }

    /** Starts watching the compiler output if this is the dev server. */
    public static void startIfEnabled(TemplatePlugin plugin) {
        if (!Boolean.getBoolean("moonlib.dev")) {
            return;
        }
        String folder = System.getProperty("moonlib.dev.classes", "");
        if (folder.isEmpty() || !new File(folder).isDirectory()) {
            plugin.getLogger().warning("[Dev] moonlib.dev.classes does not point at target/classes; auto-reload is off");
            return;
        }
        DevMode dev = new DevMode(plugin, new File(folder).toPath());
        dev.indexClasses();
        FileWatcher.watchTree(plugin, new File(folder), dev::onChange);
        plugin.getLogger().info("[Dev] Watching " + folder + ": build in IntelliJ (Ctrl+F9) to reload your code");
    }

    private void indexClasses() {
        try (Stream<Path> files = Files.walk(classes)) {
            files.filter(p -> p.toString().endsWith(".class")).forEach(p -> knownClasses.add(classes.relativize(p).toString()));
        } catch (IOException e) {
            plugin.getLogger().warning("[Dev] Could not list " + classes + ": " + e.getMessage());
        }
    }

    private void onChange(File file) {
        if (!file.getName().endsWith(".class")) {
            return;
        }
        changed.add(classes.relativize(file.toPath()).toString());
        if (!scheduled) {
            scheduled = true;
            Tasks.later(SETTLE_TICKS, this::apply);
        }
    }

    private void apply() {
        scheduled = false;
        Set<String> added = new LinkedHashSet<>();
        for (String path : changed) {
            if (!knownClasses.contains(path) && !path.contains("$")) {
                added.add(path.replace(File.separatorChar, '.').replace(".class", ""));
            }
        }
        int count = changed.size();
        changed.clear();
        if (!added.isEmpty()) {
            notifyDevelopers("&e[Dev] New class " + String.join(", ", added)
                    + " needs a restart of the Dev Server (it is not in the running jar yet).");
            indexClasses();
        }
        plugin.modules().reloadAll();
        notifyDevelopers("&d[Dev] &7" + count + " class file(s) rebuilt: modules reloaded with the new code.");
    }

    private void notifyDevelopers(String message) {
        plugin.getLogger().info(Text.strip(Text.color(message)));
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.hasPermission("template.admin")) {
                player.sendMessage(Text.color(message));
            }
        }
    }
}
