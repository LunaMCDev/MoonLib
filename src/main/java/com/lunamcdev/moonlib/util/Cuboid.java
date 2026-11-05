package com.lunamcdev.moonlib.util;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ThreadLocalRandom;

/**
 * An immutable box-shaped region of blocks between two corners, inclusive. Ideal for mines, arenas,
 * plots and safe zones.
 *
 * <pre>{@code
 * Cuboid mine = new Cuboid(pos1, pos2);
 *
 * if (mine.contains(block.getLocation())) { ... }
 *
 * // Refill with a weighted composition: 70% stone, 20% coal ore, 10% iron ore.
 * Map<Material, Double> composition = new LinkedHashMap<>();
 * composition.put(Material.STONE, 70.0);
 * composition.put(Material.COAL_ORE, 20.0);
 * composition.put(Material.IRON_ORE, 10.0);
 * mine.fill(composition);
 *
 * for (Player inside : mine.players()) inside.teleport(mine.top());
 * String saved = mine.serialize();
 * }</pre>
 *
 * <p>{@link #fill(Material)} and {@link #fill(Map)} change blocks synchronously; on big regions
 * (hundreds of thousands of blocks) spread the work over several ticks or use FastAsyncWorldEdit.</p>
 */
public final class Cuboid implements Iterable<Block> {

    private final String worldName;
    private final int minX;
    private final int minY;
    private final int minZ;
    private final int maxX;
    private final int maxY;
    private final int maxZ;

    /**
     * Creates a cuboid from two corners in the same world.
     *
     * @param a one corner
     * @param b the opposite corner
     * @throws IllegalArgumentException if the corners are in different worlds
     */
    public Cuboid(Location a, Location b) {
        if (a.getWorld() == null || !a.getWorld().equals(b.getWorld())) {
            throw new IllegalArgumentException("Both corners must be in the same world");
        }
        this.worldName = a.getWorld().getName();
        this.minX = Math.min(a.getBlockX(), b.getBlockX());
        this.minY = Math.min(a.getBlockY(), b.getBlockY());
        this.minZ = Math.min(a.getBlockZ(), b.getBlockZ());
        this.maxX = Math.max(a.getBlockX(), b.getBlockX());
        this.maxY = Math.max(a.getBlockY(), b.getBlockY());
        this.maxZ = Math.max(a.getBlockZ(), b.getBlockZ());
    }

    /**
     * Creates a cuboid from raw coordinates.
     *
     * @param worldName the world name
     * @param x1        first x
     * @param y1        first y
     * @param z1        first z
     * @param x2        second x
     * @param y2        second y
     * @param z2        second z
     */
    public Cuboid(String worldName, int x1, int y1, int z1, int x2, int y2, int z2) {
        this.worldName = worldName;
        this.minX = Math.min(x1, x2);
        this.minY = Math.min(y1, y2);
        this.minZ = Math.min(z1, z2);
        this.maxX = Math.max(x1, x2);
        this.maxY = Math.max(y1, y2);
        this.maxZ = Math.max(z1, z2);
    }

    /**
     * Parses a cuboid written by {@link #serialize()}.
     *
     * @param text {@code world;x1;y1;z1;x2;y2;z2}
     * @return the cuboid
     * @throws IllegalArgumentException if the text is invalid
     */
    public static Cuboid deserialize(String text) {
        String[] parts = text.split(";");
        if (parts.length != 7) {
            throw new IllegalArgumentException("Invalid cuboid: " + text);
        }
        try {
            return new Cuboid(parts[0], Integer.parseInt(parts[1]), Integer.parseInt(parts[2]),
                    Integer.parseInt(parts[3]), Integer.parseInt(parts[4]), Integer.parseInt(parts[5]),
                    Integer.parseInt(parts[6]));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid cuboid: " + text, e);
        }
    }

    /**
     * Serialises the cuboid as {@code world;x1;y1;z1;x2;y2;z2}.
     *
     * @return the text
     */
    public String serialize() {
        return worldName + ";" + minX + ";" + minY + ";" + minZ + ";" + maxX + ";" + maxY + ";" + maxZ;
    }

    /**
     * Returns the world.
     *
     * @return the world, or {@code null} if it is not loaded
     */
    public World world() {
        return Bukkit.getWorld(worldName);
    }

    /**
     * Returns the world name.
     *
     * @return the name
     */
    public String worldName() {
        return worldName;
    }

