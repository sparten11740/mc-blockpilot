package dev.blockpilot.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.blockpilot.api.EntityRef;
import dev.blockpilot.api.Event;
import dev.blockpilot.api.Fields;
import dev.blockpilot.api.Position;
import dev.blockpilot.api.Result;
import dev.blockpilot.api.Row;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Golden tests: each fixture is rendered in both formats, so text and JSON can't drift apart. */
class FormatterTest {
    private static Result status() {
        return Result.ok(new Fields()
            .put("pos", Position.absolute(104.5, 64, -33.2), "pos 104.5,64,-33.2")
            .tag("yaw", 90).tag("pitch", 20)
            .bare("dim", "overworld")
            .hidden("hp", 20.0).hidden("max_hp", 20.0).put("health", "20/20", "hp20/20")
            .tag("food", 18)
            .put("hand", "iron_pickaxe", "hand:iron_pickaxe"));
    }

    private static Reply reply(String id, Result... results) {
        return new Reply(id, List.of(results), List.of());
    }

    @Test
    void rendersStatusCompactly() {
        assertEquals("ok pos 104.5,64,-33.2 yaw90 pitch20 overworld hp20/20 food18 hand:iron_pickaxe",
            Formatter.text(reply(null, status())));
    }

    @Test
    void rendersStatusAsJson() {
        JsonObject result = JsonParser.parseString(Formatter.json(reply(null, status())))
            .getAsJsonObject().getAsJsonArray("results").get(0).getAsJsonObject();
        assertTrue(result.get("ok").getAsBoolean());
        assertEquals("[104.5,64.0,-33.2]", result.get("pos").toString());
        assertEquals("overworld", result.get("dim").getAsString());
        assertEquals(20.0, result.get("hp").getAsDouble());
        assertEquals(18, result.get("food").getAsInt());
    }

    @Test
    void rendersErrorsAndSkips() {
        Reply failed = reply("7", Result.error("no_item", "diamond_pickaxe"), Result.skip());
        assertEquals("#7 err no_item diamond_pickaxe\nskip", Formatter.text(failed));
        String json = Formatter.json(failed);
        assertEquals("{\"id\":\"7\",\"results\":[{\"ok\":false,\"err\":\"no_item\",\"arg\":\"diamond_pickaxe\"},"
            + "{\"skip\":true}],\"events\":[]}", json);
    }

    @Test
    void rendersRowsAndCaps() {
        Result look = Result.ok().withRows(List.of(
            Row.of("e812", new Fields().bare("type", "zombie").bare("ref", new EntityRef(812))
                .bare("at", Position.relative(4, 0, -2)).tag("hp", 14))), 3);
        assertEquals("ok\n  zombie e812 ~4,0,-2 hp14\n  +3 more", Formatter.text(reply(null, look)));
        JsonObject result = JsonParser.parseString(Formatter.json(reply(null, look)))
            .getAsJsonObject().getAsJsonArray("results").get(0).getAsJsonObject();
        assertEquals("~4,0,-2", result.getAsJsonArray("rows").get(0).getAsJsonObject().get("at").getAsString());
        assertEquals(3, result.get("more").getAsInt());
    }

    @Test
    void rendersEmptyRowsAsNone() {
        assertEquals("ok none", Formatter.text(reply(null, Result.ok().withRows(List.of()))));
    }

    @Test
    void rendersDiffs() {
        Row zombie = Row.of("e812", new Fields().bare("type", "zombie").bare("at", Position.relative(5, 0, -2)));
        Result changed = Result.ok().withRows(List.of(zombie))
            .withDiff(new Result.Diff(List.of(), List.of("e99"), List.of(zombie)));
        assertEquals("ok\n  - e99\n  ~ zombie ~5,0,-2", Formatter.text(reply(null, changed)));
        Result same = Result.ok().withDiff(new Result.Diff(List.of(), List.of(), List.of()));
        assertEquals("ok same", Formatter.text(reply(null, same)));
        assertTrue(Formatter.json(reply(null, same)).contains("\"same\":true"));
    }

    @Test
    void appendsEvents() {
        Reply withEvent = new Reply(null, List.of(Result.ok()), List.of(Event.of("hurt",
            new Fields().bare("amount", 4).bare("by", "skeleton").tag("hp", 16))));
        assertEquals("ok\n! hurt 4 skeleton hp16", Formatter.text(withEvent));
        assertTrue(Formatter.json(withEvent).contains("\"events\":[{\"event\":\"hurt\",\"amount\":4"));
    }
}
