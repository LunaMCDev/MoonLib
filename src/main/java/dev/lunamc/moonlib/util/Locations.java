package dev.lunamc.moonlib.util;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.Locale;

/**
 * Location helpers, including a compact string format that is ideal for storing locations in YAML
 * or a database.
 *
 * <pre>{@code
 * String stored = Locations.serialize(player.getLocation()); // "world;100.5;64.0;-20.5;90.0;0.0"
 * Location spawn = Locations.deserialize(stored);
 * Location middle = Locations.center(block.getLocation());   // x.5, y, z.5
 * }</pre>
 */
public final class Locations {

    private static final String SEPARATOR = ";";

    private Locations() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Serialises a location as {@code world;x;y;z;yaw;pitch}.
     *
     * @param location the location, its world must not be {@code null}
     * @return the serialised location
     */
    public static String serialize(Location location) {
        World world = location.getWorld();
        if (world == null) {
            throw new IllegalArgumentException("Location has no world");
        }
        return world.getName() + SEPARATOR + format(location.getX()) + SEPARATOR + format(location.getY())
                + SEPARATOR + format(location.getZ()) + SEPARATOR + format(location.getYaw())
                + SEPARATOR + format(location.getPitch());
    }

    /**
     * Serialises the block coordinates of a location as {@code world;x;y;z}.
     *
     * @param location the location
     * @return the serialised block location
     */
    public static String serializeBlock(Location location) {
        World world = location.getWorld();
        if (world == null) {
            throw new IllegalArgumentException("Location has no world");
        }
        return world.getName() + SEPARATOR + location.getBlockX() + SEPARATOR + location.getBlockY()
                + SEPARATOR + location.getBlockZ();
    }

    /**
     * Parses a location written by {@link #serialize(Location)} or {@link #serializeBlock(Location)}.
     * Commas are accepted as separators too.
     *
     * @param text the serialised location
     * @return the location, or {@code null} if the text is invalid or the world is not loaded
     */
    public static Location deserialize(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        String[] parts = text.split(text.contains(SEPARATOR) ? SEPARATOR : ",");
        if (parts.length < 4) {
            return null;
        }
        World world = Bukkit.getWorld(parts[0].trim());
        if (world == null) {
            return null;
        }
        try {
            double x = Double.parseDouble(parts[1].trim());
            double y = Double.parseDouble(parts[2].trim());
            double z = Double.parseDouble(parts[3].trim());
            float yaw = parts.length > 4 ? Float.parseFloat(parts[4].trim()) : 0F;
            float pitch = parts.length > 5 ? Float.parseFloat(parts[5].trim()) : 0F;
            return new Location(world, x, y, z, yaw, pitch);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Returns the centre of the block at the given location (x + 0.5, y, z + 0.5), keeping yaw and pitch.
     *
     * @param location the location
     * @return a new centred location
     */
    public static Location center(Location location) {
        Location centred = location.clone();
        centred.setX(location.getBlockX() + 0.5);
        centred.setY(location.getBlockY());
        centred.setZ(location.getBlockZ() + 0.5);
        return centred;
    }

    /**
     * Returns whether two locations are in the same block of the same world.
     *
     * @param a the first location
     * @param b the second location
     * @return {@code true} if both point at the same block
     */
    public static boolean isSameBlock(Location a, Location b) {
        return a != null && b != null && a.getWorld() != null && a.getWorld().equals(b.getWorld())
                && a.getBlockX() == b.getBlockX() && a.getBlockY() == b.getBlockY() && a.getBlockZ() == b.getBlockZ();
    }

    /**
     * Distance between two locations that never throws: different worlds return {@link Double#MAX_VALUE}.
     *
     * @param a the first location
     * @param b the second location
     * @return the distance, or {@code Double.MAX_VALUE} if the worlds differ
     */
    public static double distance(Location a, Location b) {
        if (a.getWorld() == null || !a.getWorld().equals(b.getWorld())) {
            return Double.MAX_VALUE;
        }
        return a.distance(b);
    }

    /**
     * Returns whether {@code b} is within {@code radius} blocks of {@code a}, using squared distance
     * for speed.
     *
     * @param a      the centre
     * @param b      the location to test
     * @param radius the radius in blocks
     * @return {@code true} if within range and in the same world
     */
    public static boolean isWithin(Location a, Location b, double radius) {
        return a.getWorld() != null && a.getWorld().equals(b.getWorld()) && a.distanceSquared(b) <= radius * radius;
    }

    /**
     * Returns the highest non-air block's location above the given column, plus one, so a player can
     * safely stand there.
     *
     * @param location any location in the column
     * @return a safe standing location centred on the block
     */
    public static Location highestSafe(Location location) {
        World world = location.getWorld();
        if (world == null) {
            throw new IllegalArgumentException("Location has no world");
        }
        Block highest = world.getHighestBlockAt(location);
        Location result = center(highest.getLocation());
        if (!highest.getType().name().endsWith("AIR")) {
            result.add(0, 1, 0);
        }
        result.setYaw(location.getYaw());
        result.setPitch(location.getPitch());
        return result;
    }

    /**
     * Formats a location for display: {@code "world (100, 64, -20)"}.
     *
     * @param location the location
     * @return a friendly string
     */
    public static String toFriendlyString(Location location) {
        String world = location.getWorld() == null ? "?" : location.getWorld().getName();
        return world + " (" + location.getBlockX() + ", " + location.getBlockY() + ", " + location.getBlockZ() + ")";
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }
}
