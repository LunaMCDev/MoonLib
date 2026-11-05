package com.lunamcdev.moonlib.version;

import org.bukkit.entity.Player;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Small, cached reflection helpers used by MoonLib's version adapters. Public so plugins can use the
 * same helpers for their own version-specific code.
 *
 * <pre>{@code
 * Class<?> packet = Reflection.nmsClass("PacketPlayOutChat");          // net.minecraft.server.v1_8_R3.PacketPlayOutChat
 * Class<?> craftPlayer = Reflection.craftClass("entity.CraftPlayer");  // org.bukkit.craftbukkit.v1_8_R3.entity.CraftPlayer
 * Reflection.sendPacket(player, packetInstance);
 * }</pre>
 *
 * <p>Every lookup is cached, so repeated calls are cheap. Lookup methods return {@code null}
 * instead of throwing when something does not exist, which keeps feature detection simple.</p>
 */
public final class Reflection {

    private static final Map<String, Class<?>> CLASS_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Method> METHOD_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Field> FIELD_CACHE = new ConcurrentHashMap<>();
    private static final Class<?> MISSING = Void.class;

    private Reflection() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Returns whether a class exists on the server's classpath.
     *
     * @param name the fully qualified class name
     * @return {@code true} if the class can be loaded
     */
    public static boolean classExists(String name) {
        return findClass(name) != null;
    }

    /**
     * Finds the first of the given classes that exists.
     *
     * @param names fully qualified class names to try in order
     * @return the first class found, or {@code null}
     */
    public static Class<?> findClass(String... names) {
        for (String name : names) {
            Class<?> cached = CLASS_CACHE.get(name);
            if (cached == null) {
                try {
                    cached = Class.forName(name);
                } catch (ClassNotFoundException | LinkageError e) {
                    cached = MISSING;
                }
                CLASS_CACHE.put(name, cached);
            }
            if (cached != MISSING) {
                return cached;
            }
        }
        return null;
    }

    /**
     * Finds a legacy NMS class, e.g. {@code nmsClass("PacketPlayOutChat")}. Only meaningful on
     * servers that relocate NMS by version (1.8 to 1.16).
     *
     * @param simpleName the class name inside {@code net.minecraft.server.<version>}
     * @return the class, or {@code null}
     */
    public static Class<?> nmsClass(String simpleName) {
        String pkg = ServerVersion.nmsPackage();
        return pkg.isEmpty()
                ? findClass("net.minecraft.server." + simpleName)
                : findClass("net.minecraft.server." + pkg + "." + simpleName);
    }

    /**
     * Finds a CraftBukkit class, e.g. {@code craftClass("entity.CraftPlayer")}.
     *
     * @param path the class path inside {@code org.bukkit.craftbukkit[.<version>]}
     * @return the class, or {@code null}
     */
    public static Class<?> craftClass(String path) {
        String pkg = ServerVersion.nmsPackage();
        return pkg.isEmpty()
                ? findClass("org.bukkit.craftbukkit." + path)
                : findClass("org.bukkit.craftbukkit." + pkg + "." + path);
    }

    /**
     * Finds a public or declared method in a class or any of its superclasses and makes it accessible.
     *
     * @param owner      the class to search
     * @param name       the method name
     * @param parameters the parameter types
     * @return the method, or {@code null}
     */
    public static Method method(Class<?> owner, String name, Class<?>... parameters) {
        if (owner == null) {
            return null;
        }
        StringBuilder key = new StringBuilder(owner.getName()).append('#').append(name);
        for (Class<?> parameter : parameters) {
            key.append(',').append(parameter == null ? "null" : parameter.getName());
        }
        String cacheKey = key.toString();
        Method cached = METHOD_CACHE.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        Method found = null;
        try {
            found = owner.getMethod(name, parameters);
        } catch (NoSuchMethodException | LinkageError ignored) {
            for (Class<?> type = owner; type != null && found == null; type = type.getSuperclass()) {
                try {
                    found = type.getDeclaredMethod(name, parameters);
                } catch (NoSuchMethodException | LinkageError ignoredToo) {
                    // keep looking in the superclass
                }
            }
        }
        if (found != null) {
            try {
                found.setAccessible(true);
            } catch (RuntimeException ignored) {
                // module restrictions: public methods still work
            }
            METHOD_CACHE.put(cacheKey, found);
        }
        return found;
    }

    /**
     * Finds a field in a class or any of its superclasses and makes it accessible.
     *
     * @param owner the class to search
     * @param name  the field name
     * @return the field, or {@code null}
     */
    public static Field field(Class<?> owner, String name) {
        if (owner == null) {
            return null;
        }
        String cacheKey = owner.getName() + '.' + name;
        Field cached = FIELD_CACHE.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        for (Class<?> type = owner; type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                FIELD_CACHE.put(cacheKey, field);
                return field;
            } catch (NoSuchFieldException | RuntimeException | LinkageError ignored) {
                // keep looking in the superclass
            }
        }
        return null;
    }

    /**
     * Invokes a method, wrapping checked exceptions.
     *
     * @param method the method
     * @param target the instance, or {@code null} for static methods
     * @param args   the arguments
     * @return the return value
     * @throws IllegalStateException if invocation fails
     */
    public static Object invoke(Method method, Object target, Object... args) {
        try {
            return method.invoke(target, args);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to invoke " + method, e);
        }
    }

    /**
     * Reads a field value, wrapping checked exceptions.
     *
     * @param field  the field
     * @param target the instance, or {@code null} for static fields
     * @return the value
     * @throws IllegalStateException if the field cannot be read
     */
    public static Object get(Field field, Object target) {
        try {
            return field.get(target);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Failed to read " + field, e);
        }
    }

    /**
     * Reads a public static field by name, returning {@code null} if it does not exist. This is how
     * MoonLib resolves {@code Sound} and {@code Enchantment} constants, which works whether the type is
     * an enum (old versions) or a registry-backed interface (new versions).
     *
     * @param owner the class
     * @param name  the field name
     * @return the field value, or {@code null}
     */
    public static Object staticField(Class<?> owner, String name) {
        try {
            return owner.getField(name).get(null);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            return null;
        }
    }

    /**
     * Sends a packet to a player on legacy (1.8 to 1.16) servers via
     * {@code ((CraftPlayer) player).getHandle().playerConnection.sendPacket(packet)}.
     *
     * @param player the player
     * @param packet the NMS packet instance
     * @throws IllegalStateException if the packet could not be sent
     */
    public static void sendPacket(Player player, Object packet) {
        try {
            Object handle = method(player.getClass(), "getHandle").invoke(player);
            Object connection = field(handle.getClass(), "playerConnection").get(handle);
            Method send = method(connection.getClass(), "sendPacket", nmsClass("Packet"));
            send.invoke(connection, packet);
        } catch (ReflectiveOperationException | RuntimeException e) {
            throw new IllegalStateException("Failed to send packet " + packet.getClass().getSimpleName(), e);
        }
    }
}
