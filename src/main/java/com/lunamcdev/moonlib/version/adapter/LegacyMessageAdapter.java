package com.lunamcdev.moonlib.version.adapter;

import com.lunamcdev.moonlib.version.Reflection;
import org.bukkit.entity.Player;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/**
 * Internal API. Packet based implementation for 1.8 to 1.11, where Bukkit had no complete title or
 * action bar API.
 */
public final class LegacyMessageAdapter implements MessageAdapter {

    private final Method serialize;
    private final Constructor<?> chatPacket;
    private final Constructor<?> titlePacket;
    private final Constructor<?> timesPacket;
    private final Object actionTitle;
    private final Object actionSubtitle;
    private final Object actionReset;

    private LegacyMessageAdapter(Method serialize, Constructor<?> chatPacket, Constructor<?> titlePacket,
                                 Constructor<?> timesPacket, Object actionTitle, Object actionSubtitle,
                                 Object actionReset) {
        this.serialize = serialize;
        this.chatPacket = chatPacket;
        this.titlePacket = titlePacket;
        this.timesPacket = timesPacket;
        this.actionTitle = actionTitle;
        this.actionSubtitle = actionSubtitle;
        this.actionReset = actionReset;
    }

    /**
     * Creates the adapter by resolving the required NMS classes.
     *
     * @return the adapter, or {@code null} if the classes could not be found
     */
    public static LegacyMessageAdapter tryCreate() {
        try {
            Class<?> component = Reflection.nmsClass("IChatBaseComponent");
            Class<?> serializer = Reflection.findClass(
                    nms("IChatBaseComponent$ChatSerializer"), nms("ChatSerializer"));
            Class<?> chat = Reflection.nmsClass("PacketPlayOutChat");
            Class<?> title = Reflection.nmsClass("PacketPlayOutTitle");
            Class<?> action = Reflection.findClass(nms("PacketPlayOutTitle$EnumTitleAction"), nms("EnumTitleAction"));
            if (component == null || serializer == null || chat == null || title == null || action == null) {
                return null;
            }
            Method serialize = serializer.getMethod("a", String.class);
            Constructor<?> chatPacket = chat.getConstructor(component, byte.class);
            Constructor<?> titlePacket = title.getConstructor(action, component);
            Constructor<?> timesPacket = title.getConstructor(int.class, int.class, int.class);
            Object titleAction = enumConstant(action, "TITLE");
            Object subtitleAction = enumConstant(action, "SUBTITLE");
            Object resetAction = enumConstant(action, "RESET");
            return new LegacyMessageAdapter(serialize, chatPacket, titlePacket, timesPacket,
                    titleAction, subtitleAction, resetAction);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            return null;
        }
    }

    @Override
    public void title(Player player, String title, String subtitle, int fadeIn, int stay, int fadeOut) {
        try {
            Reflection.sendPacket(player, timesPacket.newInstance(fadeIn, stay, fadeOut));
            if (subtitle != null && !subtitle.isEmpty()) {
                Reflection.sendPacket(player, titlePacket.newInstance(actionSubtitle, component(subtitle)));
            }
            Reflection.sendPacket(player, titlePacket.newInstance(actionTitle, component(title == null ? "" : title)));
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to send title", e);
        }
    }

    @Override
    public void clearTitle(Player player) {
        try {
            Reflection.sendPacket(player, titlePacket.newInstance(actionReset, component("")));
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to clear title", e);
        }
    }

    @Override
    public void actionBar(Player player, String message) {
        try {
            Reflection.sendPacket(player, chatPacket.newInstance(component(message), (byte) 2));
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to send action bar", e);
        }
    }

    @Override
    public String name() {
        return "Legacy NMS";
    }

    private Object component(String legacyText) throws ReflectiveOperationException {
        return serialize.invoke(null, "{\"text\":\"" + escape(legacyText) + "\"}");
    }

    private static String escape(String text) {
        StringBuilder sb = new StringBuilder(text.length() + 8);
        for (char c : text.toCharArray()) {
            switch (c) {
                case '"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                default:
                    sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String nms(String simpleName) {
        return "net.minecraft.server." + com.lunamcdev.moonlib.version.ServerVersion.nmsPackage() + "." + simpleName;
    }

    private static Object enumConstant(Class<?> enumClass, String name) {
        for (Object constant : enumClass.getEnumConstants()) {
            if (((Enum<?>) constant).name().equals(name)) {
                return constant;
            }
        }
        throw new IllegalStateException("Missing enum constant " + name + " in " + enumClass.getName());
    }
}
