package dev.lunamc.moonlib.util;

import dev.lunamc.moonlib.version.ServerVersion;
import org.bukkit.ChatColor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Formats text written with MiniMessage-style tags into a coloured string that works on every
 * server version.
 *
 * <pre>{@code
 * player.sendMessage(RichText.parse("<gradient:#7F7FD5:#91EAE4><bold>MOON PRISON</bold></gradient>"));
 * player.sendMessage(RichText.parse("<gray>Welcome, <player>! You have <gold><tokens></gold> tokens.",
 *         "player", player.getName(), "tokens", Numbers.commas(tokens)));
 * player.sendMessage(RichText.parse("<rainbow>Party time!</rainbow> <red>♥"));
 * }</pre>
 *
 * <h2>Tags</h2>
 * <table>
 *     <caption>Supported tags</caption>
 *     <tr><th>Tag</th><th>Effect</th></tr>
 *     <tr><td>{@code <red>}, {@code <dark_aqua>}, ... (all 16 names, {@code grey} spellings too)</td>
 *         <td>named colour</td></tr>
 *     <tr><td>{@code <#ff8800>}, {@code <color:#ff8800>}, {@code <c:gold>}</td><td>any colour</td></tr>
 *     <tr><td>{@code <bold>}/{@code <b>}, {@code <italic>}/{@code <i>}/{@code <em>},
 *         {@code <underlined>}/{@code <u>}, {@code <strikethrough>}/{@code <st>},
 *         {@code <obfuscated>}/{@code <obf>}</td><td>decorations; {@code <!bold>} turns one off</td></tr>
 *     <tr><td>{@code <gradient:#a:#b[:#c...]>}</td><td>smooth gradient through two or more colours</td></tr>
 *     <tr><td>{@code <rainbow>}, {@code <rainbow:!>} (reversed), {@code <rainbow:3>} (phase)</td>
 *         <td>rainbow</td></tr>
 *     <tr><td>{@code <reset>}/{@code <r>}</td><td>closes everything that is open</td></tr>
 *     <tr><td>{@code <newline>}/{@code <br>}</td><td>line break</td></tr>
 *     <tr><td>{@code <name>}</td><td>a placeholder passed to {@code parse}</td></tr>
 * </table>
 *
 * <p>Every tag can be closed with {@code </tag>} (closing a tag also closes the tags opened inside
 * it). Unclosed tags run to the end. Unknown tags such as {@code <3} or {@code <html>} are printed
 * as typed, so ordinary text never breaks. Write {@code \<} for a literal {@code <}. Legacy
 * {@code &} codes and {@code &#rrggbb} still work, so existing messages can be migrated gradually.</p>
 *
 * <h2>Placeholders</h2>
 * Placeholder values are inserted as plain text: a player called {@code <red>Bob} shows up literally
 * and cannot inject formatting. Wrap a value in {@link #parsed(String)} to have it formatted too.
 *
 * <h2>Versions</h2>
 * On 1.16+ colours are exact. Below 1.16 every colour, including each step of a gradient, is
 * downsampled to the nearest of the 16 legacy colours, so the same message still looks right.
 *
 * @since 1.1.1-alpha
 */
public final class RichText {

    private static final Map<String, Integer> NAMED = new HashMap<>();
    private static final Map<String, Character> DECORATIONS = new HashMap<>();

    static {
        String[][] colours = {
                {"black", "000000", "0"}, {"dark_blue", "0000AA", "1"}, {"dark_green", "00AA00", "2"},
                {"dark_aqua", "00AAAA", "3"}, {"dark_red", "AA0000", "4"}, {"dark_purple", "AA00AA", "5"},
                {"gold", "FFAA00", "6"}, {"gray", "AAAAAA", "7"}, {"dark_gray", "555555", "8"},
                {"blue", "5555FF", "9"}, {"green", "55FF55", "a"}, {"aqua", "55FFFF", "b"},
                {"red", "FF5555", "c"}, {"light_purple", "FF55FF", "d"}, {"yellow", "FFFF55", "e"},
                {"white", "FFFFFF", "f"}};
        for (String[] c : colours) {
            NAMED.put(c[0], Integer.parseInt(c[1], 16));
        }
        NAMED.put("grey", NAMED.get("gray"));
        NAMED.put("dark_grey", NAMED.get("dark_gray"));
        DECORATIONS.put("bold", 'l');
        DECORATIONS.put("b", 'l');
        DECORATIONS.put("italic", 'o');
        DECORATIONS.put("i", 'o');
        DECORATIONS.put("em", 'o');
        DECORATIONS.put("underlined", 'n');
        DECORATIONS.put("underline", 'n');
        DECORATIONS.put("u", 'n');
        DECORATIONS.put("strikethrough", 'm');
        DECORATIONS.put("st", 'm');
        DECORATIONS.put("obfuscated", 'k');
        DECORATIONS.put("obf", 'k');
    }

    private static final String DECORATION_ORDER = "klmno";
    private static final java.util.regex.Pattern LEGACY_HEX = java.util.regex.Pattern.compile("&#([A-Fa-f0-9]{6})");

    private RichText() {
    }

    /**
     * A placeholder value that is formatted as rich text instead of being inserted literally.
     */
    public static final class Parsed {
        private final String text;

        private Parsed(String text) {
            this.text = text == null ? "" : text;
        }

        @Override
        public String toString() {
            return text;
        }
    }

    /**
     * Marks a placeholder value to be formatted as rich text rather than inserted literally.
     *
     * @param richText text containing tags
     * @return the wrapped value
     */
    public static Parsed parsed(String richText) {
        return new Parsed(richText);
    }

    /**
     * Formats rich text for the running server.
     *
     * @param text         the text with tags; {@code null} gives an empty string
     * @param placeholders alternating names and values, used for {@code <name>} tags
     * @return the coloured string
     * @throws IllegalArgumentException if the placeholders are not name/value pairs
     */
    public static String parse(String text, Object... placeholders) {
        return render(text, ServerVersion.supportsHexColors(), placeholders);
    }

    /**
     * Formats rich text using only the 16 legacy colours, whatever the server version. Useful for
     * places that never show hex colours, such as scoreboard titles on some clients.
     *
     * @param text         the text with tags
     * @param placeholders alternating names and values
     * @return the coloured string
     */
    public static String parseLegacy(String text, Object... placeholders) {
        return render(text, false, placeholders);
    }

    /**
     * Formats every line of a list.
     *
     * @param lines        the lines
     * @param placeholders alternating names and values
     * @return a new list of coloured lines
     */
    public static List<String> parse(List<String> lines, Object... placeholders) {
        List<String> out = new ArrayList<>(lines.size());
        for (String line : lines) {
            out.add(parse(line, placeholders));
        }
        return out;
    }

    /**
     * Removes all tags (and legacy colour codes), leaving the text a player would read. Placeholders
     * are filled in.
     *
     * @param text         the text with tags
     * @param placeholders alternating names and values
     * @return the plain text
     */
    public static String strip(String text, Object... placeholders) {
        return Text.strip(render(text, false, placeholders));
    }

    /**
     * Escapes text so it is shown literally when parsed, for example a player's chat message
     * that is put into a rich-text template.
     *
     * @param text the text
     * @return the escaped text
     */
    public static String escape(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("\\", "\\\\").replace("<", "\\<");
    }

    /**
     * Returns whether a string contains anything that looks like a tag, a cheap way to decide
     * whether a message needs {@link #parse}.
     *
     * @param text the text
     * @return {@code true} if it contains a {@code <...>} sequence
     */
    public static boolean hasTags(String text) {
        if (text == null) {
            return false;
        }
        int open = text.indexOf('<');
        return open >= 0 && text.indexOf('>', open) > open;
    }

    // ------------------------------------------------------------------ parsing

    static String render(String text, boolean hex, Object... placeholders) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        Map<String, Object> values = placeholderMap(placeholders);
        Element root = new Element("", Collections.<String>emptyList());
        parseInto(root, prepare(text), values, 0);
        Writer writer = new Writer(hex);
        root.render(writer, new Style(), null);
        return writer.out.toString();
    }

    /**
     * Turns legacy {@code &#rrggbb} into hex tags and {@code &} codes into section codes. Only the
     * template goes through this, never placeholder values, so values cannot inject formatting.
     */
    private static String prepare(String text) {
        String result = LEGACY_HEX.matcher(text).replaceAll("<#$1>");
        return ChatColor.translateAlternateColorCodes('&', result);
    }

    private static Map<String, Object> placeholderMap(Object[] pairs) {
        if (pairs == null || pairs.length == 0) {
            return Collections.emptyMap();
        }
        if (pairs.length % 2 != 0) {
            throw new IllegalArgumentException("Placeholders must be name/value pairs, got " + pairs.length + " values");
        }
        Map<String, Object> map = new HashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put(String.valueOf(pairs[i]).toLowerCase(Locale.ROOT), pairs[i + 1]);
        }
        return map;
    }

    private static void parseInto(Element root, String text, Map<String, Object> values, int depth) {
        List<Element> stack = new ArrayList<>();
        stack.add(root);
        StringBuilder buffer = new StringBuilder();
        int i = 0;
        int n = text.length();
        while (i < n) {
            char c = text.charAt(i);
            if (c == '\\' && i + 1 < n && (text.charAt(i + 1) == '<' || text.charAt(i + 1) == '\\')) {
                buffer.append(text.charAt(i + 1));
                i += 2;
                continue;
            }
            if (c != '<') {
                buffer.append(c);
                i++;
                continue;
            }
            int close = text.indexOf('>', i + 1);
            if (close < 0) {
                buffer.append(c);
                i++;
                continue;
            }
            String raw = text.substring(i + 1, close);
            Element top = stack.get(stack.size() - 1);
            if (raw.startsWith("/")) {
                String name = raw.substring(1).trim().toLowerCase(Locale.ROOT);
                int match = -1;
                for (int s = stack.size() - 1; s > 0; s--) {
                    if (stack.get(s).matches(name)) {
                        match = s;
                        break;
                    }
                }
                if (match < 0) {
                    buffer.append('<').append(raw).append('>');
                } else {
                    flush(top, buffer);
                    while (stack.size() > match) {
                        stack.remove(stack.size() - 1);
                    }
                }
                i = close + 1;
                continue;
            }
            List<String> parts = splitArgs(raw);
            String name = parts.get(0).toLowerCase(Locale.ROOT);
            List<String> args = parts.subList(1, parts.size());

            if (name.equals("reset") || name.equals("r")) {
                flush(top, buffer);
                while (stack.size() > 1) {
                    stack.remove(stack.size() - 1);
                }
                i = close + 1;
                continue;
            }
            if (name.equals("newline") || name.equals("br")) {
                buffer.append('\n');
                i = close + 1;
                continue;
            }
            if (values.containsKey(name) && args.isEmpty()) {
                Object value = values.get(name);
                if (value instanceof Parsed && depth < 8) {
                    flush(top, buffer);
                    Element inner = new Element("", Collections.<String>emptyList());
                    parseInto(inner, prepare(value.toString()), values, depth + 1);
                    top.children.add(inner);
                } else {
                    buffer.append(value instanceof Parsed ? value.toString() : String.valueOf(value));
                }
                i = close + 1;
                continue;
            }
            if (!isKnown(name, args)) {
                buffer.append('<').append(raw).append('>');
                i = close + 1;
                continue;
            }
            flush(top, buffer);
            Element element = new Element(name, new ArrayList<>(args));
            top.children.add(element);
            stack.add(element);
            i = close + 1;
        }
        flush(stack.get(stack.size() - 1), buffer);
    }

    private static List<String> splitArgs(String raw) {
        List<String> parts = new ArrayList<>();
        int start = 0;
        for (int i = 0; i < raw.length(); i++) {
            if (raw.charAt(i) == ':') {
                parts.add(raw.substring(start, i));
                start = i + 1;
            }
        }
        parts.add(raw.substring(start));
        return parts;
    }

    private static boolean isKnown(String name, List<String> args) {
        if (name.startsWith("#")) {
            return parseColour(name) != null && args.isEmpty();
        }
        if (NAMED.containsKey(name) || DECORATIONS.containsKey(name)) {
            return args.isEmpty();
        }
        if (name.startsWith("!") && DECORATIONS.containsKey(name.substring(1))) {
            return args.isEmpty();
        }
        switch (name) {
            case "color":
            case "colour":
            case "c":
                return args.size() == 1 && parseColour(args.get(0)) != null;
            case "gradient":
                if (args.size() < 2) {
                    return false;
                }
                for (String a : args) {
                    if (parseColour(a) == null) {
                        return false;
                    }
                }
                return true;
            case "rainbow":
                return args.size() <= 1;
            default:
                return false;
        }
    }

    static Integer parseColour(String value) {
        String v = value.trim().toLowerCase(Locale.ROOT);
        if (NAMED.containsKey(v)) {
            return NAMED.get(v);
        }
        if (v.startsWith("#") && v.length() == 7) {
            try {
                return Integer.parseInt(v.substring(1), 16);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private static void flush(Element into, StringBuilder buffer) {
        if (buffer.length() > 0) {
            into.children.add(buffer.toString());
            buffer.setLength(0);
        }
    }

    // ------------------------------------------------------------------ rendering

    /** The formatting in effect for a character. */
    private static final class Style {
        int colour = -1;
        final StringBuilder decorations = new StringBuilder();

        Style copy() {
            Style s = new Style();
            s.colour = colour;
            s.decorations.append(decorations);
            return s;
        }

        void decorate(char code, boolean on) {
            int at = decorations.indexOf(String.valueOf(code));
            if (on && at < 0) {
                decorations.append(code);
            } else if (!on && at >= 0) {
                decorations.deleteCharAt(at);
            }
        }

        String sortedDecorations() {
            StringBuilder sb = new StringBuilder();
            for (char c : DECORATION_ORDER.toCharArray()) {
                if (decorations.indexOf(String.valueOf(c)) >= 0) {
                    sb.append(c);
                }
            }
            return sb.toString();
        }
    }

    /** Colours characters by their position inside a gradient or rainbow. */
    private interface Painter {
        int colourAt(int index);
    }

    private static final class Element {
        final String name;
        final List<String> args;
        final List<Object> children = new ArrayList<>();

        Element(String name, List<String> args) {
            this.name = name;
            this.args = args;
        }

        boolean matches(String closing) {
            if (closing.equals(name)) {
                return true;
            }
            if (closing.startsWith("#") || NAMED.containsKey(closing) || closing.equals("color")
                    || closing.equals("colour") || closing.equals("c")) {
                return name.startsWith("#") || NAMED.containsKey(name) || name.equals("color")
                        || name.equals("colour") || name.equals("c");
            }
            Character a = DECORATIONS.get(closing);
            Character b = DECORATIONS.get(name);
            return a != null && a.equals(b);
        }

        int visibleLength() {
            int total = 0;
            for (Object child : children) {
                if (child instanceof String) {
                    String str = (String) child;
                    for (int i = 0; i < str.length(); i++) {
                        char ch = str.charAt(i);
                        if (ch == ChatColor.COLOR_CHAR) {
                            i++;
                        } else if (ch != '\n') {
                            total++;
                        }
                    }
                } else {
                    total += ((Element) child).visibleLength();
                }
            }
            return total;
        }

        void render(Writer writer, Style inherited, Painter painter) {
            Style style = inherited.copy();
            Painter active = painter;
            if (name.startsWith("#") || NAMED.containsKey(name)) {
                style.colour = parseColour(name);
                active = null;
            } else if (name.equals("color") || name.equals("colour") || name.equals("c")) {
                style.colour = parseColour(args.get(0));
                active = null;
            } else if (DECORATIONS.containsKey(name)) {
                style.decorate(DECORATIONS.get(name), true);
            } else if (name.startsWith("!")) {
                style.decorate(DECORATIONS.get(name.substring(1)), false);
            } else if (name.equals("gradient")) {
                active = gradient(args, visibleLength(), writer);
            } else if (name.equals("rainbow")) {
                active = rainbow(args.isEmpty() ? "" : args.get(0), visibleLength(), writer);
            }
            for (Object child : children) {
                if (child instanceof String) {
                    writer.write((String) child, style, active);
                } else {
                    ((Element) child).render(writer, style, active);
                }
            }
        }

        private static Painter gradient(List<String> stops, int length, Writer writer) {
            int[] colours = new int[stops.size()];
            for (int i = 0; i < colours.length; i++) {
                colours[i] = parseColour(stops.get(i));
            }
            int start = writer.painted;
            return index -> {
                int at = index - start;
                if (length <= 1) {
                    return colours[0];
                }
                double t = (double) at / (length - 1) * (colours.length - 1);
                int segment = Math.min((int) t, colours.length - 2);
                return mix(colours[segment], colours[segment + 1], t - segment);
            };
        }

        private static Painter rainbow(String arg, int length, Writer writer) {
            boolean reversed = arg.startsWith("!");
            String phaseText = reversed ? arg.substring(1) : arg;
            int phase = 0;
            if (!phaseText.isEmpty()) {
                try {
                    phase = Integer.parseInt(phaseText);
                } catch (NumberFormatException ignored) {
                    phase = 0;
                }
            }
            int start = writer.painted;
            int finalPhase = phase;
            return index -> {
                int at = index - start;
                double fraction = length <= 0 ? 0 : (double) at / length;
                if (reversed) {
                    fraction = 1 - fraction;
                }
                float hue = (float) ((fraction + finalPhase / 10.0) % 1.0);
                return java.awt.Color.HSBtoRGB(hue, 1f, 1f) & 0xFFFFFF;
            };
        }
    }

    static int mix(int from, int to, double t) {
        int r = (int) Math.round(((from >> 16) & 0xFF) + (((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * t);
        int g = (int) Math.round(((from >> 8) & 0xFF) + (((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * t);
        int b = (int) Math.round((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * t);
        return (r << 16) | (g << 8) | b;
    }

    /** Emits formatting codes only when the wanted style changes. */
    private static final class Writer {
        final StringBuilder out = new StringBuilder();
        final boolean hex;
        int painted;
        /** The colour code last written; {@code ""} for plain text, {@code null} when unknown. */
        private String emittedColour = "";
        private String emittedDecorations = "";

        Writer(boolean hex) {
            this.hex = hex;
        }

        void write(String text, Style style, Painter painter) {
            for (int i = 0; i < text.length(); i++) {
                char ch = text.charAt(i);
                if (ch == ChatColor.COLOR_CHAR && i + 1 < text.length()) {
                    // A legacy code from the template: keep it, and forget what we last emitted.
                    out.append(ch).append(text.charAt(++i));
                    emittedColour = null;
                    emittedDecorations = "";
                    continue;
                }
                if (ch == '\n') {
                    out.append('\n');
                    emittedColour = "";
                    emittedDecorations = "";
                    continue;
                }
                int colour = painter != null ? painter.colourAt(painted) : style.colour;
                String decorations = style.sortedDecorations();
                String code = colour < 0 ? "" : colourCode(colour);
                if (code.isEmpty() && decorations.isEmpty()) {
                    if (emittedColour != null && (!emittedColour.isEmpty() || !emittedDecorations.isEmpty())) {
                        out.append(ChatColor.COLOR_CHAR).append('r');
                        emittedColour = "";
                        emittedDecorations = "";
                    }
                } else if (!code.equals(emittedColour) || !decorations.startsWith(emittedDecorations)) {
                    out.append(code.isEmpty() ? ChatColor.COLOR_CHAR + "r" : code);
                    appendDecorations(decorations);
                    emittedColour = code;
                    emittedDecorations = decorations;
                } else if (!decorations.equals(emittedDecorations)) {
                    appendDecorations(decorations.substring(emittedDecorations.length()));
                    emittedDecorations = decorations;
                }
                out.append(ch);
                painted++;
            }
        }

        private void appendDecorations(String codes) {
            for (char d : codes.toCharArray()) {
                out.append(ChatColor.COLOR_CHAR).append(d);
            }
        }

        private String colourCode(int colour) {
            if (hex) {
                StringBuilder sb = new StringBuilder(14).append(ChatColor.COLOR_CHAR).append('x');
                String h = String.format("%06x", colour);
                for (char c : h.toCharArray()) {
                    sb.append(ChatColor.COLOR_CHAR).append(c);
                }
                return sb.toString();
            }
            return Text.nearestLegacy(colour).toString();
        }
    }
}
