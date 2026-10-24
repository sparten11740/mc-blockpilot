package dev.blockpilot.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.blockpilot.api.CommandException;
import dev.blockpilot.api.Fields;
import dev.blockpilot.api.Position;
import dev.blockpilot.api.Result;
import dev.blockpilot.api.Row;
import java.util.List;
import org.junit.jupiter.api.Test;

class ConditionTest {
    @Test
    void comparesNumbersAndText() throws CommandException {
        Result block = Result.ok(new Fields().bare("id", "air"));
        assertTrue(Condition.parse(List.of("id==air")).holds(block));
        assertTrue(Condition.parse(List.of("id!=stone")).holds(block));
        Result status = Result.ok(new Fields().put("hp", 9.5).put("pos", Position.absolute(1, 64, 2)));
        assertTrue(Condition.parse(List.of("hp<10", "and", "pos.y>=64")).holds(status));
        assertFalse(Condition.parse(List.of("hp>10")).holds(status));
    }

    @Test
    void countsRows() throws CommandException {
        Result none = Result.ok().withRows(List.of());
        Result one = Result.ok().withRows(List.of(Row.of("e1", new Fields())));
        assertTrue(Condition.parse(List.of("count==0")).holds(none));
        assertFalse(Condition.parse(List.of("count==0")).holds(one));
    }

    @Test
    void okPredicateNeedsSuccess() throws CommandException {
        Condition ok = Condition.parse(List.of("ok"));
        assertTrue(ok.holds(Result.ok()));
        assertFalse(ok.holds(Result.error("unloaded")));
    }

    @Test
    void reportsObservedValues() throws CommandException {
        Condition condition = Condition.parse(List.of("hp<5"));
        assertEquals("hp=9.5", condition.observed(Result.ok(new Fields().put("hp", 9.5))).text());
        assertEquals("hp=none", condition.observed(null).text());
    }

    @Test
    void rejectsGarbage() {
        assertThrows(CommandException.class, () -> Condition.parse(List.of("banana")));
        assertThrows(CommandException.class, () -> Condition.parse(List.of()));
    }
}
