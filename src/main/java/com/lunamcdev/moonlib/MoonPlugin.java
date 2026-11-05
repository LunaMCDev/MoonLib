package com.lunamcdev.moonlib;

import com.lunamcdev.moonlib.command.CommandRegistry;
import com.lunamcdev.moonlib.command.MoonCommand;
import com.lunamcdev.moonlib.config.ConfigFile;
import com.lunamcdev.moonlib.config.DataFile;
import com.lunamcdev.moonlib.config.DataStore;
import com.lunamcdev.moonlib.config.JsonFile;
import com.lunamcdev.moonlib.config.YamlFile;
import com.lunamcdev.moonlib.util.Log;
import com.lunamcdev.moonlib.version.ServerSoftware;
import com.lunamcdev.moonlib.version.ServerVersion;
import org.bukkit.Bukkit;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Optional;
import java.util.logging.Level;

/**
 * A {@link JavaPlugin} base class that wires MoonLib into the plugin lifecycle.
 *
 * <p>Extend this instead of {@code JavaPlugin} and override {@link #enable()}, {@link #disable()} and
 * optionally {@link #load()}. The Bukkit lifecycle methods are final so MoonLib can guarantee it is
 * initialised before your code runs and torn down after it.</p>
 *
 * <pre>{@code
 * public final class MoonPrison extends MoonPlugin {
 *
 *     @Override
 *     protected void enable() {
 *         saveDefaultConfig();
 *         registerCommands(new MineCommand(), new RankupCommand());
 *         registerListeners(new BlockBreakListener(this));
 *     }
 *
 *     @Override
 *     protected void disable() {
 *         // save data, close connections...
 *     }
 * }
 * }</pre>
 *
 * <p>Any number of plugins can extend {@code MoonPlugin} on the same server, whether each one relocates
 * MoonLib or they all share one copy.</p>
 *
 * <p>If {@link #enable()} throws, the error is logged with a clear message and the plugin disables
 * itself instead of being left half-enabled. In that case {@link #disable()} is not called.</p>
 */
public abstract class MoonPlugin extends JavaPlugin {

    private boolean enabledSuccessfully;

    /** Creates the plugin. Called by the server's plugin loader. */
    protected MoonPlugin() {
    }

    @Override
    public final void onLoad() {
        MoonLib.runAs(this, () -> {
            try {
                load();
            } catch (Throwable t) {
                getLogger().log(Level.SEVERE, "Error while loading " + getName(), t);
            }
        });
    }

    @Override
    public final void onEnable() {
        MoonLib.runAs(this, this::enableInternal);
    }

    @Override
    public final void onDisable() {
        MoonLib.runAs(this, this::disableInternal);
    }

