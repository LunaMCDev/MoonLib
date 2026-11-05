package com.example.template.data;

import dev.lunamc.moonlib.database.Database;

import java.util.Arrays;
import java.util.UUID;

/**
 * Reads and writes {@link PlayerData} rows. The same SQL works on SQLite and MySQL because the
 * insert-or-update statement comes from the database's dialect.
 */
public final class PlayerRepository {

    private final Database database;
    private final String upsert;

    public PlayerRepository(Database database) {
        this.database = database;
        this.upsert = database.dialect().upsert("template_players", Arrays.asList("uuid"),
                Arrays.asList("uuid", "name", "joins", "blocks_broken", "first_join", "last_seen"));
    }

    /** Loads a player's row, or creates fresh data for a new player. Runs on a background thread. */
    public PlayerData load(UUID id, String name) {
        return database.queryOne("SELECT * FROM template_players WHERE uuid = ?", row -> {
            PlayerData data = new PlayerData(row.getString("name"));
            data.joins = row.getInt("joins");
            data.blocksBroken = row.getLong("blocks_broken");
            data.firstJoin = row.getLong("first_join");
            data.lastSeen = row.getLong("last_seen");
            return data;
        }, id).orElseGet(() -> new PlayerData(name));
    }

    /** Saves a player's row. Runs on a background thread. */
    public void save(UUID id, PlayerData data) {
        database.update(upsert, id, data.name, data.joins, data.blocksBroken, data.firstJoin, data.lastSeen);
    }
}
