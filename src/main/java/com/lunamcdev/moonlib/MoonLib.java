package com.lunamcdev.moonlib;

import com.lunamcdev.moonlib.command.CommandRegistry;
import com.lunamcdev.moonlib.input.ChatInput;
import com.lunamcdev.moonlib.integration.Integrations;
import com.lunamcdev.moonlib.integration.MoonLink;
import com.lunamcdev.moonlib.menu.MenuManager;
import com.lunamcdev.moonlib.scheduler.Tasks;
import com.lunamcdev.moonlib.scoreboard.BoardManager;
import com.lunamcdev.moonlib.util.Log;
import com.lunamcdev.moonlib.version.ServerSoftware;
import com.lunamcdev.moonlib.version.ServerVersion;
import org.bukkit.Bukkit;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

/**
 * Entry point and lifecycle owner of MoonLib. Supports any number of plugins at once.
 *
 * <h2>Deployment modes</h2>
 * <p>MoonLib works however it ends up on the server; see {@link #mode()}:</p>
 * <ul>
 *     <li><b>Relocated</b> (recommended): each plugin shades MoonLib into its own package. Every plugin
 *     has a private copy and there is nothing to coordinate.</li>
 *     <li><b>Shared</b>: several plugins use one copy, because they shaded it without relocating. MoonLib keeps
 *     separate state per plugin and works out which plugin is calling, so this just works too.</li>
 * </ul>
 *
 * <p>In every mode, each plugin calls {@link #init(Plugin)} when it enables and {@link #shutdown(Plugin)}
 * when it disables. {@link MoonPlugin} does both for you.</p>
 *
 * <pre>{@code
 * public final class MyPlugin extends JavaPlugin {
 *     @Override public void onEnable()  { MoonLib.init(this); }
 *     @Override public void onDisable() { MoonLib.shutdown(this); }
 * }
 * }</pre>
 *
 * <h2>Ownership</h2>
 * <p>Everything you create belongs to the plugin that created it: tasks, menus, commands,
 * scoreboards, chat prompts, event subscriptions, item tag keys and log output. When one plugin
 * disables, only its own things are cleaned up and the other plugins carry on untouched.</p>
 *
 * <p>MoonLib's two shared event listeners (menus and chat input) are registered under a <i>host</i>
 * plugin. When the host disables while other plugins still use MoonLib, the listeners move to the
 * next plugin automatically.</p>
 */
public final class MoonLib {

    private static final String PROPERTIES = "moonlib.properties";

    /**
     * The version of this copy of MoonLib, for example {@code "1.2.0-beta"}. It is written into the
     * jar by the build from the single {@code <version>} in {@code pom.xml}, so it never needs
     * changing by hand. {@code "dev"} when MoonLib runs from an IDE without a Maven build.
     */
    public static final String VERSION = readOwnVersion();
    private static final List<MoonContext> CONTEXTS = new CopyOnWriteArrayList<>();
    private static final List<Listener> SHARED_LISTENERS = new CopyOnWriteArrayList<>();
    private static final ThreadLocal<Plugin> BOUND = new ThreadLocal<>();
    private static volatile Plugin host;

    private MoonLib() {
        throw new UnsupportedOperationException("Utility class");
    }

    // ------------------------------------------------------------------ lifecycle

    /**
     * Initialises MoonLib for a plugin. Call from {@code onEnable} on the main thread before using any
     * other MoonLib feature. Calling it again for the same plugin does nothing.
     *
     * <p>If another plugin already uses this copy of MoonLib, the two simply share it. A warning is
     * logged if this plugin was built with a newer MoonLib than the one that is loaded.</p>
     *
     * @param owner the plugin
     */
    public static synchronized void init(Plugin owner) {
        Objects.requireNonNull(owner, "owner");
        if (findContext(owner) != null) {
            return;
        }
        MoonContext context = new MoonContext(owner, bundledVersion(owner));
        CONTEXTS.add(context);
        if (host == null) {
            host = owner;
        }
        runAs(owner, () -> {
            Tasks.init(owner);
            MenuManager.init(owner);
            ChatInput.init(owner);
            checkVersion(context);
            if (CONTEXTS.size() > 1) {
                Log.debug("Sharing MoonLib {} with {}", VERSION, describeOthers(owner));
            }
            Log.debug("MoonLib {} initialised on {} {}", VERSION,
                    ServerSoftware.current().displayName(), ServerVersion.current());
        });
    }

