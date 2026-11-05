package dev.lunamc.moonlib.integration;

import org.bukkit.plugin.Plugin;

/**
 * A link from your plugin to another plugin that switches itself on when that plugin is present and
 * enabled, and off again when it disables. Register it with {@link Integrations#register(Integration)}.
 *
 * <pre>{@code
 * public final class CratesIntegration extends Integration {
 *
 *     public CratesIntegration() {
 *         super("MoonCrates", "1.0.0");   // plugin name, minimum version (optional)
 *     }
 *
 *     @Override
 *     protected void enable(Plugin crates) {
 *         quests.addRewardType("crate-key", (player, args) ->
 *                 MoonLink.call("crates:give-key", MoonLink.payload("player", player, "crate", args[0], "amount", 1)));
 *     }
 *
 *     @Override
 *     protected void disable() {
 *         quests.removeRewardType("crate-key");
 *     }
 * }
 * }</pre>
 *
 * <p>Code inside {@link #enable(Plugin)} may use the other plugin's API classes directly: the integration is
 * only enabled when that plugin is installed, so those classes are never loaded otherwise. Keep such code in
 * the integration class (not in your main class) so a missing plugin can never cause a
 * {@code NoClassDefFoundError}. Any exception thrown here is caught and logged; the integration is simply
 * left off.</p>
 */
public abstract class Integration {

    private final String pluginName;
    private final String minVersion;
    private volatile Plugin target;

    /**
     * Creates an integration with any version of the other plugin.
     *
     * @param pluginName the other plugin's name, as in its {@code plugin.yml}
     */
    protected Integration(String pluginName) {
        this(pluginName, null);
    }

    /**
     * Creates an integration that requires a minimum version of the other plugin.
     *
     * @param pluginName the other plugin's name, as in its {@code plugin.yml}
     * @param minVersion the oldest supported version, e.g. {@code "1.2.0"}, or {@code null} for any
     */
    protected Integration(String pluginName, String minVersion) {
        if (pluginName == null || pluginName.trim().isEmpty()) {
            throw new IllegalArgumentException("Integration plugin name is empty");
        }
        this.pluginName = pluginName;
        this.minVersion = minVersion;
    }

    /**
     * Switches the integration on. Runs on the main thread, as your plugin, once the other plugin is enabled
     * (immediately if it already is).
     *
     * @param target the other plugin
     * @throws Exception any failure; it is logged and the integration stays off
     */
    protected abstract void enable(Plugin target) throws Exception;

    /**
     * Switches the integration off: the other plugin is disabling, or your plugin is. Undo anything
     * {@link #enable(Plugin)} set up. Default does nothing.
     *
     * @throws Exception any failure; it is logged
     */
    protected void disable() throws Exception {
    }

    /**
     * Returns the other plugin's name.
     *
     * @return the name
     */
    public final String pluginName() {
        return pluginName;
    }

    /**
     * Returns the minimum version of the other plugin.
     *
     * @return the version, or {@code null} for any
     */
    public final String minVersion() {
        return minVersion;
    }

    /**
     * Returns whether the integration is currently on.
     *
     * @return {@code true} while the other plugin is enabled and {@link #enable(Plugin)} succeeded
     */
    public final boolean isActive() {
        return target != null;
    }

    /**
     * Returns the other plugin while the integration is on.
     *
     * @return the plugin, or {@code null} while off
     */
    public final Plugin target() {
        return target;
    }

    final void markActive(Plugin plugin) {
        this.target = plugin;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{" + pluginName + (isActive() ? ", active" : ", inactive") + "}";
    }
}
