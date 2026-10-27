package dev.blockpilot.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import org.junit.jupiter.api.Test;

class ConfigValuesTest {
    enum Mode {
        FAST,
        // A constant with a body is an instance of a subclass of the enum.
        FANCY {
        },
        FABULOUS
    }

    @Test
    void readsSwitchesAsTheFileOrAsOptionsWriteThem() {
        assertEquals(true, ConfigValues.parse("true", false, null));
        assertEquals(true, ConfigValues.parse("on", false, null));
        assertEquals(false, ConfigValues.parse("off", true, null));
        assertNull(ConfigValues.parse("yes", true, null));
    }

    @Test
    void readsNumbersAsTheDefaultsType() {
        assertEquals(12, ConfigValues.parse("12", 4, null));
        assertEquals(5_000_000_000L, ConfigValues.parse("5000000000", 1L, null));
        assertEquals(0.25, ConfigValues.parse("0.25", 1.0, null));
        assertEquals(2f, ConfigValues.parse("2", 1f, null));
        assertNull(ConfigValues.parse("1.5", 4, null));
        assertNull(ConfigValues.parse("NaN", 1.0, null));
    }

    @Test
    void readsEnumNamesInAnyCase() {
        assertEquals(Mode.FANCY, ConfigValues.parse("fancy", Mode.FAST, null));
        assertEquals(Mode.FAST, ConfigValues.parse("Fast", Mode.FANCY, null));
        assertNull(ConfigValues.parse("shiny", Mode.FAST, null));
    }

    @Test
    void keepsTextAsWritten() {
        assertEquals("two words", ConfigValues.parse("two words", "", null));
    }

    @Test
    void readsListItemsAsTheDefaultsOrTheCurrentItems() {
        assertEquals(List.of(1, 2, 3), ConfigValues.parse("1, 2,3", List.of(9), null));
        assertEquals(List.of(4L), ConfigValues.parse("4", List.of(), List.of(7L)));
        assertEquals(List.of("a", "b"), ConfigValues.parse("a,b", List.of(), List.of()));
        assertEquals(List.of(), ConfigValues.parse("", List.of(1), null));
        assertNull(ConfigValues.parse("1,x", List.of(1), null));
    }

    @Test
    void writesValuesAsTheFileDoes() {
        assertEquals("FANCY", ConfigValues.text(Mode.FANCY));
        assertEquals("1,2", ConfigValues.text(List.of(1, 2)));
        assertEquals("0.5", ConfigValues.text(0.5));
        assertEquals("none", ConfigValues.text(null));
    }

    @Test
    void saysWhatAKeyTakes() {
        assertEquals("true|false", ConfigValues.takes(true, null, null, null, value -> true));
        assertEquals("0..64", ConfigValues.takes(8, null, 0, 64, value -> true));
        assertEquals("0.0..1.0", ConfigValues.takes(0.5, null, 0.0, 1.0, value -> true));
        assertEquals("a whole number", ConfigValues.takes(8, null, null, null, value -> true));
        assertEquals("a number", ConfigValues.takes(0.5, null, null, null, value -> true));
        assertEquals("FAST|FANCY", ConfigValues.takes(Mode.FAST, null, null, null, value -> value != Mode.FABULOUS));
        assertEquals("text", ConfigValues.takes("", null, null, null, value -> true));
        assertEquals("items separated by commas, each a whole number",
            ConfigValues.takes(List.of(), List.of(3), null, null, value -> true));
    }
}
