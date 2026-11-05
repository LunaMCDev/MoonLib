package dev.lunamc.moonlib.util;

import dev.lunamc.moonlib.MoonLib;
import dev.lunamc.moonlib.scheduler.Tasks;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * Runs code when files change on disk, for example to reload a config the moment a server owner
 * saves it in their editor.
 *
 * <pre>{@code
 * FileWatcher.watch(this, new File(getDataFolder(), "config.yml"), () -> {
 *     reloadConfig();
 *     getLogger().info("config.yml reloaded");
 * });
 *
 * // every file in a folder:
 * FileWatcher.watchFolder(this, new File(getDataFolder(), "mines"), changed -> mines.reload(changed));
 *
 * // a MoonLib file reloads itself and then calls you:
 * messages.file().watch(this, () -> getLogger().info("messages.yml reloaded"));
 * }</pre>
 *
 * <p>Editors often write a file several times in a row; changes are debounced so the callback runs
 * once, 300 ms after the last write. Callbacks run on the server thread (the global region thread
 * on Folia). Watching uses the operating system's file notifications, so it costs nothing while
 * files are unchanged. All watches end when the plugin shuts down MoonLib.</p>
 *
 * @since 1.4.0
 */
public final class FileWatcher implements AutoCloseable {

    private static final long DEBOUNCE_MS = 300;
    private static final Map<Plugin, FileWatcher> WATCHERS = new ConcurrentHashMap<>();

    private final Plugin plugin;
    private final WatchService service;
    private final Thread thread;
    private final Map<Path, WatchKey> keys = new ConcurrentHashMap<>();
    private final List<Watch> watches = new CopyOnWriteArrayList<>();
    private final Map<Watch, Map<Path, Long>> pending = new ConcurrentHashMap<>();
    private volatile boolean running = true;

    /**
     * One active watch. Close it to stop watching.
     */
    public static final class Watch implements AutoCloseable {
        private final FileWatcher owner;
        private final Path folder;
        private final Path fileName;
        private final Consumer<File> callback;
        private final boolean recursive;

        private Watch(FileWatcher owner, Path folder, Path fileName, Consumer<File> callback, boolean recursive) {
            this.owner = owner;
            this.folder = folder;
            this.fileName = fileName;
            this.callback = callback;
            this.recursive = recursive;
        }

        boolean matches(Path dir, Path name) {
            if (recursive) {
                return dir.startsWith(folder);
            }
            return folder.equals(dir) && (fileName == null || fileName.equals(name));
        }

        /** Stops this watch. */
        @Override
        public void close() {
            owner.watches.remove(this);
            owner.pending.remove(this);
        }
    }

    private FileWatcher(Plugin plugin) throws IOException {
        this.plugin = plugin;
        this.service = FileSystems.getDefault().newWatchService();
        this.thread = new Thread(this::loop, plugin.getName() + "-FileWatcher");
        this.thread.setDaemon(true);
        this.thread.start();
        if (MoonLib.isInitialised(plugin)) {
            MoonLib.context(plugin).onShutdown(this);
        }
    }

    /**
     * Calls {@code onChange} whenever a file is created or modified. The file does not need to
     * exist yet, but its folder does (it is created if missing).
     *
     * @param plugin   the owning plugin
     * @param file     the file
     * @param onChange runs on the server thread after the file changes
     * @return the watch
     * @throws IllegalStateException if the folder cannot be watched
     */
    public static Watch watch(Plugin plugin, File file, Runnable onChange) {
        File absolute = file.getAbsoluteFile();
        return forPlugin(plugin).add(absolute.getParentFile().toPath(), absolute.toPath().getFileName(),
                changed -> onChange.run());
    }

    /**
     * Calls {@code onChange} with each file that is created or modified inside a folder (not its
     * subfolders).
     *
     * @param plugin   the owning plugin
     * @param folder   the folder (created if missing)
     * @param onChange runs on the server thread with the changed file
     * @return the watch
     * @throws IllegalStateException if the folder cannot be watched
     */
    public static Watch watchFolder(Plugin plugin, File folder, Consumer<File> onChange) {
        return forPlugin(plugin).add(folder.getAbsoluteFile().toPath(), null, onChange);
    }

    /**
     * Calls {@code onChange} with each file that is created or modified anywhere inside a folder,
     * including subfolders (new subfolders are picked up automatically). Useful for build output
     * or folders of content files.
     *
     * @param plugin   the owning plugin
     * @param folder   the root folder (created if missing)
     * @param onChange runs on the server thread with each changed file (not folders)
     * @return the watch
     * @throws IllegalStateException if the folder cannot be watched
     */
    public static Watch watchTree(Plugin plugin, File folder, Consumer<File> onChange) {
        FileWatcher watcher = forPlugin(plugin);
        Path root = folder.getAbsoluteFile().toPath();
        Watch watch = watcher.add(root, null, onChange, true);
        watcher.registerTree(root);
        return watch;
    }

