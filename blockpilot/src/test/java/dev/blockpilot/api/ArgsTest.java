package dev.blockpilot.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ArgsTest {
    private static Args.Value value(String text, ArgType type) {
        return new Args.Value(text, type);
    }

    private static Args arguments(String name, String text, ArgType type) {
        return new Args(Map.of(name, value(text, type)), Map.of());
    }

    private static Args options(String name, String text, ArgType type) {
        return new Args(Map.of(), Map.of(name, value(text, type)));
    }

    @Test
    void aRepeatedSlotGivesEveryValueInOrder() {
        Args repeated = new Args(Map.of("via", value("1,2,3", ArgType.POSITION)), Map.of(), Map.of("via",
            List.of(value("1,2,3", ArgType.POSITION), value("~4,5,6", ArgType.POSITION))));
        assertEquals(List.of(Position.absolute(1, 2, 3), Position.relative(4, 5, 6)), repeated.positions("via"));
        assertEquals(Position.absolute(1, 2, 3), repeated.position("via"), "the one value is the first");
        Args single = arguments("a", "7", ArgType.INT);
        assertEquals(List.of(value("7", ArgType.INT)), single.values("a"));
        assertEquals(List.of(), single.values("b"));
        assertThrows(IllegalStateException.class, () -> single.positions("a"));
    }

    @Test
    void anUnresolvedEntityNameIsRefused() {
        IllegalStateException refused = assertThrows(IllegalStateException.class,
            () -> arguments("a", "@pip", ArgType.ENTITY).entity("a"));
        assertTrue(refused.getMessage().contains("@pip wasn't resolved"), refused.getMessage());
    }

    @Test
    void accessorsConvertTheTypeTheSlotTook() {
        assertEquals(Position.relative(1, 0, -2), arguments("a", "~1,0,-2", ArgType.POSITION).position("a"));
        assertEquals(new Point(120, 40.5), arguments("a", "120,40.5", ArgType.POINT).point("a"));
        assertEquals(new EntityRef(812), arguments("a", "e812", ArgType.ENTITY).entity("a"));
        assertEquals(UUID.fromString("0f3a9c2e-1b2c-4d5e-8f90-a1b2c3d4e5f6"),
            arguments("a", "0f3a9c2e-1b2c-4d5e-8f90-a1b2c3d4e5f6", ArgType.UUID).uuid("a"));
        assertEquals(3, arguments("a", "w3", ArgType.WIDGET).index("a", -1));
        assertEquals(5, arguments("a", "s5", ArgType.MENU_SLOT).index("a", -1));
        assertEquals(-12, arguments("a", "-12", ArgType.INT).integer("a", 0));
        assertEquals(1.5, arguments("a", "1.5", ArgType.NUMBER).number("a", 0));
        assertEquals(7, arguments("a", "7", ArgType.INT).number("a", 0));
        assertEquals(30, arguments("a", "30", ArgType.SECONDS).number("a", 0));
        assertEquals(6, arguments("a", "x6", ArgType.COUNT).count("a", 1));
        assertEquals(new Duration(20), arguments("a", "20t", ArgType.DURATION).duration("a"));
        assertEquals(new Duration(10), arguments("a", "0.5", ArgType.SECONDS).duration("a"));
        Args id = arguments("a", "stone", ArgType.ID);
        assertEquals("stone", id.word("a"));
        assertEquals("stone", id.text("a"));
        assertEquals(ArgType.ID, id.type("a"));
        assertTrue(id.has("a"));
    }

    @Test
    void absentArgumentsGiveTheFallback() {
        Args none = new Args(Map.of(), Map.of());
        assertNull(none.type("a"));
        assertFalse(none.has("a"));
        assertNull(none.word("a"));
        assertNull(none.position("a"));
        assertNull(none.point("a"));
        assertNull(none.entity("a"));
        assertNull(none.uuid("a"));
        assertNull(none.duration("a"));
        assertEquals(-1, none.index("a", -1));
        assertEquals(16, none.integer("a", 16));
        assertEquals(2.5, none.number("a", 2.5));
        assertEquals(1, none.count("a", 1));
    }

    @Test
    void askingForAnotherTypeIsAHandlerBug() {
        Args id = arguments("what", "stone", ArgType.ID);
        assertEquals("what was taken as id, not as integer",
            assertThrows(IllegalStateException.class, () -> id.integer("what", 1)).getMessage());
        assertThrows(IllegalStateException.class, () -> arguments("n", "5", ArgType.INT).count("n", 1));
        assertThrows(IllegalStateException.class, () -> id.position("what"));
        assertThrows(IllegalStateException.class, () -> id.entity("what"));
        assertThrows(IllegalStateException.class, () -> id.duration("what"));
    }

    @Test
    void optionsReadTheirValues() throws CommandException {
        assertEquals(2.5, options("near", "2.5", ArgType.NUMBER).numberOption("near", 0));
        assertEquals(3, options("near", "3", ArgType.INT).numberOption("near", 0));
        assertEquals(4, options("limit", "4", ArgType.INT).intOption("limit", 50));
        assertEquals(new Duration(5), options("every", "5t", ArgType.DURATION).durationOption("every"));
        assertEquals(Duration.seconds(90), options("timeout", "90", ArgType.SECONDS).durationOption("timeout"));
        Args none = new Args(Map.of(), Map.of());
        assertEquals(1.5, none.numberOption("near", 1.5));
        assertEquals(50, none.intOption("limit", 50));
        assertNull(none.durationOption("every"));
        assertNull(none.option("x"));
        assertNull(none.optionType("x"));
        assertFalse(none.hasOption("x"));
        Args side = options("side", "server", ArgType.KEYWORD);
        assertEquals("server", side.option("side"));
        assertEquals(ArgType.KEYWORD, side.optionType("side"));
        assertTrue(side.hasOption("side"));
    }

    @Test
    void untypedOptionsAreCheckedWhenRead() throws CommandException {
        assertEquals(1.5, options("near", "1.5", ArgType.TEXT).numberOption("near", 0));
        assertEquals(-4, options("limit", "-4", ArgType.TEXT).intOption("limit", 0));
        assertEquals(new Duration(3), options("every", "3t", ArgType.TEXT).durationOption("every"));
        assertEquals(new Duration(20), options("every", "1", ArgType.TEXT).durationOption("every"));
        assertEquals("syntax near= expects a number, got NaN",
            assertThrows(CommandException.class, () -> options("near", "NaN", ArgType.TEXT).numberOption("near", 0))
                .getMessage());
        assertThrows(CommandException.class, () -> options("limit", "+5", ArgType.TEXT).intOption("limit", 0));
        assertThrows(CommandException.class, () -> options("every", "-5", ArgType.TEXT).durationOption("every"));
        assertEquals("near= was taken as keyword, not as number", assertThrows(IllegalStateException.class,
            () -> options("near", "far", ArgType.KEYWORD).numberOption("near", 0)).getMessage());
    }

    @Test
    void familiesKeepTheirOrder() {
        Map<String, Args.Value> given = new LinkedHashMap<>();
        given.put("rule:keepInventory", value("true", ArgType.TEXT));
        given.put("seed", value("1", ArgType.TEXT));
        given.put("rule:doDaylightCycle", value("false", ArgType.TEXT));
        Map<String, String> rules = new Args(Map.of(), given).family("rule");
        assertEquals(List.of("keepInventory", "doDaylightCycle"), List.copyOf(rules.keySet()));
        assertEquals("false", rules.get("doDaylightCycle"));
        assertEquals(Map.of(), new Args(Map.of(), given).family("other"));
    }

    @Test
    void argsAreACopyThatCantChange() {
        Map<String, Args.Value> given = new HashMap<>(Map.of("a", value("1", ArgType.INT)));
        Args args = new Args(given, given);
        given.clear();
        assertEquals(1, args.integer("a", 0));
        assertEquals(Map.of("a", value("1", ArgType.INT)), args.arguments());
        assertEquals(Map.of("a", value("1", ArgType.INT)), args.options());
        assertThrows(UnsupportedOperationException.class, () -> args.arguments().clear());
        assertThrows(UnsupportedOperationException.class, () -> args.options().clear());
        assertThrows(NullPointerException.class, () -> value(null, ArgType.INT));
        assertThrows(NullPointerException.class, () -> value("1", null));
    }

    @Test
    void pointsPrintTheWayTheyAreWritten() {
        assertEquals("120,40.5", new Point(120, 40.5).toString());
        assertEquals(new Point(-1, 2), Point.parse("-1,2"));
        assertNull(Point.parse("1,2,3"));
    }
}
