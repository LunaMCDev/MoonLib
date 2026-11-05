package com.lunamcdev.moonlib.version;

import org.bukkit.enchantments.Enchantment;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Version-independent enchantment lookup.
 *
 * <p>Bukkit renamed many enchantment constants in 1.20.5 to match vanilla ({@code DURABILITY} became
 * {@code UNBREAKING}, {@code DIG_SPEED} became {@code EFFICIENCY}, ...). Referencing either name
 * directly in code crashes on the other half of versions. {@code Enchants} accepts the vanilla name,
 * the old Bukkit name, or a namespaced key, and returns whatever exists on this server.</p>
 *
 * <pre>{@code
 * Enchantment eff = Enchants.get("EFFICIENCY").orElseThrow(...);
 * Enchants.get("minecraft:fortune");
 * Enchants.get("LOOT_BONUS_BLOCKS");  // also resolves to Fortune
 * }</pre>
 */
public final class Enchants {

    /** Vanilla name to pre-1.20.5 Bukkit name. */
    private static final Map<String, String> ALIASES = new HashMap<>();
    private static final Map<String, Optional<Enchantment>> CACHE = new ConcurrentHashMap<>();

    static {
        alias("PROTECTION", "PROTECTION_ENVIRONMENTAL");
        alias("FIRE_PROTECTION", "PROTECTION_FIRE");
        alias("FEATHER_FALLING", "PROTECTION_FALL");
        alias("BLAST_PROTECTION", "PROTECTION_EXPLOSIONS");
        alias("PROJECTILE_PROTECTION", "PROTECTION_PROJECTILE");
        alias("RESPIRATION", "OXYGEN");
        alias("AQUA_AFFINITY", "WATER_WORKER");
        alias("SHARPNESS", "DAMAGE_ALL");
        alias("SMITE", "DAMAGE_UNDEAD");
        alias("BANE_OF_ARTHROPODS", "DAMAGE_ARTHROPODS");
        alias("LOOTING", "LOOT_BONUS_MOBS");
        alias("SWEEPING_EDGE", "SWEEPING_EDGE");
        alias("EFFICIENCY", "DIG_SPEED");
        alias("UNBREAKING", "DURABILITY");
        alias("FORTUNE", "LOOT_BONUS_BLOCKS");
        alias("POWER", "ARROW_DAMAGE");
        alias("PUNCH", "ARROW_KNOCKBACK");
        alias("FLAME", "ARROW_FIRE");
        alias("INFINITY", "ARROW_INFINITE");
        alias("LUCK_OF_THE_SEA", "LUCK");
    }

    private Enchants() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Resolves an enchantment by vanilla name, legacy Bukkit name or namespaced key.
     *
     * @param name the name, case-insensitive
     * @return the enchantment, or empty if it does not exist on this version
     */
    public static Optional<Enchantment> get(String name) {
        if (name == null || name.trim().isEmpty()) {
            return Optional.empty();
        }
        String key = name.trim().toUpperCase(Locale.ROOT).replace("MINECRAFT:", "").replace(' ', '_');
        Optional<Enchantment> cached = CACHE.get(key);
        if (cached == null) {
            cached = lookup(key);
            CACHE.put(key, cached);
        }
        return cached;
    }

    /**
     * Returns the enchantment MoonLib uses to make items glow on versions without a glint override
     * ({@code UNBREAKING}, a.k.a. {@code DURABILITY}).
     *
     * @return the glow enchantment
     */
    public static Enchantment glow() {
        Optional<Enchantment> unbreaking = get("UNBREAKING");
        if (unbreaking.isPresent()) {
            return unbreaking.get();
        }
        throw new IllegalStateException("Could not resolve the UNBREAKING enchantment on this server");
    }

    @SuppressWarnings("deprecation")
    private static Optional<Enchantment> lookup(String key) {
        Object direct = Reflection.staticField(Enchantment.class, key);
        if (direct instanceof Enchantment) {
            return Optional.of((Enchantment) direct);
        }
        String alias = ALIASES.get(key);
        if (alias != null) {
            Object aliased = Reflection.staticField(Enchantment.class, alias);
            if (aliased instanceof Enchantment) {
                return Optional.of((Enchantment) aliased);
            }
        }
        // Reverse lookup: legacy name given on a modern server.
        for (Map.Entry<String, String> entry : ALIASES.entrySet()) {
            if (entry.getValue().equals(key)) {
                Object modern = Reflection.staticField(Enchantment.class, entry.getKey());
                if (modern instanceof Enchantment) {
                    return Optional.of((Enchantment) modern);
                }
            }
        }
        try {
            Enchantment byName = Enchantment.getByName(key);
            return Optional.ofNullable(byName);
        } catch (Throwable ignored) {
            return Optional.empty();
        }
    }

    private static void alias(String vanilla, String bukkit) {
        ALIASES.put(vanilla, bukkit);
    }
}
