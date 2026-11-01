package dev.lunamc.moonlib;

import org.bukkit.plugin.Plugin;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The per-plugin state MoonLib keeps for every plugin that called {@link MoonLib#init(Plugin)}.
 *
 * <p>When several plugins share one copy of MoonLib (see {@link MoonLib#mode()}), everything that
 * belongs to a plugin (its logger, debug flag, command message overrides, tasks, menus, commands,
 * scoreboards and chat prompts) is tracked per context, so plugins never see or break each
 * other's state.</p>
 *
 * <pre>{@code
 * MoonContext ctx = MoonLib.context(plugin);
 * ctx.bundledVersion();   // the MoonLib version this plugin was built with, if it shaded one
 * }</pre>
 */
public final class MoonContext {

    private final Plugin plugin;
    private final ClassLoader classLoader;
    private final String bundledVersion;
    private final long initialisedAt = System.currentTimeMillis();
    private final Map<String, String> commandMessages = new ConcurrentHashMap<>();
    private volatile boolean debug;

    MoonContext(Plugin plugin, String bundledVersion) {
        this.plugin = plugin;
        this.classLoader = plugin.getClass().getClassLoader();
        this.bundledVersion = bundledVersion;
    }

    /**
     * Returns the plugin this context belongs to.
     *
     * @return the plugin
     */
    public Plugin plugin() {
        return plugin;
    }

    /**
     * Returns the class loader of the plugin's main class, used to work out which plugin is calling.
     *
     * @return the class loader
     */
    public ClassLoader classLoader() {
        return classLoader;
    }

    /**
     * Returns the MoonLib version inside this plugin's own jar.
     *
     * @return the version, or {@code null} if the plugin does not bundle MoonLib (standalone mode)
     */
    public String bundledVersion() {
        return bundledVersion;
    }

    /**
     * Returns when {@link MoonLib#init(Plugin)} was called for this plugin.
     *
     * @return epoch milliseconds
     */
    public long initialisedAt() {
        return initialisedAt;
    }

    /**
     * Returns whether {@link dev.lunamc.moonlib.util.Log#debug} output is enabled for this plugin.
     *
     * @return {@code true} if debug output is enabled
     */
    public boolean isDebug() {
        return debug;
    }

    /**
     * Enables or disables debug output for this plugin.
     *
     * @param enabled {@code true} to enable
     */
    public void setDebug(boolean enabled) {
        this.debug = enabled;
    }

    /**
     * Returns this plugin's overrides of MoonLib's built-in command messages, keyed like the config
     * keys of {@link dev.lunamc.moonlib.command.CommandMessages#load}.
     *
     * @return the live, mutable override map
     */
    public Map<String, String> commandMessages() {
        return commandMessages;
    }

    /**
     * Returns an unmodifiable view of the command message overrides.
     *
     * @return the overrides
     */
    public Map<String, String> commandMessagesView() {
        return Collections.unmodifiableMap(commandMessages);
    }

    @Override
    public String toString() {
        return "MoonContext{" + plugin.getName() + (bundledVersion == null ? "" : ", bundles " + bundledVersion) + "}";
    }
}
