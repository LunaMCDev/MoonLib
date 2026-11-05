package dev.lunamc.moonlib.module;

import dev.lunamc.moonlib.MoonLib;
import dev.lunamc.moonlib.config.DataFile;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * Registers, orders and runs a plugin's {@link MoonModule}s.
 *
 * <pre>{@code
 * modules = new ModuleManager(this)
 *         .toggles(loadConfig("config.yml"), "modules")     // modules.<id>: false turns a module off
 *         .register(new EconomyModule(), new MinesModule(), new RanksModule());
 * modules.enableAll();                             // load() all, then enable() in dependency order
 * ...
 * modules.reload("mines");                         // e.g. from /prison reload mines
 * }</pre>
 *
 * <p>Modules are enabled so that every module comes after its dependencies (and after any soft
 * dependencies that are present), and disabled in reverse. A module that throws, is turned off, or
 * has a missing or failed dependency is skipped with a clear log line, and the rest of the plugin
 * keeps working. Dependency cycles are reported when {@link #enableAll()} runs.</p>
 *
 * <p>The manager disables every module automatically when the plugin shuts down MoonLib.</p>
 *
 * @since 1.1.1-alpha
 */
public final class ModuleManager implements AutoCloseable {

    /** What happened to a module the last time the manager tried to enable it. */
    public enum State {
        /** Registered, not yet enabled. */
        REGISTERED,
        /** Running. */
        ENABLED,
        /** Turned off in config. */
        TURNED_OFF,
        /** Skipped because a dependency is missing, off or failed. */
        SKIPPED,
        /** {@code load()} or {@code enable()} threw. */
        FAILED,
        /** Disabled after running. */
        DISABLED
    }

    private final Plugin plugin;
    private final Services services = new Services();
    private final Map<Class<? extends MoonModule>, MoonModule> modules = new LinkedHashMap<>();
    private final Map<MoonModule, State> states = new LinkedHashMap<>();
    private final Map<MoonModule, String> problems = new LinkedHashMap<>();
    private final List<MoonModule> enableOrder = new ArrayList<>();
    private DataFile toggleFile;
    private String togglePath;

    /**
     * Creates a manager.
     *
     * @param plugin the owning plugin
     */
    public ModuleManager(Plugin plugin) {
        this.plugin = plugin;
        if (MoonLib.isInitialised(plugin)) {
            MoonLib.context(plugin).onShutdown(this);
        }
    }

    /**
     * Lets server owners turn modules off in a config file. A module is enabled unless
     * {@code <path>.<id>} is {@code false}. Missing keys are added as {@code true} and saved, so
     * the file lists every module.
     *
     * @param file the config file
     * @param path the section, e.g. {@code "modules"}
     * @return this manager
     */
    public ModuleManager toggles(DataFile file, String path) {
        this.toggleFile = file;
        this.togglePath = path;
        return this;
    }

    /**
     * Registers modules. Register everything before calling {@link #enableAll()}.
     *
     * @param more the modules
     * @return this manager
     * @throws IllegalArgumentException if a module class or id is registered twice
     */
    public synchronized ModuleManager register(MoonModule... more) {
        for (MoonModule module : more) {
            if (modules.containsKey(module.getClass())) {
                throw new IllegalArgumentException(module.getClass().getSimpleName() + " is registered twice");
            }
            for (MoonModule existing : modules.values()) {
                if (existing.id().equals(module.id())) {
                    throw new IllegalArgumentException("Two modules use the id '" + module.id() + "': "
                            + existing.getClass().getSimpleName() + " and " + module.getClass().getSimpleName());
                }
            }
            module.manager = this;
            modules.put(module.getClass(), module);
            states.put(module, State.REGISTERED);
        }
        return this;
    }

    /**
     * Loads every module, then enables them in dependency order.
     *
     * @return the number of modules now enabled
     * @throws IllegalStateException if the dependencies form a cycle
     */
    public synchronized int enableAll() {
        List<MoonModule> order = sort();
        boolean dirtyToggles = false;
        Set<MoonModule> loaded = new HashSet<>();
        for (MoonModule module : order) {
            if (module.isEnabled()) {
                loaded.add(module);
                continue;
            }
            if (toggleFile != null) {
                String key = togglePath + "." + module.id();
                if (!toggleFile.contains(key)) {
                    toggleFile.set(key, true);
                    dirtyToggles = true;
                }
                if (!toggleFile.getBoolean(key, true)) {
                    states.put(module, State.TURNED_OFF);
                    logger(module).info("Turned off in config (" + key + ")");
                    continue;
                }
            }
            try {
                MoonLib.runAs(plugin, () -> {
                    try {
                        module.callLoad();
                    } catch (Exception e) {
                        throw new ModuleFailure(e);
                    }
                });
                loaded.add(module);
            } catch (RuntimeException e) {
                fail(module, "load", unwrap(e));
            }
        }
        if (dirtyToggles) {
            toggleFile.save();
        }
        int count = 0;
        for (MoonModule module : order) {
            if (module.isEnabled()) {
                count++;
                continue;
            }
            if (!loaded.contains(module)) {
                continue;
            }
            String missing = missingDependency(module);
            if (missing != null) {
                states.put(module, State.SKIPPED);
                problems.put(module, missing);
                logger(module).warning("Not enabled: " + missing);
                continue;
            }
            if (start(module)) {
                count++;
            }
        }
        return count;
    }

    /**
     * Disables every module, in reverse order of enabling.
     */
    public synchronized void disableAll() {
        List<MoonModule> reversed = new ArrayList<>(enableOrder);
        Collections.reverse(reversed);
        for (MoonModule module : reversed) {
            stop(module);
        }
        enableOrder.clear();
    }

    /**
     * Reloads one module (by default: disable and enable it again).
     *
     * @param id the module id
     * @return {@code true} if the module is enabled afterwards
     * @throws IllegalArgumentException if no module has that id
     */
    public synchronized boolean reload(String id) {
        MoonModule module = byId(id);
        if (module == null) {
            throw new IllegalArgumentException("No module with id '" + id + "'");
        }
        if (!module.isEnabled()) {
            return start(module);
        }
        try {
            MoonLib.runAs(plugin, () -> {
                try {
                    module.callReload();
                } catch (Exception e) {
                    throw new ModuleFailure(e);
                }
            });
        } catch (RuntimeException e) {
            logger(module).log(Level.SEVERE, "Reload failed", unwrap(e));
        }
        return module.isEnabled();
    }

    /**
     * Reloads every enabled module, in enable order.
     */
    public synchronized void reloadAll() {
        for (MoonModule module : new ArrayList<>(enableOrder)) {
            reload(module.id());
        }
    }

    synchronized void restart(MoonModule module) {
        stop(module);
        start(module);
    }

    private boolean start(MoonModule module) {
        try {
            MoonLib.runAs(plugin, () -> {
                try {
                    module.callEnable();
                } catch (Exception e) {
                    throw new ModuleFailure(e);
                }
            });
            module.markEnabled(true);
            states.put(module, State.ENABLED);
            problems.remove(module);
            if (!enableOrder.contains(module)) {
                enableOrder.add(module);
            }
            return true;
        } catch (RuntimeException e) {
            module.runCleanup();
            fail(module, "enable", unwrap(e));
            return false;
        }
    }

    private void stop(MoonModule module) {
        if (!module.isEnabled()) {
            return;
        }
        // Dependants go first, so nothing keeps using this module after it stops.
        List<MoonModule> reversed = new ArrayList<>(enableOrder);
        Collections.reverse(reversed);
        for (MoonModule other : reversed) {
            if (other != module && other.isEnabled() && other.dependencies().contains(module.getClass())) {
                stop(other);
            }
        }
        try {
            MoonLib.runAs(plugin, () -> {
                try {
                    module.callDisable();
                } catch (Exception e) {
                    throw new ModuleFailure(e);
                }
            });
        } catch (RuntimeException e) {
            logger(module).log(Level.SEVERE, "Error while disabling", unwrap(e));
        }
        module.runCleanup();
        module.markEnabled(false);
        states.put(module, State.DISABLED);
        enableOrder.remove(module);
    }

    private void fail(MoonModule module, String phase, Throwable cause) {
        states.put(module, State.FAILED);
        problems.put(module, phase + " failed: " + cause);
        logger(module).log(Level.SEVERE, "Could not " + phase + "; the module is off, the rest of the plugin "
                + "keeps running", cause);
    }

    private String missingDependency(MoonModule module) {
        for (Class<? extends MoonModule> dependency : module.dependencies()) {
            MoonModule found = modules.get(dependency);
            if (found == null) {
                return "needs " + dependency.getSimpleName() + ", which is not registered";
            }
            if (!found.isEnabled()) {
                return "needs module '" + found.id() + "', which is " + states.get(found).name().toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
            }
        }
        return null;
    }

    /** Topological sort; registration order breaks ties so behaviour is predictable. */
    private List<MoonModule> sort() {
        List<MoonModule> out = new ArrayList<>();
        Set<MoonModule> done = new HashSet<>();
        Set<MoonModule> visiting = new HashSet<>();
        for (MoonModule module : modules.values()) {
            visit(module, done, visiting, out, new ArrayList<String>());
        }
        return out;
    }

    private void visit(MoonModule module, Set<MoonModule> done, Set<MoonModule> visiting, List<MoonModule> out,
                       List<String> path) {
        if (done.contains(module)) {
            return;
        }
        path.add(module.id());
        if (!visiting.add(module)) {
            throw new IllegalStateException("Module dependency cycle: " + String.join(" -> ", path));
        }
        List<Class<? extends MoonModule>> before = new ArrayList<>(module.dependencies());
        before.addAll(module.softDependencies());
        for (Class<? extends MoonModule> type : before) {
            MoonModule dependency = modules.get(type);
            if (dependency != null) {
                visit(dependency, done, visiting, out, new ArrayList<>(path));
            }
        }
        visiting.remove(module);
        done.add(module);
        out.add(module);
    }

    // ------------------------------------------------------------------ lookups

    /**
     * Returns a registered module by class.
     *
     * @param type the module class
     * @param <M>  the module type
     * @return the module, or {@code null}
     */
    public synchronized <M extends MoonModule> M get(Class<M> type) {
        return type.cast(modules.get(type));
    }

    /**
     * Returns a registered module by id.
     *
     * @param id the id
     * @return the module, or {@code null}
     */
    public synchronized MoonModule byId(String id) {
        for (MoonModule module : modules.values()) {
            if (module.id().equalsIgnoreCase(id)) {
                return module;
            }
        }
        return null;
    }

    /**
     * Returns every registered module, in registration order.
     *
     * @return the modules
     */
    public synchronized List<MoonModule> modules() {
        return Collections.unmodifiableList(new ArrayList<>(modules.values()));
    }

    /**
     * Returns what happened to a module, for status commands.
     *
     * @param module the module
     * @return its state
     */
    public synchronized State state(MoonModule module) {
        State state = states.get(module);
        return state == null ? State.REGISTERED : state;
    }

    /**
     * Returns why a module is not enabled, if it failed or was skipped.
     *
     * @param module the module
     * @return the reason, or {@code null}
     */
    public synchronized String problem(MoonModule module) {
        return problems.get(module);
    }

    /**
     * Returns the plugin's service registry, shared by all its modules.
     *
     * @return the services
     */
    public Services services() {
        return services;
    }

    /**
     * Returns the owning plugin.
     *
     * @return the plugin
     */
    public Plugin plugin() {
        return plugin;
    }

    /** Disables every module; called automatically when the plugin shuts down MoonLib. */
    @Override
    public void close() {
        disableAll();
    }

    Logger logger(MoonModule module) {
        Logger base = plugin.getLogger();
        String prefix = "[" + module.id() + "] ";
        return new Logger(base.getName() + "." + module.id(), null) {
            {
                setParent(base);
                setUseParentHandlers(true);
            }

            @Override
            public void log(LogRecord record) {
                record.setMessage(prefix + record.getMessage());
                base.log(record);
            }
        };
    }

    private static Throwable unwrap(Throwable t) {
        return t instanceof ModuleFailure && t.getCause() != null ? t.getCause() : t;
    }

    /** Carries a checked exception out of a lambda. */
    private static final class ModuleFailure extends RuntimeException {
        private static final long serialVersionUID = 1L;

        ModuleFailure(Throwable cause) {
            super(cause);
        }
    }
}
