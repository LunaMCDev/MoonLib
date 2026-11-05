package com.lunamcdev.moonlib.event;

import com.lunamcdev.moonlib.MoonLib;
import com.lunamcdev.moonlib.util.Log;
import org.bukkit.Bukkit;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * A functional event listener created through {@link Events}. Unregister it at any time with
 * {@link #unregister()}.
 *
 * @param <T> the event type
 */
public final class EventSubscription<T extends Event> implements Listener {

    private final Class<T> type;
    private final Plugin owner;
    private final List<Predicate<T>> filters;
    private final Consumer<T> handler;
    private final int maxCalls;
    private final AtomicInteger calls = new AtomicInteger();
    private volatile boolean active = true;

    private EventSubscription(Class<T> type, Plugin owner, List<Predicate<T>> filters, Consumer<T> handler,
                              int maxCalls) {
        this.type = type;
        this.owner = owner;
        this.filters = filters;
        this.handler = handler;
        this.maxCalls = maxCalls;
    }

    /** Stops listening. Safe to call more than once, including from inside the handler. */
    public void unregister() {
        if (active) {
            active = false;
            HandlerList.unregisterAll(this);
        }
    }

    /**
     * Returns whether the subscription is still listening.
     *
     * @return {@code true} until unregistered or expired
     */
    public boolean isActive() {
        return active;
    }

    /**
     * Returns how many times the handler has run.
     *
     * @return the call count
     */
    public int callCount() {
        return calls.get();
    }

    /**
     * Returns the plugin the listener is registered for. It is unregistered automatically when that
     * plugin disables.
     *
     * @return the owning plugin
     */
    public Plugin owner() {
        return owner;
    }

    /**
     * Returns the event type.
     *
     * @return the event class
     */
    public Class<T> eventType() {
        return type;
    }

    private void handle(Event event) {
        if (!active || !type.isInstance(event)) {
            return;
        }
        T typed = type.cast(event);
        for (Predicate<T> filter : filters) {
            if (!filter.test(typed)) {
                return;
            }
        }
        int count = calls.incrementAndGet();
        if (maxCalls > 0 && count >= maxCalls) {
            unregister();
            if (count > maxCalls) {
                return;
            }
        }
        try {
            MoonLib.runAs(owner, () -> handler.accept(typed));
        } catch (Throwable t) {
            Log.error("Error in " + type.getSimpleName() + " handler", t);
        }
    }

    /**
     * Configures a subscription before registering it. Obtain one with {@link Events#subscribe(Class)}.
     *
     * @param <T> the event type
     */
    public static final class Builder<T extends Event> {
        private final Class<T> type;
        private final List<Predicate<T>> filters = new ArrayList<>();
        private EventPriority priority = EventPriority.NORMAL;
        private boolean ignoreCancelled;
        private int maxCalls;

        Builder(Class<T> type) {
            this.type = type;
        }

        /**
         * Sets the listener priority.
         *
         * @param value the priority, {@code NORMAL} by default
         * @return this builder
         */
        public Builder<T> priority(EventPriority value) {
            this.priority = value;
            return this;
        }

        /**
         * Skips events that another listener already cancelled.
         *
         * @return this builder
         */
        public Builder<T> ignoreCancelled() {
            this.ignoreCancelled = true;
            return this;
        }

        /**
         * Only handles events matching a condition. May be called several times; all filters must pass.
         *
         * @param filter the condition
         * @return this builder
         */
        public Builder<T> filter(Predicate<T> filter) {
            filters.add(filter);
            return this;
        }

        /**
         * Unregisters automatically after the handler has run a number of times.
         *
         * @param times the number of calls, e.g. {@code 1} for a one-shot listener
         * @return this builder
         */
        public Builder<T> expireAfter(int times) {
            this.maxCalls = Math.max(0, times);
            return this;
        }

        /**
         * Registers the subscription with a handler.
         *
         * @param handler the code to run
         * @return the active subscription
         */
        public EventSubscription<T> handler(Consumer<T> handler) {
            Plugin owner = MoonLib.plugin();
            EventSubscription<T> subscription = new EventSubscription<>(type, owner, new ArrayList<>(filters),
                    handler, maxCalls);
            Bukkit.getPluginManager().registerEvent(type, subscription, priority,
                    (listener, event) -> subscription.handle(event), owner, ignoreCancelled);
            return subscription;
        }
    }
}