    /**
     * Returns whether a location is inside.
     *
     * @param location the location
     * @return {@code true} if inside
     */
    public boolean contains(Location location) {
        return location.getWorld() != null && location.getWorld().getName().equals(worldName)
                && contains(location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    /**
     * Returns whether block coordinates are inside (world not checked).
     *
     * @param x the x
     * @param y the y
     * @param z the z
     * @return {@code true} if inside
     */
    public boolean contains(int x, int y, int z) {
        return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }

    /**
     * Returns the number of blocks.
     *
     * @return the volume
     */
    public long volume() {
        return (long) sizeX() * sizeY() * sizeZ();
    }

    /**
     * Returns the width along X.
     *
     * @return the size in blocks
     */
    public int sizeX() {
        return maxX - minX + 1;
    }

    /**
     * Returns the height.
     *
     * @return the size in blocks
     */
    public int sizeY() {
        return maxY - minY + 1;
    }

    /**
     * Returns the width along Z.
     *
     * @return the size in blocks
     */
    public int sizeZ() {
        return maxZ - minZ + 1;
    }

    /**
     * Returns the minimum corner.
     *
     * @return a new location
     */
    public Location min() {
        return new Location(world(), minX, minY, minZ);
    }

    /**
     * Returns the maximum corner.
     *
     * @return a new location
     */
    public Location max() {
        return new Location(world(), maxX, maxY, maxZ);
    }

    /**
     * Returns the exact centre.
     *
     * @return a new location
     */
    public Location center() {
        return new Location(world(), (minX + maxX + 1) / 2.0, (minY + maxY + 1) / 2.0, (minZ + maxZ + 1) / 2.0);
    }

    /**
     * Returns the centre of the top face, one block above the region. A safe place to teleport
     * players out of a mine before it resets.
     *
     * @return a new location
     */
    public Location top() {
        return new Location(world(), (minX + maxX + 1) / 2.0, maxY + 1, (minZ + maxZ + 1) / 2.0);
    }

    /**
     * Returns a random block location inside.
     *
     * @return a new location
     */
    public Location randomLocation() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        return new Location(world(), random.nextInt(minX, maxX + 1), random.nextInt(minY, maxY + 1),
                random.nextInt(minZ, maxZ + 1));
    }

    /**
     * Returns every online player inside.
     *
     * @return the players
     */
    public List<Player> players() {
        List<Player> inside = new ArrayList<>();
        World world = world();
        if (world == null) {
            return inside;
        }
        for (Player player : world.getPlayers()) {
            if (contains(player.getLocation())) {
                inside.add(player);
            }
        }
        return inside;
    }

    /**
     * Sets every block to one material, without physics updates.
     *
     * @param material the material
     */
    public void fill(Material material) {
        for (Block block : this) {
            block.setType(material, false);
        }
    }

    /**
     * Fills with a weighted random mix of materials, without physics updates. Weights do not need
     * to add up to 100.
     *
     * @param weights material to weight
     */
    public void fill(Map<Material, Double> weights) {
        if (weights.isEmpty()) {
            throw new IllegalArgumentException("Composition is empty");
        }
        Map<Material, Double> cumulative = new LinkedHashMap<>();
        double total = 0;
        for (Map.Entry<Material, Double> entry : weights.entrySet()) {
            if (entry.getValue() > 0) {
                total += entry.getValue();
                cumulative.put(entry.getKey(), total);
            }
        }
        if (total <= 0) {
            throw new IllegalArgumentException("Composition weights must be positive");
        }
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (Block block : this) {
            double roll = random.nextDouble(total);
            for (Map.Entry<Material, Double> entry : cumulative.entrySet()) {
                if (roll < entry.getValue()) {
                    block.setType(entry.getKey(), false);
                    break;
                }
            }
        }
    }

    /**
     * Counts blocks that are not air. Divide by {@link #volume()} for a "percent remaining" mine reset trigger.
     *
     * @return the number of solid blocks
     */
    public long countNonAir() {
        long count = 0;
        for (Block block : this) {
            if (!block.getType().name().endsWith("AIR")) {
                count++;
            }
        }
        return count;
    }

    /**
     * Returns a cuboid expanded by an amount in every direction.
     *
     * @param amount blocks to grow (negative shrinks)
     * @return a new cuboid
     */
    public Cuboid expand(int amount) {
        return new Cuboid(worldName, minX - amount, minY - amount, minZ - amount,
                maxX + amount, maxY + amount, maxZ + amount);
    }

    /**
     * Iterates every block, x fastest, then z, then y (bottom to top).
     *
     * @return the iterator
     * @throws IllegalStateException if the world is not loaded
     */
    @Override
    public Iterator<Block> iterator() {
        World world = world();
        if (world == null) {
            throw new IllegalStateException("World " + worldName + " is not loaded");
        }
        return new Iterator<Block>() {
            private int x = minX;
            private int y = minY;
            private int z = minZ;

            @Override
            public boolean hasNext() {
                return y <= maxY;
            }

            @Override
            public Block next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                Block block = world.getBlockAt(x, y, z);
                if (++x > maxX) {
                    x = minX;
                    if (++z > maxZ) {
                        z = minZ;
                        y++;
                    }
                }
                return block;
            }
        };
    }

    @Override
    public String toString() {
        return "Cuboid{" + serialize() + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Cuboid)) {
            return false;
        }
        Cuboid other = (Cuboid) o;
        return worldName.equals(other.worldName) && minX == other.minX && minY == other.minY && minZ == other.minZ
                && maxX == other.maxX && maxY == other.maxY && maxZ == other.maxZ;
    }

    @Override
    public int hashCode() {
        int result = worldName.hashCode();
        result = 31 * result + minX;
        result = 31 * result + minY;
        result = 31 * result + minZ;
        result = 31 * result + maxX;
        result = 31 * result + maxY;
        result = 31 * result + maxZ;
        return result;
    }
}
