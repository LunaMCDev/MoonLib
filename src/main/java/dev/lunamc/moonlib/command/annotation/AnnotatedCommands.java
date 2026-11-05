package dev.lunamc.moonlib.command.annotation;

import dev.lunamc.moonlib.MoonLib;
import dev.lunamc.moonlib.command.ArgumentType;
import dev.lunamc.moonlib.command.ArgumentTypes;
import dev.lunamc.moonlib.command.CommandContext;
import dev.lunamc.moonlib.command.CommandError;
import dev.lunamc.moonlib.command.CommandMessages;
import dev.lunamc.moonlib.command.CommandRegistry;
import dev.lunamc.moonlib.command.MoonCommand;
import dev.lunamc.moonlib.util.Text;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Builds {@link MoonCommand}s from annotated classes, so a whole command tree fits in one class
 * of plain methods.
 *
 * <pre>{@code
 * @Command(value = "mine", permission = "prison.mine", description = "Manage mines")
 * public final class MineCommand {
 *
 *     @Subcommand(value = "reset", description = "Reset a mine now")
 *     public String reset(CommandSender sender, @Suggest("mines") @Arg("mine") String mine) {
 *         mines.reset(mine);
 *         return "&aReset &f" + mine;           // a returned String is sent to the sender
 *     }
 *
 *     @Subcommand("tp")
 *     public void teleport(Player player, @Arg("mine") String mine) { ... }   // Player implies player-only
 *
 *     @Subcommand("admin setblocks")
 *     public void setBlocks(CommandSender sender, @Arg("mine") String mine,
 *                           @Greedy @Arg("composition") String composition) { ... }
 *
 *     List<String> mines(CommandSender sender) { return mineManager.names(); }
 * }
 *
 * // in onEnable / enable():
 * AnnotatedCommands.register(this, new MineCommand());
 * }</pre>
 *
 * <h2>Parameters</h2>
 * <ul>
 *     <li>A leading {@link CommandSender}, {@link Player} or {@link CommandContext} receives the
 *     sender or context. A {@code Player} sender makes the (sub)command player-only.</li>
 *     <li>Every other parameter is an argument. Supported types out of the box: {@code String},
 *     {@code int}, {@code long}, {@code double}, {@code boolean} (and wrappers), {@link Player},
 *     {@link OfflinePlayer}, {@link World}, {@link Material}, {@link UUID} and any {@code enum}.
 *     Register more with {@link #registerType(Class, ArgumentType)}.</li>
 *     <li>Modifiers: {@link Arg @Arg} (name, optional, default, named type), {@link Greedy @Greedy},
 *     {@link Range @Range}, {@link Suggest @Suggest}.</li>
 * </ul>
 *
 * <h2>Return values and errors</h2>
 * A method may return {@code void}, a {@code String} (sent to the sender, colour codes allowed) or a
 * {@code boolean} ({@code false} shows the usage line). Throw {@link CommandError} to stop with a
 * message; any other exception is logged and the sender sees the internal-error message.
 *
 * <p>Mistakes in the annotations (an optional primitive with no default, an unknown parameter
 * type, a missing suggestion method) are reported when the command is built, with the class and
 * method named, rather than when a player first runs it.</p>
 *
 * @since 1.4.0
 */
public final class AnnotatedCommands {

    private static final Map<Class<?>, ArgumentType<?>> TYPES = new ConcurrentHashMap<>();
    private static final Map<String, ArgumentType<?>> NAMED_TYPES = new ConcurrentHashMap<>();

    static {
        TYPES.put(String.class, ArgumentTypes.STRING);
        TYPES.put(Player.class, ArgumentTypes.PLAYER);
        TYPES.put(OfflinePlayer.class, ArgumentTypes.OFFLINE_PLAYER);
        TYPES.put(World.class, ArgumentTypes.WORLD);
        TYPES.put(Material.class, ArgumentTypes.MATERIAL);
        TYPES.put(Boolean.class, ArgumentTypes.BOOLEAN);
        TYPES.put(boolean.class, ArgumentTypes.BOOLEAN);
        TYPES.put(Integer.class, ArgumentTypes.INTEGER);
        TYPES.put(int.class, ArgumentTypes.INTEGER);
        TYPES.put(Long.class, ArgumentTypes.LONG);
        TYPES.put(long.class, ArgumentTypes.LONG);
        TYPES.put(Double.class, ArgumentTypes.DOUBLE);
        TYPES.put(double.class, ArgumentTypes.DOUBLE);
        TYPES.put(UUID.class, (sender, input) -> {
            try {
                return UUID.fromString(input);
            } catch (IllegalArgumentException e) {
                throw new CommandError("&c'" + input + "' is not a valid UUID.");
            }
        });
    }

    private AnnotatedCommands() {
    }

    /**
     * Sets the argument type used for parameters of a Java type, for example a {@code Mine} class.
     * Applies to commands built after this call.
     *
     * @param javaType the parameter type
     * @param type     the argument type
     * @param <T>      the value type
     */
    public static <T> void registerType(Class<T> javaType, ArgumentType<? extends T> type) {
        TYPES.put(javaType, type);
    }

    /**
     * Registers an argument type under a name, for parameters annotated
     * {@code @Arg(type = "name")}.
     *
     * @param name the name
     * @param type the argument type
     */
    public static void registerType(String name, ArgumentType<?> type) {
        NAMED_TYPES.put(name.toLowerCase(Locale.ROOT), type);
    }

    /**
     * Builds the command tree for an annotated handler without registering it.
     *
     * @param handler an instance of a class annotated with {@link Command @Command}
     * @return the root command
     * @throws IllegalArgumentException if the class is not annotated or an annotation is invalid
     */
    public static MoonCommand build(Object handler) {
        Class<?> type = handler.getClass();
        Command root = type.getAnnotation(Command.class);
        if (root == null) {
            throw new IllegalArgumentException(type.getName() + " is not annotated with @Command");
        }
        Node rootNode = new Node(root.value(), root.aliases());
        rootNode.command.configure(root.permission(), root.description(), root.playerOnly(), root.hidden(),
                type.getAnnotation(Cooldown.class));

        List<Method> methods = new ArrayList<>(Arrays.asList(type.getDeclaredMethods()));
        // Shorter paths first so a declared parent is configured before its children are attached.
        methods.sort(Comparator.comparingInt(m -> {
            Subcommand sub = m.getAnnotation(Subcommand.class);
            return sub == null ? 0 : sub.value().trim().split("\\s+").length;
        }));
        boolean sawExecute = false;
        for (Method method : methods) {
            Execute execute = method.getAnnotation(Execute.class);
            Subcommand sub = method.getAnnotation(Subcommand.class);
            if (execute != null && sub != null) {
                throw invalid(method, "cannot have both @Execute and @Subcommand");
            }
            if (execute != null) {
                if (sawExecute) {
                    throw invalid(method, "is a second @Execute method; a command has at most one");
                }
                sawExecute = true;
                rootNode.command.bind(handler, method);
            } else if (sub != null) {
                String[] path = sub.value().trim().toLowerCase(Locale.ROOT).split("\\s+");
                if (path.length == 0 || path[0].isEmpty()) {
                    throw invalid(method, "has an empty @Subcommand path");
                }
                Node node = rootNode;
                for (int i = 0; i < path.length; i++) {
                    node = node.child(path[i], i == path.length - 1 ? sub.aliases() : new String[0]);
                }
                if (node.command.method != null) {
                    throw invalid(method, "declares /" + root.value() + " " + sub.value()
                            + ", which " + node.command.method.getName() + "() already declares");
                }
                node.command.configure(sub.permission(), sub.description(), sub.playerOnly(), sub.hidden(),
                        method.getAnnotation(Cooldown.class));
                node.command.bind(handler, method);
            }
        }
        rootNode.attach();
        return rootNode.command;
    }

    /**
     * Builds and registers annotated handlers for a plugin.
     *
     * @param plugin   the owning plugin
     * @param handlers instances of {@link Command @Command} classes
     * @return the registered root commands, in order
     */
    public static List<MoonCommand> register(Plugin plugin, Object... handlers) {
        List<MoonCommand> out = new ArrayList<>(handlers.length);
        for (Object handler : handlers) {
            MoonCommand command = build(handler);
            CommandRegistry.register(plugin, command);
            out.add(command);
        }
        return out;
    }

    /**
     * Builds and registers annotated handlers for the calling plugin.
     *
     * @param handlers instances of {@link Command @Command} classes
     * @return the registered root commands, in order
     */
    public static List<MoonCommand> register(Object... handlers) {
        return register(MoonLib.plugin(), handlers);
    }

    // ------------------------------------------------------------------ internals

    static IllegalArgumentException invalid(Method method, String problem) {
        return new IllegalArgumentException(method.getDeclaringClass().getSimpleName() + "." + method.getName()
                + "() " + problem);
    }

    private static final class Node {
        final ReflectiveCommand command;
        final Map<String, Node> children = new LinkedHashMap<>();

        Node(String name, String[] aliases) {
            this.command = new ReflectiveCommand(name, aliases);
        }

        Node child(String name, String[] aliases) {
            Node existing = children.get(name);
            if (existing == null) {
                existing = new Node(name, aliases);
                children.put(name, existing);
            } else if (aliases.length > 0) {
                existing.command.addAliases(aliases);
            }
            return existing;
        }

        void attach() {
            for (Node child : children.values()) {
                child.attach();
                command.addChild(child.command);
            }
        }
    }

    /**
     * One command level backed by a reflective method call (or by nothing, for a level that only
     * groups subcommands).
     */
    static final class ReflectiveCommand extends MoonCommand {
        Object handler;
        Method method;
        private final List<Binding> bindings = new ArrayList<>();
        private boolean returnsBoolean;

        ReflectiveCommand(String name, String[] aliases) {
            super(name, aliases);
        }

        void addAliases(String[] more) {
            aliases(more);
        }

        void addChild(ReflectiveCommand child) {
            subcommand(child);
        }

        void configure(String permission, String description, boolean playerOnly, boolean hidden, Cooldown cooldown) {
            if (!permission.isEmpty()) {
                permission(permission);
            }
            if (!description.isEmpty()) {
                description(description);
            }
            if (playerOnly) {
                playerOnly();
            }
            if (hidden) {
                hidden();
            }
            if (cooldown != null) {
                if (cooldown.bypass().isEmpty()) {
                    cooldown(cooldown.value(), cooldown.unit());
                } else {
                    cooldown(cooldown.value(), cooldown.unit(), cooldown.bypass());
                }
            }
        }

        void bind(Object target, Method m) {
            if (Modifier.isStatic(m.getModifiers())) {
                throw invalid(m, "must not be static");
            }
            Class<?> ret = m.getReturnType();
            if (ret != void.class && ret != String.class && ret != boolean.class && ret != Boolean.class) {
                throw invalid(m, "must return void, String or boolean, not " + ret.getSimpleName());
            }
            m.setAccessible(true);
            this.handler = target;
            this.method = m;
            this.returnsBoolean = ret == boolean.class || ret == Boolean.class;

            Parameter[] params = m.getParameters();
            for (int i = 0; i < params.length; i++) {
                Parameter p = params[i];
                Class<?> pt = p.getType();
                boolean annotatedArg = p.isAnnotationPresent(Arg.class);
                if (i == 0 && !annotatedArg && pt == Player.class) {
                    playerOnly();
                    bindings.add(Binding.PLAYER);
                    continue;
                }
                if (i == 0 && !annotatedArg && pt == CommandSender.class) {
                    bindings.add(Binding.SENDER);
                    continue;
                }
                if (pt == CommandContext.class) {
                    bindings.add(Binding.CONTEXT);
                    continue;
                }
                bindings.add(argument(m, p, i));
            }
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        private Binding argument(Method m, Parameter p, int index) {
            Arg arg = p.getAnnotation(Arg.class);
            String name = arg != null && !arg.value().isEmpty() ? arg.value()
                    : p.isNamePresent() ? p.getName() : "arg" + index;
            ArgumentType<?> type = resolveType(m, p, arg);
            Range range = p.getAnnotation(Range.class);
            if (range != null) {
                type = ranged(m, p, type, range);
            }
            if (p.isAnnotationPresent(Greedy.class)) {
                if (p.getType() != String.class) {
                    throw invalid(m, "uses @Greedy on '" + name + "', which is not a String");
                }
                type = ArgumentTypes.GREEDY_STRING;
            }
            Suggest suggest = p.getAnnotation(Suggest.class);
            if (suggest != null) {
                type = suggesting(m, type, suggest.value());
            }
            String def = arg == null ? "" : arg.def();
            boolean optional = arg != null && (arg.optional() || !def.isEmpty());
            if (optional && def.isEmpty() && p.getType().isPrimitive()) {
                throw invalid(m, "has optional primitive argument '" + name
                        + "' with no default; use a wrapper type or set def");
            }
            try {
                if (optional) {
                    optionalArgument(name, (ArgumentType) type);
                } else {
                    argument(name, (ArgumentType) type);
                }
            } catch (IllegalStateException e) {
                throw invalid(m, e.getMessage());
            }
            return new Binding(name, type, def);
        }

        private static ArgumentType<?> resolveType(Method m, Parameter p, Arg arg) {
            if (arg != null && !arg.type().isEmpty()) {
                ArgumentType<?> named = NAMED_TYPES.get(arg.type().toLowerCase(Locale.ROOT));
                if (named == null) {
                    throw invalid(m, "uses unknown argument type '" + arg.type()
                            + "'; register it with AnnotatedCommands.registerType(name, type)");
                }
                return named;
            }
            ArgumentType<?> type = TYPES.get(p.getType());
            if (type == null && p.getType().isEnum()) {
                type = enumType(p.getType());
            }
            if (type == null) {
                throw invalid(m, "has a parameter of type " + p.getType().getSimpleName()
                        + " with no argument type; register one with AnnotatedCommands.registerType");
            }
            return type;
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        private static ArgumentType<?> enumType(Class<?> type) {
            return ArgumentTypes.enumType((Class) type);
        }

        private static ArgumentType<?> ranged(Method m, Parameter p, ArgumentType<?> base, Range range) {
            Class<?> t = p.getType();
            if (t == int.class || t == Integer.class) {
                return ArgumentTypes.integer((int) Math.max(Integer.MIN_VALUE, range.min()),
                        (int) Math.min(Integer.MAX_VALUE, range.max()));
            }
            if (t == double.class || t == Double.class) {
                return ArgumentTypes.doubleRange(range.min(), range.max());
            }
            if (t == long.class || t == Long.class) {
                return (sender, input) -> {
                    long value = (Long) base.parse(sender, input);
                    if (value < range.min() || value > range.max()) {
                        throw new CommandError(Text.replace(CommandMessages.get("number-out-of-range"),
                                "input", input, "min", (long) range.min(), "max", (long) range.max()));
                    }
                    return value;
                };
            }
            throw invalid(m, "uses @Range on a " + t.getSimpleName() + "; only int, long and double are supported");
        }

        private ArgumentType<?> suggesting(Method m, ArgumentType<?> base, String methodName) {
            Method source = findSuggester(m.getDeclaringClass(), methodName);
            if (source == null) {
                throw invalid(m, "refers to @Suggest(\"" + methodName + "\"), but there is no method " + methodName
                        + "() or " + methodName + "(CommandSender) returning a Collection<String>");
            }
            source.setAccessible(true);
            Object target = handler;
            boolean takesSender = source.getParameterCount() == 1;
            return new ArgumentType<Object>() {
                @Override
                public Object parse(CommandSender sender, String input) {
                    return base.parse(sender, input);
                }

                @Override
                public List<String> suggest(CommandSender sender, String input) {
                    try {
                        Object result = takesSender ? source.invoke(target, sender) : source.invoke(target);
                        if (result == null) {
                            return Collections.emptyList();
                        }
                        List<String> out = new ArrayList<>();
                        for (Object o : (Collection<?>) result) {
                            out.add(String.valueOf(o));
                        }
                        return out;
                    } catch (ReflectiveOperationException e) {
                        return Collections.emptyList();
                    }
                }

                @Override
                public boolean greedy() {
                    return base.greedy();
                }
            };
        }

        private static Method findSuggester(Class<?> type, String name) {
            for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
                for (Method candidate : c.getDeclaredMethods()) {
                    if (!candidate.getName().equals(name) || !Collection.class.isAssignableFrom(candidate.getReturnType())) {
                        continue;
                    }
                    Class<?>[] params = candidate.getParameterTypes();
                    if (params.length == 0 || (params.length == 1 && params[0] == CommandSender.class)) {
                        return candidate;
                    }
                }
            }
            return null;
        }

        @Override
        protected void execute(CommandContext ctx) {
            if (method == null) {
                super.execute(ctx);
                return;
            }
            Object[] values = new Object[bindings.size()];
            for (int i = 0; i < values.length; i++) {
                values[i] = bindings.get(i).value(ctx);
            }
            Object result;
            try {
                result = method.invoke(handler, values);
            } catch (InvocationTargetException e) {
                Throwable cause = e.getCause();
                if (cause instanceof RuntimeException) {
                    throw (RuntimeException) cause;
                }
                if (cause instanceof Error) {
                    throw (Error) cause;
                }
                throw new IllegalStateException(cause);
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(e);
            }
            if (result instanceof String) {
                ctx.reply((String) result);
            } else if (returnsBoolean && Boolean.FALSE.equals(result)) {
                ctx.usage();
            }
        }
    }

    private static final class Binding {
        static final Binding SENDER = new Binding(null, null, "");
        static final Binding PLAYER = new Binding(null, null, "");
        static final Binding CONTEXT = new Binding(null, null, "");

        final String name;
        final ArgumentType<?> type;
        final String def;

        Binding(String name, ArgumentType<?> type, String def) {
            this.name = name;
            this.type = type;
            this.def = def;
        }

        Object value(CommandContext ctx) {
            if (this == SENDER) {
                return ctx.sender();
            }
            if (this == PLAYER) {
                return ctx.player();
            }
            if (this == CONTEXT) {
                return ctx;
            }
            if (ctx.has(name)) {
                return ctx.get(name);
            }
            if (!def.isEmpty()) {
                return type.parse(ctx.sender(), def);
            }
            return null;
        }
    }
}
