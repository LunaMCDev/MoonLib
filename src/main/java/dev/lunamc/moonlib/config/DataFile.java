package dev.lunamc.moonlib.config;

import dev.lunamc.moonlib.scheduler.Tasks;
import dev.lunamc.moonlib.util.Locations;
import dev.lunamc.moonlib.util.Log;
import dev.lunamc.moonlib.util.Text;
import org.bukkit.Location;
import org.bukkit.plugin.Plugin;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * A YAML or JSON file you can create, read, edit and save with one API.
 *
 * <pre>{@code
 * // Create or load (copied from the jar's resource of the same name if it exists)
 * YamlFile mines = DataFile.yaml(plugin, "mines.yml");
 * JsonFile stats = DataFile.json(plugin, "data/stats.json");
 * DataFile any   = DataFile.of(plugin, "warps.json");          // format picked from the extension
 *
 * // Read with dot paths
 * int size = mines.getInt("mines.a.size", 50);
 * List<String> owners = mines.getStringList("mines.a.owners");
 * PlayerData data = stats.get("players." + uuid, PlayerData.class);   // your own classes
 *
 * // Edit and save
 * stats.set("players." + uuid, data).set("updated", Instant.now());
 * stats.save();                 // or saveAsync()
 * stats.edit(f -> f.set("resets", f.getInt("resets", 0) + 1));       // change + save in one call
 * }</pre>
 *
 * <h2>Behaviour shared by both formats</h2>
 * <ul>
 *     <li><b>Paths</b> use dots: {@code "players.<uuid>.tokens"}. Missing parent sections are created by
 *     {@link #set(String, Object)}. The empty path {@code ""} means the whole file.</li>
 *     <li><b>Values</b> are converted by {@link DataMapper}: strings, numbers, booleans, enums, UUIDs, lists,
 *     maps, Bukkit objects such as {@code ItemStack} and {@code Location}, and your own classes and records.</li>
 *     <li><b>Defaults:</b> if a plugin is given and its jar contains a resource with the same path, a missing
 *     file is created from it, and keys added to the resource in later versions are merged into existing
 *     files without touching existing values (see {@link #mergeDefaults(boolean)}).</li>
 *     <li><b>Broken files</b> are never lost: an unparsable file is backed up as
 *     {@code name.broken-<time>.ext}, the problem is logged with its line number, and the defaults are used.</li>
 *     <li><b>Saving is atomic</b> (written to a temporary file, then moved into place), so a crash mid-save
 *     can't leave a half-written file. {@link #saveAsync()} snapshots the data on the calling thread.</li>
 *     <li><b>Thread-safe:</b> all methods synchronise on the file object.</li>
 * </ul>
 */
public abstract class DataFile {

    /** The supported formats. */
    public enum Format {
        /** YAML via Bukkit's {@code YamlConfiguration} (comments are kept on 1.18.1+). */
        YAML,
        /** JSON via MoonLib's own {@link Json} reader and writer. */
        JSON;

        /**
         * Picks a format from a file name's extension.
         *
         * @param fileName e.g. {@code "players.json"}
         * @return the format
         * @throws IllegalArgumentException if the extension is not {@code .yml}, {@code .yaml} or {@code .json}
         */
        public static Format fromFileName(String fileName) {
            String lower = fileName.toLowerCase(Locale.ROOT);
            if (lower.endsWith(".yml") || lower.endsWith(".yaml")) {
                return YAML;
            }
            if (lower.endsWith(".json")) {
                return JSON;
            }
            throw new IllegalArgumentException("Can't tell the format of '" + fileName
                    + "': use a .yml, .yaml or .json extension, or DataFile.yaml/json(...)");
        }
    }

    private final Plugin plugin;
    private final File file;
    /** Hash of the text this instance last wrote, so file watching can ignore our own saves. */
    private volatile int lastWrittenHash;
    private volatile boolean hasWritten;
    private final String resourceName;
    private boolean mergeDefaults = true;

    /**
     * Creates a file handle without loading it. Subclass constructors call {@link #reload()}.
     *
     * @param plugin       the plugin whose jar holds default resources, or {@code null} for none
     * @param file         the file on disk
     * @param resourceName the path of the default resource inside the jar, or {@code null} for none
     */
    protected DataFile(Plugin plugin, File file, String resourceName) {
        this.plugin = plugin;
        this.file = file;
        this.resourceName = resourceName == null ? null : resourceName.replace('\\', '/');
    }

    // ------------------------------------------------------------------ factories

    /**
     * Loads (creating it if needed) a YAML file in a plugin's data folder.
     *
     * @param plugin the plugin
     * @param name   the path inside the data folder, e.g. {@code "mines.yml"} or {@code "data/ranks.yml"}
     * @return the loaded file
     */
    public static YamlFile yaml(Plugin plugin, String name) {
        return new YamlFile(plugin, name);
    }

    /**
     * Loads (creating it if needed) a JSON file in a plugin's data folder.
     *
     * @param plugin the plugin
     * @param name   the path inside the data folder, e.g. {@code "data/stats.json"}
     * @return the loaded file
     */
    public static JsonFile json(Plugin plugin, String name) {
        return new JsonFile(plugin, name);
    }

    /**
     * Loads a YAML or JSON file in a plugin's data folder, choosing the format from the extension.
     *
     * @param plugin the plugin
     * @param name   the path inside the data folder
     * @return the loaded file
     * @throws IllegalArgumentException if the extension is not recognised
     */
    public static DataFile of(Plugin plugin, String name) {
        return Format.fromFileName(name) == Format.YAML ? yaml(plugin, name) : json(plugin, name);
    }

    /**
     * Loads (creating it if needed) a YAML file at any location, without default resources.
     *
     * @param file the file
     * @return the loaded file
     */
    public static YamlFile yaml(File file) {
        return new YamlFile(file);
    }

    /**
     * Loads (creating it if needed) a JSON file at any location, without default resources.
     *
     * @param file the file
     * @return the loaded file
     */
    public static JsonFile json(File file) {
        return new JsonFile(file);
    }

    /**
     * Loads a YAML or JSON file at any location, choosing the format from the extension.
     *
     * @param file the file
     * @return the loaded file
     */
    public static DataFile of(File file) {
        return Format.fromFileName(file.getName()) == Format.YAML ? yaml(file) : json(file);
    }

    // ------------------------------------------------------------------ lifecycle

    /**
     * Re-reads the file from disk, creating it first if it does not exist.
     *
     * @return this file
     */
    public final synchronized DataFile reload() {
        String defaults = readDefaults();
        if (!file.exists()) {
            try {
                writeAtomically(file, defaults != null ? defaults : emptyContent());
            } catch (IOException e) {
                Log.error("Could not create " + file.getPath(), e);
            }
        }
        String text = "";
        if (file.exists()) {
            try {
                text = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            } catch (IOException e) {
                Log.error("Could not read " + file.getPath(), e);
            }
        }
        try {
            parse(text);
        } catch (MalformedDataException e) {
            backupBrokenFile(e);
            try {
                parse(defaults != null ? defaults : emptyContent());
            } catch (MalformedDataException fatal) {
                Log.error("The default " + resourceName + " inside the plugin jar is itself invalid", fatal);
                clear();
            }
        }
        if (mergeDefaults && defaults != null) {
            try {
                if (mergeMissing(defaults)) {
                    save();
                }
            } catch (MalformedDataException e) {
                Log.error("The default " + resourceName + " inside the plugin jar is invalid", e);
            }
        }
        return this;
    }

    /**
     * Writes the file to disk now.
     *
     * @return {@code true} if it was saved, {@code false} if an error was logged
     */
    public synchronized boolean save() {
        try {
            String text = serialize();
            writeAtomically(file, text);
            rememberWrite(text);
            return true;
        } catch (IOException e) {
            Log.error("Could not save " + file.getPath(), e);
            return false;
        }
    }

    /**
     * Writes the file on another thread. The contents are captured immediately, so changes made after
     * this call are not included.
     *
     * @return a future completed once the file is written
     */
    public CompletableFuture<Void> saveAsync() {
        String snapshot;
        synchronized (this) {
            snapshot = serialize();
        }
        CompletableFuture<Void> future = new CompletableFuture<>();
        Runnable write = () -> {
            try {
                writeAtomically(file, snapshot);
                rememberWrite(snapshot);
                future.complete(null);
            } catch (IOException e) {
                Log.error("Could not save " + file.getPath(), e);
                future.completeExceptionally(e);
            }
        };
        try {
            Tasks.async(write);
        } catch (IllegalStateException notInitialised) {
            CompletableFuture.runAsync(write);
        }
        return future;
    }

    /**
     * Runs changes and saves the file straight after.
     *
     * @param changes the edits
     * @return this file
     */
    public synchronized DataFile edit(Consumer<DataFile> changes) {
        changes.accept(this);
        save();
        return this;
    }

    /**
     * Enables or disables merging new keys from the jar's default resource on {@link #reload()}.
     * Enabled by default. Takes effect on the next reload.
     *
     * @param merge {@code true} to merge
     * @return this file
     */
    public DataFile mergeDefaults(boolean merge) {
        this.mergeDefaults = merge;
        return this;
    }

    /**
     * Returns whether the file exists on disk.
     *
     * @return {@code true} if it exists
     */
    public boolean exists() {
        return file.exists();
    }

    /**
     * Deletes the file from disk. The data stays in memory until {@link #reload()} or {@link #clear()}.
     *
     * @return {@code true} if a file was deleted
     */
    public synchronized boolean delete() {
        try {
            return Files.deleteIfExists(file.toPath());
        } catch (IOException e) {
            Log.error("Could not delete " + file.getPath(), e);
            return false;
        }
    }

    /**
     * Returns the file on disk.
     *
     * @return the file
     */
    public File file() {
        return file;
    }

    /**
     * Returns the file name, e.g. {@code "stats.json"}.
     *
     * @return the name
     */
    public String name() {
        return file.getName();
    }

    /**
     * Returns the format.
     *
     * @return YAML or JSON
     */
    public abstract Format format();

    // ------------------------------------------------------------------ reading

    /**
     * Returns the raw value at a path: a string, number, boolean, list, map (for sections) or Bukkit object.
     *
     * @param path the dot path, {@code ""} for the whole file
     * @return a copy of the value, or {@code null} if absent
     */
    public synchronized Object get(String path) {
        return rawGet(path);
    }

    /**
     * Reads and converts a value with {@link DataMapper}.
     *
     * @param path the dot path
     * @param type the class to create, e.g. {@code PlayerData.class}, {@code UUID.class}, {@code Material.class}
     * @param <T>  the type
     * @return the value, or {@code null} if absent
     * @throws IllegalArgumentException if the stored data can't be converted (the message names the path)
     */
    public synchronized <T> T get(String path, Class<T> type) {
        Object raw = rawGet(path);
        if (raw == null) {
            return null;
        }
        try {
            return DataMapper.fromTree(raw, type);
        } catch (IllegalArgumentException e) {
            throw readError(path, type.getSimpleName(), e);
        }
    }

    /**
     * Reads and converts a value, falling back if it is absent or can't be converted.
     *
     * @param path     the dot path
     * @param type     the class to create
     * @param fallback returned if absent or invalid (invalid values are logged)
     * @param <T>      the type
     * @return the value or {@code fallback}
     */
    public synchronized <T> T get(String path, Class<T> type, T fallback) {
        try {
            T value = get(path, type);
            return value == null ? fallback : value;
        } catch (IllegalArgumentException e) {
            Log.warn(e.getMessage() + ". Using the default.");
            return fallback;
        }
    }

    /**
     * Reads and converts a value of a generic type such as {@code DataMapper.listOf(UUID.class)}.
     *
     * @param path the dot path
     * @param type the type
     * @return the value, or {@code null} if absent
     */
    public synchronized Object get(String path, Type type) {
        Object raw = rawGet(path);
        if (raw == null) {
            return null;
        }
        try {
            return DataMapper.fromTree(raw, type);
        } catch (IllegalArgumentException e) {
            throw readError(path, type.getTypeName(), e);
        }
    }

    /**
     * Reads a list, converting every element.
     *
     * @param path    the dot path
     * @param element the element class
     * @param <T>     the element type
     * @return a new mutable list, empty if absent
     */
    @SuppressWarnings("unchecked")
    public synchronized <T> List<T> getList(String path, Class<T> element) {
        Object value = get(path, DataMapper.listOf(element));
        return value == null ? new ArrayList<T>() : (List<T>) value;
    }

    /**
     * Reads a section as a map, converting every value.
     *
     * @param path  the dot path
     * @param value the value class
     * @param <V>   the value type
     * @return a new mutable, ordered map, empty if absent
     */
    @SuppressWarnings("unchecked")
    public synchronized <V> Map<String, V> getMap(String path, Class<V> value) {
        Object result = get(path, DataMapper.mapOf(String.class, value));
        return result == null ? new LinkedHashMap<String, V>() : (Map<String, V>) result;
    }

    /**
     * Reads the whole file as one object, e.g. a settings class.
     *
     * @param type the class
     * @param <T>  the type
     * @return the object (never {@code null}: an empty file gives an object with default field values)
     */
    public synchronized <T> T as(Class<T> type) {
        Object raw = rawGet("");
        return DataMapper.fromTree(raw == null ? new LinkedHashMap<String, Object>() : raw, type);
    }

    /**
     * Reads a string. Numbers and booleans are converted to text.
     *
     * @param path the dot path
     * @return the value, or {@code null} if absent or a section/list
     */
    public synchronized String getString(String path) {
        return getString(path, null);
    }

    /**
     * Reads a string.
     *
     * @param path     the dot path
     * @param fallback returned if absent or a section/list
     * @return the value or {@code fallback}
     */
    public synchronized String getString(String path, String fallback) {
        Object raw = rawGet(path);
        if (raw == null || raw instanceof Map || raw instanceof Collection) {
            return fallback;
        }
        return raw.toString();
    }

    /**
     * Reads an int. Text such as {@code "12"} is converted.
     *
     * @param path     the dot path
     * @param fallback returned if absent or not a number
     * @return the value
     */
    public synchronized int getInt(String path, int fallback) {
        Integer value = scalar(path, Integer.class);
        return value == null ? fallback : value;
    }

    /**
     * Reads a long.
     *
     * @param path     the dot path
     * @param fallback returned if absent or not a number
     * @return the value
     */
    public synchronized long getLong(String path, long fallback) {
        Long value = scalar(path, Long.class);
        return value == null ? fallback : value;
    }

    /**
     * Reads a double.
     *
     * @param path     the dot path
     * @param fallback returned if absent or not a number
     * @return the value
     */
    public synchronized double getDouble(String path, double fallback) {
        Double value = scalar(path, Double.class);
        return value == null ? fallback : value;
    }

    /**
     * Reads a boolean ({@code true/false/yes/no}).
     *
     * @param path     the dot path
     * @param fallback returned if absent or invalid
     * @return the value
     */
    public synchronized boolean getBoolean(String path, boolean fallback) {
        Boolean value = scalar(path, Boolean.class);
        return value == null ? fallback : value;
    }

    /**
     * Reads a list of strings. A single value is returned as a one-element list.
     *
     * @param path the dot path
     * @return a new mutable list, empty if absent
     */
    public synchronized List<String> getStringList(String path) {
        Object raw = rawGet(path);
        List<String> out = new ArrayList<>();
        if (raw instanceof Collection) {
            for (Object element : (Collection<?>) raw) {
                if (element != null && !(element instanceof Map) && !(element instanceof Collection)) {
                    out.add(element.toString());
                }
            }
        } else if (raw != null && !(raw instanceof Map)) {
            out.add(raw.toString());
        }
        return out;
    }

    /**
     * Reads a string and colours it with {@link Text#color(String)}.
     *
     * @param path the dot path
     * @return the coloured text, or an empty string if absent
     */
    public synchronized String getText(String path) {
        return Text.color(getString(path, ""));
    }

    /**
     * Reads a list of strings and colours every line.
     *
     * @param path the dot path
     * @return the coloured lines
     */
    public synchronized List<String> getTextList(String path) {
        return Text.color(getStringList(path));
    }

    /**
     * Reads an enum constant by name, case-insensitively.
     *
     * @param path     the dot path
     * @param type     the enum class
     * @param fallback returned if absent or invalid (invalid values are logged)
     * @param <E>      the enum type
     * @return the constant
     */
    public synchronized <E extends Enum<E>> E getEnum(String path, Class<E> type, E fallback) {
        return get(path, type, fallback);
    }

    /**
     * Reads a UUID.
     *
     * @param path the dot path
     * @return the UUID, or {@code null} if absent or invalid
     */
    public synchronized UUID getUuid(String path) {
        return get(path, UUID.class, null);
    }

    /**
     * Reads a location stored by {@link #setLocation(String, Location)}, or a Bukkit-serialised location.
     *
     * @param path the dot path
     * @return the location, or {@code null} if missing, invalid, or its world is not loaded
     */
    public synchronized Location getLocation(String path) {
        Object raw = rawGet(path);
        if (raw instanceof Location) {
            return (Location) raw;
        }
        if (raw instanceof Map) {
            return get(path, Location.class, null);
        }
        return raw == null ? null : Locations.deserialize(raw.toString());
    }

    /**
     * Returns whether a path has a value (including a section).
     *
     * @param path the dot path
     * @return {@code true} if set
     */
    public synchronized boolean contains(String path) {
        return rawGet(path) != null;
    }

    /**
     * Returns whether a path is a section (a nested object).
     *
     * @param path the dot path
     * @return {@code true} for sections
     */
    public synchronized boolean isSection(String path) {
        return rawGet(path) instanceof Map;
    }

    /**
     * Returns the direct child keys of a section.
     *
     * @param path the section's dot path, or {@code ""} for the top level
     * @return the keys in file order, empty if the section is missing
     */
    public synchronized Set<String> keys(String path) {
        return rawKeys(path, false);
    }

    /**
     * Returns the keys of a section.
     *
     * @param path the section's dot path, or {@code ""} for the top level
     * @param deep {@code true} to include every nested key as a full dot path
     * @return the keys in file order
     */
    public synchronized Set<String> keys(String path, boolean deep) {
        return rawKeys(path, deep);
    }

    /**
     * Returns a deep copy of the whole file as a map.
     *
     * @return the data
     */
    @SuppressWarnings("unchecked")
    public synchronized Map<String, Object> toMap() {
        Object raw = rawGet("");
        return raw instanceof Map ? (Map<String, Object>) raw : new LinkedHashMap<String, Object>();
    }

    // ------------------------------------------------------------------ writing

    /**
     * Sets a value, creating parent sections as needed. Objects are converted with {@link DataMapper}.
     * Call {@link #save()} (or use {@link #edit(Consumer)}) to write it to disk.
     *
     * @param path  the dot path
     * @param value the value; {@code null} removes the path
     * @return this file, for chaining
     * @throws IllegalArgumentException if the value can't be stored
     */
    public synchronized DataFile set(String path, Object value) {
        if (path == null || path.isEmpty()) {
            return setRoot(value);
        }
        rawSet(path, DataMapper.toTree(value, keepsSerializableObjects()));
        return this;
    }

    /**
     * Sets a value only if the path has none yet.
     *
     * @param path  the dot path
     * @param value the value
     * @return this file
     */
    public synchronized DataFile setIfAbsent(String path, Object value) {
        if (!contains(path)) {
            set(path, value);
        }
        return this;
    }

    /**
     * Removes a path.
     *
     * @param path the dot path
     * @return this file
     */
    public synchronized DataFile remove(String path) {
        rawSet(path, null);
        return this;
    }

    /**
     * Stores a location as a compact {@code world;x;y;z;yaw;pitch} string.
     *
     * @param path     the dot path
     * @param location the location, {@code null} removes it
     * @return this file
     */
    public synchronized DataFile setLocation(String path, Location location) {
        rawSet(path, location == null ? null : Locations.serialize(location));
        return this;
    }

    /**
     * Replaces the whole file's contents with an object (or map).
     *
     * @param value the new contents; {@code null} clears the file
     * @return this file
     * @throws IllegalArgumentException if the value is not stored as a section (e.g. a plain string)
     */
    public synchronized DataFile setRoot(Object value) {
        if (value == null) {
            clear();
            return this;
        }
        Object tree = DataMapper.toTree(value, keepsSerializableObjects());
        if (!(tree instanceof Map)) {
            throw new IllegalArgumentException("The top level of a file must be a section, not "
                    + value.getClass().getSimpleName());
        }
        clear();
        for (Map.Entry<?, ?> entry : ((Map<?, ?>) tree).entrySet()) {
            rawSet(String.valueOf(entry.getKey()), entry.getValue());
        }
        return this;
    }

    /**
     * Removes everything from the file (in memory).
     *
     * @return this file
     */
    public synchronized DataFile clear() {
        rawClear();
        return this;
    }

    /**
     * Returns the file's contents as text, exactly as {@link #save()} would write it.
     *
     * @return the YAML or JSON text
     */
    public synchronized String saveToString() {
        return serialize();
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{" + file.getPath() + "}";
    }

    // ------------------------------------------------------------------ backend contract

    /**
     * Returns the value at a path as a detached copy: maps for sections, lists, scalars or Bukkit objects.
     *
     * @param path the dot path, {@code ""} for the root map
     * @return the value or {@code null}
     */
    protected abstract Object rawGet(String path);

    /**
     * Stores a converted value (maps, lists, scalars, and Bukkit objects if {@link #keepsSerializableObjects()}).
     *
     * @param path  a non-empty dot path
     * @param value the value, {@code null} to remove
     */
    protected abstract void rawSet(String path, Object value);

    /**
     * Returns the keys of a section.
     *
     * @param path the section path, {@code ""} for the root
     * @param deep whether to include nested keys
     * @return an ordered, unmodifiable set
     */
    protected abstract Set<String> rawKeys(String path, boolean deep);

    /** Removes all data. */
    protected abstract void rawClear();

    /**
     * Replaces all data with the parsed text.
     *
     * @param text the file contents
     * @throws MalformedDataException if the text is invalid
     */
    protected abstract void parse(String text) throws MalformedDataException;

    /**
     * Converts the current data to text.
     *
     * @return the file contents
     */
    protected abstract String serialize();

    /**
     * Adds keys that exist in the defaults but not in the current data.
     *
     * @param defaultsText the default resource's contents
     * @return {@code true} if anything was added
     * @throws MalformedDataException if the defaults are invalid
     */
    protected abstract boolean mergeMissing(String defaultsText) throws MalformedDataException;

    /**
     * Returns the contents of a new, empty file.
     *
     * @return e.g. {@code "{}"} for JSON
     */
    protected abstract String emptyContent();

    /**
     * Returns whether Bukkit {@code ConfigurationSerializable} objects are stored as-is (YAML) or as maps (JSON).
     *
     * @return {@code true} to keep them as objects
     */
    protected abstract boolean keepsSerializableObjects();

    // ------------------------------------------------------------------ internals

    private <T> T scalar(String path, Class<T> type) {
        Object raw = rawGet(path);
        if (raw == null || raw instanceof Map || raw instanceof Collection) {
            return null;
        }
        try {
            return DataMapper.fromTree(raw, type);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private IllegalArgumentException readError(String path, String typeName, IllegalArgumentException cause) {
        return new IllegalArgumentException("Could not read '" + path + "' in " + file.getName() + " as "
                + typeName + ": " + cause.getMessage(), cause);
    }

    private String readDefaults() {
        if (plugin == null || resourceName == null) {
            return null;
        }
        try (InputStream in = plugin.getResource(resourceName)) {
            if (in == null) {
                return null;
            }
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                bytes.write(buffer, 0, read);
            }
            return new String(bytes.toByteArray(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            Log.error("Could not read the default " + resourceName + " from the plugin jar", e);
            return null;
        }
    }

    private void backupBrokenFile(MalformedDataException cause) {
        String fileName = file.getName();
        int dot = fileName.lastIndexOf('.');
        String base = dot < 0 ? fileName : fileName.substring(0, dot);
        String extension = dot < 0 ? "" : fileName.substring(dot);
        File backup = new File(file.getParentFile(), base + ".broken-" + System.currentTimeMillis() + extension);
        try {
            Files.copy(file.toPath(), backup.toPath());
            Log.error("{} is invalid and was backed up to {}. Using defaults until it is fixed. Problem: {}",
                    fileName, backup.getName(), cause.getMessage());
        } catch (IOException e) {
            Log.error("{} is invalid (backup failed). Problem: {}", fileName, cause.getMessage());
        }
    }

    private void rememberWrite(String text) {
        lastWrittenHash = text.hashCode();
        hasWritten = true;
    }

    /**
     * Reloads this file automatically whenever it changes on disk (for example when a server owner
     * saves it in an editor), then runs {@code afterReload} on the server thread. Changes made by
     * this object's own {@link #save()} are ignored. A file with syntax errors is handled like any
     * reload: the broken file is backed up and the previous values stay in use.
     *
     * <pre>{@code
     * config.watch(this, () -> {
     *     applySettings();
     *     getLogger().info("config.yml reloaded");
     * });
     * }</pre>
     *
     * @param plugin      the owning plugin; watching stops when it shuts down MoonLib
     * @param afterReload runs after each reload, may be {@code null}
     * @return the watch, which can be closed to stop watching
     * @since 1.1.1-alpha
     */
    public dev.lunamc.moonlib.util.FileWatcher.Watch watch(Plugin plugin, Runnable afterReload) {
        return dev.lunamc.moonlib.util.FileWatcher.watch(plugin, file, () -> {
            if (hasWritten && file.isFile()) {
                try {
                    String onDisk = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
                    if (onDisk.hashCode() == lastWrittenHash) {
                        return;
                    }
                } catch (IOException e) {
                    return;
                }
            }
            reload();
            if (afterReload != null) {
                afterReload.run();
            }
        });
    }

    /**
     * Writes text to a file via a temporary file and an atomic move, creating parent folders.
     *
     * @param target the file
     * @param text   the contents
     * @throws IOException if writing fails
     */
    static void writeAtomically(File target, String text) throws IOException {
        File parent = target.getAbsoluteFile().getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs() && !parent.exists()) {
            throw new IOException("Could not create folder " + parent);
        }
        Path temp = new File(parent, target.getName() + ".tmp").toPath();
        Files.write(temp, text.getBytes(StandardCharsets.UTF_8));
        try {
            Files.move(temp, target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temp, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * Splits a dot path into segments.
     *
     * @param path the path
     * @return the segments, empty for the root
     */
    static List<String> segments(String path) {
        if (path == null || path.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> parts = new ArrayList<>();
        for (String part : path.split("\\.", -1)) {
            if (part.isEmpty()) {
                throw new IllegalArgumentException("Invalid path '" + path + "' (empty segment)");
            }
            parts.add(part);
        }
        return parts;
    }
}
