package com.lunamcdev.moonlib.config;

import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.configuration.serialization.ConfigurationSerialization;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Converts Java objects to and from the plain data trees (maps, lists, strings, numbers, booleans)
 * stored in YAML and JSON files. {@link DataFile} uses it for every {@code get}/{@code set}, so you can
 * store your own classes directly:
 *
 * <pre>{@code
 * public final class PlayerData {
 *     private int tokens;
 *     private String rank = "A";
 *     private List<String> unlockedMines = new ArrayList<>();
 *     private Map<Material, Integer> blocksMined = new EnumMap<>(Material.class);
 *     private Location home;                  // Bukkit types work too
 *     private transient boolean dirty;        // transient fields are skipped
 * }
 *
 * file.set("players." + uuid, data);
 * PlayerData loaded = file.get("players." + uuid, PlayerData.class);
 * }</pre>
 *
 * <h2>Supported types</h2>
 * <ul>
 *     <li>Strings, primitives and their wrappers, {@link BigInteger}, {@link BigDecimal}</li>
 *     <li>Enums (by name, case-insensitive when reading), {@link UUID}</li>
 *     <li>{@link Instant}, {@link LocalDate}, {@link LocalDateTime}, {@link Duration} (ISO-8601 text)</li>
 *     <li>{@link List}, {@link Set}, any {@link Collection}, arrays, {@link EnumSet}</li>
 *     <li>{@link Map} with string, enum, UUID or number keys ({@link EnumMap} and sorted maps included)</li>
 *     <li>Bukkit {@link ConfigurationSerializable} types: {@code ItemStack}, {@code Location},
 *     {@code Vector}, {@code Color}, ... (also inside JSON, using Bukkit's {@code "=="} type key)</li>
 *     <li>Your own classes: every non-static, non-transient field, including inherited ones. A no-argument
 *     constructor (any visibility) is required; fields missing from the file keep the value that
 *     constructor gave them, so adding new fields later is safe.</li>
 *     <li>Java records (Java 16+), through their canonical constructor</li>
 * </ul>
 *
 * <p>Anything else can be supported with {@link #register(Class, Adapter)}.</p>
 */
public final class DataMapper {

    /**
     * Custom conversion for one type.
     *
     * @param <T> the type
     */
    public interface Adapter<T> {
        /**
         * Converts a value to something storable: a string, number, boolean, map, list, or any object
         * {@link DataMapper} already supports.
         *
         * @param value the value, never {@code null}
         * @return the stored form
         */
        Object write(T value);

        /**
         * Converts the stored form back.
         *
         * @param stored what {@link #write} produced, after a round trip through the file
         * @return the value
         */
        T read(Object stored);
    }

    private static final Map<Class<?>, Adapter<?>> ADAPTERS = new ConcurrentHashMap<>();
    private static final Map<Class<?>, List<Field>> FIELD_CACHE = new ConcurrentHashMap<>();
    private static final String TYPE_KEY = "==";
    private static final int MAX_DEPTH = 128;

    static {
        register(Instant.class, adapter(Instant::toString, s -> Instant.parse(s.toString())));
        register(LocalDate.class, adapter(LocalDate::toString, s -> LocalDate.parse(s.toString())));
        register(LocalDateTime.class, adapter(LocalDateTime::toString, s -> LocalDateTime.parse(s.toString())));
        register(Duration.class, adapter(Duration::toString, s -> Duration.parse(s.toString())));
    }

    private DataMapper() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Registers a custom conversion. It takes priority over the built-in rules and applies to subclasses.
     *
     * <pre>{@code
     * DataMapper.register(Mine.class, new DataMapper.Adapter<Mine>() {
     *     public Object write(Mine mine) { return mine.id(); }
     *     public Mine read(Object stored) { return mines.byId(stored.toString()); }
     * });
     * }</pre>
     *
     * @param type    the type
     * @param adapter the conversion
     * @param <T>     the type
     */
    public static <T> void register(Class<T> type, Adapter<T> adapter) {
        ADAPTERS.put(type, adapter);
    }

    /**
     * Removes a custom conversion registered with {@link #register(Class, Adapter)}.
     *
     * @param type the type
     */
    public static void unregister(Class<?> type) {
        ADAPTERS.remove(type);
    }

    /**
     * Creates an adapter from two functions.
     *
     * @param write converts a value to its stored form
     * @param read  converts the stored form back
     * @param <T>   the type
     * @return the adapter
     */
    public static <T> Adapter<T> adapter(java.util.function.Function<T, Object> write,
                                         java.util.function.Function<Object, T> read) {
        return new Adapter<T>() {
            @Override
            public Object write(T value) {
                return write.apply(value);
            }

            @Override
            public T read(Object stored) {
                return read.apply(stored);
            }
        };
    }

    // ------------------------------------------------------------------ object -> tree

    /**
     * Converts an object to a JSON-compatible tree: Bukkit serializable objects become maps with a
     * {@code "=="} type key.
     *
     * @param value the object
     * @return maps, lists, strings, numbers, booleans and {@code null}
     */
    public static Object toTree(Object value) {
        return toTree(value, false);
    }

    /**
     * Converts an object to a tree.
     *
     * @param value            the object
     * @param keepSerializable {@code true} to leave {@link ConfigurationSerializable} objects as they are
     *                         (Bukkit's YAML writes them natively)
     * @return the tree
     */
    public static Object toTree(Object value, boolean keepSerializable) {
        return write(value, keepSerializable, new IdentityHashMap<>(), 0);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object write(Object value, boolean keepSerializable, IdentityHashMap<Object, Boolean> seen, int depth) {
        if (value == null || value instanceof String || value instanceof Boolean) {
            return value;
        }
        if (depth > MAX_DEPTH) {
            throw new IllegalArgumentException("Object graph is nested too deeply (or contains a cycle)");
        }
        Adapter adapter = adapterFor(value.getClass());
        if (adapter != null) {
            return write(adapter.write(value), keepSerializable, seen, depth + 1);
        }
        if (value instanceof Character) {
            return value.toString();
        }
        if (value instanceof BigInteger || value instanceof BigDecimal) {
            return value.toString(); // YAML and JSON readers can't be trusted to keep arbitrary precision
        }
        if (value instanceof Number) {
            return value;
        }
        if (value instanceof Enum) {
            return ((Enum<?>) value).name();
        }
        if (value instanceof UUID) {
            return value.toString();
        }
        if (value instanceof ConfigurationSerializable) {
            if (keepSerializable) {
                return value;
            }
            Map<String, Object> map = new LinkedHashMap<>();
            map.put(TYPE_KEY, ConfigurationSerialization.getAlias((Class<? extends ConfigurationSerializable>) value.getClass()));
            for (Map.Entry<String, Object> entry : ((ConfigurationSerializable) value).serialize().entrySet()) {
                map.put(entry.getKey(), write(entry.getValue(), false, seen, depth + 1));
            }
            return map;
        }
        if (seen.put(value, Boolean.TRUE) != null) {
            throw new IllegalArgumentException("Cannot store an object that references itself ("
                    + value.getClass().getName() + ")");
        }
        try {
            if (value instanceof Map) {
                Map<String, Object> map = new LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                    map.put(keyToString(entry.getKey()), write(entry.getValue(), keepSerializable, seen, depth + 1));
                }
                return map;
            }
            if (value instanceof Collection) {
                List<Object> list = new ArrayList<>(((Collection<?>) value).size());
                for (Object element : (Collection<?>) value) {
                    list.add(write(element, keepSerializable, seen, depth + 1));
                }
                return list;
            }
            if (value.getClass().isArray()) {
                int length = Array.getLength(value);
                List<Object> list = new ArrayList<>(length);
                for (int i = 0; i < length; i++) {
                    list.add(write(Array.get(value, i), keepSerializable, seen, depth + 1));
                }
                return list;
            }
            return writeObject(value, keepSerializable, seen, depth);
        } finally {
            seen.remove(value);
        }
    }

    private static Map<String, Object> writeObject(Object value, boolean keepSerializable,
                                                   IdentityHashMap<Object, Boolean> seen, int depth) {
        Class<?> type = value.getClass();
        requireMappable(type);
        Map<String, Object> map = new LinkedHashMap<>();
        for (Field field : fields(type)) {
            try {
                map.put(field.getName(), write(field.get(value), keepSerializable, seen, depth + 1));
            } catch (IllegalAccessException e) {
                throw new IllegalArgumentException("Cannot read field " + type.getSimpleName() + "." + field.getName(), e);
            }
        }
        return map;
    }

    private static String keyToString(Object key) {
        if (key instanceof Enum) {
            return ((Enum<?>) key).name();
        }
        return String.valueOf(key);
    }

    // ------------------------------------------------------------------ tree -> object

    /**
     * Converts a tree back to an object.
     *
     * @param tree the stored data
     * @param type the class to create
     * @param <T>  the type
     * @return the object, or {@code null} if {@code tree} is {@code null} (zero/false for primitives)
     * @throws IllegalArgumentException if the data can't be converted, with a message explaining why
     */
    @SuppressWarnings("unchecked")
    public static <T> T fromTree(Object tree, Class<T> type) {
        return (T) read(tree, type, 0);
    }

    /**
     * Converts a tree back to an object of a generic type, such as {@code List<UUID>}.
     *
     * @param tree the stored data
     * @param type the target type (use {@link #listOf(Class)}, {@link #setOf(Class)}, {@link #mapOf(Class, Class)}
     *             or a field's generic type)
     * @return the object
     */
    public static Object fromTree(Object tree, Type type) {
        return read(tree, type, 0);
    }

    /**
     * Returns the type {@code List<E>}.
     *
     * @param element the element class
     * @return the parameterized type
     */
    public static Type listOf(Class<?> element) {
        return parameterized(List.class, element);
    }

    /**
     * Returns the type {@code Set<E>}.
     *
     * @param element the element class
     * @return the parameterized type
     */
    public static Type setOf(Class<?> element) {
        return parameterized(Set.class, element);
    }

    /**
     * Returns the type {@code Map<K, V>}.
     *
     * @param key   the key class
     * @param value the value class
     * @return the parameterized type
     */
    public static Type mapOf(Class<?> key, Class<?> value) {
        return parameterized(Map.class, key, value);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object read(Object tree, Type type, int depth) {
        if (depth > MAX_DEPTH) {
            throw new IllegalArgumentException("Data is nested too deeply");
        }
        Class<?> raw = rawClass(type);
        if (tree == null) {
            return defaultValue(raw);
        }
        Adapter adapter = adapterFor(raw);
        if (adapter != null) {
            return adapter.read(tree);
        }
        if (raw == Object.class) {
            return deserializeNested(tree, depth);
        }
        if (raw == String.class) {
            if (tree instanceof Map || tree instanceof Collection) {
                throw mismatch(tree, raw);
            }
            return tree.toString();
        }
        if (raw.isPrimitive() || Number.class.isAssignableFrom(raw) || raw == Boolean.class || raw == Character.class) {
            return readScalar(tree, raw);
        }
        if (raw.isEnum()) {
            return readEnum(tree.toString(), (Class<? extends Enum>) raw);
        }
        if (raw == UUID.class) {
            try {
                return UUID.fromString(tree.toString().trim());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("'" + tree + "' is not a valid UUID");
            }
        }
        if (ConfigurationSerializable.class.isAssignableFrom(raw)) {
            return readSerializable(tree, (Class<? extends ConfigurationSerializable>) raw, depth);
        }
        if (raw.isArray()) {
            List<Object> elements = asList(tree);
            Type component = type instanceof GenericArrayType
                    ? ((GenericArrayType) type).getGenericComponentType() : raw.getComponentType();
            Object array = Array.newInstance(rawClass(component), elements.size());
            for (int i = 0; i < elements.size(); i++) {
                Array.set(array, i, read(elements.get(i), component, depth + 1));
            }
            return array;
        }
        if (Collection.class.isAssignableFrom(raw)) {
            Type element = typeArgument(type, 0);
            Collection collection = newCollection(raw, rawClass(element));
            for (Object item : asList(tree)) {
                collection.add(read(item, element, depth + 1));
            }
            return collection;
        }
        if (Map.class.isAssignableFrom(raw)) {
            if (!(tree instanceof Map)) {
                throw mismatch(tree, raw);
            }
            Type keyType = typeArgument(type, 0);
            Type valueType = typeArgument(type, 1);
            Map map = newMap(raw, rawClass(keyType));
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) tree).entrySet()) {
                Object key = rawClass(keyType) == Object.class ? String.valueOf(entry.getKey())
                        : read(String.valueOf(entry.getKey()), keyType, depth + 1);
                map.put(key, read(entry.getValue(), valueType, depth + 1));
            }
            return map;
        }
        if (raw.isInstance(tree) && !(tree instanceof Map)) {
            return tree;
        }
        if (!(tree instanceof Map)) {
            throw mismatch(tree, raw);
        }
        return readObject((Map<?, ?>) tree, raw, depth);
    }

    private static Object readObject(Map<?, ?> tree, Class<?> type, int depth) {
        requireMappable(type);
        Object recordInstance = readRecord(tree, type, depth);
        if (recordInstance != null) {
            return recordInstance;
        }
        Object instance;
        try {
            Constructor<?> constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            instance = constructor.newInstance();
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException(type.getName() + " needs a no-argument constructor (it may be private) "
                    + "to be loaded from a file, or register a DataMapper.Adapter for it");
        } catch (ReflectiveOperationException e) {
            throw new IllegalArgumentException("Could not create " + type.getName(), e);
        }
        for (Field field : fields(type)) {
            if (!tree.containsKey(field.getName())) {
                continue; // keep the constructor's default
            }
            try {
                field.set(instance, read(tree.get(field.getName()), field.getGenericType(), depth + 1));
            } catch (IllegalAccessException e) {
                throw new IllegalArgumentException("Cannot set field " + type.getSimpleName() + "." + field.getName(), e);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Field '" + field.getName() + "' of " + type.getSimpleName()
                        + ": " + e.getMessage(), e);
            }
        }
        return instance;
    }

    /** Java 16+ records, via reflection so MoonLib still targets Java 8. */
    private static Object readRecord(Map<?, ?> tree, Class<?> type, int depth) {
        try {
            Method isRecord = Class.class.getMethod("isRecord");
            if (!(Boolean) isRecord.invoke(type)) {
                return null;
            }
            Object[] components = (Object[]) Class.class.getMethod("getRecordComponents").invoke(type);
            Class<?> componentClass = Class.forName("java.lang.reflect.RecordComponent");
            Method getName = componentClass.getMethod("getName");
            Method getType = componentClass.getMethod("getType");
            Method getGenericType = componentClass.getMethod("getGenericType");
            Class<?>[] parameterTypes = new Class<?>[components.length];
            Object[] arguments = new Object[components.length];
            for (int i = 0; i < components.length; i++) {
                parameterTypes[i] = (Class<?>) getType.invoke(components[i]);
                String name = (String) getName.invoke(components[i]);
                arguments[i] = read(tree.get(name), (Type) getGenericType.invoke(components[i]), depth + 1);
            }
            Constructor<?> constructor = type.getDeclaredConstructor(parameterTypes);
            constructor.setAccessible(true);
            return constructor.newInstance(arguments);
        } catch (NoSuchMethodException e) {
            return null; // Java 8 to 15: records don't exist
        } catch (ReflectiveOperationException e) {
            throw new IllegalArgumentException("Could not create record " + type.getName(), e);
        }
    }

    private static Object readScalar(Object tree, Class<?> raw) {
        Class<?> type = wrap(raw);
        if (type == Boolean.class) {
            if (tree instanceof Boolean) {
                return tree;
            }
            String text = tree.toString().trim();
            if (text.equalsIgnoreCase("true") || text.equalsIgnoreCase("yes")) {
                return Boolean.TRUE;
            }
            if (text.equalsIgnoreCase("false") || text.equalsIgnoreCase("no")) {
                return Boolean.FALSE;
            }
            throw new IllegalArgumentException("'" + tree + "' is not true or false");
        }
        if (type == Character.class) {
            String text = tree.toString();
            if (text.length() != 1) {
                throw new IllegalArgumentException("'" + tree + "' is not a single character");
            }
            return text.charAt(0);
        }
        if (tree instanceof Map || tree instanceof Collection || tree instanceof Boolean) {
            throw mismatch(tree, raw);
        }
        BigDecimal number;
        try {
            number = tree instanceof BigDecimal ? (BigDecimal) tree : new BigDecimal(tree.toString().trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("'" + tree + "' is not a number");
        }
        if (type == Integer.class) {
            return number.intValue();
        }
        if (type == Long.class) {
            return number.longValue();
        }
        if (type == Double.class) {
            return number.doubleValue();
        }
        if (type == Float.class) {
            return number.floatValue();
        }
        if (type == Short.class) {
            return number.shortValue();
        }
        if (type == Byte.class) {
            return number.byteValue();
        }
        if (type == BigInteger.class) {
            return number.toBigInteger();
        }
        if (type == BigDecimal.class) {
            return number;
        }
        return number.doubleValue(); // Number.class
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object readEnum(String text, Class<? extends Enum> type) {
        String trimmed = text.trim();
        for (Enum constant : type.getEnumConstants()) {
            if (constant.name().equals(trimmed)) {
                return constant;
            }
        }
        String normalised = trimmed.toUpperCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
        for (Enum constant : type.getEnumConstants()) {
            if (constant.name().equals(normalised)) {
                return constant;
            }
        }
        throw new IllegalArgumentException("'" + text + "' is not a valid " + type.getSimpleName());
    }

    private static Object readSerializable(Object tree, Class<? extends ConfigurationSerializable> type, int depth) {
        if (type.isInstance(tree)) {
            return tree;
        }
        if (!(tree instanceof Map)) {
            throw mismatch(tree, type);
        }
        Object nested = deserializeNested(tree, depth);
        if (type.isInstance(nested)) {
            return nested;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) nested;
        ConfigurationSerializable result = ConfigurationSerialization.deserializeObject(map, type);
        if (result == null) {
            throw new IllegalArgumentException("Bukkit could not deserialize " + type.getSimpleName());
        }
        return result;
    }

    /** Deserializes Bukkit objects bottom-up, the same way Bukkit's YAML loader does. */
    private static Object deserializeNested(Object tree, int depth) {
        if (depth > MAX_DEPTH) {
            throw new IllegalArgumentException("Data is nested too deeply");
        }
        if (tree instanceof Map) {
            Map<String, Object> map = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) tree).entrySet()) {
                map.put(String.valueOf(entry.getKey()), deserializeNested(entry.getValue(), depth + 1));
            }
            if (map.containsKey(TYPE_KEY)) {
                ConfigurationSerializable object = ConfigurationSerialization.deserializeObject(map);
                if (object != null) {
                    return object;
                }
            }
            return map;
        }
        if (tree instanceof Collection) {
            List<Object> list = new ArrayList<>();
            for (Object element : (Collection<?>) tree) {
                list.add(deserializeNested(element, depth + 1));
            }
            return list;
        }
        return tree;
    }

    // ------------------------------------------------------------------ helpers

    @SuppressWarnings("rawtypes")
    private static Adapter adapterFor(Class<?> type) {
        Adapter exact = ADAPTERS.get(type);
        if (exact != null) {
            return exact;
        }
        for (Map.Entry<Class<?>, Adapter<?>> entry : ADAPTERS.entrySet()) {
            if (entry.getKey().isAssignableFrom(type)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private static List<Field> fields(Class<?> type) {
        return FIELD_CACHE.computeIfAbsent(type, DataMapper::scanFields);
    }

    private static List<Field> scanFields(Class<?> type) {
        List<Field> fields = new ArrayList<>();
        List<Class<?>> hierarchy = new ArrayList<>();
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            hierarchy.add(c);
        }
        Collections.reverse(hierarchy); // superclass fields first
        for (Class<?> c : hierarchy) {
            for (Field field : c.getDeclaredFields()) {
                int modifiers = field.getModifiers();
                if (Modifier.isStatic(modifiers) || Modifier.isTransient(modifiers) || field.isSynthetic()) {
                    continue;
                }
                field.setAccessible(true);
                fields.add(field);
            }
        }
        return Collections.unmodifiableList(fields);
    }

    private static void requireMappable(Class<?> type) {
        String name = type.getName();
        if (name.startsWith("java.") || name.startsWith("javax.") || name.startsWith("sun.")
                || name.startsWith("jdk.") || type.isInterface() || Modifier.isAbstract(type.getModifiers())) {
            throw new IllegalArgumentException("Don't know how to store " + name
                    + ". Use a supported type or register a DataMapper.Adapter for it.");
        }
    }

    private static Class<?> rawClass(Type type) {
        if (type instanceof Class) {
            return (Class<?>) type;
        }
        if (type instanceof ParameterizedType) {
            return rawClass(((ParameterizedType) type).getRawType());
        }
        if (type instanceof GenericArrayType) {
            return Array.newInstance(rawClass(((GenericArrayType) type).getGenericComponentType()), 0).getClass();
        }
        if (type instanceof WildcardType) {
            Type[] upper = ((WildcardType) type).getUpperBounds();
            return upper.length == 0 ? Object.class : rawClass(upper[0]);
        }
        return Object.class; // type variables
    }

    private static Type typeArgument(Type type, int index) {
        if (type instanceof ParameterizedType) {
            Type[] arguments = ((ParameterizedType) type).getActualTypeArguments();
            if (index < arguments.length) {
                return arguments[index];
            }
        }
        return Object.class;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Collection newCollection(Class<?> raw, Class<?> element) {
        if (raw.isAssignableFrom(ArrayList.class)) {
            return new ArrayList<>();
        }
        if (EnumSet.class.isAssignableFrom(raw) || (Set.class.isAssignableFrom(raw) && element.isEnum()
                && raw.isAssignableFrom(EnumSet.class))) {
            return EnumSet.noneOf((Class<? extends Enum>) element);
        }
        if (raw.isAssignableFrom(LinkedHashSet.class)) {
            return new LinkedHashSet<>();
        }
        if (raw.isAssignableFrom(TreeSet.class) || SortedSet.class.isAssignableFrom(raw)) {
            return new TreeSet<>();
        }
        try {
            return (Collection) raw.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalArgumentException("Cannot create a " + raw.getName());
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Map newMap(Class<?> raw, Class<?> key) {
        if (EnumMap.class.isAssignableFrom(raw) || (key.isEnum() && raw.isAssignableFrom(EnumMap.class))) {
            return new EnumMap(key);
        }
        if (raw.isAssignableFrom(LinkedHashMap.class)) {
            return new LinkedHashMap<>();
        }
        if (raw.isAssignableFrom(TreeMap.class) || SortedMap.class.isAssignableFrom(raw)) {
            return new TreeMap<>();
        }
        if (raw.isAssignableFrom(ConcurrentHashMap.class)) {
            return new ConcurrentHashMap<>();
        }
        try {
            return (Map) raw.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalArgumentException("Cannot create a " + raw.getName());
        }
    }

    private static List<Object> asList(Object tree) {
        if (tree instanceof Collection) {
            return new ArrayList<>((Collection<?>) tree);
        }
        if (tree instanceof Map) {
            throw mismatch(tree, List.class);
        }
        return new ArrayList<>(Collections.singletonList(tree)); // a single value counts as a one-item list
    }

    private static Object defaultValue(Class<?> raw) {
        if (!raw.isPrimitive()) {
            return null;
        }
        if (raw == boolean.class) {
            return false;
        }
        if (raw == char.class) {
            return '\0';
        }
        return readScalar(0, raw);
    }

    private static Class<?> wrap(Class<?> type) {
        if (!type.isPrimitive()) {
            return type;
        }
        if (type == int.class) {
            return Integer.class;
        }
        if (type == long.class) {
            return Long.class;
        }
        if (type == double.class) {
            return Double.class;
        }
        if (type == float.class) {
            return Float.class;
        }
        if (type == boolean.class) {
            return Boolean.class;
        }
        if (type == short.class) {
            return Short.class;
        }
        if (type == byte.class) {
            return Byte.class;
        }
        return Character.class;
    }

    private static IllegalArgumentException mismatch(Object tree, Class<?> type) {
        String found = tree instanceof Map ? "a section" : tree instanceof Collection ? "a list" : "'" + tree + "'";
        return new IllegalArgumentException("Expected " + type.getSimpleName() + " but found " + found);
    }

    private static Type parameterized(Class<?> raw, Type... arguments) {
        return new ParameterizedType() {
            @Override
            public Type[] getActualTypeArguments() {
                return arguments.clone();
            }

            @Override
            public Type getRawType() {
                return raw;
            }

            @Override
            public Type getOwnerType() {
                return null;
            }

            @Override
            public String toString() {
                StringBuilder sb = new StringBuilder(raw.getSimpleName()).append('<');
                for (int i = 0; i < arguments.length; i++) {
                    sb.append(i == 0 ? "" : ", ").append(arguments[i].getTypeName());
                }
                return sb.append('>').toString();
            }
        };
    }
}
