package dev.lunamc.moonlib.config;

import dev.lunamc.moonlib.scheduler.Tasks;
import dev.lunamc.moonlib.util.Locations;
import dev.lunamc.moonlib.util.Log;
import dev.lunamc.moonlib.util.Text;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * A YAML file in the plugin's data folder, with defaults from the jar.
 *
 * <ul>
 *     <li>If the file does not exist it is copied from the jar resource of the same name (or created empty).</li>
 *     <li>Keys that exist in the jar's copy but not on disk are added automatically when you ship an update,
 *     without touching the server owner's values (disable with {@link #mergeDefaults(boolean)}).</li>
 *     <li>A file with invalid YAML is backed up as {@code name.broken-<time>.yml} and the defaults are used, so a
 *     typo never crashes the plugin.</li>
 * </ul>
 *
 * <pre>{@code
 * ConfigFile mines = new ConfigFile(plugin, "mines.yml");
 * int resetMinutes = mines.getInt("reset-interval", 10);
 * String prefix = mines.getText("prefix");              // coloured
 * mines.set("mines.a.size", 50);
 * mines.saveAsync();
 * mines.reload();
 * }</pre>
 */
public final class ConfigFile {

    private final Plugin plugin;
    private final String name;
    private final File file;
    private YamlConfiguration config;
    private boolean mergeDefaults = true;

    /**
     * Loads (creating if needed) a file in the data folder.
     *
     * @param plugin the owning plugin
     * @param name   the path relative to the data folder, e.g. {@code "config.yml"} or {@code "data/ranks.yml"}
     */
    public ConfigFile(Plugin plugin, String name) {
        this.plugin = plugin;
        this.name = name.replace('\\', '/');
        this.file = new File(plugin.getDataFolder(), this.name);
        reload();
    }

    /**
     * Enables or disables adding missing keys from the jar's copy on load. Enabled by default.
     *
     * @param merge {@code true} to merge
     * @return this file
     */
    public ConfigFile mergeDefaults(boolean merge) {
        this.mergeDefaults = merge;
        return this;
    }

    /** Re-reads the file from disk. */
    public void reload() {
        createIfMissing();
        YamlConfiguration loaded = new YamlConfiguration();
        try {
            loaded.load(file);
        } catch (InvalidConfigurationException e) {
            backupBrokenFile(e);
        } catch (IOException e) {
            Log.error("Could not read " + name, e);
        }
        YamlConfiguration defaults = loadDefaults();
        if (defaults != null) {
            loaded.setDefaults(defaults);
            if (mergeDefaults && hasMissingKeys(loaded, defaults)) {
                loaded.options().copyDefaults(true);
                this.config = loaded;
                save();
            }
        }
        this.config = loaded;
    }

