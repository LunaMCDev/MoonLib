package dev.lunamc.moonlib.version.adapter;

import dev.lunamc.moonlib.version.Reflection;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.time.Duration;

/**
 * Internal API. Paper implementation using the native Adventure API through reflection, so MoonLib
 * does not need Adventure at compile time and keeps working if Paper removes the legacy BungeeCord
 * chat API in the future.
 */
public final class AdventureMessageAdapter implements MessageAdapter {

    private final Object serializer;
    private final Method deserialize;
    private final Method sendActionBar;
    private final Method showTitle;
    private final Method clearTitle;
    private final Method titleFactory;
    private final Method timesFactory;

    private AdventureMessageAdapter(Object serializer, Method deserialize, Method sendActionBar, Method showTitle,
                                    Method clearTitle, Method titleFactory, Method timesFactory) {
        this.serializer = serializer;
        this.deserialize = deserialize;
        this.sendActionBar = sendActionBar;
        this.showTitle = showTitle;
        this.clearTitle = clearTitle;
        this.titleFactory = titleFactory;
        this.timesFactory = timesFactory;
    }

    /**
     * Creates the adapter if the server ships Adventure natively.
     *
     * @return the adapter, or {@code null} if Adventure is unavailable
     */
    public static AdventureMessageAdapter tryCreate() {
        try {
            Class<?> component = Reflection.findClass("net.kyori.adventure.text.Component");
            Class<?> legacy = Reflection.findClass(
                    "net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer");
            Class<?> title = Reflection.findClass("net.kyori.adventure.title.Title");
            Class<?> times = Reflection.findClass("net.kyori.adventure.title.Title$Times");
            if (component == null || legacy == null || title == null || times == null) {
                return null;
            }
            Object serializer = legacy.getMethod("legacySection").invoke(null);
            Method deserialize = legacy.getMethod("deserialize", String.class);
            Method sendActionBar = Player.class.getMethod("sendActionBar", component);
            Method showTitle = Player.class.getMethod("showTitle", title);
            Method clearTitle = Player.class.getMethod("clearTitle");
            Method titleFactory = title.getMethod("title", component, component, times);
            Method timesFactory;
            try {
                timesFactory = times.getMethod("times", Duration.class, Duration.class, Duration.class);
            } catch (NoSuchMethodException e) {
                timesFactory = times.getMethod("of", Duration.class, Duration.class, Duration.class);
            }
            return new AdventureMessageAdapter(serializer, deserialize, sendActionBar, showTitle, clearTitle,
                    titleFactory, timesFactory);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            return null;
        }
    }

    @Override
    public void title(Player player, String title, String subtitle, int fadeIn, int stay, int fadeOut) {
        Object times = Reflection.invoke(timesFactory, null, ticks(fadeIn), ticks(stay), ticks(fadeOut));
        Object adventureTitle = Reflection.invoke(titleFactory, null, component(title), component(subtitle), times);
        Reflection.invoke(showTitle, player, adventureTitle);
    }

    @Override
    public void clearTitle(Player player) {
        Reflection.invoke(clearTitle, player);
    }

    @Override
    public void actionBar(Player player, String message) {
        Reflection.invoke(sendActionBar, player, component(message));
    }

    @Override
    public String name() {
        return "Adventure";
    }

    private Object component(String legacyText) {
        return Reflection.invoke(deserialize, serializer, legacyText == null ? "" : legacyText);
    }

    private static Duration ticks(int ticks) {
        return Duration.ofMillis(Math.max(0, ticks) * 50L);
    }
}
