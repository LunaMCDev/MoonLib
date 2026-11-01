package dev.lunamc.moonlib.event;

import org.bukkit.Bukkit;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;

import java.util.function.Consumer;

/**
 * Functional event listening, without writing a listener class.
 *
 * <pre>{@code
 * Events.listen(PlayerJoinEvent.class, event -> event.setJoinMessage(null));
 *
 * Events.subscribe(BlockBreakEvent.class)
 *         .priority(EventPriority.HIGH)
 *         .ignoreCancelled()
 *         .filter(e -> mines.isInMine(e.getBlock().getLocation()))
 *         .handler(e -> mines.handleBreak(e));
 *
 * // A one-shot listener that removes itself after the first matching event:
 * Events.subscribe(AsyncPlayerChatEvent.class)
 *         .filter(e -> e.getPlayer().equals(player))
 *         .expireAfter(1)
 *         .handler(e -> ...);
 *
 * MyCustomEvent fired = Events.call(new MyCustomEvent(player));
 * if (!fired.isCancelled()) { ... }
 * }</pre>
 *
 * <p>Exceptions thrown by handlers are caught and logged with the event name, so one bad handler
 * never breaks other plugins' listeners.</p>
 */
public final class Events {

    private Events() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Listens for an event at normal priority.
     *
     * @param type    the event class
     * @param handler the handler
     * @param <T>     the event type
     * @return the subscription
     */
    public static <T extends Event> EventSubscription<T> listen(Class<T> type, Consumer<T> handler) {
        return subscribe(type).handler(handler);
    }

    /**
     * Listens for an event at a given priority.
     *
     * @param type     the event class
     * @param priority the priority
     * @param handler  the handler
     * @param <T>      the event type
     * @return the subscription
     */
    public static <T extends Event> EventSubscription<T> listen(Class<T> type, EventPriority priority, Consumer<T> handler) {
        return subscribe(type).priority(priority).handler(handler);
    }

    /**
     * Starts configuring a subscription. Finish with {@link EventSubscription.Builder#handler(Consumer)}.
     *
     * @param type the event class
     * @param <T>  the event type
     * @return a builder
     */
    public static <T extends Event> EventSubscription.Builder<T> subscribe(Class<T> type) {
        return new EventSubscription.Builder<>(type);
    }

    /**
     * Fires an event and returns it, so you can check whether it was cancelled.
     *
     * @param event the event
     * @param <T>   the event type
     * @return the same event, after all listeners ran
     */
    public static <T extends Event> T call(T event) {
        Bukkit.getPluginManager().callEvent(event);
        return event;
    }
}
