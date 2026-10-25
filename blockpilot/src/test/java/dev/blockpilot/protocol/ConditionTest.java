package dev.blockpilot.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.blockpilot.api.Fields;
import dev.blockpilot.api.Lexicon;
import dev.blockpilot.api.Position;
import dev.blockpilot.api.Result;
import dev.blockpilot.api.Row;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class ConditionTest {
    /** The condition of these test tokens, as the parser builds it. */
    private static Condition tests(String... tokens) {
        return Condition.of(Arrays.stream(tokens).map(Lexicon::testShape).toList(), false);
    }

    @Test
    void comparesNumbersAndText() {
        Result block = Result.ok(new Fields().bare("id", "air"));
        assertTrue(tests("id==air").holds(block));
        assertTrue(tests("id!=stone").holds(block));
        Result status = Result.ok(new Fields().put("hp", 9.5).put("pos", Position.absolute(1, 64, 2)));
        assertTrue(tests("hp<10", "pos.y>=64").holds(status));
        assertFalse(tests("hp>10").holds(status));
    }

    @Test
    void countsRows() {
        Result none = Result.ok().withRows(List.of());
        Result one = Result.ok().withRows(List.of(Row.of("e1", new Fields())));
        assertTrue(tests("count==0").holds(none));
        assertFalse(tests("count==0").holds(one));
    }

    @Test
    void okPredicateNeedsSuccess() {
        Condition ok = Condition.of(List.of(), true);
        assertTrue(ok.holds(Result.ok()));
        assertFalse(ok.holds(Result.error("unloaded")));
    }

    @Test
    void reportsObservedValues() {
        Condition condition = tests("hp<5");
        assertEquals("hp=9.5", condition.observed(Result.ok(new Fields().put("hp", 9.5))).text());
        assertEquals("hp=none", condition.observed(null).text());
    }

    @Test
    void comparesTwoFields() {
        Result status = Result.ok(new Fields().put("hp", 14).put("max_hp", 20));
        Condition hurt = tests("hp<$max_hp");
        assertTrue(hurt.holds(status));
        assertFalse(tests("hp==$max_hp").holds(status));
        assertFalse(tests("hp<$missing").holds(status));
        assertEquals("hp=14 max_hp=20", hurt.observed(status).text());
    }

    @Test
    void aFieldLeftOutReadsAsNone() {
        // As status: state=menu and the screen in menus, neither field in a world.
        Result inWorld = Result.ok(new Fields().put("hp", 20));
        Result inMenu = Result.ok(new Fields().bare("state", "menu").bare("screen", "title"));
        assertTrue(tests("state!=menu").holds(inWorld));
        assertTrue(tests("state==none").holds(inWorld));
        assertFalse(tests("state!=menu").holds(inMenu));
        assertFalse(tests("state==none").holds(inMenu));
        assertFalse(tests("food<5").holds(inWorld));
        assertFalse(tests("food>=0").holds(inWorld));
        assertTrue(tests("screen==$vehicle").holds(inWorld));
        assertEquals("state=none", tests("state!=menu").observed(inWorld).text());
    }

    @Test
    void idsMatchWithOrWithoutTheDefaultNamespace() {
        Result block = Result.ok(new Fields().bare("id", "air"));
        assertTrue(tests("id==minecraft:air").holds(block));
        assertFalse(tests("id!=minecraft:air").holds(block));
        assertTrue(tests("id==air").holds(Result.ok(new Fields().bare("id", "minecraft:air"))));
        assertFalse(tests("id<air").holds(block));
    }

    @Test
    void listIndexesInFieldPaths() {
        Result rows = Result.ok(new Fields().put("items", List.of("a", "b")));
        assertTrue(tests("items.1==b").holds(rows));
        assertFalse(tests("items.5==b").holds(rows));
    }

    @Test
    void needsATestOrOk() {
        assertThrows(IllegalArgumentException.class, () -> Condition.of(List.of(), false));
    }
}
