package dev.blockpilot.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import dev.blockpilot.cli.Results.Result;
import java.util.List;
import org.junit.jupiter.api.Test;

class ResultsTest {
    @Test
    void readsTextResultsPastRowsEventsAndIds() {
        String reply = "#7 ok status overworld\n  row one\n! hurt 2\nerr unknown_block stoen at 1,2,3\nskip";
        assertEquals(List.of(new Result("ok", "status", "overworld"), new Result("err", "unknown_block",
            "stoen at 1,2,3"), new Result("skip", "", "")), Results.of(reply));
    }

    @Test
    void readsJsonResults() {
        String reply = "{\"id\":\"7\",\"results\":[{\"ok\":true,\"x\":1},{\"err\":\"syntax\",\"arg\":\"col 4: what\"},"
            + "{\"skip\":true}]}";
        assertEquals(List.of(new Result("ok", "", ""), new Result("err", "syntax", "col 4: what"),
            new Result("skip", "", "")), Results.of(reply));
    }

    @Test
    void summarizesFailuresAndSkips() {
        Result ok = new Result("ok", "", "");
        Result skip = new Result("skip", "", "");
        assertNull(Results.summary(List.of(ok, ok)));
        assertEquals("blockpilot: 1 err, 2 skip (a syntax error, so nothing ran)",
            Results.summary(List.of(new Result("err", "syntax", "col 12: unknown command"), skip, skip)));
        assertEquals("blockpilot: 1 err, 1 skip (nothing after the error ran)",
            Results.summary(List.of(ok, new Result("err", "unknown_block", "stoen"), skip)));
        assertEquals("blockpilot: 2 err, 0 skip",
            Results.summary(List.of(new Result("err", "a", ""), ok, new Result("err", "b", ""))));
    }

    @Test
    void findsTheQuitPid() {
        assertEquals(4242L, Results.quitPid("ok pid=4242"));
        assertEquals(4242L, Results.quitPid("#q ok pid=4242"));
        assertEquals(4242L, Results.quitPid("{\"results\":[{\"ok\":true},{\"ok\":true,\"pid\":4242}]}"));
        assertNull(Results.quitPid("ok status pid=4242 hp20"));
        assertNull(Results.quitPid("{\"results\":[{\"err\":\"x\",\"pid\":4242}]}"));
    }
}
