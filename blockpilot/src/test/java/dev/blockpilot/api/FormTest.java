package dev.blockpilot.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

class FormTest {
    private static String rejection(Consumer<Form> build) {
        Form form = new Form();
        build.accept(form);
        return assertThrows(IllegalArgumentException.class, form::freeze).getMessage();
    }

    private static void accepted(Consumer<Form> build) {
        Form form = new Form();
        build.accept(form);
        form.freeze();
    }

    @Test
    void formsPrintTheirUsage() {
        Form camera = new Form().keyword("op", "at").arg("pos", ArgType.POSITION)
            .optional(group -> group.keyword("look", "look").arg("target", ArgType.POSITION, ArgType.ENTITY));
        assertEquals("at <pos> [look <target>]", camera.usage());
        assertEquals(3, camera.elements().size());
        assertEquals("[team] <message>", new Form().optionalKeyword("to", "team").text("message").usage());
        assertEquals("[note]", new Form().optionalText("note").usage());
        assertEquals("[n]", new Form().optionalArg("n", ArgType.INT).usage());
        assertEquals("<message>", new Form().arg("message", ArgType.TEXT).usage());
        assertEquals("", new Form().usage());
    }

    @Test
    void aSlotCanTakeWordsOrTokens() {
        Form swap = new Form().arg("a", List.of("head", "offhand"), ArgType.INT)
            .optionalArg("b", List.of("feet"), ArgType.INT, ArgType.ID);
        Slot first = (Slot) swap.elements().get(0);
        assertEquals(List.of(ArgType.KEYWORD, ArgType.INT), first.types());
        assertEquals(ArgType.KEYWORD, first.match("head"));
        assertEquals(ArgType.INT, first.match("7"));
        assertEquals(ArgType.ID, ((Slot) swap.elements().get(1)).match("stone"));
        assertEquals("<a> [b]", swap.usage());
    }

    @Test
    void keywordsAndTextAreDeclaredAsSuch() {
        assertThrows(IllegalStateException.class, () -> new Form().text("a").arg("b", ArgType.INT));
        assertThrows(IllegalArgumentException.class, () -> new Form().arg("a", ArgType.KEYWORD));
        assertThrows(IllegalArgumentException.class, () -> new Form().arg("a", ArgType.INT, ArgType.KEYWORD));
        assertThrows(IllegalArgumentException.class, () -> new Form().arg("a", List.of("x"), ArgType.KEYWORD));
        assertThrows(IllegalArgumentException.class, () -> new Form().arg("a", ArgType.TEXT, ArgType.WORD));
        assertThrows(IllegalArgumentException.class, () -> new Form().arg("a", List.of("x"), ArgType.TEXT));
        assertThrows(IllegalArgumentException.class, () -> new Form().keyword("a"));
    }

    @Test
    void anOptionalGroupStartsWithARequiredPartAndHoldsNoText() {
        assertEquals("an optional group needs parts",
            assertThrows(IllegalArgumentException.class, () -> new Form().optional(group -> { })).getMessage());
        assertEquals("an optional group's first part can't be optional", assertThrows(IllegalArgumentException.class,
            () -> new Form().optional(group -> group.optionalArg("n", ArgType.INT).arg("id", ArgType.ID)))
            .getMessage());
        assertThrows(IllegalArgumentException.class,
            () -> new Form().optional(group -> group.optional(inner -> inner.keyword("x", "x"))));
        assertEquals("an optional group can't hold free text", assertThrows(IllegalArgumentException.class,
            () -> new Form().optional(group -> group.keyword("say", "say").text("what"))).getMessage());
        assertThrows(IllegalArgumentException.class, () -> new Form.Group(List.of()));
    }

    @Test
    void slotNamesAreUniqueAcrossGroups() {
        assertEquals("two slots are named pos", rejection(form -> form.arg("pos", ArgType.POSITION)
            .optional(group -> group.keyword("look", "look").arg("pos", ArgType.ENTITY))));
        assertEquals("two slots are named a", rejection(form -> form.keyword("a", "x").keyword("a", "y")));
    }

    @Test
    void anOptionalPartCantTakeWhatTheNextPartIsMeantFor() {
        assertEquals("optional [name] would take a token meant for <amount>; give them distinct types or group "
            + "them as [<a> [b]]", rejection(form -> form.optionalArg("name", ArgType.WORD).arg("amount",
            ArgType.INT)));
        assertEquals("optional [radius] would take a token meant for [limit]; give them distinct types or group "
            + "them as [<a> [b]]", rejection(form -> form.optionalArg("radius", ArgType.INT)
            .optionalArg("limit", ArgType.INT)));
        rejection(form -> form.optionalArg("n", ArgType.INT).optionalKeyword("mode", "x").arg("m", ArgType.NUMBER));
        rejection(form -> form.optionalKeyword("side", "head").arg("slot", List.of("head"), ArgType.INT));
        rejection(form -> form.optionalKeyword("unit", "5").arg("n", ArgType.INT));
        rejection(form -> form.optionalArg("n", ArgType.INT).arg("unit", List.of("5"), ArgType.ID));
        rejection(form -> form.optional(group -> group.keyword("look", "look").arg("at", ArgType.POSITION))
            .keyword("then", "look"));
        rejection(form -> form.optional(group -> group.arg("a", ArgType.INT).optionalArg("b", ArgType.INT))
            .arg("c", ArgType.INT));
        rejection(form -> form.optional(group -> group.arg("a", ArgType.INT).optionalArg("b", ArgType.INT))
            .optionalKeyword("x", "x").arg("c", ArgType.INT));
    }

    @Test
    void optionalPartsWithDistinctTypesAreFine() {
        accepted(form -> form.optionalArg("target", ArgType.POSITION, ArgType.TAG, ArgType.PATTERN, ArgType.ID)
            .optionalArg("count", ArgType.COUNT));
        accepted(form -> form.arg("what", ArgType.ID)
            .optional(range -> range.arg("radius", ArgType.INT).optionalArg("limit", ArgType.INT)));
        accepted(form -> form.optionalKeyword("to", "team").text("message"));
        accepted(form -> form.optionalArg("ticks", ArgType.DURATION));
        accepted(form -> form.arg("a", ArgType.INT).arg("b", ArgType.INT));
    }

    @Test
    void aFrozenFormCantChange() {
        Form form = new Form().keyword("op", "go");
        form.freeze();
        assertThrows(IllegalStateException.class, () -> form.arg("late", ArgType.WORD));
        assertThrows(UnsupportedOperationException.class, () -> form.elements().clear());
    }
}