    private void registerTree(Path root) {
        try {
            java.nio.file.Files.walk(root).filter(java.nio.file.Files::isDirectory).forEach(this::register);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot watch " + root + ": " + e.getMessage(), e);
        }
    }

    private void register(Path dir) {
        keys.computeIfAbsent(dir, f -> {
            try {
                return f.register(service, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_MODIFY);
            } catch (IOException e) {
                throw new IllegalStateException("Cannot watch " + f + ": " + e.getMessage(), e);
            }
        });
    }

    private static FileWatcher forPlugin(Plugin plugin) {
        return WATCHERS.computeIfAbsent(plugin, p -> {
            try {
                return new FileWatcher(p);
            } catch (IOException e) {
                throw new IllegalStateException("File watching is not available: " + e.getMessage(), e);
            }
        });
    }

    private Watch add(Path folder, Path fileName, Consumer<File> callback) {
        return add(folder, fileName, callback, false);
    }

    private Watch add(Path folder, Path fileName, Consumer<File> callback, boolean recursive) {
        File dir = folder.toFile();
        if (!dir.isDirectory() && !dir.mkdirs()) {
            throw new IllegalStateException("Cannot watch " + folder + ": the folder could not be created");
        }
        register(folder);
        Watch watch = new Watch(this, folder, fileName, callback, recursive);
        watches.add(watch);
        return watch;
    }

    private void loop() {
        while (running) {
            WatchKey key;
            try {
                key = service.poll(100, TimeUnit.MILLISECONDS);
            } catch (InterruptedException | ClosedWatchServiceException e) {
                return;
            }
            if (key != null) {
                Path dir = (Path) key.watchable();
                for (WatchEvent<?> event : key.pollEvents()) {
                    if (event.kind() == StandardWatchEventKinds.OVERFLOW) {
                        continue;
                    }
                    Path name = (Path) event.context();
                    long now = System.currentTimeMillis();
                    Path full = dir.resolve(name);
                    if (java.nio.file.Files.isDirectory(full)) {
                        boolean underTree = false;
                        for (Watch watch : watches) {
                            underTree |= watch.recursive && full.startsWith(watch.folder);
                        }
                        if (underTree) {
                            try {
                                registerTree(full);
                                // Files written before the folder was registered would be missed otherwise.
                                java.nio.file.Files.walk(full).filter(java.nio.file.Files::isRegularFile).forEach(existing -> {
                                    for (Watch watch : watches) {
                                        if (watch.recursive && existing.startsWith(watch.folder)) {
                                            pending.computeIfAbsent(watch, w -> new ConcurrentHashMap<>()).put(existing, now);
                                        }
                                    }
                                });
                            } catch (IllegalStateException | IOException ignored) {
                                // the folder vanished again
                            }
                        }
                        continue;
                    }
                    for (Watch watch : watches) {
                        if (watch.matches(dir, name)) {
                            pending.computeIfAbsent(watch, w -> new ConcurrentHashMap<>()).put(dir.resolve(name), now);
                        }
                    }
                }
                key.reset();
            }
            firePending();
        }
    }

    private void firePending() {
        long now = System.currentTimeMillis();
        for (Map.Entry<Watch, Map<Path, Long>> entry : pending.entrySet()) {
            List<File> ready = new ArrayList<>();
            Iterator<Map.Entry<Path, Long>> it = entry.getValue().entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<Path, Long> change = it.next();
                if (now - change.getValue() >= DEBOUNCE_MS) {
                    ready.add(change.getKey().toFile());
                    it.remove();
                }
            }
            Watch watch = entry.getKey();
            for (File file : ready) {
                MoonLib.runAs(plugin, () -> Tasks.sync(() -> {
                    try {
                        watch.callback.accept(file);
                    } catch (RuntimeException e) {
                        plugin.getLogger().log(Level.SEVERE, "Error while handling a change to " + file.getName(), e);
                    }
                }));
            }
        }
    }

    /** Stops every watch of this plugin. Called automatically on shutdown. */
    @Override
    public void close() {
        running = false;
        WATCHERS.remove(plugin, this);
        watches.clear();
        try {
            service.close();
        } catch (IOException ignored) {
            // closing anyway
        }
        thread.interrupt();
    }
}
