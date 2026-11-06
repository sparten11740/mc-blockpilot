package dev.blockpilot.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class SlotTest {
    private static Slot slot(ArgType... types) {
        return new Slot("s", List.of(types), List.of(), false);
    }

    private static Slot keyword(String... words) {
        return new Slot("k", List.of(ArgType.KEYWORD), List.of(words), false);
    }

    private static String rejection(String name, List<ArgType> types, List<String> words) {
        return assertThrows(IllegalArgumentException.class, () -> new Slot(name, types, words, false)).getMessage();
    }

    @Test
    void aSlotTakesTheFirstTypeThatMatches() {
        Slot what = slot(ArgType.INT, ArgType.ID);
        assertEquals(ArgType.INT, what.match("5"));
        assertEquals(ArgType.ID, what.match("stone"));
        assertNull(what.match("~1,0,0"));
        Slot side = new Slot("side", List.of(ArgType.KEYWORD, ArgType.INT), List.of("head", "feet"), false);
        assertEquals(ArgType.KEYWORD, side.match("head"));
        assertEquals(ArgType.INT, side.match("3"));
        assertNull(side.match("chest"));
        assertNull(slot(ArgType.TEXT).match("anything"));
    }

    @Test
    void slotsDescribeThemselves() {
        assertEquals("down|up", keyword("down", "up").usage());
        assertEquals("[down|up]", new Slot("op", List.of(ArgType.KEYWORD), List.of("down", "up"), true).usage());
        assertEquals("down|up", keyword("down", "up").expectation());
        Slot target = new Slot("target", List.of(ArgType.POSITION, ArgType.ENTITY), List.of(), true);
        assertEquals("[target]", target.usage());
        assertEquals("<target> (a position x,y,z or ~dx,dy,dz or an entity e<id> or @<name>)", target.expectation());
        assertEquals("<s>", slot(ArgType.NUMBER).usage());
        Slot mixed = new Slot("a", List.of(ArgType.KEYWORD, ArgType.INT), List.of("head", "offhand"), false);
        assertEquals("<a> (head|offhand or a whole number)", mixed.expectation());
        assertTrue(keyword("x").isKeyword());
        assertFalse(mixed.isKeyword());
        assertTrue(slot(ArgType.TEXT).isText());
        assertFalse(mixed.isText());
    }

    @Test
    void namesAndTypesAreChecked() {
        assertEquals("a slot name is an identifier, not max-hp", rejection("max-hp", List.of(ArgType.INT), List.of()));
        assertEquals("a slot name is an identifier, not null", rejection(null, List.of(ArgType.INT), List.of()));
        assertEquals("slot s needs one or more distinct types", rejection("s", List.of(), List.of()));
        assertEquals("slot s needs one or more distinct types",
            rejection("s", List.of(ArgType.INT, ArgType.INT), List.of()));
        assertEquals("slot s: free text can't be combined with other types",
            rejection("s", List.of(ArgType.TEXT, ArgType.INT), List.of()));
        assertEquals("slot s: words go with the keyword type, and a keyword needs at least one word",
            rejection("s", List.of(ArgType.KEYWORD), List.of()));
        assertEquals("slot s: words go with the keyword type, and a keyword needs at least one word",
            rejection("s", List.of(ArgType.INT), List.of("x")));
        assertEquals("slot s: the words come before the other types",
            rejection("s", List.of(ArgType.INT, ArgType.KEYWORD), List.of("x")));
        assertEquals("slot s lists a word twice", rejection("s", List.of(ArgType.KEYWORD), List.of("x", "x")));
    }

    @Test
    void keywordWordsAreWordsTheParserCanReach() {
        for (String word : List.of("ok", "until", "every", "then", "and", "full", "a=b", "hp<5")) {
            assertEquals("slot s: " + word + " can't be a keyword (not a word, or one of and every full ok then "
                + "until)", rejection("s", List.of(ArgType.KEYWORD), List.of(word)));
        }
        keyword("0", "shift", "key.use");
    }

    @Test
    void aTypeAnEarlierOneTakesFirstIsRejected() {
        assertEquals("slot s: word takes every id, so id must come first",
            rejection("s", List.of(ArgType.WORD, ArgType.ID), List.of()));
        assertEquals("slot s: number takes every integer, so integer must come first",
            rejection("s", List.of(ArgType.NUMBER, ArgType.INT), List.of()));
        rejection("s", List.of(ArgType.ID, ArgType.NUMBER, ArgType.SECONDS), List.of());
        slot(ArgType.INT, ArgType.NUMBER, ArgType.WORD);
        slot(ArgType.ID, ArgType.ENTITY, ArgType.COUNT);
    }

    @Test
    void slotsOverlapWhenATokenFitsBoth() {
        assertTrue(slot(ArgType.INT).overlaps(slot(ArgType.NUMBER)));
        assertTrue(slot(ArgType.SECONDS).overlaps(slot(ArgType.INT)));
        assertTrue(slot(ArgType.ID).overlaps(slot(ArgType.WORD)));
        assertFalse(slot(ArgType.ID).overlaps(slot(ArgType.COUNT)));
        assertFalse(slot(ArgType.POSITION, ArgType.ENTITY).overlaps(slot(ArgType.DURATION)));
        assertTrue(keyword("a", "b").overlaps(keyword("b")));
        assertFalse(keyword("a").overlaps(keyword("b")));
        assertTrue(keyword("5").overlaps(slot(ArgType.INT)));
        assertTrue(slot(ArgType.ID).overlaps(keyword("stone")));
        assertFalse(keyword("stone").overlaps(slot(ArgType.INT)));
        assertFalse(slot(ArgType.TEXT).overlaps(slot(ArgType.WORD)));
        assertFalse(slot(ArgType.WORD).overlaps(slot(ArgType.TEXT)));
    }
}
