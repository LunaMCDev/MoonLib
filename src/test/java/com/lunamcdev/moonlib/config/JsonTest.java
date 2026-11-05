package com.lunamcdev.moonlib.config;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonTest {

    @Test
    void parsesAllValueTypes() throws Exception {
        Map<String, Object> map = Json.parseObject(
                "{\"s\": \"hi\", \"i\": 42, \"l\": 9999999999, \"d\": 1.5, \"e\": 2e3, \"t\": true, \"n\": null,"
                        + " \"a\": [1, \"two\", false], \"o\": {\"x\": -3}}");
        assertEquals("hi", map.get("s"));
        assertEquals(42, map.get("i"));
        assertEquals(9999999999L, map.get("l"));
        assertEquals(1.5, map.get("d"));
        assertEquals(2000.0, map.get("e"));
        assertEquals(Boolean.TRUE, map.get("t"));
        assertTrue(map.containsKey("n"));
        assertEquals(Arrays.asList(1, "two", false), map.get("a"));
        assertEquals(-3, ((Map<?, ?>) map.get("o")).get("x"));
    }

    @Test
    void keepsKeyOrder() throws Exception {
        Map<String, Object> map = Json.parseObject("{\"z\": 1, \"a\": 2, \"m\": 3}");
        assertEquals(Arrays.asList("z", "a", "m"), Arrays.asList(map.keySet().toArray()));
    }

    @Test
    void handlesEscapes() throws Exception {
        String text = "quote\" backslash\\ newline\n tab\t unicodeé control\u0001";
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("text", text);
        String written = Json.write(map, false);
        assertEquals(text, Json.parseObject(written).get("text"));
        assertEquals("Aé", Json.parse("\"\\u0041\\u00e9\""));
    }

    @Test
    void acceptsCommentsAndTrailingCommas() throws Exception {
        Map<String, Object> map = Json.parseObject("{\n  // a comment\n  \"a\": [1, 2,],\n  /* block */ \"b\": 3,\n}");
        assertEquals(Arrays.asList(1, 2), map.get("a"));
        assertEquals(3, map.get("b"));
    }

    @Test
    void reportsErrorPosition() {
        MalformedDataException error = assertThrows(MalformedDataException.class,
                () -> Json.parse("{\n  \"a\": 1\n  \"b\": 2\n}"));
        assertEquals(3, error.line());
        assertTrue(error.getMessage().contains("Expected ',' or '}'"));
        assertThrows(MalformedDataException.class, () -> Json.parse("{\"a\": tru}"));
        assertThrows(MalformedDataException.class, () -> Json.parse("[1, 2"));
        assertThrows(MalformedDataException.class, () -> Json.parse("{} extra"));
        assertThrows(MalformedDataException.class, () -> Json.parseObject("[1]"));
    }

    @Test
    void writesPrettyAndCompact() throws Exception {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("a", 1);
        map.put("b", Arrays.asList("x", "y"));
        map.put("c", new LinkedHashMap<>());
        assertEquals("{\"a\":1,\"b\":[\"x\",\"y\"],\"c\":{}}", Json.write(map, false));
        assertEquals("{\n  \"a\": 1,\n  \"b\": [\n    \"x\",\n    \"y\"\n  ],\n  \"c\": {}\n}", Json.write(map));
        assertEquals(map, Json.parseObject(Json.write(map)));
    }

    @Test
    void writesNumbersSafely() {
        assertEquals("[1.0,2.5,null,10]", Json.write(Arrays.asList(1.0, 2.5, Double.NaN, 10L), false));
    }

    @Test
    void deepCopyIsIndependent() throws Exception {
        Map<String, Object> original = Json.parseObject("{\"list\": [1], \"map\": {\"k\": 1}}");
        @SuppressWarnings("unchecked")
        Map<String, Object> copy = (Map<String, Object>) Json.deepCopy(original);
        @SuppressWarnings("unchecked")
        List<Object> list = (List<Object>) copy.get("list");
        list.add(2);
        assertEquals(1, ((List<?>) original.get("list")).size());
    }
}
