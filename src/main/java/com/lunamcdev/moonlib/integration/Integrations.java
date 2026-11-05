package com.lunamcdev.moonlib.integration;

import com.lunamcdev.moonlib.MoonLib;
import com.lunamcdev.moonlib.util.Log;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Detects other plugins and turns {@link Integration}s on and off as they enable and disable.
 *
 * <pre>{@code
 * // a class, for anything non-trivial
 * Integrations.register(new CratesIntegration());
 *
 * // or inline
 * Integrations.when("PlaceholderAPI", papi -> new MyExpansion().register());
 * Integrations.when("MoonGangs", gangs -> sellBoosts.enable(), () -> sellBoosts.disable());
 *
 * Integrations.isActive("MoonCrates");
 * }</pre>
 *
 * <ul>
 *     <li><b>Load order doesn't matter.</b> If the other plugin is already enabled the integration switches on
 *     immediately; otherwise it switches on the moment that plugin enables. Add the plugin to
 *     {@code softdepend} in your {@code plugin.yml} if you want it enabled first.</li>
 *     <li><b>It follows the other plugin.</b> If that plugin is disabled (or reloaded with a plugin manager), the
 *     integration switches off, and on again when it comes back.</li>
 *     <li><b>Nothing can crash your plugin.</b> A version that's too old is skipped with a warning, and any exception
 *     or missing class during {@code enable} is logged and leaves that one integration off.</li>
 *     <li><b>Cleanup is automatic:</b> every integration is switched off when your plugin disables.</li>
 * </ul>
 *
 * <p>To call features of another plugin without depending on its API jar, use {@link MoonLink}.</p>
 */
public final class Integrations {

    private static final Map<Plugin, Owner> OWNERS = new ConcurrentHashMap<>();

    private Integrations() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Registers an integration for the calling plugin. It switches on now if the other plugin is enabled.
     *
     * @param integration the integration
     * @param <T>         the integration type
     * @return the same integration
     */
    public static <T extends Integration> T register(T integration) {
        Plugin owner = MoonLib.plugin();
        Owner state = owner(owner);
        state.integrations.add(integration);
        Plugin target = Bukkit.getPluginManager().getPlugin(integration.pluginName());
        if (target != null && target.isEnabled() && target != owner) {
            activate(owner, integration, target);
        }
        return integration;
    }

    /**
     * Registers an inline integration.
     *
     * @param pluginName the other plugin's name
     * @param onEnable   runs when the other plugin is (or becomes) enabled
     * @return the integration
     */
    public static Integration when(String pluginName, Consumer<Plugin> onEnable) {
        return when(pluginName, onEnable, null);
    }

    /**
     * Registers an inline integration with cleanup.
     *
     * @param pluginName the other plugin's name
     * @param onEnable   runs when the other plugin is (or becomes) enabled
     * @param onDisable  runs when the other plugin, or yours, disables; may be {@code null}
     * @return the integration
     */
    public static Integration when(String pluginName, Consumer<Plugin> onEnable, Runnable onDisable) {
        return register(new Integration(pluginName) {
            @Override
            protected void enable(Plugin target) {
                onEnable.accept(target);
            }

            @Override
            protected void disable() {
                if (onDisable != null) {
                    onDisable.run();
                }
            }
        });
    }