    /**
     * Cleans up everything a plugin created and forgets the plugin. Call from {@code onDisable}.
     * Other plugins sharing MoonLib are not affected. Safe to call more than once.
     *
     * @param owner the plugin
     */
    public static synchronized void shutdown(Plugin owner) {
        MoonContext context = findContext(owner);
        if (context == null) {
            return;
        }
        runAs(owner, () -> {
            for (AutoCloseable resource : context.drainCloseables()) {
                runQuietly(() -> {
                    try {
                        resource.close();
                    } catch (Exception e) {
                        throw new IllegalStateException("Could not close " + resource, e);
                    }
                });
            }
            runQuietly(() -> MenuManager.shutdown(owner));
            runQuietly(() -> BoardManager.stopAll(owner));
            runQuietly(() -> ChatInput.shutdown(owner));
            runQuietly(() -> CommandRegistry.unregisterAll(owner));
            runQuietly(() -> Integrations.shutdown(owner));
            runQuietly(() -> MoonLink.shutdown(owner));
            runQuietly(() -> Tasks.shutdown(owner));
        });
        CONTEXTS.remove(context);
        if (host == owner) {
            host = CONTEXTS.isEmpty() ? null : CONTEXTS.get(0).plugin();
            rehostSharedListeners();
        }
    }

    /**
     * Shuts down MoonLib for the calling plugin.
     *
     * @see #shutdown(Plugin)
     */
    public static void shutdown() {
        Plugin current = pluginOrNull();
        if (current != null) {
            shutdown(current);
        }
    }

    // ------------------------------------------------------------------ ownership

    /**
     * Returns the plugin currently using MoonLib, i.e. the plugin that owns whatever is being created.
     *
     * <p>Resolution order: a plugin bound with {@link #runAs(Plugin, Runnable)}; the only registered plugin
     * when there is just one; otherwise the first registered plugin found on the call stack; otherwise
     * the {@link #host() host}.</p>
     *
     * @return the owning plugin
     * @throws IllegalStateException if no plugin has initialised MoonLib
     */
    public static Plugin plugin() {
        Plugin current = pluginOrNull();
        if (current == null) {
            throw new IllegalStateException("MoonLib has not been initialised. Extend MoonPlugin or call "
                    + "MoonLib.init(this) at the start of onEnable().");
        }
        return current;
    }

    /**
     * Like {@link #plugin()}, but returns {@code null} instead of throwing.
     *
     * @return the owning plugin, or {@code null}
     */
    public static Plugin pluginOrNull() {
        Plugin bound = BOUND.get();
        if (bound != null) {
            return bound;
        }
        List<MoonContext> contexts = CONTEXTS;
        int size = contexts.size();
        if (size == 0) {
            return null;
        }
        if (size == 1) {
            return contexts.get(0).plugin();
        }
        Plugin caller = PluginResolver.callingPlugin(contexts);
        return caller != null ? caller : host;
    }

    /**
     * Returns the context of the plugin currently using MoonLib.
     *
     * @return the context, or {@code null} if no plugin is initialised
     */
    public static MoonContext context() {
        Plugin current = pluginOrNull();
        return current == null ? null : findContext(current);
    }

    /**
     * Returns a plugin's context.
     *
     * @param owner the plugin
     * @return the context, or {@code null} if the plugin has not called {@link #init(Plugin)}
     */
    public static MoonContext context(Plugin owner) {
        return findContext(owner);
    }

    /**
     * Runs code as a specific plugin: everything MoonLib creates inside belongs to that plugin. Nesting is
     * supported. Useful in code that MoonLib can't attribute by itself, such as a library shared by two
     * of your plugins.
     *
     * @param owner the plugin
     * @param action the code to run
     */
    public static void runAs(Plugin owner, Runnable action) {
        Plugin previous = BOUND.get();
        BOUND.set(owner);
        try {
            action.run();
        } finally {
            if (previous == null) {
                BOUND.remove();
            } else {
                BOUND.set(previous);
            }
        }
    }

