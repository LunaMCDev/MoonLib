package com.lunamcdev.moonlib.version;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Version-independent material lookup.
 *
 * <p>Always write <b>modern (1.13+) names</b>. On legacy servers (1.8 to 1.12) they are translated to the
 * old name and data value automatically, so {@code "LIME_STAINED_GLASS_PANE"} becomes
 * {@code STAINED_GLASS_PANE:5} on 1.8 and works unchanged on 1.21.</p>
 *
 * <pre>{@code
 * ItemStack pane = Materials.item("GRAY_STAINED_GLASS_PANE");
 * Optional<Material> clock = Materials.material("CLOCK");          // WATCH on 1.8
 * ItemStack head = Materials.item("PLAYER_HEAD");                  // SKULL_ITEM:3 on 1.8
 * ItemStack legacySyntax = Materials.item("WOOL:14");              // also accepted
 * }</pre>
 *
 * <p>Colour variants (wool, glass, glass panes, terracotta, concrete, concrete powder, carpet, beds,
 * banners and dyes) are mapped for all 16 colours, along with the most common renamed items. Unknown names
 * fall back to {@link Material#matchMaterial(String)}. If you need exhaustive coverage of every
 * material in every version, XSeries can be shaded alongside MoonLib.</p>
 */
public final class Materials {

    private static final String[] COLORS = {
            "WHITE", "ORANGE", "MAGENTA", "LIGHT_BLUE", "YELLOW", "LIME", "PINK", "GRAY",
            "LIGHT_GRAY", "CYAN", "PURPLE", "BLUE", "BROWN", "GREEN", "RED", "BLACK"
    };

    /** Modern colourable suffix to legacy material name. */
    private static final Map<String, String> COLORED_LEGACY = new HashMap<>();
    /** Modern name to legacy {@code NAME:data}. */
    private static final Map<String, String> RENAMED = new HashMap<>();
    private static final Map<String, Optional<ItemStack>> CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    static {
        COLORED_LEGACY.put("WOOL", "WOOL");
        COLORED_LEGACY.put("STAINED_GLASS", "STAINED_GLASS");
        COLORED_LEGACY.put("STAINED_GLASS_PANE", "STAINED_GLASS_PANE");
        COLORED_LEGACY.put("TERRACOTTA", "STAINED_CLAY");
        COLORED_LEGACY.put("CONCRETE", "CONCRETE");
        COLORED_LEGACY.put("CONCRETE_POWDER", "CONCRETE_POWDER");
        COLORED_LEGACY.put("CARPET", "CARPET");
        COLORED_LEGACY.put("BED", "BED");
        COLORED_LEGACY.put("BANNER", "BANNER");

        // Dyes used the inverted data scale on legacy versions.
        String[] dyeNames = {"BONE_MEAL", "ORANGE_DYE", "MAGENTA_DYE", "LIGHT_BLUE_DYE", "DANDELION_YELLOW",
                "LIME_DYE", "PINK_DYE", "GRAY_DYE", "LIGHT_GRAY_DYE", "CYAN_DYE", "PURPLE_DYE", "LAPIS_LAZULI",
                "COCOA_BEANS", "CACTUS_GREEN", "ROSE_RED", "INK_SAC"};
        for (int i = 0; i < dyeNames.length; i++) {
            RENAMED.put(dyeNames[i], "INK_SACK:" + (15 - i));
        }
        RENAMED.put("WHITE_DYE", "INK_SACK:15");
        RENAMED.put("YELLOW_DYE", "INK_SACK:11");
        RENAMED.put("BLUE_DYE", "INK_SACK:4");
        RENAMED.put("BROWN_DYE", "INK_SACK:3");
        RENAMED.put("GREEN_DYE", "INK_SACK:2");
        RENAMED.put("RED_DYE", "INK_SACK:1");
        RENAMED.put("BLACK_DYE", "INK_SACK:0");

        RENAMED.put("PLAYER_HEAD", "SKULL_ITEM:3");
        RENAMED.put("SKELETON_SKULL", "SKULL_ITEM:0");
        RENAMED.put("WITHER_SKELETON_SKULL", "SKULL_ITEM:1");
        RENAMED.put("ZOMBIE_HEAD", "SKULL_ITEM:2");
        RENAMED.put("CREEPER_HEAD", "SKULL_ITEM:4");
        RENAMED.put("DRAGON_HEAD", "SKULL_ITEM:5");
        RENAMED.put("CLOCK", "WATCH");
        RENAMED.put("EXPERIENCE_BOTTLE", "EXP_BOTTLE");
        RENAMED.put("ENCHANTING_TABLE", "ENCHANTMENT_TABLE");
        RENAMED.put("CRAFTING_TABLE", "WORKBENCH");
        RENAMED.put("OAK_PLANKS", "WOOD");
        RENAMED.put("OAK_LOG", "LOG");
        RENAMED.put("OAK_SAPLING", "SAPLING");
        RENAMED.put("OAK_LEAVES", "LEAVES");
        RENAMED.put("OAK_FENCE", "FENCE");
        RENAMED.put("OAK_FENCE_GATE", "FENCE_GATE");
        RENAMED.put("OAK_DOOR", "WOOD_DOOR");
        RENAMED.put("OAK_SIGN", "SIGN");
        RENAMED.put("OAK_BOAT", "BOAT");
        RENAMED.put("OAK_SLAB", "WOOD_STEP");
        RENAMED.put("OAK_STAIRS", "WOOD_STAIRS");
        RENAMED.put("OAK_PRESSURE_PLATE", "WOOD_PLATE");
        RENAMED.put("OAK_BUTTON", "WOOD_BUTTON");
        RENAMED.put("OAK_TRAPDOOR", "TRAP_DOOR");
        RENAMED.put("STONE_SLAB", "STEP");
        RENAMED.put("SMOOTH_STONE", "STONE");
        RENAMED.put("STONE_BRICKS", "SMOOTH_BRICK");
        RENAMED.put("NETHER_BRICKS", "NETHER_BRICK");
        RENAMED.put("NETHER_BRICK", "NETHER_BRICK_ITEM");
        RENAMED.put("NETHER_QUARTZ_ORE", "QUARTZ_ORE");
        RENAMED.put("NETHER_PORTAL", "PORTAL");
        RENAMED.put("END_STONE", "ENDER_STONE");
        RENAMED.put("END_PORTAL_FRAME", "ENDER_PORTAL_FRAME");
        RENAMED.put("GRASS_BLOCK", "GRASS");
        RENAMED.put("SHORT_GRASS", "LONG_GRASS:1");
        RENAMED.put("TALL_GRASS", "DOUBLE_PLANT:2");
        RENAMED.put("SUNFLOWER", "DOUBLE_PLANT");
        RENAMED.put("POPPY", "RED_ROSE");
        RENAMED.put("DANDELION", "YELLOW_FLOWER");
        RENAMED.put("COBWEB", "WEB");
        RENAMED.put("LILY_PAD", "WATER_LILY");
        RENAMED.put("IRON_BARS", "IRON_FENCE");
        RENAMED.put("GLASS_PANE", "THIN_GLASS");
        RENAMED.put("SNOWBALL", "SNOW_BALL");
        RENAMED.put("GUNPOWDER", "SULPHUR");
        RENAMED.put("COMPARATOR", "REDSTONE_COMPARATOR");
        RENAMED.put("REPEATER", "DIODE");
        RENAMED.put("REDSTONE_TORCH", "REDSTONE_TORCH_ON");
        RENAMED.put("REDSTONE_LAMP", "REDSTONE_LAMP_OFF");
        RENAMED.put("ENDER_EYE", "EYE_OF_ENDER");
        RENAMED.put("MUSHROOM_STEW", "MUSHROOM_SOUP");
        RENAMED.put("PORKCHOP", "PORK");
        RENAMED.put("COOKED_PORKCHOP", "GRILLED_PORK");
        RENAMED.put("COD", "RAW_FISH");
        RENAMED.put("COOKED_COD", "COOKED_FISH");
        RENAMED.put("BEEF", "RAW_BEEF");
        RENAMED.put("CHICKEN", "RAW_CHICKEN");
        RENAMED.put("MELON_SLICE", "MELON");
        RENAMED.put("MELON", "MELON_BLOCK");
        RENAMED.put("SPAWNER", "MOB_SPAWNER");
        RENAMED.put("PISTON", "PISTON_BASE");
        RENAMED.put("STICKY_PISTON", "PISTON_STICKY_BASE");
        RENAMED.put("WRITABLE_BOOK", "BOOK_AND_QUILL");
        RENAMED.put("FIREWORK_ROCKET", "FIREWORK");
        RENAMED.put("FIREWORK_STAR", "FIREWORK_CHARGE");
        RENAMED.put("FIRE_CHARGE", "FIREBALL");
        RENAMED.put("ENCHANTED_GOLDEN_APPLE", "GOLDEN_APPLE:1");
        RENAMED.put("FILLED_MAP", "MAP");
        RENAMED.put("MAP", "EMPTY_MAP");
        RENAMED.put("CHEST_MINECART", "STORAGE_MINECART");
        RENAMED.put("FURNACE_MINECART", "POWERED_MINECART");
        RENAMED.put("TNT_MINECART", "EXPLOSIVE_MINECART");
        RENAMED.put("COMMAND_BLOCK", "COMMAND");
        RENAMED.put("RAIL", "RAILS");
        RENAMED.put("CAULDRON", "CAULDRON_ITEM");
        RENAMED.put("BREWING_STAND", "BREWING_STAND_ITEM");
        RENAMED.put("FLOWER_POT", "FLOWER_POT_ITEM");
        RENAMED.put("WHEAT_SEEDS", "SEEDS");
        RENAMED.put("CARROT", "CARROT_ITEM");
        RENAMED.put("POTATO", "POTATO_ITEM");
        RENAMED.put("GLISTERING_MELON_SLICE", "SPECKLED_MELON");
        RENAMED.put("NETHER_WART", "NETHER_STALK");
        RENAMED.put("CARVED_PUMPKIN", "PUMPKIN");
        RENAMED.put("LEAD", "LEASH");
        RENAMED.put("LIGHT_WEIGHTED_PRESSURE_PLATE", "GOLD_PLATE");
        RENAMED.put("HEAVY_WEIGHTED_PRESSURE_PLATE", "IRON_PLATE");
        RENAMED.put("STONE_PRESSURE_PLATE", "STONE_PLATE");
        RENAMED.put("CHIPPED_ANVIL", "ANVIL:1");
        RENAMED.put("DAMAGED_ANVIL", "ANVIL:2");
        RENAMED.put("MUSIC_DISC_13", "GOLD_RECORD");
        RENAMED.put("MUSIC_DISC_CAT", "GREEN_RECORD");
        RENAMED.put("ZOMBIE_SPAWN_EGG", "MONSTER_EGG:54");
        RENAMED.put("TOTEM_OF_UNDYING", "TOTEM");
    }

    private Materials() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Resolves a material name for the current version.
     *
     * @param name a modern name ({@code "LIME_WOOL"}), a legacy name, or {@code NAME:data}
     * @return the material, or empty if it does not exist on this version
     */
    public static Optional<Material> material(String name) {
        Optional<ItemStack> item = parse(name);
        return item.isPresent() ? Optional.of(item.get().getType()) : Optional.<Material>empty();
    }

    /**
     * Resolves the first name that exists on the current version.
     *
     * @param names candidate names, modern or legacy, tried in order
     * @return the first material that resolves, or empty
     */
    public static Optional<Material> firstMaterial(String... names) {
        for (String name : names) {
            Optional<Material> material = material(name);
            if (material.isPresent()) {
                return material;
            }
        }
        return Optional.empty();
    }

    /**
     * Resolves a name to an {@link ItemStack}, which can carry the legacy data value needed for coloured
     * blocks before 1.13.
     *
     * @param name a modern name, a legacy name, or {@code NAME:data}
     * @return a new single item stack, or empty if the material does not exist on this version
     */
    @SuppressWarnings("deprecation")
    public static Optional<ItemStack> parse(String name) {
        if (name == null || name.trim().isEmpty()) {
            return Optional.empty();
        }
        String key = name.trim().toUpperCase(Locale.ROOT).replace(' ', '_').replace("MINECRAFT:", "");
        Optional<ItemStack> cached = CACHE.get(key);
        if (cached == null) {
            cached = resolve(key);
            CACHE.put(key, cached);
        }
        return cached.isPresent() ? Optional.of(cached.get().clone()) : cached;
    }

    /**
     * Resolves a name to an {@link ItemStack}, falling back to {@code STONE} if it cannot be found. Handy
     * for menu decorations where a missing item should never break the menu.
     *
     * @param name a modern name, a legacy name, or {@code NAME:data}
     * @return a new item stack
     */
    public static ItemStack item(String name) {
        Optional<ItemStack> item = parse(name);
        return item.isPresent() ? item.get() : new ItemStack(Material.STONE);
    }

    /**
     * Resolves a name to an {@link ItemStack} with the given amount.
     *
     * @param name   the material name
     * @param amount the stack size
     * @return a new item stack
     */
    public static ItemStack item(String name, int amount) {
        ItemStack item = item(name);
        item.setAmount(amount);
        return item;
    }

    /**
     * Returns whether a material is any kind of air. Works on every version (1.14 added
     * {@code Material#isAir()}).
     *
     * @param material the material, may be {@code null}
     * @return {@code true} for {@code null}, {@code AIR}, {@code CAVE_AIR} and {@code VOID_AIR}
     */
    public static boolean isAir(Material material) {
        return material == null || material.name().endsWith("AIR");
    }

    /**
     * Returns an unmodifiable view of the 16 dye colour names in legacy data-value order.
     *
     * @return colour names, {@code WHITE} first and {@code BLACK} last
     */
    public static java.util.List<String> colors() {
        return Collections.unmodifiableList(java.util.Arrays.asList(COLORS));
    }

    @SuppressWarnings("deprecation")
    private static Optional<ItemStack> resolve(String key) {
        String materialName = key;
        short data = 0;
        int colon = key.indexOf(':');
        if (colon > 0) {
            materialName = key.substring(0, colon);
            try {
                data = Short.parseShort(key.substring(colon + 1));
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
        }

        if (!ServerVersion.isLegacy()) {
            Material modern = Material.matchMaterial(materialName);
            if (modern == null || isLegacyEnumConstant(modern)) {
                // A legacy name (optionally with data) on a modern server, e.g. "WOOL:14" or "WATCH".
                String translated = toModern(materialName, data);
                modern = translated == null ? null : Material.matchMaterial(translated);
            }
            if (modern != null && !isLegacyEnumConstant(modern)) {
                return Optional.of(new ItemStack(modern));
            }
            return Optional.empty();
        }

        // Legacy server: translate modern names first.
        String legacy = toLegacy(materialName);
        if (legacy != null && colon < 0) {
            int legacyColon = legacy.indexOf(':');
            if (legacyColon > 0) {
                data = Short.parseShort(legacy.substring(legacyColon + 1));
                legacy = legacy.substring(0, legacyColon);
            }
            materialName = legacy;
        }
        Material material = Material.matchMaterial(materialName);
        if (material == null) {
            return Optional.empty();
        }
        return Optional.of(new ItemStack(material, 1, data));
    }

    private static String toLegacy(String modern) {
        String renamed = RENAMED.get(modern);
        if (renamed != null) {
            return renamed;
        }
        for (int i = 0; i < COLORS.length; i++) {
            String color = COLORS[i];
            if (modern.startsWith(color + "_")) {
                String suffix = modern.substring(color.length() + 1);
                String legacy = COLORED_LEGACY.get(suffix);
                if (legacy != null) {
                    // LIGHT_GRAY used to be "SILVER" but data values are what matter on legacy.
                    int data = suffix.equals("BANNER") ? 15 - i : i;
                    return legacy + ":" + data;
                }
            }
        }
        if (modern.endsWith("_SHOVEL")) {
            String prefix = modern.substring(0, modern.length() - "_SHOVEL".length());
            if (prefix.equals("WOODEN")) {
                prefix = "WOOD";
            } else if (prefix.equals("GOLDEN")) {
                prefix = "GOLD";
            }
            return prefix + "_SPADE";
        }
        if (modern.startsWith("WOODEN_")) {
            return "WOOD_" + modern.substring("WOODEN_".length());
        }
        if (modern.startsWith("GOLDEN_") && !modern.equals("GOLDEN_APPLE") && !modern.equals("GOLDEN_CARROT")) {
            return "GOLD_" + modern.substring("GOLDEN_".length());
        }
        return null;
    }

    private static String toModern(String legacy, short data) {
        for (Map.Entry<String, String> entry : COLORED_LEGACY.entrySet()) {
            if (entry.getValue().equals(legacy) && data >= 0 && data < COLORS.length) {
                int index = entry.getKey().equals("BANNER") ? 15 - data : data;
                return COLORS[index] + "_" + entry.getKey();
            }
        }
        String withData = data == 0 ? legacy : legacy + ":" + data;
        for (Map.Entry<String, String> entry : RENAMED.entrySet()) {
            if (entry.getValue().equals(withData)) {
                return entry.getKey();
            }
        }
        if (data == 0) {
            for (Map.Entry<String, String> entry : RENAMED.entrySet()) {
                if (entry.getValue().equals(legacy + ":0")) {
                    return entry.getKey();
                }
            }
        }
        if (legacy.endsWith("_SPADE")) {
            String prefix = legacy.substring(0, legacy.length() - "_SPADE".length());
            return (prefix.equals("WOOD") ? "WOODEN" : prefix.equals("GOLD") ? "GOLDEN" : prefix) + "_SHOVEL";
        }
        if (legacy.startsWith("WOOD_")) {
            return "WOODEN_" + legacy.substring("WOOD_".length());
        }
        if (legacy.startsWith("GOLD_")) {
            return "GOLDEN_" + legacy.substring("GOLD_".length());
        }
        return null;
    }

    private static boolean isLegacyEnumConstant(Material material) {
        // Modern servers keep LEGACY_* constants for backwards compatibility; never hand those out.
        return material.name().startsWith("LEGACY_");
    }
}