    /** Writes the file to disk synchronously. */
    public void save() {
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                throw new IOException("Could not create " + parent);
            }
            config.save(file);
        } catch (IOException e) {
            Log.error("Could not save " + name, e);
        }
    }

    /**
     * Writes the file to disk on another thread. The contents are captured immediately on the
     * calling thread, so later changes are not included.
     *
     * @return a future completed when the file is written
     */
    public CompletableFuture<Void> saveAsync() {
        String contents = config.saveToString();
        CompletableFuture<Void> future = new CompletableFuture<>();
        Tasks.async(() -> {
            try {
                File parent = file.getParentFile();
                if (parent != null && !parent.exists() && !parent.mkdirs()) {
                    throw new IOException("Could not create " + parent);
                }
                File temp = new File(file.getPath() + ".tmp");
                try (Writer writer = new OutputStreamWriter(Files.newOutputStream(temp.toPath()), StandardCharsets.UTF_8)) {
                    writer.write(contents);
                }
                Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
                future.complete(null);
            } catch (IOException e) {
                Log.error("Could not save " + name, e);
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    // ------------------------------------------------------------------ access

    /**
     * Returns the underlying Bukkit configuration.
     *
     * @return the configuration
     */
    public FileConfiguration config() {
        return config;
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
     * Returns whether a path exists.
     *
     * @param path the path
     * @return {@code true} if set (or set in the defaults)
     */
    public boolean contains(String path) {
        return config.contains(path);
    }

    /**
     * Sets a value. Call {@link #save()} to persist.
     *
     * @param path  the path
     * @param value the value, {@code null} removes it
     */
    public void set(String path, Object value) {
        config.set(path, value);
    }

    /**
     * Reads a string.
     *
     * @param path the path
     * @return the value, or {@code null}
     */
    public String getString(String path) {
        return config.getString(path);
    }

    /**
     * Reads a string.
     *
     * @param path     the path
     * @param fallback returned if missing
     * @return the value or {@code fallback}
     */
    public String getString(String path, String fallback) {
        return config.getString(path, fallback);
    }

    /**
     * Reads a string and colours it.
     *
     * @param path the path
     * @return the coloured value, or an empty string if missing
     */
    public String getText(String path) {
        return Text.color(config.getString(path, ""));
    }

    /**
     * Reads a string list and colours every line.
     *
     * @param path the path
     * @return the coloured lines; a single string is returned as a one-line list
     */
    public List<String> getTextList(String path) {
        if (config.isString(path)) {
            return new ArrayList<>(Collections.singletonList(getText(path)));
        }
        return Text.color(config.getStringList(path));
    }

    /**
     * Reads an int.
     *
     * @param path     the path
     * @param fallback returned if missing
     * @return the value
     */
    public int getInt(String path, int fallback) {
        return config.getInt(path, fallback);
    }

    /**
     * Reads a long.
     *
     * @param path     the path
     * @param fallback returned if missing
     * @return the value
     */
    public long getLong(String path, long fallback) {
        return config.getLong(path, fallback);
    }

    /**
     * Reads a double.
     *
     * @param path     the path
     * @param fallback returned if missing
     * @return the value
     */
    public double getDouble(String path, double fallback) {
        return config.getDouble(path, fallback);
    }

    /**
     * Reads a boolean.
     *
     * @param path     the path
     * @param fallback returned if missing
     * @return the value
     */
    public boolean getBoolean(String path, boolean fallback) {
        return config.getBoolean(path, fallback);
    }

    /**
     * Reads a string list.
     *
     * @param path the path
     * @return the list, empty if missing
     */
    public List<String> getStringList(String path) {
        return config.getStringList(path);
    }

    /**
     * Returns a section.
     *
     * @param path the path
     * @return the section, or {@code null}
     */
    public ConfigurationSection getSection(String path) {
        return config.getConfigurationSection(path);
    }

    /**
     * Returns a section, creating it if it does not exist.
     *
     * @param path the path
     * @return the section
     */
    public ConfigurationSection getOrCreateSection(String path) {
        ConfigurationSection section = config.getConfigurationSection(path);
        return section == null ? config.createSection(path) : section;
    }

    /**
     * Returns the direct child keys of a section.
     *
     * @param path the section path, or {@code ""} for the root
     * @return the keys, empty if the section is missing
     */
    public Set<String> keys(String path) {
        ConfigurationSection section = path.isEmpty() ? config : config.getConfigurationSection(path);
        return section == null ? Collections.<String>emptySet() : section.getKeys(false);
    }

    /**
     * Reads a location stored with {@link #setLocation(String, Location)}.
     *
     * @param path the path
     * @return the location, or {@code null} if missing, invalid, or its world is not loaded
     */
    public Location getLocation(String path) {
        return Locations.deserialize(config.getString(path));
    }

    /**
     * Stores a location as a compact string.
     *
     * @param path     the path
     * @param location the location
     */
    public void setLocation(String path, Location location) {
        config.set(path, location == null ? null : Locations.serialize(location));
    }

    /**
     * Reads an enum constant by name, case-insensitively.
     *
     * @param path     the path
     * @param type     the enum class
     * @param fallback returned if missing or invalid
     * @param <E>      the enum type
     * @return the constant
     */
    public <E extends Enum<E>> E getEnum(String path, Class<E> type, E fallback) {
        String value = config.getString(path);
        if (value == null) {
            return fallback;
        }
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(java.util.Locale.ROOT).replace(' ', '_'));
        } catch (IllegalArgumentException e) {
            Log.warn("Invalid value '{}' at {} in {}, using {}", value, path, name, fallback);
            return fallback;
        }
    }

    // ------------------------------------------------------------------ internals

    private void createIfMissing() {
        if (file.exists()) {
            return;
        }
        try {
            if (hasResource()) {
                plugin.saveResource(name, false);
            } else {
                File parent = file.getParentFile();
                if (parent != null && !parent.exists() && !parent.mkdirs()) {
                    throw new IOException("Could not create " + parent);
                }
                if (!file.createNewFile()) {
                    throw new IOException("Could not create " + file);
                }
            }
        } catch (IOException | IllegalArgumentException e) {
            Log.error("Could not create " + name, e);
        }
    }

    private boolean hasResource() throws IOException {
        try (InputStream stream = plugin.getResource(name)) {
            return stream != null;
        }
    }

    private YamlConfiguration loadDefaults() {
        InputStream stream = plugin.getResource(name);
        if (stream == null) {
            return null;
        }
        try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return YamlConfiguration.loadConfiguration(reader);
        } catch (IOException e) {
            return null;
        }
    }

    private static boolean hasMissingKeys(YamlConfiguration loaded, YamlConfiguration defaults) {
        for (String key : defaults.getKeys(true)) {
            if (!loaded.isSet(key)) {
                return true;
            }
        }
        return false;
    }

    private void backupBrokenFile(InvalidConfigurationException cause) {
        File backup = new File(file.getParentFile(), file.getName().replace(".yml", "")
                + ".broken-" + System.currentTimeMillis() + ".yml");
        try {
            Files.copy(file.toPath(), backup.toPath());
            Log.error("{} contains invalid YAML and was backed up to {}. Using defaults. Error: {}",
                    name, backup.getName(), cause.getMessage());
        } catch (IOException e) {
            Log.error("{} contains invalid YAML (backup failed): {}", name, cause.getMessage());
        }
    }
}
