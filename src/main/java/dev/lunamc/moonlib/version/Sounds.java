package dev.lunamc.moonlib.version;

import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Version-independent sounds.
 *
 * <p>Write modern (1.13+) {@link Sound} names. On older versions the most commonly used sounds are
 * translated to their old names ({@code ENTITY_EXPERIENCE_ORB_PICKUP} becomes {@code ORB_PICKUP} on
 * 1.8). Lookups go through reflection on the constant's name, so they keep working on 1.21.3+ where
 * {@code Sound} stopped being an enum.</p>
 *
 * <pre>{@code
 * Sounds.play(player, "ENTITY_EXPERIENCE_ORB_PICKUP");
 * Sounds.play(player, "UI_BUTTON_CLICK", 1F, 1.2F);
 * Sounds.play(player, "BLOCK_NOTE_BLOCK_PLING", "NOTE_PLING");   // explicit fallbacks
 *
 * // From config: "ENTITY_PLAYER_LEVELUP:1.0:1.5"  (name:volume:pitch)
 * SoundEffect levelUp = Sounds.parse(config.getString("sounds.rankup"));
 * levelUp.play(player);
 * }</pre>
 *
 * <p>Unknown sounds never throw; they are silently skipped, so a typo in a config can't break a feature.
 * Namespaced keys such as {@code "minecraft:entity.villager.yes"} or resource-pack sounds are played
 * by key.</p>
 */
public final class Sounds {

    private static final Map<String, String[]> LEGACY = new HashMap<>();
    private static final Map<String, Optional<Sound>> CACHE = new ConcurrentHashMap<>();

    static {
        legacy("ENTITY_EXPERIENCE_ORB_PICKUP", "ORB_PICKUP");
        legacy("ENTITY_PLAYER_LEVELUP", "LEVEL_UP");
        legacy("UI_BUTTON_CLICK", "CLICK");
        legacy("BLOCK_NOTE_BLOCK_PLING", "BLOCK_NOTE_PLING", "NOTE_PLING");
        legacy("BLOCK_NOTE_BLOCK_BASS", "BLOCK_NOTE_BASS", "NOTE_BASS");
        legacy("BLOCK_NOTE_BLOCK_HARP", "BLOCK_NOTE_HARP", "NOTE_PIANO");
        legacy("BLOCK_NOTE_BLOCK_HAT", "BLOCK_NOTE_HAT", "NOTE_STICKS");
        legacy("BLOCK_NOTE_BLOCK_SNARE", "BLOCK_NOTE_SNARE", "NOTE_SNARE_DRUM");
        legacy("ENTITY_VILLAGER_NO", "VILLAGER_NO");
        legacy("ENTITY_VILLAGER_YES", "VILLAGER_YES");
        legacy("ENTITY_VILLAGER_TRADE", "VILLAGER_HAGGLE");
        legacy("ENTITY_ENDERMAN_TELEPORT", "ENTITY_ENDERMEN_TELEPORT", "ENDERMAN_TELEPORT");
        legacy("ENTITY_ITEM_PICKUP", "ITEM_PICKUP");
        legacy("ENTITY_ITEM_BREAK", "ITEM_BREAK");
        legacy("ENTITY_GENERIC_EXPLODE", "EXPLODE");
        legacy("ENTITY_ARROW_HIT_PLAYER", "SUCCESSFUL_HIT");
        legacy("ENTITY_CHICKEN_EGG", "CHICKEN_EGG_POP");
        legacy("ENTITY_FIREWORK_ROCKET_LAUNCH", "ENTITY_FIREWORK_LAUNCH", "FIREWORK_LAUNCH");
        legacy("ENTITY_FIREWORK_ROCKET_BLAST", "ENTITY_FIREWORK_BLAST", "FIREWORK_BLAST");
        legacy("ENTITY_FIREWORK_ROCKET_TWINKLE", "ENTITY_FIREWORK_TWINKLE", "FIREWORK_TWINKLE");
        legacy("ENTITY_WITHER_SPAWN", "WITHER_SPAWN");
        legacy("ENTITY_ENDER_DRAGON_GROWL", "ENTITY_ENDERDRAGON_GROWL", "ENDERDRAGON_GROWL");
        legacy("BLOCK_CHEST_OPEN", "CHEST_OPEN");
        legacy("BLOCK_CHEST_CLOSE", "CHEST_CLOSE");
        legacy("BLOCK_ANVIL_LAND", "ANVIL_LAND");
        legacy("BLOCK_ANVIL_USE", "ANVIL_USE");
        legacy("BLOCK_LEVER_CLICK", "CLICK");
        legacy("BLOCK_WOODEN_BUTTON_CLICK_ON", "BLOCK_WOOD_BUTTON_CLICK_ON", "WOOD_CLICK");
        legacy("BLOCK_STONE_BREAK", "DIG_STONE");
        legacy("BLOCK_GLASS_BREAK", "GLASS");
        legacy("BLOCK_LAVA_POP", "LAVA_POP");
        legacy("BLOCK_FIRE_EXTINGUISH", "FIZZ");
        legacy("BLOCK_PORTAL_TRAVEL", "PORTAL_TRAVEL");
        legacy("ENTITY_PLAYER_ATTACK_CRIT", "ENTITY_PLAYER_ATTACK_CRIT", "HURT_FLESH");
        legacy("ENTITY_PLAYER_HURT", "HURT_FLESH");
        legacy("ENTITY_CAT_PURREOW", "CAT_PURREOW");
        legacy("ENTITY_BAT_TAKEOFF", "BAT_TAKEOFF");
        legacy("ENTITY_HORSE_SADDLE", "HORSE_SADDLE");
        legacy("ENTITY_SPLASH_POTION_BREAK", "SPLASH_POTION_BREAK", "GLASS");
        legacy("ENTITY_GENERIC_DRINK", "DRINK");
        legacy("ENTITY_GENERIC_EAT", "EAT");
    }

