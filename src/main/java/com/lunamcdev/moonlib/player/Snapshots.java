package com.lunamcdev.moonlib.player;

import com.lunamcdev.moonlib.config.DataMapper;
import com.lunamcdev.moonlib.config.Json;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Serialises data the same way for every built-in storage, and spots unchanged data. */
final class Snapshots {
    private final Map<UUID, Integer> lastWritten = new ConcurrentHashMap<>();

    Snapshots() {
    }

    static String toJson(Object data) {
        return Json.write(DataMapper.toTree(data), false);
    }

    /** Returns the JSON to write, or {@code null} if it is identical to the last write. */
    String changed(UUID id, Object data) {
        String json = toJson(data);
        Integer previous = lastWritten.get(id);
        return previous != null && previous == json.hashCode() ? null : json;
    }

    void written(UUID id, String json) {
        lastWritten.put(id, json.hashCode());
    }

    void loaded(UUID id, Object data) {
        if (data != null) {
            lastWritten.put(id, toJson(data).hashCode());
        }
    }

    void forget(UUID id) {
        lastWritten.remove(id);
    }
}
