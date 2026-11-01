package dev.lunamc.moonlib.config;

import dev.lunamc.moonlib.scheduler.Tasks;
import dev.lunamc.moonlib.util.Log;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * A folder of files, one per key, each holding one object. The simplest way to persist per-player data.
 *
 * <pre>{@code
 * public final class PlayerData {
 *     private int tokens;
 *     private String rank = "A";
 *     private Set<String> unlockedMines = new HashSet<>();
 * }
 *
 * DataStore<PlayerData> players = DataStore.json(plugin, "players", PlayerData.class);
 *
 * // on join (async, then back on the player's thread)
 * players.loadAsync(uuid, PlayerData::new)
 *        .thenAccept(data -> Tasks.entity(player, () -> cache.put(uuid, data)));
 *
 * // on quit
 * players.saveAsync(uuid, cache.remove(uuid));
 *
 * players.exists(uuid);  players.delete(uuid);  players.keys();  players.loadAll();
 * }</pre>
 *
 * <p>This writes {@code plugins/YourPlugin/players/<uuid>.json}. Saves are atomic and snapshot the
 * object on the calling thread, so you can keep modifying it while the async save runs. Keys may only
 * contain letters, digits, {@code _}, {@code -} and {@code .}, which also rules out path tricks like
 * {@code ../}.</p>
 *
 * @param <T> the stored type
 */
public final class DataStore<T> {

    private static final Pattern VALID_KEY = Pattern.compile("[A-Za-z0-9_.-]{1,128}");

    private final File folder;
    private final Class<T> type;
    private final DataFile.Format format;
    private final String extension;

    private DataStore(File folder, Class<T> type, DataFile.Format format) {
        this.folder = folder;
        this.type = type;
        this.format = format;
        this.extension = format == DataFile.Format.YAML ? ".yml" : ".json";
    }

    /**
     * Creates a store of JSON files in a folder inside the plugin's data folder.
     *
     * @param plugin the plugin
     * @param folder the folder name, e.g. {@code "players"}
     * @param type   the stored class
     * @param <T>    the stored type
     * @return the store
     */
    public static <T> DataStore<T> json(Plugin plugin, String folder, Class<T> type) {
        return new DataStore<>(new File(plugin.getDataFolder(), folder), type, DataFile.Format.JSON);
    }

    /**
     * Creates a store of YAML files in a folder inside the plugin's data folder.
     *
     * @param plugin the plugin
     * @param folder the folder name, e.g. {@code "players"}
     * @param type   the stored class
     * @param <T>    the stored type
     * @return the store
     */
    public static <T> DataStore<T> yaml(Plugin plugin, String folder, Class<T> type) {
        return new DataStore<>(new File(plugin.getDataFolder(), folder), type, DataFile.Format.YAML);
    }

    /**
     * Creates a store in any folder.
     *
     * @param folder the folder
     * @param type   the stored class
     * @param format the file format
     * @param <T>    the stored type
     * @return the store
     */
    public static <T> DataStore<T> of(File folder, Class<T> type, DataFile.Format format) {
        return new DataStore<>(folder, type, format);
    }

    // ------------------------------------------------------------------ loading

    /**
     * Loads an entry.
     *
     * @param key the key, e.g. a player's UUID as text
     * @return the object, or {@code null} if there is no file for this key
     * @throws IllegalArgumentException if the file exists but can't be read as the stored type
     */
    public T load(String key) {
        File file = fileFor(key);
        if (!file.exists()) {
            return null;
        }
        return open(file).as(type);
    }

    /**
     * Loads an entry, or creates a new object if there is none yet (the new object is not saved).
     *
     * @param key     the key
     * @param creator makes a fresh object, e.g. {@code PlayerData::new}
     * @return the loaded or new object
     */
    public T load(String key, Supplier<T> creator) {
        T value = load(key);
        return value == null ? creator.get() : value;
    }

    /**
     * Loads an entry by UUID.
     *
     * @param id the UUID
     * @return the object, or {@code null}
     */
    public T load(UUID id) {
        return load(id.toString());
    }

    /**
     * Loads an entry by UUID, or creates a new object.
     *
     * @param id      the UUID
     * @param creator makes a fresh object
     * @return the loaded or new object
     */
    public T load(UUID id, Supplier<T> creator) {
        return load(id.toString(), creator);
    }

    /**
     * Loads an entry off the main thread.
     *
     * @param key the key
     * @return a future with the object, or {@code null} if there is none
     */
    public CompletableFuture<T> loadAsync(String key) {
        return async(() -> load(key));
    }

    /**
     * Loads an entry off the main thread, creating a new object if there is none.
     *
     * @param key     the key
     * @param creator makes a fresh object
     * @return a future with the loaded or new object
     */
    public CompletableFuture<T> loadAsync(String key, Supplier<T> creator) {
        return async(() -> load(key, creator));
    }