    private Sounds() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Resolves the first sound name that exists on this server.
     *
     * @param names modern or legacy {@link Sound} constant names
     * @return the sound, or empty if none exist
     */
    public static Optional<Sound> resolve(String... names) {
        for (String name : names) {
            if (name == null) {
                continue;
            }
            String key = name.trim().toUpperCase(Locale.ROOT).replace('.', '_').replace("MINECRAFT:", "");
            Optional<Sound> cached = CACHE.get(key);
            if (cached == null) {
                cached = lookup(key);
                CACHE.put(key, cached);
            }
            if (cached.isPresent()) {
                return cached;
            }
        }
        return Optional.empty();
    }

    /**
     * Plays a sound to a player at their own location with volume and pitch 1.
     *
     * @param player the player
     * @param names  candidate sound names, tried in order
     */
    public static void play(Player player, String... names) {
        play(player, 1F, 1F, names);
    }

    /**
     * Plays a sound to a player at their own location.
     *
     * @param player the player
     * @param name   the sound name
     * @param volume the volume, 1 is normal
     * @param pitch  the pitch, from 0.5 to 2
     */
    public static void play(Player player, String name, float volume, float pitch) {
        play(player, volume, pitch, name);
    }

    /**
     * Plays a sound to a player at their own location.
     *
     * @param player the player
     * @param volume the volume
     * @param pitch  the pitch
     * @param names  candidate sound names, tried in order
     */
    public static void play(Player player, float volume, float pitch, String... names) {
        if (player == null) {
            return;
        }
        playAt(player, player.getLocation(), volume, pitch, names);
    }

    /**
     * Plays a sound to a single player at a specific location.
     *
     * @param player   the listener
     * @param location where the sound comes from
     * @param volume   the volume
     * @param pitch    the pitch
     * @param names    candidate sound names, tried in order
     */
    public static void playAt(Player player, Location location, float volume, float pitch, String... names) {
        Optional<Sound> sound = resolve(names);
        if (sound.isPresent()) {
            player.playSound(location, sound.get(), volume, pitch);
            return;
        }
        // Namespaced / resource pack sounds are played by key.
        for (String name : names) {
            if (name != null && (name.indexOf(':') > 0 || name.indexOf('.') > 0)) {
                player.playSound(location, name.toLowerCase(Locale.ROOT), volume, pitch);
                return;
            }
        }
    }

