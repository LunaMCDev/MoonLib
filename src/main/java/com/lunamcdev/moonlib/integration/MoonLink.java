package com.lunamcdev.moonlib.integration;

import com.lunamcdev.moonlib.MoonLib;
import com.lunamcdev.moonlib.util.Log;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicePriority;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Lets plugins built on MoonLib find each other's features and call them, with no API jar and no compile-time
 * dependency between them.
 *
 * <pre>{@code
 * // In MoonCrates: offer a feature
 * MoonLink.provide("crates:give-key", payload -> {
 *     Map<String, Object> args = MoonLink.args(payload);
 *     Player player = (Player) args.get("player");
 *     crates.giveKey(player, (String) args.get("crate"), (Integer) args.getOrDefault("amount", 1));
 *     return true;
 * });
 *
 * // In MoonQuests: use it if MoonCrates is installed
 * if (MoonLink.available("crates:give-key")) {
 *     MoonLink.call("crates:give-key", MoonLink.payload("player", player, "crate", "vote", "amount", 1));
 * }
 *
 * // Events across plugins
 * MoonLink.subscribe("prison:rankup", payload -> quests.progress((Player) MoonLink.args(payload).get("player"), "rankup"));
 * MoonLink.publish("prison:rankup", MoonLink.payload("player", player, "rank", "B"));
 * }</pre>
 *
 * <h2>How it works</h2>
 * <p>Each plugin's copy of MoonLib registers one endpoint with Bukkit's {@code ServicesManager}. Other copies
 * find those endpoints, ask which capabilities they offer, and call them. Because every plugin usually has its
 * own relocated copy of MoonLib, the endpoint is a plain {@link BiFunction}, a type every plugin shares.</p>
 *
 * <h2>Rules for payloads and results</h2>
 * <ul>
 *     <li>Use only Java and Bukkit types: {@code String}, numbers, booleans, {@code UUID}, {@code Map}, {@code List},
 *     {@code Player}, {@code ItemStack}, {@code Location}, ... Never your own classes: the other plugin can't see them.</li>
 *     <li>By convention, pass a {@code Map<String, Object>} built with {@link #payload(Object...)}.</li>
 *     <li>Name capabilities and topics {@code plugin:action}, e.g. {@code "crates:give-key"}.</li>
 *     <li>Calls run synchronously on the caller's thread, as the providing plugin.</li>
 * </ul>
 *
 * <p>Everything a plugin provides or subscribes to is removed automatically when it disables.</p>
 */
public final class MoonLink {

    /** Protocol identifier exchanged by endpoints. Bumped only for incompatible changes. */
    public static final String PROTOCOL = "moonlink/1";

    private static final String MARKER = ".integration.MoonLink$Endpoint";
    private static final String OP_PROTOCOL = "__protocol";
    private static final String OP_PLUGIN = "__plugin";
    private static final String OP_CAPABILITIES = "__capabilities";
    private static final String OP_EVENT = "__event";

    private static final Map<Plugin, Endpoint> LOCAL = new ConcurrentHashMap<>();

    private MoonLink() {
        throw new UnsupportedOperationException("Utility class");
    }

    // ------------------------------------------------------------------ providing

    /**
     * Offers a capability to every other plugin. Providing the same name again replaces the handler.
     *
     * @param capability the name, e.g. {@code "crates:give-key"}
     * @param handler    receives the payload and returns a result (or {@code null})
     */
    public static void provide(String capability, Function<Object, Object> handler) {
        requireName(capability);
        if (handler == null) {
            throw new IllegalArgumentException("Handler is null");
        }
        endpoint(MoonLib.plugin()).capabilities.put(capability, handler);
    }

    /**
     * Stops offering a capability.
     *
     * @param capability the name
     */
    public static void withdraw(String capability) {
        Endpoint endpoint = LOCAL.get(MoonLib.plugin());
        if (endpoint != null) {
            endpoint.capabilities.remove(capability);
        }
    }

    /**
     * Listens for a topic published by any plugin, including your own.
     *
     * @param topic    the topic, e.g. {@code "prison:rankup"}
     * @param listener receives the payload
     */
    public static void subscribe(String topic, Consumer<Object> listener) {
        requireName(topic);
        endpoint(MoonLib.plugin()).subscribers
                .computeIfAbsent(topic, t -> new CopyOnWriteArrayList<>()).add(listener);
    }

    /**
     * Stops listening to a topic.
     *
     * @param topic the topic
     */
    public static void unsubscribe(String topic) {
        Endpoint endpoint = LOCAL.get(MoonLib.plugin());
        if (endpoint != null) {
            endpoint.subscribers.remove(topic);
        }
    }

    // ------------------------------------------------------------------ using

    /**
     * Returns whether any plugin offers a capability.
     *
     * @param capability the name
     * @return {@code true} if it can be called
     */
    public static boolean available(String capability) {
        return find(capability) != null;
    }

    /**
     * Calls a capability offered by another plugin (or your own).
     *
     * @param capability the name
     * @param payload    the argument, usually from {@link #payload(Object...)}
     * @return the result, or empty if nobody offers the capability or it returned {@code null}
     * @throws IllegalStateException if the provider threw; the message names the provider
     */
    public static Optional<Object> call(String capability, Object payload) {
        RegisteredServiceProvider<?> provider = find(capability);
        if (provider == null) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(invoke(provider, capability, payload));
        } catch (RuntimeException e) {
            throw new IllegalStateException(provider.getPlugin().getName() + " failed to handle '" + capability
                    + "': " + e.getMessage(), e);
        }
    }

    /**
     * Calls a capability and casts the result.
     *
     * @param capability the name
     * @param payload    the argument
     * @param resultType the expected result class (a Java or Bukkit type)
     * @param <T>        the result type
     * @return the result, or empty if unavailable, {@code null}, or of another type
     */
    public static <T> Optional<T> call(String capability, Object payload, Class<T> resultType) {
        Optional<Object> result = call(capability, payload);
        return result.isPresent() && resultType.isInstance(result.get())
                ? Optional.of(resultType.cast(result.get())) : Optional.<T>empty();
    }

    /**
     * Sends a topic to every subscriber in every plugin. Subscribers that throw are logged and skipped.
     *
     * @param topic   the topic
     * @param payload the payload
     * @return how many plugins received it
     */
    public static int publish(String topic, Object payload) {
        int delivered = 0;
        for (RegisteredServiceProvider<?> provider : endpoints()) {
            try {
                Object handled = invoke(provider, OP_EVENT, new Object[]{topic, payload});
                if (Boolean.TRUE.equals(handled)) {
                    delivered++;
                }
            } catch (RuntimeException e) {
                Log.error(provider.getPlugin().getName() + " failed to handle event '" + topic + "'", e);
            }
        }
        return delivered;
    }

    /**
     * Returns the plugins offering a capability, best first.
     *
     * @param capability the name
     * @return plugin names
     */
    public static List<String> providers(String capability) {
        List<String> names = new ArrayList<>();
        for (RegisteredServiceProvider<?> provider : endpoints()) {
            if (capabilitiesOf(provider).contains(capability)) {
                names.add(provider.getPlugin().getName());
            }
        }
        return names;
    }

    /**
     * Returns every MoonLink-enabled plugin and what it offers, for diagnostics.
     *
     * @return plugin name to capability names, sorted by plugin name
     */
    public static Map<String, Set<String>> capabilities() {
        Map<String, Set<String>> all = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (RegisteredServiceProvider<?> provider : endpoints()) {
            all.put(provider.getPlugin().getName(), new LinkedHashSet<>(capabilitiesOf(provider)));
        }
        return all;
    }

    // ------------------------------------------------------------------ payload helpers

    /**
     * Builds a payload map from alternating keys and values.
     *
     * @param pairs e.g. {@code "player", player, "amount", 3}
     * @return a new mutable, ordered map
     */
    public static Map<String, Object> payload(Object... pairs) {
        if (pairs.length % 2 != 0) {
            throw new IllegalArgumentException("Payload needs key/value pairs, got " + pairs.length + " values");
        }
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put(String.valueOf(pairs[i]), pairs[i + 1]);
        }
        return map;
    }

    /**
     * Reads a payload as a map.
     *
     * @param payload the received payload
     * @return the map, or an empty map if the payload is not a map
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> args(Object payload) {
        return payload instanceof Map ? (Map<String, Object>) payload : Collections.<String, Object>emptyMap();
    }

    // ------------------------------------------------------------------ lifecycle

    /**
     * Removes everything a plugin provided or subscribed to. Called by {@code MoonLib.shutdown(plugin)}.
     *
     * @param owner the plugin
     */
    public static void shutdown(Plugin owner) {
        Endpoint endpoint = LOCAL.remove(owner);
        if (endpoint != null) {
            endpoint.capabilities.clear();
            endpoint.subscribers.clear();
            Bukkit.getServicesManager().unregister(BiFunction.class, endpoint);
        }
    }

    // ------------------------------------------------------------------ internals

    private static Endpoint endpoint(Plugin owner) {
        return LOCAL.computeIfAbsent(owner, plugin -> {
            Endpoint endpoint = new Endpoint(plugin);
            Bukkit.getServicesManager().register(BiFunction.class, endpoint, plugin, ServicePriority.Normal);
            return endpoint;
        });
    }

    private static RegisteredServiceProvider<?> find(String capability) {
        for (RegisteredServiceProvider<?> provider : endpoints()) {
            if (capabilitiesOf(provider).contains(capability)) {
                return provider;
            }
        }
        return null;
    }

    /** Every MoonLink endpoint from every plugin, whatever package its MoonLib copy was relocated to. */
    @SuppressWarnings("rawtypes")
    private static List<RegisteredServiceProvider<?>> endpoints() {
        Collection<RegisteredServiceProvider<BiFunction>> registrations =
                Bukkit.getServicesManager().getRegistrations(BiFunction.class);
        List<RegisteredServiceProvider<?>> endpoints = new ArrayList<>();
        for (RegisteredServiceProvider<BiFunction> registration : registrations) {
            Object provider = registration.getProvider();
            if (provider == null || !provider.getClass().getName().endsWith(MARKER)) {
                continue;
            }
            try {
                if (PROTOCOL.equals(invoke(registration, OP_PROTOCOL, null))
                        && registration.getPlugin().isEnabled()) {
                    endpoints.add(registration);
                }
            } catch (RuntimeException ignored) {
                // an incompatible endpoint; skip it
            }
        }
        return endpoints;
    }

    @SuppressWarnings("unchecked")
    private static Set<String> capabilitiesOf(RegisteredServiceProvider<?> provider) {
        try {
            Object result = invoke(provider, OP_CAPABILITIES, null);
            return result instanceof Set ? (Set<String>) result : Collections.<String>emptySet();
        } catch (RuntimeException e) {
            return Collections.emptySet();
        }
    }

    @SuppressWarnings("unchecked")
    private static Object invoke(RegisteredServiceProvider<?> provider, String op, Object argument) {
        return ((BiFunction<String, Object, Object>) provider.getProvider()).apply(op, argument);
    }

    private static void requireName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Capability and topic names must not be empty");
        }
        if (name.startsWith("__")) {
            throw new IllegalArgumentException("Names starting with __ are reserved: " + name);
        }
    }

    /**
     * One plugin's endpoint. Its class name ends with {@code .integration.MoonLink$Endpoint} in every relocated
     * copy, which is how copies recognise each other.
     */
    static final class Endpoint implements BiFunction<String, Object, Object> {
        private final Plugin owner;
        private final Map<String, Function<Object, Object>> capabilities = new ConcurrentHashMap<>();
        private final Map<String, List<Consumer<Object>>> subscribers = new ConcurrentHashMap<>();

        Endpoint(Plugin owner) {
            this.owner = owner;
        }

        @Override
        public Object apply(String op, Object argument) {
            switch (op) {
                case OP_PROTOCOL:
                    return PROTOCOL;
                case OP_PLUGIN:
                    return owner.getName();
                case OP_CAPABILITIES:
                    return new LinkedHashSet<>(capabilities.keySet());
                case OP_EVENT:
                    return deliver(argument);
                default:
                    Function<Object, Object> handler = capabilities.get(op);
                    if (handler == null) {
                        throw new IllegalArgumentException(owner.getName() + " does not provide '" + op + "'");
                    }
                    return MoonLib.callAs(owner, () -> handler.apply(argument));
            }
        }

        private Boolean deliver(Object argument) {
            if (!(argument instanceof Object[]) || ((Object[]) argument).length != 2) {
                return Boolean.FALSE;
            }
            Object[] event = (Object[]) argument;
            List<Consumer<Object>> listeners = subscribers.get(String.valueOf(event[0]));
            if (listeners == null || listeners.isEmpty()) {
                return Boolean.FALSE;
            }
            for (Consumer<Object> listener : listeners) {
                MoonLib.runAs(owner, () -> {
                    try {
                        listener.accept(event[1]);
                    } catch (Throwable t) {
                        Log.error("Error in MoonLink subscriber for '" + event[0] + "'", t);
                    }
                });
            }
            return Boolean.TRUE;
        }

        @Override
        public String toString() {
            return "MoonLink endpoint of " + owner.getName();
        }
    }
}
