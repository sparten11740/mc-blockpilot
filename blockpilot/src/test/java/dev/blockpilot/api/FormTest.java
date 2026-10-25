package dev.blockpilot.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FormTest {
    @Test
    void aSlotTakesTheFirstTypeThatMatches() {
        Slot what = new Slot("what", List.of(ArgType.INT, ArgType.ID), List.of(), false);
        assertEquals(ArgType.INT, what.match("5"));
        assertEquals(ArgType.ID, what.match("stone"));
        assertNull(what.match("~1,0,0"));
        Slot side = new Slot("side", List.of(ArgType.INT, ArgType.CHOICE), List.of("head", "feet"), false);
        assertEquals(ArgType.CHOICE, side.match("head"));
        assertNull(side.match("chest"));
        assertNull(new Slot("text", List.of(ArgType.TEXT), List.of(), false).match("anything"));
    }

    @Test
    void slotsDescribeThemselves() {
        Slot keyword = new Slot("op", List.of(ArgType.CHOICE), List.of("down", "up"), false);
        assertEquals("down|up", keyword.usage());
        assertEquals("[down|up]", new Slot("op", List.of(ArgType.CHOICE), List.of("down", "up"), true).usage());
        assertEquals("down|up", keyword.expectation());
        Slot target = new Slot("target", List.of(ArgType.POSITION, ArgType.ENTITY), List.of(), true);
        assertEquals("[target]", target.usage());
        assertEquals("<target> (a position x,y,z or ~dx,dy,dz or an entity e<id>)", target.expectation());
        assertEquals("<dy>", new Slot("dy", List.of(ArgType.NUMBER), List.of(), false).usage());
        assertTrue(keyword.isKeyword());
        assertTrue(new Slot("text", List.of(ArgType.TEXT), List.of(), false).isText());
    }

    @Test
    void formsPrintTheirUsage() {
        Form camera = new Form().keyword("op", "at").arg("pos", ArgType.POSITION)
            .optional(group -> group.keyword("look", "look").arg("target", ArgType.POSITION, ArgType.ENTITY));
        assertEquals("at <pos> [look <target>]", camera.usage());
        Form chat = new Form().optionalKeyword("to", "team").text("message");
        assertEquals("[team] <message>", chat.usage());
        assertEquals("[note]", new Form().optionalText("note").usage());
        assertEquals("[n]", new Form().optionalArg("n", ArgType.INT).usage());
        assertEquals(3, camera.elements().size());
    }

    @Test
    void aSlotCanTakeWordsOrTokens() {
        Form swap = new Form().arg("a", List.of("head", "offhand"), ArgType.INT)
            .optionalArg("b", List.of("feet"), ArgType.INT, ArgType.ID);
        Slot a = (Slot) swap.elements().get(0);
        assertEquals(ArgType.CHOICE, a.match("head"));
        assertEquals(ArgType.INT, a.match("7"));
        assertNull(a.match("stone"));
        assertEquals("<a> (head|offhand or a whole number)", a.expectation());
        assertEquals("<a> [b]", swap.usage());
        assertEquals(ArgType.ID, ((Slot) swap.elements().get(1)).match("stone"));
        assertThrows(IllegalArgumentException.class, () -> new Form().arg("a", List.of(), ArgType.INT));
        assertThrows(IllegalArgumentException.class, () -> new Form().arg("a", List.of("x"), ArgType.TEXT));
    }

    @Test
    void textComesLastAndKeywordsAreDeclaredAsSuch() {
        assertThrows(IllegalStateException.class, () -> new Form().text("a").arg("b", ArgType.INT));
        assertThrows(IllegalArgumentException.class, () -> new Form().arg("a", ArgType.CHOICE));
        assertThrows(IllegalArgumentException.class, () -> new Form().arg("a", ArgType.TEXT, ArgType.WORD));
        assertThrows(IllegalArgumentException.class, () -> new Form().keyword("a"));
        assertThrows(IllegalArgumentException.class, () -> new Form().optional(group -> { }));
        assertThrows(IllegalArgumentException.class, () -> new Form().optional(group -> group.text("a")));
        assertEquals("<message>", new Form().arg("message", ArgType.TEXT).usage());
    }

    @Test
    void commandsListOneUsagePerForm() {
        CommandSpec mouse = CommandSpec.action("mouse", (context, args) -> () -> Result.ok())
            .form(form -> form.keyword("op", "down", "up").keyword("button", "left", "right"))
            .form(form -> form.keyword("op", "move").arg("dx", ArgType.NUMBER).arg("dy", ArgType.NUMBER)
                .optionalArg("ticks", ArgType.DURATION));
        assertEquals(List.of("mouse down|up left|right", "mouse move <dx> <dy> [ticks]"), mouse.usages("mouse"));
        CommandSpec create = CommandSpec.action("create", (context, args) -> () -> Result.ok())
            .choice("what", false, "world").arg("name", ArgType.WORD).option("seed", "world seed")
            .option("rule:*", "a game rule");
        assertEquals(List.of("create world <name> [seed=…] [rule:<name>=…]"), create.usages("create"));
        CommandSpec stop = CommandSpec.query("stop", (context, args) -> Result.ok());
        assertEquals(List.of("stop"), stop.usages("stop"));
        assertEquals(1, stop.forms().size());
        CommandSpec optional = CommandSpec.query("look", (context, args) -> Result.ok())
            .choice("detail", true, "detail").optionalArg("radius", ArgType.INT);
        assertEquals(List.of("look [detail] [radius]"), optional.usages("look"));
    }

    @Test
    void argsKnowTheMatchedTypeAndOptionFamilies() {
        Args args = new Args(Map.of("what", "5"), Map.of("what", ArgType.INT),
            Map.of("rule:doDaylightCycle", "false", "seed", "1"));
        assertEquals(ArgType.INT, args.kind("what"));
        assertNull(args.kind("missing"));
        assertEquals(Map.of("doDaylightCycle", "false"), args.family("rule"));
        assertEquals(Map.of(), args.family("other"));
    }

    @Test
    void argsConvertTheirTokens() throws CommandException {
        Args args = new Args(Map.of("count", "x6", "radius", "12"), Map.of(), Map.of("timeout", "90", "every", "5t"));
        assertEquals(6, args.count("count", 1));
        assertEquals(1, args.count("missing", 1));
        assertEquals(12, args.integer("radius", 16));
        assertEquals(16, args.integer("missing", 16));
        assertEquals(Duration.seconds(90), args.durationOption("timeout"));
        assertEquals(new Duration(5), args.durationOption("every"));
        assertNull(args.durationOption("missing"));
    }
}
