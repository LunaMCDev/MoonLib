package com.lunamcdev.moonlib.player;

import com.lunamcdev.moonlib.config.DataFile;
import com.lunamcdev.moonlib.config.DataStore;
import com.lunamcdev.moonlib.database.Database;
import org.bukkit.plugin.Plugin;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Where a {@link PlayerCache} keeps its data: a JSON or YAML file per player, or a SQL table, or
 * your own implementation. Pick one in code, or let server owners pick in config with
 * {@link #fromConfig}.
 *
 * <pre>{@code
 * PlayerStorage<PlayerData> storage = PlayerStorage.json(this, "players", PlayerData.class);   // players/<uuid>.json
 * PlayerStorage<PlayerData> storage = PlayerStorage.yaml(this, "players", PlayerData.class);   // players/<uuid>.yml
 * PlayerStorage<PlayerData> storage = PlayerStorage.sql(this, database, PlayerData.class);     // one table row each
 * PlayerStorage<PlayerData> storage = PlayerStorage.fromConfig(this, config, "storage", PlayerData.class);
 *
 * PlayerCache<PlayerData> players = PlayerCache.<PlayerData>builder(this)
 *         .storage(storage, PlayerData::new)
 *         .build();
 * }</pre>
 *
 * <h2>Efficient by default</h2>
 * The built-in storages remember what they last wrote for each player and skip saves when nothing
 * changed, so an autosave of 100 idle players writes nothing at all. The SQL storage writes every
 * changed player of an autosave in one batched transaction.
 *
 * <h2>Several plugins on one server</h2>
 * File storages live in the owning plugin's own data folder. The SQL storage keys every row by the
 * plugin's name as well as the player's UUID, so any number of plugins can share one database (and
 * one copy of MoonLib) without seeing or overwriting each other's data.
 *
 * <p>Your data class is stored with {@link com.lunamcdev.moonlib.config.DataMapper}: plain fields, a no-argument constructor,
 * and any type DataMapper supports (primitives, strings, enums, UUIDs, collections, maps, nested
 * objects). Implementations are called from the cache's background thread.</p>
 *
 * @param <T> the data type
 * @since 1.2.0
 */
public interface PlayerStorage<T> {

    /**
     * Loads a player's data.
     *
     * @param id   the player's UUID
     * @param name the player's name
     * @return the data, or {@code null} if this player has none yet
     * @throws Exception if the data exists but cannot be read
     */
    T load(UUID id, String name) throws Exception;

    /**
     * Saves a player's data.
     *
     * @param id   the player's UUID
     * @param data the data
     * @throws Exception if saving fails
     */
    void save(UUID id, T data) throws Exception;

    /**
     * Saves several players at once. The default saves them one by one; storages that can do
     * better (a SQL batch) override it.
     *
     * @param batch UUID to data
     * @throws Exception if saving fails
     */
    default void saveAll(Map<UUID, T> batch) throws Exception {
        Exception first = null;
        for (Map.Entry<UUID, T> entry : batch.entrySet()) {
            try {
                save(entry.getKey(), entry.getValue());
            } catch (Exception e) {
                if (first == null) {
                    first = e;
                } else {
                    first.addSuppressed(e);
                }
            }
        }
        if (first != null) {
            throw first;
        }
    }

    /**
     * Deletes a player's data.
     *
     * @param id the player's UUID
     * @throws Exception if deleting fails
     */
    void delete(UUID id) throws Exception;

    /**
     * Returns a short description for logs, e.g. {@code "JSON files in players/"}.
     *
     * @return the description
     */
    String describe();

    /**
     * Releases resources (a database this storage created). Called when the cache closes.
     */
    default void close() {
    }

    // ------------------------------------------------------------------ built-in storages

    /**
     * One JSON file per player in a folder of the plugin's data folder.
     *
     * @param plugin the owning plugin
     * @param folder the folder, e.g. {@code "players"}
     * @param type   the data class
     * @param <T>    the data type
     * @return the storage
     */
    static <T> PlayerStorage<T> json(Plugin plugin, String folder, Class<T> type) {
        return new FileStorage<>(DataStore.json(plugin, folder, type), "JSON files in " + folder + "/");
    }

    /**
     * One YAML file per player in a folder of the plugin's data folder.
     *
     * @param plugin the owning plugin
     * @param folder the folder, e.g. {@code "players"}
     * @param type   the data class
     * @param <T>    the data type
     * @return the storage
     */
    static <T> PlayerStorage<T> yaml(Plugin plugin, String folder, Class<T> type) {
        return new FileStorage<>(DataStore.yaml(plugin, folder, type), "YAML files in " + folder + "/");
    }

    /**
     * A row per player in the shared {@code moonlib_player_data} table, keyed by plugin name and
     * UUID, with the data stored as JSON. Works on every database MoonLib supports.
     *
     * @param plugin   the owning plugin (its name keeps its rows apart from other plugins')
     * @param database the database
     * @param type     the data class
     * @param <T>      the data type
     * @return the storage
     */
    static <T> PlayerStorage<T> sql(Plugin plugin, Database database, Class<T> type) {
        return new SqlStorage<>(plugin.getName(), database, type, false);
    }

    /**
     * Lets server owners choose the storage in config:
     *
     * <pre>{@code
     * storage:
     *   type: json          # json, yaml, sqlite, mysql or mariadb
     *   folder: players     # json / yaml only
     *   # sqlite: file: data.db    mysql/mariadb: host, port, name, username, password, pool-size
     * }</pre>
     *
     * <p>For {@code sqlite}, {@code mysql} and {@code mariadb} the section is read exactly like
     * {@link Database#fromConfig}, and the database is closed with the cache. To share one
     * database with the rest of your plugin, use {@link #sql} with your own {@link Database}.</p>
     *
     * @param plugin the owning plugin
     * @param config the config file
     * @param path   the section, e.g. {@code "storage"}
     * @param type   the data class
     * @param <T>    the data type
     * @return the storage
     * @throws IllegalArgumentException if {@code type} is unknown
     */
    static <T> PlayerStorage<T> fromConfig(Plugin plugin, DataFile config, String path, Class<T> type) {
        String p = path == null || path.isEmpty() ? "" : path + ".";
        String kind = config.getString(p + "type", "json").trim().toLowerCase(Locale.ROOT);
        String folder = config.getString(p + "folder", "players");
        switch (kind) {
            case "json":
                return json(plugin, folder, type);
            case "yaml":
            case "yml":
                return yaml(plugin, folder, type);
            case "sqlite":
            case "mysql":
            case "mariadb":
                return new SqlStorage<>(plugin.getName(), Database.fromConfig(plugin, config, path), type, true);
            default:
                throw new IllegalArgumentException("Unknown storage type '" + kind + "' at " + p + "type"
                        + " (use json, yaml, sqlite, mysql or mariadb)");
        }
    }
}