    private void enableInternal() {
        long start = System.nanoTime();
        enabledSuccessfully = false;
        MoonLib.init(this);
        try {
            enable();
            enabledSuccessfully = true;
        } catch (Throwable t) {
            getLogger().log(Level.SEVERE, "Failed to enable " + getName() + " v" + getDescription().getVersion()
                    + ". The plugin will now disable itself.", t);
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        long tookMs = (System.nanoTime() - start) / 1_000_000L;
        Log.info("Enabled {} v{} on {} {} in {}ms", getName(), getDescription().getVersion(),
                ServerSoftware.current().displayName(), ServerVersion.current(), tookMs);
    }

    private void disableInternal() {
        try {
            if (enabledSuccessfully) {
                disable();
            }
        } catch (Throwable t) {
            getLogger().log(Level.SEVERE, "Error while disabling " + getName(), t);
        } finally {
            enabledSuccessfully = false;
            MoonLib.shutdown(this);
        }
    }

    /**
     * Called when the plugin is loaded, before worlds are loaded and before any plugin is enabled.
     * MoonLib is <b>not</b> initialised yet at this point. Default implementation does nothing.
     */
    protected void load() {
    }

    /**
     * Called when the plugin is enabled. MoonLib is fully initialised. Default implementation does nothing.
     *
     * @throws Exception any exception aborts enabling and disables the plugin cleanly
     */
    protected void enable() throws Exception {
    }

    /**
     * Called when the plugin is disabled, before MoonLib is shut down, so menus, tasks and commands are
     * still available. Default implementation does nothing.
     *
     * @throws Exception any exception is logged
     */
    protected void disable() throws Exception {
    }

    /**
     * Registers one or more event listeners for this plugin.
     *
     * @param listeners the listeners to register
     */
    protected final void registerListeners(Listener... listeners) {
        for (Listener listener : listeners) {
            getServer().getPluginManager().registerEvents(listener, this);
        }
    }

    /**
     * Registers one or more commands directly into the server's command map. The commands do
     * <b>not</b> need to be declared in {@code plugin.yml}.
     *
     * @param commands the commands to register
     * @see CommandRegistry#register(Plugin, MoonCommand)
     */
    protected final void registerCommands(MoonCommand... commands) {
        for (MoonCommand command : commands) {
            CommandRegistry.register(this, command);
        }
    }

    /**
     * Registers commands that may be a mix of {@link MoonCommand}s and annotated handlers (instances
     * of classes marked {@link com.lunamcdev.moonlib.command.annotation.Command @Command}).
     *
     * @param commands the commands and handlers to register
     * @see com.lunamcdev.moonlib.command.annotation.AnnotatedCommands
     * @since 1.2.0
     */
    protected final void registerCommands(Object... commands) {
        for (Object command : commands) {
            if (command instanceof MoonCommand) {
                CommandRegistry.register(this, (MoonCommand) command);
            } else {
                com.lunamcdev.moonlib.command.annotation.AnnotatedCommands.register(this, command);
            }
        }
    }

    /**
     * Loads (creating it from the jar's resource if missing) a YAML file in the data folder.
     *
     * @param name the file name relative to the data folder, e.g. {@code "mines.yml"} or {@code "data/ranks.yml"}
     * @return the loaded file
     */
    protected final ConfigFile loadConfig(String name) {
        return new ConfigFile(this, name);
    }

    /**
     * Loads (creating it if needed) a YAML file in the data folder, with defaults from the jar.
     *
     * @param name e.g. {@code "mines.yml"}
     * @return the loaded file
     */
    protected final YamlFile loadYaml(String name) {
        return DataFile.yaml(this, name);
    }

    /**
     * Loads (creating it if needed) a JSON file in the data folder, with defaults from the jar.
     *
     * @param name e.g. {@code "data/stats.json"}
     * @return the loaded file
     */
    protected final JsonFile loadJson(String name) {
        return DataFile.json(this, name);
    }

    /**
     * Creates a folder-backed store with one JSON file per key, e.g. per-player data.
     *
     * @param folder the folder inside the data folder, e.g. {@code "players"}
     * @param type   the stored class
     * @param <T>    the stored type
     * @return the store
     */
    protected final <T> DataStore<T> dataStore(String folder, Class<T> type) {
        return DataStore.json(this, folder, type);
    }

    /**
     * Looks up a service registered with Bukkit's {@link org.bukkit.plugin.ServicesManager}, for example
     * a Vault economy or permission provider.
     *
     * @param type the service interface
     * @param <T>  the service type
     * @return the highest priority provider, or empty if none is registered
     */
    protected final <T> Optional<T> service(Class<T> type) {
        RegisteredServiceProvider<T> registration = getServer().getServicesManager().getRegistration(type);
        return registration == null ? Optional.<T>empty() : Optional.ofNullable(registration.getProvider());
    }

    /**
     * Checks whether another plugin is installed and enabled.
     *
     * @param name the plugin name as written in its {@code plugin.yml}
     * @return {@code true} if the plugin is present and enabled
     */
    protected final boolean isPluginEnabled(String name) {
        Plugin other = getServer().getPluginManager().getPlugin(name);
        return other != null && other.isEnabled();
    }
}
