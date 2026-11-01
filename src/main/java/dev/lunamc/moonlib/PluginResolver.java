package dev.lunamc.moonlib;

import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Internal API. Works out which plugin is calling into MoonLib by walking the call stack and
 * matching each frame's class loader against the registered plugins.
 *
 * <p>Only used when more than one plugin shares this copy of MoonLib and no plugin is bound with
 * {@link MoonLib#runAs(Plugin, Runnable)}. In the common, relocated setup there is exactly one plugin
 * and this class is never touched.</p>
 */
final class PluginResolver {

    /** Package prefix of this copy of MoonLib, computed at runtime so it is correct after relocation. */
    private static final String MOONLIB_PREFIX;

    private static final Object WALKER;
    private static final Method WALK;
    private static final Method DECLARING_CLASS;

    static {
        String name = MoonLib.class.getName();
        MOONLIB_PREFIX = name.substring(0, name.lastIndexOf('.') + 1);

        Object walker = null;
        Method walk = null;
        Method declaringClass = null;
        // -Dmoonlib.stackwalker=false forces the Java 8 strategy (diagnostics / testing).
        boolean useWalker = !"false".equalsIgnoreCase(System.getProperty("moonlib.stackwalker"));
        try {
            if (!useWalker) {
                throw new ClassNotFoundException("StackWalker disabled by moonlib.stackwalker=false");
            }
            // Java 9+: StackWalker, accessed reflectively because MoonLib targets Java 8 bytecode.
            Class<?> walkerClass = Class.forName("java.lang.StackWalker");
            Class<?> optionClass = Class.forName("java.lang.StackWalker$Option");
            Class<?> frameClass = Class.forName("java.lang.StackWalker$StackFrame");
            Object retain = null;
            for (Object constant : optionClass.getEnumConstants()) {
                if (((Enum<?>) constant).name().equals("RETAIN_CLASS_REFERENCE")) {
                    retain = constant;
                }
            }
            walker = walkerClass.getMethod("getInstance", optionClass).invoke(null, retain);
            walk = walkerClass.getMethod("walk", Function.class);
            declaringClass = frameClass.getMethod("getDeclaringClass");
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            walker = null; // Java 8: fall back to SecurityManager#getClassContext
        }
        WALKER = walker;
        WALK = walk;
        DECLARING_CLASS = declaringClass;
    }

    private PluginResolver() {
    }

    /**
     * Returns the first plugin found on the call stack, ignoring MoonLib's own classes.
     *
     * @param contexts the registered plugins
     * @return the calling plugin, or {@code null} if no registered plugin is on the stack
     */
    static Plugin callingPlugin(List<MoonContext> contexts) {
        if (WALKER != null) {
            try {
                Function<Stream<Object>, Plugin> search = frames -> frames
                        .map(PluginResolver::declaringClass)
                        .map(type -> pluginFor(type, contexts))
                        .filter(plugin -> plugin != null)
                        .findFirst()
                        .orElse(null);
                return (Plugin) WALK.invoke(WALKER, search);
            } catch (ReflectiveOperationException | RuntimeException e) {
                // fall through to the Java 8 strategy
            }
        }
        try {
            for (Class<?> type : new ClassContext().classes()) {
                Plugin plugin = pluginFor(type, contexts);
                if (plugin != null) {
                    return plugin;
                }
            }
        } catch (RuntimeException | LinkageError ignored) {
            // stack inspection unavailable
        }
        return null;
    }

    /**
     * Returns whether a class belongs to this copy of MoonLib.
     *
     * @param type the class
     * @return {@code true} for MoonLib classes
     */
    static boolean isMoonLibClass(Class<?> type) {
        return type.getName().startsWith(MOONLIB_PREFIX);
    }

    private static Class<?> declaringClass(Object frame) {
        try {
            return (Class<?>) DECLARING_CLASS.invoke(frame);
        } catch (ReflectiveOperationException e) {
            return Object.class;
        }
    }

    private static Plugin pluginFor(Class<?> type, List<MoonContext> contexts) {
        if (type == null || isMoonLibClass(type)) {
            return null;
        }
        ClassLoader loader = type.getClassLoader();
        if (loader == null) {
            return null;
        }
        for (MoonContext context : contexts) {
            if (context.classLoader() == loader) {
                return context.plugin();
            }
        }
        return null;
    }

    /** Java 8 stack inspection. */
    @SuppressWarnings("removal")
    private static final class ClassContext extends SecurityManager {
        Class<?>[] classes() {
            return getClassContext();
        }
    }
}
