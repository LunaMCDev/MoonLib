package com.lunamcdev.moonlib.config;

import org.bukkit.plugin.Plugin;

/**
 * A YAML file in the plugin's data folder, with defaults from the jar. This is a {@link YamlFile}; the
 * name is kept for configs and backwards compatibility.
 *
 * <ul>
 *     <li>If the file does not exist it is copied from the jar resource of the same name (or created empty).</li>
 *     <li>Keys that exist in the jar's copy but not on disk are added automatically when you ship an update,
 *     without touching the server owner's values.</li>
 *     <li>A file with invalid YAML is backed up as {@code name.broken-<time>.yml} and the defaults are used,
 *     so a typo never crashes the plugin.</li>
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
 *
 * @see DataFile for the full API, including JSON files and storing your own classes
 */
public final class ConfigFile extends YamlFile {

    /**
     * Loads (creating if needed) a file in the data folder.
     *
     * @param plugin the owning plugin
     * @param name   the path relative to the data folder, e.g. {@code "config.yml"} or {@code "data/ranks.yml"}
     */
    public ConfigFile(Plugin plugin, String name) {
        super(plugin, name);
    }

    @Override
    public ConfigFile mergeDefaults(boolean merge) {
        super.mergeDefaults(merge);
        return this;
    }
}
