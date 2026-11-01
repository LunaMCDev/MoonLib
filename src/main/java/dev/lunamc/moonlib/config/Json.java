package dev.lunamc.moonlib.config;

import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A small, dependency-free JSON reader and writer.
 *
 * <p>MoonLib uses its own implementation rather than the Gson bundled with the server, because that
 * Gson ranges from 2.2.4 on 1.8 to 2.11+ on modern versions with different APIs. This class behaves
 * the same everywhere.</p>
 *
 * <pre>{@code
 * Object tree = Json.parse("{\"name\": \"Luna\", \"coins\": 50, \"ranks\": [\"A\", \"B\"]}");
 * Map<String, Object> map = Json.parseObject(text);
 * String pretty = Json.write(map);              // 2-space indented
 * String compact = Json.write(map, false);      // one line
 * }</pre>
 *
 * <h2>Types</h2>
 * <p>Objects parse to {@link LinkedHashMap} (key order is kept), arrays to {@link ArrayList}, strings to
 * {@link String}, {@code true}/{@code false} to {@link Boolean} and {@code null} to {@code null}.
 * Whole numbers become {@link Integer} when they fit, then {@link Long}, then {@link BigInteger};
 * decimals become {@link Double}.</p>
 *
 * <h2>Leniency</h2>
 * <p>Parsing accepts {@code //} and {@code /* *}{@code /} comments and trailing commas, which are
 * common in hand-edited files. Writing always produces strict, standard JSON.</p>
 */
public final class Json {

    private Json() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Parses any JSON value.
     *
     * @param text the JSON text
     * @return the parsed value (map, list, string, number, boolean or {@code null})
     * @throws MalformedDataException if the text is not valid JSON
     */
    public static Object parse(String text) throws MalformedDataException {
        Parser parser = new Parser(text);
        parser.skipWhitespace();
        Object value = parser.readValue();
        parser.skipWhitespace();
        if (!parser.atEnd()) {
            throw parser.error("Unexpected content after the JSON value");
        }
        return value;
    }

    /**
     * Parses a JSON object. Blank text gives an empty map.
     *
     * @param text the JSON text
     * @return the object as a mutable, insertion-ordered map
     * @throws MalformedDataException if the text is not a valid JSON object
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseObject(String text) throws MalformedDataException {
        if (text == null || text.trim().isEmpty()) {
            return new LinkedHashMap<>();
        }
        Object value = parse(text);
        if (!(value instanceof Map)) {
            throw new MalformedDataException("Expected a JSON object at the top level but found "
                    + (value == null ? "null" : value.getClass().getSimpleName()), null);
        }
        return (Map<String, Object>) value;
    }

    /**
     * Writes a value as pretty-printed JSON with 2-space indentation.
     *
     * @param value maps, collections, arrays, strings, numbers, booleans, enums and {@code null};
     *              anything else is written as its {@code toString()}
     * @return the JSON text
     */
    public static String write(Object value) {
        return write(value, true);
    }

    /**
     * Writes a value as JSON.
     *
     * @param value  the value
     * @param pretty {@code true} for indented output, {@code false} for a single line
     * @return the JSON text
     */
    public static String write(Object value, boolean pretty) {
        StringBuilder out = new StringBuilder(256);
        writeValue(out, value, pretty, 0);
        return out.toString();
    }

    /**
     * Returns a deep copy of a JSON tree, so the copy can be modified freely.
     *
     * @param value a tree of maps, lists and plain values
     * @return the copy
     */
    public static Object deepCopy(Object value) {
        if (value instanceof Map) {
            Map<String, Object> copy = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                copy.put(String.valueOf(entry.getKey()), deepCopy(entry.getValue()));
            }
            return copy;
        }
        if (value instanceof Collection) {
            List<Object> copy = new ArrayList<>(((Collection<?>) value).size());
            for (Object element : (Collection<?>) value) {
                copy.add(deepCopy(element));
            }
            return copy;
        }
        return value;
    }

    // ------------------------------------------------------------------ writing

    private static void writeValue(StringBuilder out, Object value, boolean pretty, int depth) {
        if (value == null) {
            out.append("null");
        } else if (value instanceof String || value instanceof Character) {
            writeString(out, value.toString());
        } else if (value instanceof Boolean) {
            out.append(value);
        } else if (value instanceof Number) {
            writeNumber(out, (Number) value);
        } else if (value instanceof Enum) {
            writeString(out, ((Enum<?>) value).name());
        } else if (value instanceof Map) {
            writeObject(out, (Map<?, ?>) value, pretty, depth);
        } else if (value instanceof Collection) {
            writeArray(out, ((Collection<?>) value).toArray(), pretty, depth);
        } else if (value.getClass().isArray()) {
            int length = Array.getLength(value);
            Object[] elements = new Object[length];
            for (int i = 0; i < length; i++) {
                elements[i] = Array.get(value, i);
            }
            writeArray(out, elements, pretty, depth);
        } else {
            writeString(out, value.toString());
        }
    }

    private static void writeObject(StringBuilder out, Map<?, ?> map, boolean pretty, int depth) {
        if (map.isEmpty()) {
            out.append("{}");
            return;
        }
        out.append('{');
        boolean first = true;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!first) {
                out.append(',');
            }
            first = false;
            newline(out, pretty, depth + 1);
            writeString(out, String.valueOf(entry.getKey()));
            out.append(pretty ? ": " : ":");
            writeValue(out, entry.getValue(), pretty, depth + 1);
        }
        newline(out, pretty, depth);
        out.append('}');
    }

    private static void writeArray(StringBuilder out, Object[] elements, boolean pretty, int depth) {
        if (elements.length == 0) {
            out.append("[]");
            return;
        }
        out.append('[');
        for (int i = 0; i < elements.length; i++) {
            if (i > 0) {
                out.append(',');
            }
            newline(out, pretty, depth + 1);
            writeValue(out, elements[i], pretty, depth + 1);
        }
        newline(out, pretty, depth);
        out.append(']');
    }

    private static void writeNumber(StringBuilder out, Number number) {
        if (number instanceof Double || number instanceof Float) {
            double d = number.doubleValue();
            if (Double.isNaN(d) || Double.isInfinite(d)) {
                out.append("null"); // not representable in JSON
                return;
            }
            out.append(d == Math.rint(d) && Math.abs(d) < 1e15 ? String.valueOf((long) d) + ".0" : String.valueOf(d));
        } else if (number instanceof BigDecimal) {
            out.append(((BigDecimal) number).toPlainString());
        } else {
            out.append(number.toString());
        }
    }

    private static void writeString(StringBuilder out, String text) {
        out.append('"');
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '"':
                    out.append("\\\"");
                    break;
                case '\\':
                    out.append("\\\\");
                    break;
                case '\n':
                    out.append("\\n");
                    break;
                case '\r':
                    out.append("\\r");
                    break;
                case '\t':
                    out.append("\\t");
                    break;
                case '\b':
                    out.append("\\b");
                    break;
                case '\f':
                    out.append("\\f");
                    break;
                default:
                    if (c < 0x20 || c == ' ' || c == ' ') {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
            }
        }
        out.append('"');
    }

    private static void newline(StringBuilder out, boolean pretty, int depth) {
        if (pretty) {
            out.append('\n');
            for (int i = 0; i < depth; i++) {
                out.append("  ");
            }
        }
    }

    // ------------------------------------------------------------------ parsing

    private static final class Parser {
        private static final int MAX_DEPTH = 512;
        private final String text;
        private int pos;
        private int depth;

        Parser(String text) {
            this.text = text == null ? "" : text;
            // Ignore a UTF-8 byte order mark.
            if (this.text.startsWith("﻿")) {
                pos = 1;
            }
        }

        boolean atEnd() {
            return pos >= text.length();
        }

        Object readValue() throws MalformedDataException {
            if (atEnd()) {
                throw error("Unexpected end of input");
            }
            char c = text.charAt(pos);
            switch (c) {
                case '{':
                    return readObject();
                case '[':
                    return readArray();
                case '"':
                    return readString();
                case 't':
                    expectWord("true");
                    return Boolean.TRUE;
                case 'f':
                    expectWord("false");
                    return Boolean.FALSE;
                case 'n':
                    expectWord("null");
                    return null;
                default:
                    if (c == '-' || (c >= '0' && c <= '9')) {
                        return readNumber();
                    }
                    throw error("Unexpected character '" + c + "'");
            }
        }

        private Map<String, Object> readObject() throws MalformedDataException {
            enter();
            pos++; // {
            Map<String, Object> map = new LinkedHashMap<>();
            skipWhitespace();
            if (peek() == '}') {
                pos++;
                depth--;
                return map;
            }
            while (true) {
                skipWhitespace();
                if (peek() == '}') { // trailing comma
                    pos++;
                    break;
                }
                if (peek() != '"') {
                    throw error("Expected a quoted key");
                }
                String key = readString();
                skipWhitespace();
                if (peek() != ':') {
                    throw error("Expected ':' after key \"" + key + "\"");
                }
                pos++;
                skipWhitespace();
                map.put(key, readValue());
                skipWhitespace();
                char next = peek();
                if (next == ',') {
                    pos++;
                } else if (next == '}') {
                    pos++;
                    break;
                } else {
                    throw error("Expected ',' or '}'");
                }
            }
            depth--;
            return map;
        }

        private List<Object> readArray() throws MalformedDataException {
            enter();
            pos++; // [
            List<Object> list = new ArrayList<>();
            skipWhitespace();
            if (peek() == ']') {
                pos++;
                depth--;
                return list;
            }
            while (true) {
                skipWhitespace();
                if (peek() == ']') { // trailing comma
                    pos++;
                    break;
                }
                list.add(readValue());
                skipWhitespace();
                char next = peek();
                if (next == ',') {
                    pos++;
                } else if (next == ']') {
                    pos++;
                    break;
                } else {
                    throw error("Expected ',' or ']'");
                }
            }
            depth--;
            return list;
        }

        private String readString() throws MalformedDataException {
            pos++; // opening quote
            StringBuilder sb = new StringBuilder();
            while (true) {
                if (atEnd()) {
                    throw error("Unterminated string");
                }
                char c = text.charAt(pos++);
                if (c == '"') {
                    return sb.toString();
                }
                if (c == '\\') {
                    if (atEnd()) {
                        throw error("Unterminated escape sequence");
                    }
                    char e = text.charAt(pos++);
                    switch (e) {
                        case '"': sb.append('"'); break;
                        case '\\': sb.append('\\'); break;
                        case '/': sb.append('/'); break;
                        case 'b': sb.append('\b'); break;
                        case 'f': sb.append('\f'); break;
                        case 'n': sb.append('\n'); break;
                        case 'r': sb.append('\r'); break;
                        case 't': sb.append('\t'); break;
                        case 'u':
                            if (pos + 4 > text.length()) {
                                throw error("Incomplete \\u escape");
                            }
                            try {
                                sb.append((char) Integer.parseInt(text.substring(pos, pos + 4), 16));
                            } catch (NumberFormatException ex) {
                                throw error("Invalid \\u escape");
                            }
                            pos += 4;
                            break;
                        default:
                            throw error("Invalid escape '\\" + e + "'");
                    }
                } else if (c < 0x20) {
                    pos--;
                    throw error("Control character in string (use \\n for new lines)");
                } else {
                    sb.append(c);
                }
            }
        }

        private Number readNumber() throws MalformedDataException {
            int start = pos;
            if (peek() == '-') {
                pos++;
            }
            boolean decimal = false;
            while (!atEnd()) {
                char c = text.charAt(pos);
                if (c >= '0' && c <= '9') {
                    pos++;
                } else if (c == '.' || c == 'e' || c == 'E' || c == '+' || (c == '-' && pos > start)) {
                    decimal = true;
                    pos++;
                } else {
                    break;
                }
            }
            String number = text.substring(start, pos);
            try {
                if (!decimal) {
                    BigInteger big = new BigInteger(number);
                    if (big.bitLength() < 32) {
                        return big.intValue();
                    }
                    if (big.bitLength() < 64) {
                        return big.longValue();
                    }
                    return big;
                }
                return Double.parseDouble(number);
            } catch (NumberFormatException e) {
                pos = start;
                throw error("Invalid number '" + number + "'");
            }
        }

        private void expectWord(String word) throws MalformedDataException {
            if (!text.startsWith(word, pos)) {
                throw error("Expected '" + word + "'");
            }
            pos += word.length();
        }

        void skipWhitespace() throws MalformedDataException {
            while (!atEnd()) {
                char c = text.charAt(pos);
                if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                    pos++;
                } else if (c == '/' && pos + 1 < text.length() && text.charAt(pos + 1) == '/') {
                    while (!atEnd() && text.charAt(pos) != '\n') {
                        pos++;
                    }
                } else if (c == '/' && pos + 1 < text.length() && text.charAt(pos + 1) == '*') {
                    int end = text.indexOf("*/", pos + 2);
                    if (end < 0) {
                        throw error("Unterminated comment");
                    }
                    pos = end + 2;
                } else {
                    break;
                }
            }
        }

        private char peek() throws MalformedDataException {
            if (atEnd()) {
                throw error("Unexpected end of input");
            }
            return text.charAt(pos);
        }

        private void enter() throws MalformedDataException {
            if (++depth > MAX_DEPTH) {
                throw error("Document is nested too deeply");
            }
        }

        MalformedDataException error(String message) {
            int line = 1;
            int column = 1;
            for (int i = 0; i < Math.min(pos, text.length()); i++) {
                if (text.charAt(i) == '\n') {
                    line++;
                    column = 1;
                } else {
                    column++;
                }
            }
            return new MalformedDataException(message, line, column);
        }
    }
}
