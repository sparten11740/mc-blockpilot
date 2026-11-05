package dev.blockpilot.wire;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JsonTest {
    @Test
    void writesBackWhatItRead() {
        String text = "{\"id\":7,\"b\":[1,-2.5e3,0.62,true,false,null,\"x\\\"y\\\\z\\n\u00e9\"],\"a\":{}}";
        assertEquals("{\"id\":7,\"b\":[1,-2.5E+3,0.62,true,false,null,\"x\\\"y\\\\z\\n\u00e9\"],\"a\":{}}",
            Json.write(Json.parse(text)));
    }

    @Test
    void readsTheValueKinds() {
        Map<String, Object> expected = new LinkedHashMap<>();
        expected.put("zeta", new BigDecimal("12"));
        expected.put("alpha", Arrays.asList(Boolean.TRUE, null, "s", new BigDecimal("-0.5E2")));
        assertEquals(expected, Json.parse(" {\"zeta\" : 12 ,\n\t\"alpha\":[ true , null,\"s\", -0.5E2 ] } "));
        assertEquals(List.of("zeta", "alpha"),
            new ArrayList<>(((Map<?, ?>) Json.parse("{\"zeta\":1,\"alpha\":2}")).keySet()));
    }

    @Test
    void readsEscapes() {
        assertEquals("\u00e9\ud83d\ude00/\b\f\r\t", Json.parse("\"\\u00E9\\ud83d\\ude00\\/\\b\\f\\r\\t\""));
    }

    @Test
    void escapesControlCharacters() {
        assertEquals("\"a\\u0001\\t\\b\"", Json.write("a\u0001\t\b"));
    }

    @Test
    void refusesMalformedText() {
        for (String text : List.of("", "{", "[1,]", "{\"a\" 1}", "{a:1}", "01", "-", "1.", "1e", "\"abc", "tru",
            "1 2", "\"\\x\"", "\"\\u12g4\"", "\"\\u+123\"", "\"a\u0001\"", "[1 2]", "{\"a\":1,}")) {
            assertThrows(IllegalArgumentException.class, () -> Json.parse(text), text);
        }
    }

    @Test
    void writesOnlyJsonValues() {
        assertThrows(IllegalArgumentException.class, () -> Json.write(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> Json.write(List.of(new Object())));
    }
}
