package com.lunamcdev.moonlib.player;

import com.lunamcdev.moonlib.config.DataMapper;
import com.lunamcdev.moonlib.config.Json;
import com.lunamcdev.moonlib.database.Database;
import com.lunamcdev.moonlib.database.Dialect;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** A row per (plugin, player) in {@code moonlib_player_data}, data as JSON. */
final class SqlStorage<T> implements PlayerStorage<T> {
    static final String TABLE = "moonlib_player_data";

    private final String owner;
    private final Database database;
    private final Class<T> type;
    private final boolean ownsDatabase;
    private final String upsert;
    private final Snapshots snapshots = new Snapshots();
    private volatile boolean tableReady;

    SqlStorage(String owner, Database database, Class<T> type, boolean ownsDatabase) {
        this.owner = Objects.requireNonNull(owner, "owner");
        this.database = database;
        this.type = type;
        this.ownsDatabase = ownsDatabase;
        this.upsert = database.dialect().upsert(TABLE, Arrays.asList("owner", "uuid"),
                Arrays.asList("owner", "uuid", "data", "updated_at"));
    }

    private void ensureTable() {
        if (tableReady) {
            return;
        }
        synchronized (this) {
            if (tableReady) {
                return;
            }
            String text = database.dialect().isMySqlFamily() ? "MEDIUMTEXT" : "TEXT";
            database.update("CREATE TABLE IF NOT EXISTS " + TABLE + " (owner VARCHAR(64) NOT NULL, "
                    + "uuid CHAR(36) NOT NULL, data " + text + " NOT NULL, updated_at BIGINT NOT NULL, "
                    + "PRIMARY KEY (owner, uuid))");
            tableReady = true;
        }
    }

    @Override
    public T load(UUID id, String name) throws Exception {
        ensureTable();
        String json = database.value("SELECT data FROM " + TABLE + " WHERE owner = ? AND uuid = ?",
                String.class, owner, id).orElse(null);
        if (json == null) {
            return null;
        }
        T data = DataMapper.fromTree(Json.parse(json), type);
        snapshots.loaded(id, data);
        return data;
    }

    @Override
    public void save(UUID id, T data) {
        ensureTable();
        String json = snapshots.changed(id, data);
        if (json == null) {
            return;
        }
        database.update(upsert, owner, id, json, System.currentTimeMillis());
        snapshots.written(id, json);
    }

    @Override
    public void saveAll(Map<UUID, T> batch) {
        ensureTable();
        List<Object[]> rows = new ArrayList<>();
        List<UUID> ids = new ArrayList<>();
        List<String> written = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, T> entry : batch.entrySet()) {
            String json = snapshots.changed(entry.getKey(), entry.getValue());
            if (json != null) {
                rows.add(new Object[]{owner, entry.getKey(), json, now});
                ids.add(entry.getKey());
                written.add(json);
            }
        }
        if (rows.isEmpty()) {
            return;
        }
        database.batch(upsert, rows);
        for (int i = 0; i < ids.size(); i++) {
            snapshots.written(ids.get(i), written.get(i));
        }
    }

    @Override
    public void delete(UUID id) {
        ensureTable();
        database.update("DELETE FROM " + TABLE + " WHERE owner = ? AND uuid = ?", owner, id);
        snapshots.forget(id);
    }

    @Override
    public String describe() {
        return Dialect.SQLITE == database.dialect() ? "SQLite table " + TABLE : database.dialect() + " table " + TABLE;
    }

    @Override
    public void close() {
        if (ownsDatabase) {
            database.close();
        }
    }
}
