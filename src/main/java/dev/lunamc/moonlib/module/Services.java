package dev.lunamc.moonlib.module;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * A per-plugin registry of shared objects ("services"), so modules depend on interfaces rather
 * than on each other's internals.
 *
 * <pre>{@code
 * services.register(Economy.class, new TokenEconomy());
 * Economy economy = services.require(Economy.class);
 * services.find(StatsService.class).ifPresent(stats -> stats.track(player));
 * }</pre>
 *
 * <p>This is deliberately simple: a type maps to one instance. Services are private to the plugin;
 * use {@link dev.lunamc.moonlib.integration.MoonLink} to offer features to other plugins.</p>
 *
 * @since 1.4.0
 */
public final class Services {

    private final Map<Class<?>, Entry> services = new LinkedHashMap<>();

    /** Creates an empty registry. Each {@link ModuleManager} has its own. */
    public Services() {
    }

    private static final class Entry {
        final Object service;
        final Object owner;

        Entry(Object service, Object owner) {
            this.service = service;
            this.owner = owner;
        }
    }

    /**
     * Registers a service, replacing any previous one of the same type.
     *
     * @param type    the type it is looked up by
     * @param service the implementation
     * @param <T>     the service type
     * @return the service
     */
    public <T> T register(Class<T> type, T service) {
        return register(type, service, null);
    }

    synchronized <T> T register(Class<T> type, T service, Object owner) {
        if (!type.isInstance(service)) {
            throw new IllegalArgumentException(service + " is not a " + type.getName());
        }
        services.put(type, new Entry(service, owner));
        return service;
    }

    /**
     * Removes a service if it is still the registered one.
     *
     * @param type    the type
     * @param service the implementation that was registered
     */
    public synchronized void unregister(Class<?> type, Object service) {
        Entry entry = services.get(type);
        if (entry != null && entry.service == service) {
            services.remove(type);
        }
    }

    /**
     * Looks up a service.
     *
     * @param type the type
     * @param <T>  the service type
     * @return the service, or empty
     */
    public synchronized <T> Optional<T> find(Class<T> type) {
        Entry entry = services.get(type);
        return entry == null ? Optional.<T>empty() : Optional.of(type.cast(entry.service));
    }

    /**
     * Looks up a service that must exist.
     *
     * @param type the type
     * @param <T>  the service type
     * @return the service
     * @throws IllegalStateException if nothing provides it, naming the missing type
     */
    public <T> T require(Class<T> type) {
        return find(type).orElseThrow(() -> new IllegalStateException("No service provides " + type.getName()
                + ". Is the module that provides it enabled, and declared as a dependency?"));
    }

    /**
     * Returns which module (or other owner) registered a service.
     *
     * @param type the type
     * @return the owner, or empty if registered directly or not at all
     */
    public synchronized Optional<Object> owner(Class<?> type) {
        Entry entry = services.get(type);
        return entry == null ? Optional.empty() : Optional.ofNullable(entry.owner);
    }

    /**
     * Returns a snapshot of every registered service type.
     *
     * @return the types, in registration order
     */
    public synchronized Map<Class<?>, Object> all() {
        Map<Class<?>, Object> out = new LinkedHashMap<>();
        for (Map.Entry<Class<?>, Entry> e : services.entrySet()) {
            out.put(e.getKey(), e.getValue().service);
        }
        return Collections.unmodifiableMap(out);
    }
}