    /**
     * Plays a sound in the world at a location, audible to every nearby player.
     *
     * @param location the location
     * @param volume   the volume
     * @param pitch    the pitch
     * @param names    candidate sound names, tried in order
     */
    public static void playWorld(Location location, float volume, float pitch, String... names) {
        if (location.getWorld() == null) {
            return;
        }
        Optional<Sound> sound = resolve(names);
        if (sound.isPresent()) {
            location.getWorld().playSound(location, sound.get(), volume, pitch);
        }
    }

    /**
     * Parses a sound effect written as {@code NAME}, {@code NAME:volume} or {@code NAME:volume:pitch}.
     * A namespaced key may also be given, e.g. {@code "minecraft:entity.villager.yes:1:1.2"}.
     *
     * @param spec the specification, may be {@code null} or empty for a silent effect
     * @return the parsed effect, never {@code null}
     */
    public static SoundEffect parse(String spec) {
        if (spec == null || spec.trim().isEmpty() || spec.equalsIgnoreCase("none")) {
            return SoundEffect.NONE;
        }
        String[] parts = spec.trim().split(":");
        // Support "minecraft:some.sound:1:1" by re-joining a leading namespace.
        int start = 0;
        String name = parts[0];
        if (parts.length > 1 && !isNumber(parts[1])) {
            name = parts[0] + ":" + parts[1];
            start = 1;
        }
        float volume = parts.length > start + 1 ? parseFloat(parts[start + 1], 1F) : 1F;
        float pitch = parts.length > start + 2 ? parseFloat(parts[start + 2], 1F) : 1F;
        return new SoundEffect(name, volume, pitch);
    }

    private static Optional<Sound> lookup(String key) {
        Object value = Reflection.staticField(Sound.class, key);
        if (value instanceof Sound) {
            return Optional.of((Sound) value);
        }
        String[] legacyNames = LEGACY.get(key);
        if (legacyNames != null) {
            for (String legacyName : legacyNames) {
                Object legacy = Reflection.staticField(Sound.class, legacyName);
                if (legacy instanceof Sound) {
                    return Optional.of((Sound) legacy);
                }
            }
        }
        return Optional.empty();
    }

    private static void legacy(String modern, String... old) {
        LEGACY.put(modern, old);
    }

    private static boolean isNumber(String s) {
        try {
            Float.parseFloat(s);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static float parseFloat(String s, float def) {
        try {
            return Float.parseFloat(s);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    /**
     * An immutable sound with volume and pitch, usually loaded from a config with {@link #parse(String)}.
     */
    public static final class SoundEffect {

        /** A sound effect that plays nothing. */
        public static final SoundEffect NONE = new SoundEffect(null, 0F, 0F);

        private final String name;
        private final float volume;
        private final float pitch;

        /**
         * Creates a sound effect.
         *
         * @param name   the sound name or namespaced key, {@code null} for silence
         * @param volume the volume
         * @param pitch  the pitch
         */
        public SoundEffect(String name, float volume, float pitch) {
            this.name = name;
            this.volume = volume;
            this.pitch = pitch;
        }

        /**
         * Plays this effect to a player.
         *
         * @param player the player
         */
        public void play(Player player) {
            if (name != null) {
                Sounds.play(player, volume, pitch, name);
            }
        }

        /**
         * Plays this effect in the world at a location.
         *
         * @param location the location
         */
        public void playAt(Location location) {
            if (name != null) {
                Sounds.playWorld(location, volume, pitch, name);
            }
        }

        /**
         * Returns the sound name.
         *
         * @return the name, or {@code null} for {@link #NONE}
         */
        public String name() {
            return name;
        }

        /**
         * Returns the volume.
         *
         * @return the volume
         */
        public float volume() {
            return volume;
        }

        /**
         * Returns the pitch.
         *
         * @return the pitch
         */
        public float pitch() {
            return pitch;
        }

        @Override
        public String toString() {
            return name == null ? "none" : name + ":" + volume + ":" + pitch;
        }
    }
}