    /**
     * Loads an entry by UUID off the main thread, creating a new object if there is none.
     *
     * @param id      the UUID
     * @param creator makes a fresh object
     * @return a future with the loaded or new object
     */
    public CompletableFuture<T> loadAsync(UUID id, Supplier<T> creator) {
        return loadAsync(id.toString(), creator);
    }

    /**
     * Loads every entry. Entries that can't be read are logged and skipped.
     *
     * @return key to object, sorted by key
     */
    public Map<String, T> loadAll() {
        Map<String, T> all = new LinkedHashMap<>();
        for (String key : keys()) {
            try {
                T value = load(key);
                if (value != null) {
                    all.put(key, value);
                }
            } catch (RuntimeException e) {
                Log.error("Skipping unreadable entry '" + key + "' in " + folder.getName(), e);
            }
        }
        return all;
    }

    // ------------------------------------------------------------------ saving

    /**
     * Saves an entry now, replacing any existing file.
     *
     * @param key   the key
     * @param value the object; {@code null} deletes the entry
     * @return {@code true} if saved
     */
    public boolean save(String key, T value) {
        if (value == null) {
            return delete(key);
        }
        return blank(fileFor(key)).setRoot(value).save();
    }

    /**
     * Saves an entry by UUID now.
     *
     * @param id    the UUID
     * @param value the object
     * @return {@code true} if saved
     */
    public boolean save(UUID id, T value) {
        return save(id.toString(), value);
    }

    /**
     * Saves an entry off the main thread. The object is converted immediately on the calling thread.
     *
     * @param key   the key
     * @param value the object; {@code null} deletes the entry
     * @return a future completed once written
     */
    public CompletableFuture<Void> saveAsync(String key, T value) {
        if (value == null) {
            return async(() -> {
                delete(key);
                return null;
            });
        }
        return blank(fileFor(key)).setRoot(value).saveAsync();
    }

    /**
     * Saves an entry by UUID off the main thread.
     *
     * @param id    the UUID
     * @param value the object
     * @return a future completed once written
     */
    public CompletableFuture<Void> saveAsync(UUID id, T value) {
        return saveAsync(id.toString(), value);
    }

    // ------------------------------------------------------------------ management

    /**
     * Returns whether an entry exists.
     *
     * @param key the key
     * @return {@code true} if there is a file for it
     */
    public boolean exists(String key) {
        return fileFor(key).exists();
    }

    /**
     * Returns whether an entry exists for a UUID.
     *
     * @param id the UUID
     * @return {@code true} if there is a file for it
     */
    public boolean exists(UUID id) {
        return exists(id.toString());
    }

    /**
     * Deletes an entry.
     *
     * @param key the key
     * @return {@code true} if a file was deleted
     */
    public boolean delete(String key) {
        File file = fileFor(key);
        return file.exists() && file.delete();
    }

    /**
     * Deletes an entry by UUID.
     *
     * @param id the UUID
     * @return {@code true} if a file was deleted
     */
    public boolean delete(UUID id) {
        return delete(id.toString());
    }

    /**
     * Returns every stored key.
     *
     * @return the keys, sorted
     */
    public Set<String> keys() {
        File[] files = folder.listFiles((dir, name) -> name.endsWith(extension));
        if (files == null) {
            return Collections.emptySet();
        }
        Set<String> keys = new TreeSet<>();
        for (File file : files) {
            String name = file.getName();
            keys.add(name.substring(0, name.length() - extension.length()));
        }
        return Collections.unmodifiableSet(keys);
    }

    /**
     * Opens the file of an entry for direct path-based editing.
     *
     * @param key the key
     * @return the loaded file (created if missing)
     */
    public DataFile file(String key) {
        return open(fileFor(key));
    }

    /**
     * Returns the folder holding the files.
     *
     * @return the folder
     */
    public File folder() {
        return folder;
    }

    private File fileFor(String key) {
        if (key == null || !VALID_KEY.matcher(key).matches() || key.startsWith(".")) {
            throw new IllegalArgumentException("Invalid data store key '" + key
                    + "': use letters, digits, '_', '-' and '.' only");
        }
        return new File(folder, key + extension);
    }

    private DataFile open(File file) {
        return format == DataFile.Format.YAML ? new YamlFile(file) : new JsonFile(file);
    }

    /** An empty in-memory file that overwrites whatever is on disk when saved (nothing is read first). */
    private DataFile blank(File file) {
        return format == DataFile.Format.YAML ? new YamlFile(file, false) : new JsonFile(file, false);
    }

    private static <R> CompletableFuture<R> async(Supplier<R> work) {
        try {
            return Tasks.supplyAsync(work);
        } catch (IllegalStateException notInitialised) {
            return CompletableFuture.supplyAsync(work);
        }
    }
}
