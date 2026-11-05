package dev.lunamc.moonlib.display;

import dev.lunamc.moonlib.MoonLib;
import dev.lunamc.moonlib.util.Text;
import dev.lunamc.moonlib.version.Reflection;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Floating text in the world, for mine names, leaderboards and NPC labels.
 *
 * <pre>{@code
 * Hologram top = Hologram.create(location, "&d&lTOP MINERS", "&f1. Luna &7- &e1.2M", "&f2. Kai &7- &e900K");
 * top.line(1, "&f1. Luna &7- &e1.3M");          // change one line
 * top.lines(Arrays.asList("&d&lTOP MINERS", "&7(updating...)"));
 * top.teleport(newLocation);
 * top.delete();
 * }</pre>
 *
 * <p>On 1.19.4+ a hologram is a single text display entity (crisp, one entity for all lines).
 * Older versions use one invisible marker armor stand per line. Hologram entities are not saved
 * with the world where the server supports that (1.13+), so a crash never leaves orphaned text
 * behind; holograms are deleted when the owning plugin shuts down MoonLib.</p>
 *
 * <p>Create, change and delete holograms on the server thread (the location's region thread on
 * Folia).</p>
 *
 * @since 1.1.1-alpha
 */
public final class Hologram implements AutoCloseable {

    /** Vertical space between armor-stand lines. */
    private static final double LINE_SPACING = 0.27;

    private static final Class<?> TEXT_DISPLAY = Reflection.findClass("org.bukkit.entity.TextDisplay");
    private static final Class<?> ARMOR_STAND = Reflection.findClass("org.bukkit.entity.ArmorStand");

    private final List<String> lines = new ArrayList<>();
    private final List<Entity> entities = new ArrayList<>();
    private Location location;
    private boolean deleted;

    private Hologram(Location location) {
        this.location = location.clone();
    }

    /**
     * Spawns a hologram owned by the calling plugin.
     *
     * @param location where the top line appears
     * @param lines    the lines, colour codes allowed
     * @return the hologram
     */
    public static Hologram create(Location location, String... lines) {
        return create(MoonLib.pluginOrNull(), location, Arrays.asList(lines));
    }

    /**
     * Spawns a hologram owned by a plugin.
     *
     * @param plugin   the owner, or {@code null} to delete it yourself
     * @param location where the top line appears
     * @param lines    the lines
     * @return the hologram
     */
    public static Hologram create(Plugin plugin, Location location, List<String> lines) {
        if (location.getWorld() == null) {
            throw new IllegalArgumentException("A hologram location needs a world");
        }
        Hologram hologram = new Hologram(location);
        hologram.lines.addAll(lines);
        hologram.respawn();
        if (plugin != null && MoonLib.isInitialised(plugin)) {
            MoonLib.context(plugin).onShutdown(hologram);
        }
        return hologram;
    }

    /**
     * Returns whether this server uses text displays (1.19.4+) rather than armor stands.
     *
     * @return {@code true} for text displays
     */
    public static boolean usesTextDisplays() {
        return TEXT_DISPLAY != null;
    }

    /**
     * Replaces every line.
     *
     * @param newLines the lines
     * @return this hologram
     */
    public Hologram lines(List<String> newLines) {
        checkAlive();
        boolean sameCount = newLines.size() == lines.size();
        lines.clear();
        lines.addAll(newLines);
        if (TEXT_DISPLAY != null && !entities.isEmpty()) {
            setText(entities.get(0), joined());
        } else if (sameCount && entities.size() == lines.size()) {
            for (int i = 0; i < lines.size(); i++) {
                setName(entities.get(i), lines.get(i));
            }
        } else {
            respawn();
        }
        return this;
    }

    /**
     * Changes one line.
     *
     * @param index the zero-based line
     * @param text  the new text
     * @return this hologram
     * @throws IndexOutOfBoundsException if there is no such line
     */
    public Hologram line(int index, String text) {
        checkAlive();
        lines.set(index, text);
        if (TEXT_DISPLAY != null && !entities.isEmpty()) {
            setText(entities.get(0), joined());
        } else if (index < entities.size()) {
            setName(entities.get(index), text);
        }
        return this;
    }

    /**
     * Returns the lines.
     *
     * @return an unmodifiable view
     */
    public List<String> lines() {
        return Collections.unmodifiableList(lines);
    }

    /**
     * Moves the hologram.
     *
     * @param target the new top-line location
     * @return this hologram
     */
    public Hologram teleport(Location target) {
        checkAlive();
        if (target.getWorld() == null || !target.getWorld().equals(location.getWorld())) {
            location = target.clone();
            respawn();
            return this;
        }
        location = target.clone();
        for (int i = 0; i < entities.size(); i++) {
            entities.get(i).teleport(lineLocation(i));
        }
        return this;
    }

