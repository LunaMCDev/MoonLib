package com.lunamcdev.moonlib.config;

import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A JSON {@link DataFile}, using MoonLib's own {@link Json} reader and writer.
 *
 * <pre>{@code
 * JsonFile stats = DataFile.json(plugin, "data/stats.json");
 * stats.set("totals.blocksMined", stats.getLong("totals.blocksMined", 0) + mined);
 * stats.set("players." + uuid, playerData);   // any object DataMapper supports
 * stats.saveAsync();
 * }</pre>
 *
 * <p>Files are written pretty-printed (2-space indent) by default; use {@link #prettyPrint(boolean)} for
 * compact output. Reading accepts comments and trailing commas. Bukkit objects such as {@code ItemStack}
 * are stored as maps with Bukkit's {@code "=="} type key and restored exactly.</p>
 */
public final class JsonFile extends DataFile {

    private Map<String, Object> root = new LinkedHashMap<>();
    private boolean pretty = true;

    /**
     * Loads (creating it if needed) a JSON file in a plugin's data folder, with defaults from the jar
     * resource of the same path.
     *
     * @param plugin the plugin
     * @param name   e.g. {@code "data/stats.json"}
     */
    public JsonFile(Plugin plugin, String name) {
        super(plugin, new File(plugin.getDataFolder(), name.replace('\\', '/')), name);
        reload();
    }

    /**
     * Loads (creating it if needed) a JSON file at any location, without defaults.
     *
     * @param file the file
     */
    public JsonFile(File file) {
        super(null, file, null);
        reload();
    }

    /**
     * Creates a file handle without reading the disk; saving overwrites the file. Used by {@link DataStore}.
     *
     * @param file the file
     * @param load whether to load it now
     */
    JsonFile(File file, boolean load) {
        super(null, file, null);
        if (load) {
            reload();
        }
    }

    /**
     * Chooses indented or single-line output for future saves.
     *
     * @param enabled {@code true} for indented output (the default)
     * @return this file
     */
    public synchronized JsonFile prettyPrint(boolean enabled) {
        this.pretty = enabled;
        return this;
    }

    @Override
    public JsonFile mergeDefaults(boolean merge) {
        super.mergeDefaults(merge);
        return this;
    }

    @Override
    public Format format() {
        return Format.JSON;
    }

    @Override
    protected Object rawGet(String path) {
        List<String> segments = segments(path);
        Object current = root;
        for (String segment : segments) {
            if (!(current instanceof Map)) {
                return null;
            }
            current = ((Map<?, ?>) current).get(segment);
        }
        return Json.deepCopy(current);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void rawSet(String path, Object value) {
        List<String> segments = segments(path);
        Map<String, Object> current = root;
        for (int i = 0; i < segments.size() - 1; i++) {
            Object next = current.get(segments.get(i));
            if (!(next instanceof Map)) {
                if (value == null) {
                    return; // removing something that doesn't exist
                }
                next = new LinkedHashMap<String, Object>();
                current.put(segments.get(i), next);
            }
            current = (Map<String, Object>) next;
        }
        String last = segments.get(segments.size() - 1);
        if (value == null) {
            current.remove(last);
        } else {
            current.put(last, Json.deepCopy(value));
        }
    }

    @Override
    protected Set<String> rawKeys(String path, boolean deep) {
        Object section = path.isEmpty() ? root : rawGet(path);
        if (!(section instanceof Map)) {
            return Collections.emptySet();
        }
        Set<String> keys = new LinkedHashSet<>();
        collectKeys((Map<?, ?>) section, "", deep, keys);
        return Collections.unmodifiableSet(keys);
    }

    @Override
    protected void rawClear() {
        root = new LinkedHashMap<>();
    }

    @Override
    protected void parse(String text) throws MalformedDataException {
        root = Json.parseObject(text);
    }

    @Override
    protected String serialize() {
        return Json.write(root, pretty) + "\n";
    }

    @Override
    protected boolean mergeMissing(String defaultsText) throws MalformedDataException {
        return merge(root, Json.parseObject(defaultsText));
    }

    @Override
    protected String emptyContent() {
        return "{}\n";
    }

    @Override
    protected boolean keepsSerializableObjects() {
        return false;
    }

    @SuppressWarnings("unchecked")
    private static boolean merge(Map<String, Object> target, Map<String, Object> defaults) {
        boolean added = false;
        for (Map.Entry<String, Object> entry : defaults.entrySet()) {
            Object existing = target.get(entry.getKey());
            if (!target.containsKey(entry.getKey())) {
                target.put(entry.getKey(), Json.deepCopy(entry.getValue()));
                added = true;
            } else if (existing instanceof Map && entry.getValue() instanceof Map) {
                added |= merge((Map<String, Object>) existing, (Map<String, Object>) entry.getValue());
            }
        }
        return added;
    }

    private static void collectKeys(Map<?, ?> section, String prefix, boolean deep, Set<String> out) {
        for (Map.Entry<?, ?> entry : section.entrySet()) {
            String key = prefix + entry.getKey();
            out.add(key);
            if (deep && entry.getValue() instanceof Map) {
                collectKeys((Map<?, ?>) entry.getValue(), key + ".", true, out);
            }
        }
    }
}