    /**
     * Computes a value as a specific plugin.
     *
     * @param owner    the plugin
     * @param supplier the computation
     * @param <T>      the result type
     * @return the result
     * @see #runAs(Plugin, Runnable)
     */
    public static <T> T callAs(Plugin owner, Supplier<T> supplier) {
        Plugin previous = BOUND.get();
        BOUND.set(owner);
        try {
            return supplier.get();
        } finally {
            if (previous == null) {
                BOUND.remove();
            } else {
                BOUND.set(previous);
            }
        }
    }

    // ------------------------------------------------------------------ information

    /**
     * Returns whether at least one plugin has initialised MoonLib.
     *
     * @return {@code true} while any plugin is using MoonLib
     */
    public static boolean isInitialised() {
        return !CONTEXTS.isEmpty();
    }

    /**
     * Returns whether a specific plugin has initialised MoonLib.
     *
     * @param owner the plugin
     * @return {@code true} if initialised
     */
    public static boolean isInitialised(Plugin owner) {
        return findContext(owner) != null;
    }

    /**
     * Returns every plugin using this copy of MoonLib, in initialisation order.
     *
     * @return an unmodifiable snapshot
     */
    public static List<Plugin> plugins() {
        List<Plugin> plugins = new ArrayList<>(CONTEXTS.size());
        for (MoonContext context : CONTEXTS) {
            plugins.add(context.plugin());
        }
        return Collections.unmodifiableList(plugins);
    }

    /**
     * Returns the plugin that MoonLib's shared listeners are registered under.
     *
     * @return the host, or {@code null} if no plugin is initialised
     */
    public static Plugin host() {
        return host;
    }

    /**
     * Returns how this copy of MoonLib is being used.
     *
     * @return {@link Mode#SHARED} if more than one plugin uses it, otherwise {@link Mode#ISOLATED}
     */
    public static Mode mode() {
        return CONTEXTS.size() > 1 ? Mode.SHARED : Mode.ISOLATED;
    }

    /**
     * Returns the version of the MoonLib copy that is actually running.
     *
     * @return the version, e.g. {@code "1.1.0"}
     */
    public static String version() {
        return VERSION;
    }

    /**
     * Returns whether the running MoonLib is at least a given version. Useful in shared mode, where
     * another plugin's copy may be the one loaded.
     *
     * @param required e.g. {@code "1.1.0"}
     * @return {@code true} if {@link #version()} is the same or newer
     */
    public static boolean isVersionAtLeast(String required) {
        return "dev".equals(VERSION) || compareVersions(VERSION, required) >= 0;
    }

    /**
     * Compares two dotted version strings numerically ({@code 1.10.0 > 1.9.2}). Suffixes such as
     * {@code -SNAPSHOT} are ignored.
     *
     * @param a the first version
     * @param b the second version
     * @return negative, zero or positive like {@link Comparable#compareTo}
     */
    public static int compareVersions(String a, String b) {
        String[] left = a.split("[-+]")[0].split("\\.");
        String[] right = b.split("[-+]")[0].split("\\.");
        for (int i = 0; i < Math.max(left.length, right.length); i++) {
            int l = i < left.length ? parsePart(left[i]) : 0;
            int r = i < right.length ? parsePart(right[i]) : 0;
            if (l != r) {
                return Integer.compare(l, r);
            }
        }
        return 0;
    }

    // ------------------------------------------------------------------ shared listeners

    /**
     * Internal API. Registers a listener that serves every plugin using this copy of MoonLib. It is
     * registered under the {@link #host()} and moved to another plugin if the host disables.
     *
     * @param listener the listener; registering the same instance twice does nothing
     */
    public static synchronized void shareListener(Listener listener) {
        if (SHARED_LISTENERS.contains(listener)) {
            return;
        }
        SHARED_LISTENERS.add(listener);
        if (host != null) {
            Bukkit.getPluginManager().registerEvents(listener, host);
        }
    }

    /**
     * Internal API. Stops sharing a listener and unregisters it.
     *
     * @param listener the listener
     */
    public static synchronized void unshareListener(Listener listener) {
        if (SHARED_LISTENERS.remove(listener)) {
            HandlerList.unregisterAll(listener);
        }
    }