    /**
     * Returns the top-line location.
     *
     * @return a copy of the location
     */
    public Location location() {
        return location.clone();
    }

    /**
     * Returns whether {@link #delete()} has been called.
     *
     * @return {@code true} once deleted
     */
    public boolean isDeleted() {
        return deleted;
    }

    /** Removes the hologram from the world. Safe to call more than once. */
    public void delete() {
        if (deleted) {
            return;
        }
        deleted = true;
        despawn();
    }

    /** Same as {@link #delete()}. */
    @Override
    public void close() {
        delete();
    }

    // ------------------------------------------------------------------ internals

    private void checkAlive() {
        if (deleted) {
            throw new IllegalStateException("This hologram was deleted");
        }
    }

    private String joined() {
        List<String> coloured = new ArrayList<>(lines.size());
        for (String line : lines) {
            coloured.add(Text.color(line));
        }
        return String.join("\n", coloured);
    }

    private Location lineLocation(int index) {
        if (TEXT_DISPLAY != null) {
            return location.clone();
        }
        return location.clone().add(0, -LINE_SPACING * index, 0);
    }

    private void respawn() {
        despawn();
        World world = location.getWorld();
        if (TEXT_DISPLAY != null) {
            // Text displays anchor at the bottom; lift the entity so the top line sits at the location.
            Location at = location.clone().add(0, -LINE_SPACING * Math.max(0, lines.size() - 1), 0);
            Entity display = spawn(world, at, TEXT_DISPLAY);
            setText(display, joined());
            Object center = Reflection.staticField(Reflection.findClass("org.bukkit.entity.Display$Billboard"), "CENTER");
            call(display, "setBillboard", center);
            entities.add(display);
            return;
        }
        if (ARMOR_STAND == null) {
            throw new IllegalStateException("This server has neither text displays nor armor stands");
        }
        for (int i = 0; i < lines.size(); i++) {
            Entity stand = spawn(world, lineLocation(i), ARMOR_STAND);
            call(stand, "setVisible", false);
            call(stand, "setGravity", false);
            call(stand, "setMarker", true);
            call(stand, "setSmall", true);
            call(stand, "setBasePlate", false);
            call(stand, "setInvulnerable", true);
            setName(stand, lines.get(i));
            call(stand, "setCustomNameVisible", true);
            entities.add(stand);
        }
    }

    private void despawn() {
        for (Entity entity : entities) {
            call(entity, "remove");
        }
        entities.clear();
    }

    private static Entity spawn(World world, Location at, Class<?> type) {
        Method spawn = Reflection.method(World.class, "spawn", Location.class, Class.class);
        if (spawn == null) {
            throw new IllegalStateException("World#spawn is not available on this server");
        }
        Entity entity = (Entity) Reflection.invoke(spawn, world, at, type);
        call(entity, "setPersistent", false);
        call(entity, "addScoreboardTag", "moonlib-hologram");
        return entity;
    }

    private static void setText(Entity entity, String text) {
        call(entity, "setText", text);
    }

    private static void setName(Entity entity, String text) {
        String coloured = Text.color(text);
        // An empty custom name hides the stand's label entirely; a space keeps the line's height.
        call(entity, "setCustomName", coloured.isEmpty() ? " " : coloured);
    }

    /** Calls an optional method, ignoring it if this server version does not have it. */
    private static void call(Object target, String name, Object... args) {
        if (target == null) {
            return;
        }
        for (Method method : target.getClass().getMethods()) {
            if (!method.getName().equals(name) || method.getParameterCount() != args.length) {
                continue;
            }
            Class<?>[] types = method.getParameterTypes();
            boolean fits = true;
            for (int i = 0; i < args.length && fits; i++) {
                Class<?> t = types[i].isPrimitive() ? box(types[i]) : types[i];
                fits = args[i] == null ? !types[i].isPrimitive() : t.isInstance(args[i]);
            }
            if (fits) {
                Reflection.invoke(method, target, args);
                return;
            }
        }
    }

    private static Class<?> box(Class<?> primitive) {
        if (primitive == boolean.class) {
            return Boolean.class;
        }
        if (primitive == int.class) {
            return Integer.class;
        }
        if (primitive == double.class) {
            return Double.class;
        }
        if (primitive == float.class) {
            return Float.class;
        }
        if (primitive == long.class) {
            return Long.class;
        }
        return primitive;
    }
}
