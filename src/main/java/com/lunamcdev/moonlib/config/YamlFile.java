package com.lunamcdev.moonlib.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A YAML {@link DataFile}, backed by Bukkit's {@link YamlConfiguration}.
 *
 * <pre>{@code
 * YamlFile mines = DataFile.yaml(plugin, "mines.yml");
 * mines.set("mines.a.size", 50).set("mines.a.owner", player.getUniqueId());
 * mines.save();
 *
 * ConfigurationSection section = mines.getSection("mines.a");   // Bukkit API when you need it
 * }</pre>
 *
 * <p>Comments in the file are kept when it is saved on 1.18.1+ (Bukkit's own behaviour), so it is safe
 * to edit commented config files from code. Bukkit objects such as {@code ItemStack} and
 * {@code Location} are written in Bukkit's native YAML form.</p>
 */
public class YamlFile extends DataFile {

    private YamlConfiguration config = new YamlConfiguration();

    /**
     * Loads (creating it if needed) a YAML file in a plugin's data folder, with defaults from the jar
     * resource of the same path.
     *
     * @param plugin the plugin
     * @param name   e.g. {@code "config.yml"} or {@code "data/ranks.yml"}
     */
    @SuppressWarnings("this-escape")
    public YamlFile(Plugin plugin, String name) {
        super(plugin, new File(plugin.getDataFolder(), name.replace('\\', '/')), name);
        reload();
    }

    /**
     * Loads (creating it if needed) a YAML file at any location, without defaults.
     *
     * @param file the file
     */
    @SuppressWarnings("this-escape")
    public YamlFile(File file) {
        super(null, file, null);
        reload();
    }

    /**
     * Creates a file handle without reading the disk; saving overwrites the file. Used by {@link DataStore}.
     *
     * @param file the file
     * @param load whether to load it now
     */
    YamlFile(File file, boolean load) {
        super(null, file, null);
        if (load) {
            reload();
        }
    }

    /**
     * Returns the underlying Bukkit configuration, for anything not covered by {@link DataFile}.
     *
     * @return the configuration
     */
    public synchronized FileConfiguration config() {
        return config;
    }

    /**
     * Returns a Bukkit configuration section.
     *
     * @param path the dot path
     * @return the live section, or {@code null}
     */
    public synchronized ConfigurationSection getSection(String path) {
        return path.isEmpty() ? config : config.getConfigurationSection(path);
    }

    /**
     * Returns a Bukkit configuration section, creating it if it does not exist.
     *
     * @param path the dot path
     * @return the live section
     */
    public synchronized ConfigurationSection getOrCreateSection(String path) {
        ConfigurationSection section = getSection(path);
        return section == null ? config.createSection(path) : section;
    }

    @Override
    public YamlFile mergeDefaults(boolean merge) {
        super.mergeDefaults(merge);
        return this;
    }

    @Override
    public Format format() {
        return Format.YAML;
    }

    @Override
    protected Object rawGet(String path) {
        Object value = path.isEmpty() ? config : config.get(path);
        return copy(value);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void rawSet(String path, Object value) {
        if (value instanceof Map) {
            config.createSection(path, (Map<?, ?>) value);
        } else {
            config.set(path, value);
        }
    }

    @Override
    protected Set<String> rawKeys(String path, boolean deep) {
        ConfigurationSection section = path.isEmpty() ? config : config.getConfigurationSection(path);
        if (section == null) {
            return Collections.emptySet();
        }
        return Collections.unmodifiableSet(new LinkedHashSet<>(section.getKeys(deep)));
    }

    @Override
    protected void rawClear() {
        for (String key : config.getKeys(false)) {
            config.set(key, null);
        }
    }

    @Override
    protected void parse(String text) throws MalformedDataException {
        YamlConfiguration loaded = new YamlConfiguration();
        try {
            loaded.loadFromString(text);
        } catch (InvalidConfigurationException e) {
            throw new MalformedDataException(firstLine(e.getMessage()), e);
        }
        this.config = loaded;
    }

    @Override
    protected String serialize() {
        return config.saveToString();
    }

    @Override
    protected boolean mergeMissing(String defaultsText) throws MalformedDataException {
        YamlConfiguration defaults = new YamlConfiguration();
        try {
            defaults.loadFromString(defaultsText);
        } catch (InvalidConfigurationException e) {
            throw new MalformedDataException(firstLine(e.getMessage()), e);
        }
        boolean added = false;
        for (String key : defaults.getKeys(true)) {
            if (config.isSet(key)) {
                continue;
            }
            if (defaults.isConfigurationSection(key)) {
                ConfigurationSection section = defaults.getConfigurationSection(key);
                if (section != null && section.getKeys(false).isEmpty()) {
                    config.createSection(key);
                    added = true;
                }
            } else {
                config.set(key, defaults.get(key));
                added = true;
            }
        }
        return added;
    }

    @Override
    protected String emptyContent() {
        return "";
    }

    @Override
    protected boolean keepsSerializableObjects() {
        return true;
    }

    /** Detaches a value from the live configuration: sections become maps, lists are copied. */
    private static Object copy(Object value) {
        if (value instanceof ConfigurationSection) {
            Map<String, Object> map = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : ((ConfigurationSection) value).getValues(false).entrySet()) {
                map.put(entry.getKey(), copy(entry.getValue()));
            }
            return map;
        }
        if (value instanceof Map) {
            Map<String, Object> map = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                map.put(String.valueOf(entry.getKey()), copy(entry.getValue()));
            }
            return map;
        }
        if (value instanceof Collection) {
            List<Object> list = new java.util.ArrayList<>();
            for (Object element : (Collection<?>) value) {
                list.add(copy(element));
            }
            return list;
        }
        return value;
    }

    private static String firstLine(String message) {
        if (message == null) {
            return "Invalid YAML";
        }
        int newline = message.indexOf('\n');
        return newline < 0 ? message : message.substring(0, newline) + " ...";
    }
}