    /**
     * Returns whether the calling plugin has an active integration with a plugin.
     *
     * @param pluginName the other plugin's name
     * @return {@code true} if one is on
     */
    public static boolean isActive(String pluginName) {
        Owner state = OWNERS.get(MoonLib.plugin());
        if (state == null) {
            return false;
        }
        for (Integration integration : state.integrations) {
            if (integration.isActive() && integration.pluginName().equalsIgnoreCase(pluginName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns the calling plugin's integrations.
     *
     * @return an unmodifiable snapshot
     */
    public static List<Integration> all() {
        Plugin owner = MoonLib.pluginOrNull();
        Owner state = owner == null ? null : OWNERS.get(owner);
        return state == null ? Collections.<Integration>emptyList()
                : Collections.unmodifiableList(new ArrayList<>(state.integrations));
    }

    /**
     * Returns a plugin's integrations.
     *
     * @param owner the plugin
     * @return an unmodifiable snapshot
     */
    public static List<Integration> all(Plugin owner) {
        Owner state = OWNERS.get(owner);
        return state == null ? Collections.<Integration>emptyList()
                : Collections.unmodifiableList(new ArrayList<>(state.integrations));
    }

    /**
     * Switches off every integration of a plugin and forgets them. Called by {@code MoonLib.shutdown(plugin)}.
     *
     * @param owner the plugin
     */
    public static void shutdown(Plugin owner) {
        Owner state = OWNERS.remove(owner);
        if (state == null) {
            return;
        }
        HandlerList.unregisterAll(state.listener);
        for (Integration integration : state.integrations) {
            deactivate(owner, integration);
        }
        state.integrations.clear();
    }

    private static Owner owner(Plugin owner) {
        return OWNERS.computeIfAbsent(owner, plugin -> {
            Owner state = new Owner(plugin);
            Bukkit.getPluginManager().registerEvents(state.listener, plugin);
            return state;
        });
    }

    private static void activate(Plugin owner, Integration integration, Plugin target) {
        if (integration.isActive()) {
            return;
        }
        String required = integration.minVersion();
        String actual = target.getDescription().getVersion();
        if (required != null && actual != null && MoonLib.compareVersions(actual, required) < 0) {
            MoonLib.runAs(owner, () -> Log.warn("Not linking with {} {}: version {} or newer is required",
                    target.getName(), actual, required));
            return;
        }
        MoonLib.runAs(owner, () -> {
            try {
                integration.enable(target);
                integration.markActive(target);
                Log.info("Linked with {} {}", target.getName(), actual == null ? "" : actual);
            } catch (Throwable t) {
                Log.error("Could not link with " + target.getName() + "; that integration stays off", t);
            }
        });
    }

    private static void deactivate(Plugin owner, Integration integration) {
        if (!integration.isActive()) {
            return;
        }
        Plugin target = integration.target();
        integration.markActive(null);
        MoonLib.runAs(owner, () -> {
            try {
                integration.disable();
                Log.debug("Unlinked from {}", target.getName());
            } catch (Throwable t) {
                Log.error("Error while unlinking from " + target.getName(), t);
            }
        });
    }

    /** One plugin's integrations and the listener that follows other plugins. */
    private static final class Owner {
        final Plugin plugin;
        final List<Integration> integrations = new CopyOnWriteArrayList<>();
        final Listener listener;

        Owner(Plugin plugin) {
            this.plugin = plugin;
            this.listener = new Follower(this);
        }
    }

    /**
     * Internal listener. Public only because Bukkit requires listener methods to be accessible.
     */
    public static final class Follower implements Listener {
        private final Owner state;

        private Follower(Owner state) {
            this.state = state;
        }

        /**
         * Switches integrations on when their plugin enables.
         *
         * @param event the event
         */
        @EventHandler(priority = EventPriority.MONITOR)
        public void onEnable(PluginEnableEvent event) {
            Plugin enabled = event.getPlugin();
            if (enabled == state.plugin) {
                return;
            }
            for (Integration integration : state.integrations) {
                if (integration.pluginName().equalsIgnoreCase(enabled.getName())) {
                    activate(state.plugin, integration, enabled);
                }
            }
        }

        /**
         * Switches integrations off when their plugin disables.
         *
         * @param event the event
         */
        @EventHandler(priority = EventPriority.MONITOR)
        public void onDisable(PluginDisableEvent event) {
            Plugin disabled = event.getPlugin();
            if (disabled == state.plugin) {
                return; // our own shutdown is handled by MoonLib.shutdown
            }
            for (Integration integration : state.integrations) {
                if (integration.isActive() && integration.target() == disabled) {
                    deactivate(state.plugin, integration);
                }
            }
        }
    }
}
