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
        return Condition.of(Arrays.stream(tokens).map(Lexicon::test).toList(), false);
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
    void needsATestOrOk() {
        assertThrows(IllegalArgumentException.class, () -> Condition.of(List.of(), false));
    }
}