    /**
     * Returns whether a listener is currently shared.
     *
     * @param listener the listener
     * @return {@code true} if registered through {@link #shareListener(Listener)}
     */
    public static boolean isShared(Listener listener) {
        return SHARED_LISTENERS.contains(listener);
    }

    private static void rehostSharedListeners() {
        for (Listener listener : SHARED_LISTENERS) {
            HandlerList.unregisterAll(listener);
            if (host != null) {
                Bukkit.getPluginManager().registerEvents(listener, host);
            }
        }
        if (host == null) {
            SHARED_LISTENERS.clear();
        } else {
            Log.debug("MoonLib listeners are now hosted by {}", host.getName());
        }
    }

    // ------------------------------------------------------------------ internals

    private static MoonContext findContext(Plugin owner) {
        for (MoonContext context : CONTEXTS) {
            if (context.plugin() == owner) {
                return context;
            }
        }
        return null;
    }

    /** Reads the MoonLib version shipped inside a plugin's own jar, if any. */
    private static String readOwnVersion() {
        try (InputStream in = MoonLib.class.getResourceAsStream(PROPERTIES)) {
            if (in != null) {
                Properties properties = new Properties();
                properties.load(in);
                String version = properties.getProperty("version", "").trim();
                if (!version.isEmpty() && !version.startsWith("${")) {
                    return version;
                }
            }
        } catch (IOException | RuntimeException ignored) {
            // fall through
        }
        return "dev";
    }

    private static String bundledVersion(Plugin owner) {
        String path = MoonLib.class.getName().replace('.', '/');
        path = path.substring(0, path.lastIndexOf('/') + 1) + PROPERTIES;
        try {
            URL resource = owner.getClass().getClassLoader().getResource(path);
            if (resource == null) {
                return null;
            }
            try (InputStream in = resource.openStream()) {
                Properties properties = new Properties();
                properties.load(in);
                return properties.getProperty("version");
            }
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private static void checkVersion(MoonContext context) {
        String bundled = context.bundledVersion();
        if (bundled == null || bundled.startsWith("${")) {
            return;
        }
        int comparison = compareVersions(bundled, VERSION);
        if (comparison == 0) {
            return;
        }
        String loadedFrom = providerName();
        if (comparison > 0) {
            Log.warn("{} was built with MoonLib {}, but MoonLib {} (from {}) is the copy in use. Features added "
                            + "after {} will not be available. Update {}, or relocate MoonLib in {} so it uses its own copy.",
                    context.plugin().getName(), bundled, VERSION, loadedFrom, VERSION, loadedFrom,
                    context.plugin().getName());
        } else if (!majorOf(bundled).equals(majorOf(VERSION))) {
            Log.warn("{} was built with MoonLib {} but is running on MoonLib {} (from {}). Major versions differ, "
                    + "so some APIs may have changed.", context.plugin().getName(), bundled, VERSION, loadedFrom);
        }
    }

    private static String providerName() {
        try {
            return JavaPlugin.getProvidingPlugin(MoonLib.class).getName();
        } catch (RuntimeException | LinkageError e) {
            return "another plugin";
        }
    }

    private static String describeOthers(Plugin self) {
        List<String> names = new ArrayList<>();
        for (MoonContext context : CONTEXTS) {
            if (context.plugin() != self) {
                names.add(context.plugin().getName());
            }
        }
        return String.join(", ", names);
    }

    private static String majorOf(String version) {
        int dot = version.indexOf('.');
        return dot < 0 ? version : version.substring(0, dot);
    }

    private static int parsePart(String part) {
        try {
            return Integer.parseInt(part.replaceAll("\\D.*", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static void runQuietly(Runnable action) {
        try {
            action.run();
        } catch (Throwable t) {
            Log.error("Error while shutting down MoonLib", t);
        }
    }

    /**
     * How the running copy of MoonLib is being used.
     */
    public enum Mode {
        /** Exactly one plugin uses this copy (it was relocated, or only one plugin is installed). */
        ISOLATED,
        /** Several plugins share this copy (unrelocated shading). */
        SHARED
    }
}
