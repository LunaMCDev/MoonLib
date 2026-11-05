package com.lunamcdev.moonlib.util;

import com.lunamcdev.moonlib.version.ServerVersion;
import org.bukkit.ChatColor;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Text and colour utilities.
 *
 * <h2>Colour formats</h2>
 * <p>{@link #color(String)} understands every common format, so config authors can use whichever
 * they prefer:</p>
 * <ul>
 *     <li>Legacy codes: {@code &a}, {@code &l}, {@code &r} ...</li>
 *     <li>Hex: {@code &#FF55AA}, {@code <#FF55AA>} and {@code {#FF55AA}}</li>
 * </ul>
 * <p>Hex colours need 1.16+. On older servers they are automatically replaced with the closest of
 * the 16 legacy colours, so the same config works on every version.</p>
 *
 * <pre>{@code
 * player.sendMessage(Text.color("&#FFD700&lMoonPrison &8» &7Welcome back!"));
 * String bar = Text.progressBar(blocksMined, blocksNeeded, 20, '|', "&a", "&7");
 * String title = Text.gradient("MOON PRISON", "#7F7FD5", "#91EAE4");
 * }</pre>
 */
public final class Text {

    /** The section sign used internally by Minecraft for colour codes. */
    public static final char SECTION = ChatColor.COLOR_CHAR;

    private static final Pattern HEX_PATTERN = Pattern.compile("(?:&#|<#|\\{#)([A-Fa-f0-9]{6})[>}]?");
    private static final int CENTER_PX = 154;
    private static final ChatColor[] LEGACY_COLORS = {
            ChatColor.BLACK, ChatColor.DARK_BLUE, ChatColor.DARK_GREEN, ChatColor.DARK_AQUA,
            ChatColor.DARK_RED, ChatColor.DARK_PURPLE, ChatColor.GOLD, ChatColor.GRAY,
            ChatColor.DARK_GRAY, ChatColor.BLUE, ChatColor.GREEN, ChatColor.AQUA,
            ChatColor.RED, ChatColor.LIGHT_PURPLE, ChatColor.YELLOW, ChatColor.WHITE
    };
    private static final int[] LEGACY_RGB = {
            0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA,
            0x555555, 0x5555FF, 0x55FF55, 0x55FFFF, 0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF
    };

    private Text() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Translates {@code &} colour codes and hex colours into Minecraft formatting.
     *
     * @param text the text to colour, may be {@code null}
     * @return the coloured text, or an empty string if {@code text} was {@code null}
     */
    public static String color(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        String result = text;
        if (result.indexOf('#') >= 0) {
            result = translateHex(result, ServerVersion.supportsHexColors());
        }
        return ChatColor.translateAlternateColorCodes('&', result);
    }

    /**
     * Colours every line of a collection.
     *
     * @param lines the lines to colour
     * @return a new mutable list containing the coloured lines
     */
    public static List<String> color(Collection<String> lines) {
        List<String> out = new ArrayList<>(lines == null ? 0 : lines.size());
        if (lines != null) {
            for (String line : lines) {
                out.add(color(line));
            }
        }
        return out;
    }

    /**
     * Colours every argument.
     *
     * @param lines the lines to colour
     * @return a new mutable list containing the coloured lines
     */
    public static List<String> color(String... lines) {
        List<String> out = new ArrayList<>(lines.length);
        for (String line : lines) {
            out.add(color(line));
        }
        return out;
    }

    /**
     * Removes all colour and formatting, including codes that have not yet been translated.
     *
     * @param text the text
     * @return plain text
     */
    public static String strip(String text) {
        if (text == null) {
            return "";
        }
        String stripped = ChatColor.stripColor(color(text));
        return stripped == null ? "" : stripped;
    }

    /**
     * Replaces {@code {key}} placeholders using alternating key/value pairs.
     *
     * <pre>{@code Text.replace("Hello {player}, you have {coins} coins", "player", name, "coins", 50)}</pre>
     *
     * @param text  the template
     * @param pairs alternating keys (without braces) and values
     * @return the text with placeholders replaced
     * @throws IllegalArgumentException if an odd number of pair elements is supplied
     */
    public static String replace(String text, Object... pairs) {
        if (text == null) {
            return "";
        }
        if (pairs == null || pairs.length == 0) {
            return text;
        }
        if (pairs.length % 2 != 0) {
            throw new IllegalArgumentException("Placeholders must be key/value pairs, got " + pairs.length + " values");
        }
        String result = text;
        for (int i = 0; i < pairs.length; i += 2) {
            result = result.replace("{" + pairs[i] + "}", String.valueOf(pairs[i + 1]));
        }
        return result;
    }

    /**
     * Applies a smooth gradient between two hex colours across the visible characters of a text.
     * Falls back to the closest legacy colour of the start colour below 1.16.
     *
     * @param text     plain text (existing colour codes are stripped)
     * @param fromHex  start colour, e.g. {@code "#FF0000"} or {@code "FF0000"}
     * @param toHex    end colour
     * @return the coloured text
     */
    public static String gradient(String text, String fromHex, String toHex) {
        return gradient(text, fromHex, toHex, "");
    }

    /**
     * Applies a gradient and adds the given formatting (e.g. {@code "&l"}) to every character.
     *
     * @param text       plain text (existing colour codes are stripped)
     * @param fromHex    start colour
     * @param toHex      end colour
     * @param formatting extra formatting codes such as {@code "&l"} or {@code "&o&n"}
     * @return the coloured text
     */
    public static String gradient(String text, String fromHex, String toHex, String formatting) {
        String plain = strip(text);
        String format = color(formatting);
        int from = parseHex(fromHex);
        int to = parseHex(toHex);
        if (!ServerVersion.supportsHexColors()) {
            return nearestLegacy(from) + format + plain;
        }
        StringBuilder out = new StringBuilder(plain.length() * 16);
        int length = plain.length();
        for (int i = 0; i < length; i++) {
            char c = plain.charAt(i);
            double ratio = length == 1 ? 0 : (double) i / (length - 1);
            int r = (int) Math.round(((from >> 16) & 0xFF) + ratio * (((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)));
            int g = (int) Math.round(((from >> 8) & 0xFF) + ratio * (((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)));
            int b = (int) Math.round((from & 0xFF) + ratio * ((to & 0xFF) - (from & 0xFF)));
            out.append(hexToSection((r << 16) | (g << 8) | b)).append(format).append(c);
        }
        return out.toString();
    }

    /**
     * Pads a message with spaces so it appears centred in the default chat window.
     *
     * @param message the message (colour codes are supported and not counted)
     * @return the centred message
     */
    public static String center(String message) {
        String colored = color(message);
        int messagePx = 0;
        boolean previousCode = false;
        boolean bold = false;
        for (char c : colored.toCharArray()) {
            if (c == SECTION) {
                previousCode = true;
            } else if (previousCode) {
                previousCode = false;
                bold = c == 'l' || c == 'L' || (bold && !(isColorChar(c) || c == 'r' || c == 'R'));
            } else {
                messagePx += charWidth(c, bold) + 1;
            }
        }
        int toCompensate = CENTER_PX - messagePx / 2;
        int spaceLength = charWidth(' ', false) + 1;
        StringBuilder sb = new StringBuilder();
        int compensated = 0;
        while (compensated < toCompensate) {
            sb.append(' ');
            compensated += spaceLength;
        }
        return sb.append(colored).toString();
    }

    /**
     * Builds a text progress bar such as {@code &a||||||&7||||}.
     *
     * @param current         the current value
     * @param max             the value that represents a full bar
     * @param length          the number of symbols in the bar
     * @param symbol          the symbol to repeat
     * @param completeColor   colour for the filled part, e.g. {@code "&a"}
     * @param incompleteColor colour for the empty part, e.g. {@code "&7"}
     * @return the coloured progress bar
     */
    public static String progressBar(double current, double max, int length, char symbol,
                                     String completeColor, String incompleteColor) {
        double ratio = max <= 0 ? 0 : Math.max(0, Math.min(1, current / max));
        int filled = (int) Math.round(ratio * length);
        String sym = String.valueOf(symbol);
        return color(completeColor + repeat(sym, filled) + incompleteColor + repeat(sym, length - filled));
    }

    /**
     * Converts an enum-style name to a friendly title: {@code DIAMOND_PICKAXE -> Diamond Pickaxe}.
     *
     * @param name the name to format
     * @return the formatted name
     */
    public static String formatEnum(String name) {
        if (name == null || name.isEmpty()) {
            return "";
        }
        String[] parts = name.toLowerCase(Locale.ROOT).split("_");
        StringBuilder sb = new StringBuilder(name.length());
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.toString();
    }

    /**
     * Converts an enum constant to a friendly title.
     *
     * @param value the enum constant
     * @return the formatted name
     * @see #formatEnum(String)
     */
    public static String formatEnum(Enum<?> value) {
        return value == null ? "" : formatEnum(value.name());
    }

    /**
     * Repeats a string. ({@code String.repeat} is Java 11+.)
     *
     * @param text  the text
     * @param times how many times, values below 1 return an empty string
     * @return the repeated text
     */
    public static String repeat(String text, int times) {
        if (times <= 0 || text == null || text.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder(text.length() * times);
        for (int i = 0; i < times; i++) {
            sb.append(text);
        }
        return sb.toString();
    }

    /**
     * Returns the closest legacy colour to an RGB value.
     *
     * @param rgb the colour as {@code 0xRRGGBB}
     * @return the nearest of the 16 legacy colours
     */
    public static ChatColor nearestLegacy(int rgb) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        ChatColor best = ChatColor.WHITE;
        long bestDistance = Long.MAX_VALUE;
        for (int i = 0; i < LEGACY_RGB.length; i++) {
            int lr = (LEGACY_RGB[i] >> 16) & 0xFF;
            int lg = (LEGACY_RGB[i] >> 8) & 0xFF;
            int lb = LEGACY_RGB[i] & 0xFF;
            long distance = (long) (r - lr) * (r - lr) + (long) (g - lg) * (g - lg) + (long) (b - lb) * (b - lb);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = LEGACY_COLORS[i];
            }
        }
        return best;
    }

    /**
     * Converts a {@link Color} into the {@code §x§R§R§G§G§B§B} sequence understood by 1.16+ clients.
     *
     * @param color the colour
     * @return the formatting sequence
     */
    public static String hex(Color color) {
        return hexToSection(color.getRGB() & 0xFFFFFF);
    }

    static String translateHex(String text, boolean supportsHex) {
        Matcher matcher = HEX_PATTERN.matcher(text);
        StringBuffer sb = new StringBuffer(text.length() + 32);
        while (matcher.find()) {
            int rgb = Integer.parseInt(matcher.group(1), 16);
            String replacement = supportsHex ? hexToSection(rgb) : nearestLegacy(rgb).toString();
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private static String hexToSection(int rgb) {
        String hex = String.format("%06x", rgb & 0xFFFFFF);
        StringBuilder sb = new StringBuilder(14).append(SECTION).append('x');
        for (char c : hex.toCharArray()) {
            sb.append(SECTION).append(c);
        }
        return sb.toString();
    }

    private static int parseHex(String hex) {
        String clean = hex.startsWith("#") ? hex.substring(1) : hex;
        try {
            return Integer.parseInt(clean, 16) & 0xFFFFFF;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid hex colour: " + hex, e);
        }
    }

    private static boolean isColorChar(char c) {
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F') || c == 'x' || c == 'X';
    }

    private static int charWidth(char c, boolean bold) {
        int width;
        switch (c) {
            case 'i': case '!': case ',': case '.': case ':': case ';': case '|':
                width = 1;
                break;
            case '\'': case 'l': case '`':
                width = 2;
                break;
            case 'I': case 't': case '[': case ']': case ' ':
                width = 3;
                break;
            case 'f': case 'k': case '<': case '>': case '"': case '(': case ')': case '*': case '{': case '}':
                width = 4;
                break;
            case '@': case '~':
                width = 6;
                break;
            default:
                width = 5;
        }
        return bold && c != ' ' ? width + 1 : width;
    }
}
