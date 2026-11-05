package com.lunamcdev.moonlib.command;

import com.lunamcdev.moonlib.MoonLib;
import com.lunamcdev.moonlib.scheduler.Tasks;
import com.lunamcdev.moonlib.util.Log;
import com.lunamcdev.moonlib.version.Reflection;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.PluginIdentifiableCommand;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Registers {@link MoonCommand}s with the server.
 *
 * <p>Two styles are supported:</p>
 * <ul>
 *     <li>{@link #register(Plugin, MoonCommand)} injects the command straight into the server's command
 *     map. Nothing needs to be declared in {@code plugin.yml}, and commands are cleanly removed again
 *     on disable, which makes {@code /reload} and plugin managers behave.</li>
 *     <li>{@link #bind(PluginCommand, MoonCommand)} attaches a command to an entry that <i>is</i>
 *     declared in {@code plugin.yml}, if you prefer that style.</li>
 * </ul>
 *
 * <p>On 1.13+ the command tree is re-sent to online players after late registrations, so new
 * commands get tab completion without a relog.</p>
 *
 * <p>Each command remembers which plugin registered it. It runs as that plugin (so its tasks, menus
 * and messages belong to it) and is removed only when that plugin disables. If two plugins register
 * the same command name, the second one stays reachable as {@code /plugin:name} and a warning is
 * logged; nothing throws.</p>
 */
public final class CommandRegistry {

    private static final List<RegisteredCommand> REGISTERED = new CopyOnWriteArrayList<>();
    private static volatile boolean syncScheduled;

    private CommandRegistry() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Registers a command in the server's command map under {@code name}, every alias, and
     * {@code plugin:name} / {@code plugin:alias}.
     *
     * @param plugin  the owning plugin (used as the fallback prefix)
     * @param command the command
     * @return the Bukkit command that was registered
     * @throws IllegalStateException if the command map cannot be accessed
     */
    public static Command register(Plugin plugin, MoonCommand command) {
        CommandMap map = commandMap();
        RegisteredCommand bukkitCommand = new RegisteredCommand(plugin, command);
        String prefix = plugin.getName().toLowerCase(java.util.Locale.ROOT).replace(' ', '_');
        boolean primaryRegistered = map.register(prefix, bukkitCommand);
        if (!primaryRegistered) {
            Log.warn("/{} is already taken by another plugin; it is available as /{}:{}",
                    command.name(), prefix, command.name());
        }
        REGISTERED.add(bukkitCommand);
        scheduleSync(plugin);
        return bukkitCommand;
    }

    /**
     * Attaches a command to a {@code plugin.yml} declared command as its executor and tab completer.
     *
     * @param pluginCommand the declared command, from {@code getCommand("name")}
     * @param command       the implementation
     */
    public static void bind(PluginCommand pluginCommand, MoonCommand command) {
        if (pluginCommand == null) {
            throw new IllegalArgumentException("Command /" + command.name() + " is not declared in plugin.yml");
        }
        Plugin owner = pluginCommand.getPlugin();
        pluginCommand.setExecutor((sender, cmd, label, args) -> {
            MoonLib.runAs(owner, () -> command.dispatch(sender, label, args));
            return true;
        });
        pluginCommand.setTabCompleter((sender, cmd, alias, args) ->
                MoonLib.callAs(owner, () -> command.complete(sender, alias, args)));
    }

    /**
     * Unregisters one command previously registered with {@link #register(Plugin, MoonCommand)}.
     *
     * @param command the command
     */
    public static void unregister(MoonCommand command) {
        CommandMap map = commandMap();
        Map<String, Command> known = knownCommands(map);
        Plugin owner = null;
        for (RegisteredCommand registered : REGISTERED) {
            if (registered.command == command) {
                removeFrom(known, registered);
                registered.unregister(map);
                REGISTERED.remove(registered);
                owner = registered.plugin;
            }
        }
        if (owner != null) {
            scheduleSync(owner);
        }
    }

    /**
     * Unregisters every command a plugin registered through this class. Commands of other plugins
     * sharing MoonLib are left alone. Called by {@code MoonLib.shutdown(plugin)}.
     *
     * @param plugin the plugin whose commands to remove
     */
    public static void unregisterAll(Plugin plugin) {
        List<RegisteredCommand> owned = new ArrayList<>();
        for (RegisteredCommand registered : REGISTERED) {
            if (registered.plugin == plugin) {
                owned.add(registered);
            }
        }
        if (owned.isEmpty()) {
            return;
        }
        CommandMap map = commandMap();
        Map<String, Command> known = knownCommands(map);
        for (RegisteredCommand registered : owned) {
            removeFrom(known, registered);
            registered.unregister(map);
        }
        REGISTERED.removeAll(owned);
        syncCommands();
    }

    /**
     * Returns the commands currently registered through this class, by every plugin.
     *
     * @return an unmodifiable snapshot
     */
    public static List<MoonCommand> registered() {
        List<MoonCommand> list = new ArrayList<>();
        for (RegisteredCommand registered : REGISTERED) {
            list.add(registered.command);
        }
        return Collections.unmodifiableList(list);
    }

    /**
     * Returns the commands a specific plugin registered through this class.
     *
     * @param plugin the plugin
     * @return an unmodifiable snapshot
     */
    public static List<MoonCommand> registered(Plugin plugin) {
        List<MoonCommand> list = new ArrayList<>();
        for (RegisteredCommand registered : REGISTERED) {
            if (registered.plugin == plugin) {
                list.add(registered.command);
            }
        }
        return Collections.unmodifiableList(list);
    }

    /**
     * Returns the server's command map.
     *
     * @return the command map
     * @throws IllegalStateException if it cannot be accessed
     */
    public static CommandMap commandMap() {
        Object server = Bukkit.getServer();
        Method getter = Reflection.method(server.getClass(), "getCommandMap");
        if (getter != null) {
            return (CommandMap) Reflection.invoke(getter, server);
        }
        Field field = Reflection.field(server.getClass(), "commandMap");
        if (field != null) {
            return (CommandMap) Reflection.get(field, server);
        }
        throw new IllegalStateException("Unable to access the server command map");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Command> knownCommands(CommandMap map) {
        Method getter = Reflection.method(map.getClass(), "getKnownCommands");
        if (getter != null) {
            return (Map<String, Command>) Reflection.invoke(getter, map);
        }
        Field field = Reflection.field(map.getClass(), "knownCommands");
        if (field != null) {
            return (Map<String, Command>) Reflection.get(field, map);
        }
        return null;
    }

    private static void removeFrom(Map<String, Command> known, Command command) {
        if (known == null) {
            return;
        }
        Iterator<Map.Entry<String, Command>> iterator = known.entrySet().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().getValue() == command) {
                iterator.remove();
            }
        }
    }

    private static void scheduleSync(Plugin owner) {
        if (syncScheduled || Bukkit.getOnlinePlayers().isEmpty()) {
            return;
        }
        syncScheduled = true;
        try {
            MoonLib.runAs(owner, () -> Tasks.sync(() -> {
                syncScheduled = false;
                syncCommands();
            }));
        } catch (IllegalStateException e) {
            syncScheduled = false; // MoonLib not initialised; players will get the commands on next join
        }
    }

    private static void syncCommands() {
        Object server = Bukkit.getServer();
        Method sync = Reflection.method(server.getClass(), "syncCommands");
        if (sync != null) {
            try {
                sync.invoke(server);
            } catch (ReflectiveOperationException | RuntimeException e) {
                Log.debug("Could not sync commands: {}", e);
            }
        }
    }

    /** The Bukkit-facing wrapper around a {@link MoonCommand}. */
    private static final class RegisteredCommand extends Command implements PluginIdentifiableCommand {
        private final Plugin plugin;
        private final MoonCommand command;

        RegisteredCommand(Plugin plugin, MoonCommand command) {
            super(command.name(), command.description(), command.usage(command.name()),
                    new ArrayList<>(command.aliases()));
            this.plugin = plugin;
            this.command = command;
            if (command.permission() != null && !command.permission().isEmpty()) {
                setPermission(command.permission());
            }
        }

        @Override
        public boolean execute(CommandSender sender, String label, String[] args) {
            if (!plugin.isEnabled()) {
                return false;
            }
            MoonLib.runAs(plugin, () -> command.dispatch(sender, label.toLowerCase(java.util.Locale.ROOT), args));
            return true;
        }

        @Override
        public List<String> tabComplete(CommandSender sender, String alias, String[] args) {
            if (!plugin.isEnabled()) {
                return Collections.emptyList();
            }
            return MoonLib.callAs(plugin, () -> command.complete(sender, alias, args));
        }

        @Override
        public Plugin getPlugin() {
            return plugin;
        }
    }
}
