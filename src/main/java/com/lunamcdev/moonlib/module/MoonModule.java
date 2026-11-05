package com.lunamcdev.moonlib.module;

import com.lunamcdev.moonlib.MoonLib;
import com.lunamcdev.moonlib.command.CommandRegistry;
import com.lunamcdev.moonlib.command.MoonCommand;
import com.lunamcdev.moonlib.command.annotation.AnnotatedCommands;
import com.lunamcdev.moonlib.event.EventSubscription;
import com.lunamcdev.moonlib.event.Events;
import com.lunamcdev.moonlib.scheduler.TaskHandle;
import org.bukkit.Bukkit;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * One self-contained feature of a plugin, such as "mines", "ranks" or "crates", with its own
 * lifecycle. Splitting a large plugin into modules keeps features independent: each can be turned
 * off in config, fails on its own without taking the plugin down, and cleans up after itself.
 *
 * <pre>{@code
 * public final class RanksModule extends MoonModule {
 *
 *     public RanksModule() {
 *         super("ranks", EconomyModule.class);           // enabled after EconomyModule
 *     }
 *
 *     @Override
 *     protected void enable() {
 *         RankService ranks = new RankService(require(EconomyModule.class).economy());
 *         provide(RankService.class, ranks);              // other modules can use it
 *         listen(new RankupListener(ranks));              // unregistered on disable
 *         command(new RankupCommand(ranks));              // annotated or MoonCommand
 *         track(Tasks.timer(0, 1200, ranks::saveAll));    // cancelled on disable
 *     }
 * }
 * }</pre>
 *
 * <p>Everything registered through {@link #listen}, {@link #command}, {@link #track},
 * {@link #onDisable} and {@link #provide} is undone automatically when the module is disabled, so
 * {@link #disable()} only needs to save data.</p>
 *
 * @since 1.2.0
 */
public abstract class MoonModule {

    private final String id;
    private final List<Class<? extends MoonModule>> dependencies;
    private final List<Class<? extends MoonModule>> softDependencies = new ArrayList<>();
    private final List<Runnable> cleanup = new ArrayList<>();
    ModuleManager manager;
    private volatile boolean enabled;

    /**
     * Creates a module.
     *
     * @param id           a short lower-case id, used in logs and as the config toggle
     *                     ({@code modules.<id>})
     * @param dependencies modules that must be enabled first; if one fails or is turned off, this
     *                     module is skipped
     */
    @SafeVarargs
    protected MoonModule(String id, Class<? extends MoonModule>... dependencies) {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("A module needs an id");
        }
        this.id = id.trim().toLowerCase(Locale.ROOT);
        List<Class<? extends MoonModule>> copy = new ArrayList<>();
        for (Class<? extends MoonModule> dependency : dependencies) {
            copy.add(dependency);
        }
        this.dependencies = Collections.unmodifiableList(copy);
    }

    /**
     * Declares modules that should be enabled first if they are present, without requiring them.
     *
     * @param modules the optional dependencies
     */
    @SafeVarargs
    protected final void softDepend(Class<? extends MoonModule>... modules) {
        for (Class<? extends MoonModule> module : modules) {
            softDependencies.add(module);
        }
    }

    // ------------------------------------------------------------------ lifecycle

    /**
     * Called once, for every registered module, before any module is enabled. Use it for setup
     * that other modules' {@link #enable()} may rely on. Optional.
     *
     * @throws Exception to skip the module
     */
    protected void load() throws Exception {
    }

    /**
     * Starts the module.
     *
     * @throws Exception to mark the module as failed; it is then cleaned up and modules depending
     *                   on it are skipped
     */
    protected abstract void enable() throws Exception;

    /**
     * Stops the module: save data here. Registrations are undone automatically afterwards.
     * Optional.
     *
     * @throws Exception logged; cleanup still happens
     */
    protected void disable() throws Exception {
    }

    /**
     * Applies changed configuration without a full restart. The default disables and re-enables
     * the module; override for something cheaper.
     *
     * @throws Exception logged; the module is left disabled if re-enabling fails
     */
    protected void reload() throws Exception {
        manager.restart(this);
    }

    // ------------------------------------------------------------------ helpers

    /**
     * Registers a listener until the module is disabled.
     *
     * @param listener the listener
     * @param <L>      the listener type
     * @return the listener
     */
    protected final <L extends Listener> L listen(L listener) {
        Bukkit.getPluginManager().registerEvents(listener, plugin());
        cleanup.add(() -> HandlerList.unregisterAll(listener));
        return listener;
    }

    /**
     * Listens to an event with a lambda until the module is disabled.
     *
     * <pre>{@code
     * on(PlayerJoinEvent.class, event -> event.getPlayer().sendMessage("Welcome!"));
     * }</pre>
     *
     * @param type    the event class
     * @param handler the handler
     * @param <T>     the event type
     * @return the subscription
     */
    protected final <T extends Event> EventSubscription<T> on(Class<T> type, Consumer<T> handler) {
        EventSubscription<T> subscription = MoonLib.callAs(plugin(), () -> Events.listen(type, handler));
        cleanup.add(subscription::unregister);
        return subscription;
    }

    /**
     * Registers commands until the module is disabled. Accepts {@link MoonCommand}s and annotated
     * handler objects.
     *
     * @param commands the commands or handlers
     */
    protected final void command(Object... commands) {
        for (Object command : commands) {
            List<MoonCommand> registered = new ArrayList<>();
            if (command instanceof MoonCommand) {
                CommandRegistry.register(plugin(), (MoonCommand) command);
                registered.add((MoonCommand) command);
            } else {
                registered.addAll(AnnotatedCommands.register(plugin(), command));
            }
            for (MoonCommand moonCommand : registered) {
                cleanup.add(() -> CommandRegistry.unregister(moonCommand));
            }
        }
    }

    /**
     * Cancels a task when the module is disabled.
     *
     * @param task the task
     * @return the task
     */
    protected final TaskHandle track(TaskHandle task) {
        cleanup.add(task::cancel);
        return task;
    }

    /**
     * Runs an action when the module is disabled, after {@link #disable()}. Actions run in
     * reverse order of registration.
     *
     * @param action the action
     */
    protected final void onDisable(Runnable action) {
        cleanup.add(action);
    }

    /**
     * Offers a service to other modules (and other code via {@link ModuleManager#services()}) until
     * this module is disabled.
     *
     * @param type    the service type other modules look up
     * @param service the implementation
     * @param <T>     the service type
     * @return the service
     */
    protected final <T> T provide(Class<T> type, T service) {
        manager.services().register(type, service, this);
        cleanup.add(() -> manager.services().unregister(type, service));
        return service;
    }

    /**
     * Looks up a service provided by another module.
     *
     * @param type the service type
     * @param <T>  the service type
     * @return the service
     * @throws IllegalStateException if nothing provides it
     */
    protected final <T> T service(Class<T> type) {
        return manager.services().require(type);
    }

    /**
     * Returns another enabled module, typically a declared dependency.
     *
     * @param type the module class
     * @param <M>  the module type
     * @return the module
     * @throws IllegalStateException if it is not registered or not enabled
     */
    protected final <M extends MoonModule> M require(Class<M> type) {
        M module = manager.get(type);
        if (module == null || !module.isEnabled()) {
            throw new IllegalStateException("Module " + id + " needs " + type.getSimpleName() + ", which is not enabled");
        }
        return module;
    }

    // ------------------------------------------------------------------ accessors

    /**
     * Returns the module id.
     *
     * @return the id
     */
    public final String id() {
        return id;
    }

    /**
     * Returns the owning plugin.
     *
     * @return the plugin
     * @throws IllegalStateException before the module is registered
     */
    public final Plugin plugin() {
        if (manager == null) {
            throw new IllegalStateException("Module " + id + " is not registered with a ModuleManager yet");
        }
        return manager.plugin();
    }

    /**
     * Returns the plugin's logger, with messages prefixed by the module id.
     *
     * @return a logger
     */
    protected final Logger logger() {
        return manager.logger(this);
    }

    /**
     * Returns whether the module is enabled.
     *
     * @return {@code true} while enabled
     */
    public final boolean isEnabled() {
        return enabled;
    }

    /**
     * Returns the required dependencies.
     *
     * @return the dependency classes
     */
    public final List<Class<? extends MoonModule>> dependencies() {
        return dependencies;
    }

    /**
     * Returns the optional dependencies.
     *
     * @return the soft dependency classes
     */
    public final List<Class<? extends MoonModule>> softDependencies() {
        return Collections.unmodifiableList(softDependencies);
    }

    // ------------------------------------------------------------------ manager hooks

    final void markEnabled(boolean value) {
        this.enabled = value;
    }

    final void runCleanup() {
        for (int i = cleanup.size() - 1; i >= 0; i--) {
            try {
                cleanup.get(i).run();
            } catch (RuntimeException e) {
                manager.logger(this).warning("Cleanup step failed: " + e);
            }
        }
        cleanup.clear();
    }

    final void callLoad() throws Exception {
        load();
    }

    final void callEnable() throws Exception {
        enable();
    }

    final void callDisable() throws Exception {
        disable();
    }

    final void callReload() throws Exception {
        reload();
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" + id + (enabled ? ", enabled" : "") + "]";
    }
}
