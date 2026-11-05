package com.lunamcdev.moonlib.player;

import com.lunamcdev.moonlib.config.DataStore;

import java.util.UUID;

/** JSON or YAML files through a {@link DataStore}. */
final class FileStorage<T> implements PlayerStorage<T> {
    private final DataStore<T> store;
    private final String description;
    private final Snapshots snapshots = new Snapshots();

    FileStorage(DataStore<T> store, String description) {
        this.store = store;
        this.description = description;
    }

    @Override
    public T load(UUID id, String name) {
        if (!store.exists(id)) {
            return null;
        }
        T data = store.load(id);
        if (data == null) {
            throw new IllegalStateException("The data file for " + name + " (" + id + ") could not be read");
        }
        snapshots.loaded(id, data);
        return data;
    }

    @Override
    public void save(UUID id, T data) {
        String json = snapshots.changed(id, data);
        if (json == null) {
            return;
        }
        if (!store.save(id, data)) {
            throw new IllegalStateException("Could not write the data file for " + id);
        }
        snapshots.written(id, json);
    }

    @Override
    public void delete(UUID id) {
        store.delete(id);
        snapshots.forget(id);
    }

    @Override
    public String describe() {
        return description;
    }
}
