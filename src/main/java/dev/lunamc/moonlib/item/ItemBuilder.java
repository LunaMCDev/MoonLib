package dev.lunamc.moonlib.item;

import dev.lunamc.moonlib.util.Text;
import dev.lunamc.moonlib.version.Enchants;
import dev.lunamc.moonlib.version.Materials;
import dev.lunamc.moonlib.version.Reflection;
import dev.lunamc.moonlib.version.ServerVersion;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * A fluent, version-independent {@link ItemStack} builder.
 *
 * <pre>{@code
 * ItemStack sword = ItemBuilder.of("DIAMOND_SWORD")
 *         .name("&b&lMoon Blade")
 *         .lore("&7A blade forged from moonlight.", "", "&eDamage: &f{damage}")
 *         .placeholder("damage", 12)
 *         .enchant("SHARPNESS", 5)
 *         .unbreakable(true)
 *         .hideAll()
 *         .build();
 *
 * ItemStack pane  = ItemBuilder.filler("BLACK_STAINED_GLASS_PANE");
 * ItemStack head  = ItemBuilder.skull().texture("e3b6b6...").name("&dMoon Crate").build();
 * ItemStack glow  = ItemBuilder.of(Material.PAPER).name("&6Voucher").glow().tag("voucher", "vip").build();
 * }</pre>
 *
 * <p>Material names are resolved through {@link Materials}, so modern names work on 1.8. Name and lore
 * are coloured with {@link Text#color(String)} at {@link #build()} time, after placeholders are
 * replaced. Every method that a given version does not support degrades gracefully instead of throwing
 * (for example {@link #customModelData(int)} does nothing on 1.13 and below).</p>
 */
public final class ItemBuilder {

    private final ItemStack item;
    private final ItemMeta meta;
    private String name;
    private List<String> lore;
    private final Map<String, Object> placeholders = new LinkedHashMap<>();

    private ItemBuilder(ItemStack item) {
        this.item = item;
        this.meta = item.getItemMeta();
        if (meta != null) {
            if (meta.hasDisplayName()) {
                this.name = meta.getDisplayName();
            }
            if (meta.hasLore()) {
                this.lore = new ArrayList<>(meta.getLore());
            }
        }
    }

    // ------------------------------------------------------------------ factories

    /**
     * Starts from a material.
     *
     * @param material the material
     * @return a new builder
     */
    public static ItemBuilder of(Material material) {
        return new ItemBuilder(new ItemStack(material));
    }

    /**
     * Starts from a material with an amount.
     *
     * @param material the material
     * @param amount   the stack size
     * @return a new builder
     */
    public static ItemBuilder of(Material material, int amount) {
        return new ItemBuilder(new ItemStack(material, amount));
    }

    /**
     * Starts from a material name, resolved for the current version (see {@link Materials}).
     * Unknown names fall back to {@code STONE} so menus never break on a typo.
     *
     * @param material e.g. {@code "LIME_STAINED_GLASS_PANE"}
     * @return a new builder
     */
    public static ItemBuilder of(String material) {
        return new ItemBuilder(Materials.item(material));
    }

    /**
     * Starts from a copy of an existing item.
     *
     * @param item the item to copy
     * @return a new builder
     */
    public static ItemBuilder of(ItemStack item) {
        return new ItemBuilder(item.clone());
    }

    /**
     * Starts from a player head.
     *
     * @return a new builder
     */
    public static ItemBuilder skull() {
        return new ItemBuilder(Skulls.blank());
    }

    /**
     * Creates a nameless filler item for menu backgrounds and borders.
     *
     * @param material e.g. {@code "GRAY_STAINED_GLASS_PANE"}
     * @return the item
     */
    public static ItemStack filler(String material) {
        return of(material).name(" ").hideAll().build();
    }

    // ------------------------------------------------------------------ basic properties

    /**
     * Sets the display name. Colour codes and placeholders are applied at build time.
     *
     * @param displayName the name
     * @return this builder
     */
    public ItemBuilder name(String displayName) {
        this.name = displayName;
        return this;
    }

    /**
     * Replaces the lore.
     *
     * @param lines the lines
     * @return this builder
     */
    public ItemBuilder lore(String... lines) {
        this.lore = new ArrayList<>(Arrays.asList(lines));
        return this;
    }

    /**
     * Replaces the lore.
     *
     * @param lines the lines
     * @return this builder
     */
    public ItemBuilder lore(Collection<String> lines) {
        this.lore = new ArrayList<>(lines);
        return this;
    }

    /**
     * Appends lore lines.
     *
     * @param lines the lines
     * @return this builder
     */
    public ItemBuilder addLore(String... lines) {
        return addLore(Arrays.asList(lines));
    }

    /**
     * Appends lore lines.
     *
     * @param lines the lines
     * @return this builder
     */
    public ItemBuilder addLore(Collection<String> lines) {
        if (lore == null) {
            lore = new ArrayList<>();
        }
        lore.addAll(lines);
        return this;
    }

    /**
     * Removes all lore.
     *
     * @return this builder
     */
    public ItemBuilder clearLore() {
        this.lore = null;
        return this;
    }

    /**
     * Sets the stack size.
     *
     * @param amount 1 to 64 (or the material's max stack size)
     * @return this builder
     */
    public ItemBuilder amount(int amount) {
        item.setAmount(amount);
        return this;
    }

    /**
     * Adds a {@code {key}} placeholder that is replaced in the name and lore at build time.
     *
     * @param key   the key, without braces
     * @param value the value
     * @return this builder
     */
    public ItemBuilder placeholder(String key, Object value) {
        placeholders.put(key, value);
        return this;
    }

    /**
     * Adds several placeholders as alternating key/value pairs.
     *
     * @param pairs keys and values
     * @return this builder
     */
    public ItemBuilder placeholders(Object... pairs) {
        if (pairs.length % 2 != 0) {
            throw new IllegalArgumentException("Placeholders must be key/value pairs");
        }
        for (int i = 0; i < pairs.length; i += 2) {
            placeholders.put(String.valueOf(pairs[i]), pairs[i + 1]);
        }
        return this;
    }

    // ------------------------------------------------------------------ enchantments & flags

    /**
     * Adds an enchantment, ignoring vanilla level limits.
     *
     * @param enchantment the enchantment
     * @param level       the level
     * @return this builder
     */
    public ItemBuilder enchant(Enchantment enchantment, int level) {
        if (meta != null && enchantment != null) {
            meta.addEnchant(enchantment, level, true);
        }
        return this;
    }

    /**
     * Adds an enchantment by name (vanilla or legacy Bukkit name, see {@link Enchants}). Unknown names
     * are ignored.
     *
     * @param enchantment the name, e.g. {@code "EFFICIENCY"} or {@code "DIG_SPEED"}
     * @param level       the level
     * @return this builder
     */
    public ItemBuilder enchant(String enchantment, int level) {
        Optional<Enchantment> resolved = Enchants.get(enchantment);
        return resolved.isPresent() ? enchant(resolved.get(), level) : this;
    }

    /**
     * Makes the item glow as if enchanted, without showing an enchantment.
     *
     * @return this builder
     */
    public ItemBuilder glow() {
        return glow(true);
    }

    /**
     * Toggles the enchantment glint.
     *
     * @param glow {@code true} to glow
     * @return this builder
     */
    public ItemBuilder glow(boolean glow) {
        if (meta == null) {
            return this;
        }
        if (ServerVersion.current().isAtLeast(ServerVersion.V1_20_5)) {
            ModernItemSupport.setGlintOverride(meta, glow ? Boolean.TRUE : null);
            return this;
        }
        Enchantment glowEnchant = Enchants.glow();
        if (glow) {
            meta.addEnchant(glowEnchant, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        } else {
            meta.removeEnchant(glowEnchant);
        }
        return this;
    }

    /**
     * Adds item flags.
     *
     * @param flags the flags
     * @return this builder
     */
    public ItemBuilder flags(ItemFlag... flags) {
        if (meta != null) {
            meta.addItemFlags(flags);
        }
        return this;
    }

    /**
     * Hides every tooltip section the server can hide (enchants, attributes, effects, ...).
     *
     * @return this builder
     */
    public ItemBuilder hideAll() {
        if (meta != null) {
            meta.addItemFlags(ItemFlag.values());
        }
        return this;
    }

    // ------------------------------------------------------------------ durability & model

    /**
     * Makes the item unbreakable.
     *
     * @param unbreakable {@code true} to make it unbreakable
     * @return this builder
     */
    public ItemBuilder unbreakable(boolean unbreakable) {
        if (meta == null) {
            return this;
        }
        if (ServerVersion.current().isAtLeast(ServerVersion.V1_11)) {
            ModernItemSupport.setUnbreakable(meta, unbreakable);
        } else {
            // 1.8 to 1.10: meta.spigot().setUnbreakable(boolean)
            try {
                Method spigot = Reflection.method(meta.getClass(), "spigot");
                Object spigotMeta = spigot.invoke(meta);
                Reflection.method(spigotMeta.getClass(), "setUnbreakable", boolean.class).invoke(spigotMeta, unbreakable);
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // not supported on this implementation
            }
        }
        return this;
    }

    /**
     * Sets the damage taken (durability used). Uses the legacy durability value before 1.13.
     *
     * @param damage the damage
     * @return this builder
     */
    @SuppressWarnings("deprecation")
    public ItemBuilder damage(int damage) {
        if (ServerVersion.current().isAtLeast(ServerVersion.V1_13)) {
            if (meta != null) {
                ModernItemSupport.setDamage(meta, damage);
            }
        } else {
            item.setDurability((short) damage);
        }
        return this;
    }

    /**
     * Sets the custom model data used by resource packs. Ignored before 1.14.
     *
     * @param data the model data
     * @return this builder
     */
    public ItemBuilder customModelData(int data) {
        if (meta != null && ServerVersion.current().isAtLeast(ServerVersion.V1_14)) {
            ModernItemSupport.setCustomModelData(meta, data);
        }
        return this;
    }

    // ------------------------------------------------------------------ special meta

    /**
     * Dyes leather armour.
     *
     * @param color the colour
     * @return this builder
     */
    public ItemBuilder color(Color color) {
        if (meta instanceof LeatherArmorMeta) {
            ((LeatherArmorMeta) meta).setColor(color);
        }
        return this;
    }

    /**
     * Sets the owner of a player head.
     *
     * @param owner the player
     * @return this builder
     */
    public ItemBuilder skullOwner(OfflinePlayer owner) {
        if (meta instanceof SkullMeta) {
            Skulls.applyOwner((SkullMeta) meta, owner);
        }
        return this;
    }

    /**
     * Applies a custom head texture (base64 value, texture URL, or hash).
     *
     * @param texture the texture
     * @return this builder
     */
    public ItemBuilder texture(String texture) {
        if (meta instanceof SkullMeta) {
            Skulls.applyTexture((SkullMeta) meta, texture);
        }
        return this;
    }

    /**
     * Stores a hidden string tag on the item (1.14+, ignored on older versions). See {@link ItemTags}.
     *
     * @param key   the key
     * @param value the value
     * @return this builder
     */
    public ItemBuilder tag(String key, String value) {
        if (meta != null && ItemTags.isSupported()) {
            ItemTags.setString(meta, key, value);
        }
        return this;
    }

    /**
     * Edits the meta directly for anything not covered by the builder.
     *
     * @param action the edit
     * @return this builder
     */
    public ItemBuilder meta(Consumer<ItemMeta> action) {
        if (meta != null) {
            action.accept(meta);
        }
        return this;
    }

    /**
     * Edits the meta as a specific subtype, if it is one.
     *
     * @param type   the meta class, e.g. {@code PotionMeta.class}
     * @param action the edit
     * @param <M>    the meta type
     * @return this builder
     */
    public <M extends ItemMeta> ItemBuilder meta(Class<M> type, Consumer<M> action) {
        if (type.isInstance(meta)) {
            action.accept(type.cast(meta));
        }
        return this;
    }

    // ------------------------------------------------------------------ build

    /**
     * Builds the item. The builder can be reused; every call returns a new copy.
     *
     * @return the item
     */
    public ItemStack build() {
        ItemStack result = item.clone();
        if (meta == null) {
            return result;
        }
        ItemMeta resultMeta = meta.clone();
        if (name != null) {
            resultMeta.setDisplayName(Text.color(apply(name)));
        }
        if (lore != null) {
            List<String> coloured = new ArrayList<>(lore.size());
            for (String line : lore) {
                // Allow a placeholder to expand into several lines with "\n".
                for (String part : apply(line).split("\n", -1)) {
                    coloured.add(Text.color(part));
                }
            }
            resultMeta.setLore(coloured);
        } else {
            resultMeta.setLore(null);
        }
        result.setItemMeta(resultMeta);
        return result;
    }

    private String apply(String text) {
        String result = text;
        for (Map.Entry<String, Object> entry : placeholders.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", String.valueOf(entry.getValue()));
        }
        return result;
    }
}
