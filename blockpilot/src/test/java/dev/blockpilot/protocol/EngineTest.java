package dev.blockpilot.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.blockpilot.api.Action;
import dev.blockpilot.api.ArgType;
import dev.blockpilot.api.BlockpilotApi;
import dev.blockpilot.api.CommandException;
import dev.blockpilot.api.CommandSpec;
import dev.blockpilot.api.Event;
import dev.blockpilot.api.Fields;
import dev.blockpilot.api.Result;
import dev.blockpilot.api.Row;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The engine against a fake game: tasks run immediately, ticks are driven by the test. */
class EngineTest {
    private final List<String> log = new ArrayList<>();
    private final AtomicInteger counter = new AtomicInteger();
    private final FakeGame game = new FakeGame();
    private Engine engine;

    @BeforeEach
    void setUp() {
        CommandRegistry registry = new CommandRegistry();
        registry.register(CommandSpec.query("counter", (context, args) ->
            Result.ok(new Fields().put("value", counter.get()))).field("value", "the counter"));
        registry.register(CommandSpec.query("items", (context, args) -> Result.ok().withRows(List.of(
            Row.of("a", new Fields().bare("name", "a").put("n", counter.get()))))).diffable());
        registry.register(CommandSpec.query("fail", (context, args) -> {
            throw new CommandException("no_item", "torch");
        }));
        registry.register(CommandSpec.action("walk", (context, args) -> new Steps(args.integer("ticks", 3),
            args.word("name"))).arg("name", ArgType.WORD).optionalArg("ticks", ArgType.INT));
        registry.register(CommandSpec.query("world", (context, args) -> Result.ok()).needsWorld());
        registry.register(CommandSpec.query("boom", (context, args) -> {
            throw new IllegalStateException("boom");
        }));
        registry.register(CommandSpec.query("probe", (context, args) ->
            Result.ok(new Fields().put("value", counter.get()))).onServer().field("value", "the counter"));
        registry.register(CommandSpec.action("spin", (context, args) -> new Steps(1000, "spin")).onServer());
        registry.register(CommandSpec.action("roll", (context, args) -> new Steps(args.integer("ticks", 3),
            args.word("name"))).onServer().arg("name", ArgType.WORD).optionalArg("ticks", ArgType.INT));
        engine = new Engine(registry, game);
    }

    /** Takes a number of ticks, counting each, then succeeds. */
    private final class Steps implements Action {
        private final String name;
        private int remaining;

        Steps(int ticks, String name) {
            this.remaining = ticks;
            this.name = name;
        }

        @Override
        public Result tick() {
            log.add(name);
            counter.incrementAndGet();
            return --remaining <= 0 ? Result.ok() : null;
        }

        @Override
        public void cancel() {
            log.add(name + " cancelled");
        }
    }

    private String text(CompletableFuture<Reply> reply) {
        return Formatter.text(reply.join());
    }

    private void tick(int times) {
        for (int index = 0; index < times; index++) {
            engine.tickClient();
        }
    }

    @Test
    void queriesAnswerImmediately() {
        Session session = engine.openSession();
        CompletableFuture<Reply> reply = engine.submit(session, null, "counter; counter");
        assertTrue(reply.isDone());
        assertEquals("ok value=0\nok value=0", text(reply));
    }

    @Test
    void failureSkipsTheRest() {
        Session session = engine.openSession();
        assertEquals("err no_item torch\nskip", text(engine.submit(session, null, "fail; counter")));
        assertEquals("err syntax col 1: unknown command nope; try help\nskip",
            text(engine.submit(session, null, "nope; counter")));
    }

    @Test
    void aSoftCommandsErrorDoesNotStopTheScript() {
        Session session = engine.openSession();
        assertEquals("err no_item torch\nok value=0\nerr no_item torch\nskip",
            text(engine.submit(session, null, "?fail; counter; fail; counter")));
    }

    @Test
    void stopEndsAScriptEvenAtASoftCommand() {
        Session session = engine.openSession();
        CompletableFuture<Reply> walk = engine.submit(session, null, "?walk a 100; counter");
        tick(2);
        engine.submit(engine.openSession(), null, "stop").join();
        assertEquals("err cancelled\nskip", text(walk));
    }

    @Test
    void actionsRunTickByTickInQueueOrder() {
        Session first = engine.openSession();
        Session second = engine.openSession();
        CompletableFuture<Reply> walk = engine.submit(first, "1", "walk a 2; counter");
        CompletableFuture<Reply> jump = engine.submit(second, "2", "walk b 1");
        tick(1);
        assertFalse(walk.isDone());
        assertFalse(jump.isDone());
        tick(1);
        // The next queued action starts in the tick the previous one finished.
        assertEquals("#1 ok\nok value=2", text(walk));
        assertEquals("#2 ok", text(jump));
        assertEquals(List.of("a", "a", "b"), log);
    }

    @Test
    void actionsOfBothThreadsRunInQueueOrder() {
        CompletableFuture<Reply> walk = engine.submit(engine.openSession(), null, "walk a 2");
        CompletableFuture<Reply> roll = engine.submit(engine.openSession(), null, "roll s 2");
        CompletableFuture<Reply> jump = engine.submit(engine.openSession(), null, "walk b 1");
        for (int index = 0; index < 6; index++) {
            engine.tickClient();
            engine.tickServer();
        }
        assertEquals(List.of("a", "a", "s", "s", "b"), log);
        assertEquals("ok", text(walk));
        assertEquals("ok", text(roll));
        assertEquals("ok", text(jump));
    }

    @Test
    void stopCancelsQueuedActions() {
        Session first = engine.openSession();
        Session second = engine.openSession();
        CompletableFuture<Reply> walk = engine.submit(first, null, "walk a 100; counter");
        tick(2);
        CompletableFuture<Reply> stop = engine.submit(second, null, "stop");
        assertEquals("ok stopped 1", text(stop));
        assertEquals("err cancelled\nskip", text(walk));
        assertTrue(log.contains("a cancelled"));
        assertTrue(game.releases.get() > 0);
    }

    @Test
    void timeoutStopsAnAction() {
        Session session = engine.openSession();
        CompletableFuture<Reply> walk = engine.submit(session, null, "walk a 1000 timeout=5t");
        tick(10);
        assertTrue(text(walk).startsWith("err timeout"));
    }

    @Test
    void waitUntilPollsTheQueryEveryTick() {
        Session session = engine.openSession();
        CompletableFuture<Reply> wait = engine.submit(session, null, "wait counter until value>=3 timeout=2s");
        for (int index = 0; index < 3 && !wait.isDone(); index++) {
            counter.incrementAndGet();
            tick(1);
        }
        assertTrue(wait.isDone());
        assertTrue(text(wait).startsWith("ok"), text(wait));
        assertTrue(text(wait).contains("value=3"));
    }

    @Test
    void waitUntilTimesOutWithLastValues() {
        Session session = engine.openSession();
        CompletableFuture<Reply> wait = engine.submit(session, null, "wait counter until value>5 timeout=3t");
        tick(5);
        assertEquals("err timeout 0.15s value=0", text(wait));
    }

    @Test
    void serverClockWaitReportsTheServerTick() {
        Session session = engine.openSession();
        CompletableFuture<Reply> wait = engine.submit(session, null, "wait probe until value>=2 clock=server");
        tick(1);
        for (int index = 0; index < 10 && !wait.isDone(); index++) {
            game.time = 100 + index;
            counter.incrementAndGet();
            engine.tickServer();
        }
        assertEquals("ok 0.05s t101 value=2", text(wait));
    }

    @Test
    void consecutiveServerQueriesAnswerInOneServerTick() {
        Session session = engine.openSession();
        CompletableFuture<Reply> reply = engine.submit(session, null, "probe; probe; probe; counter; probe");
        tick(1);
        assertFalse(reply.isDone());
        counter.set(7);
        engine.tickServer();
        assertFalse(reply.isDone());
        // The client query and the server query after it each need their own tick.
        tick(1);
        engine.tickServer();
        assertEquals("ok value=7\nok value=7\nok value=7\nok value=7\nok value=7", text(reply));
    }

    @Test
    void serverActionsAreCancelledOnTheServerThread() {
        Session first = engine.openSession();
        Session second = engine.openSession();
        CompletableFuture<Reply> spin = engine.submit(first, null, "spin");
        // The gate is checked on a client tick, then the action starts and ticks on server ticks.
        tick(1);
        engine.tickServer();
        assertTrue(log.contains("spin"));
        assertEquals("ok stopped 1", text(engine.submit(second, null, "stop")));
        assertEquals("err cancelled", text(spin));
        assertFalse(log.contains("spin cancelled"));
        game.serverTasks.forEach(Runnable::run);
        assertTrue(log.contains("spin cancelled"));
    }

    @Test
    void waitRejectsActionsAsConditions() {
        Session session = engine.openSession();
        CompletableFuture<Reply> wait = engine.submit(session, null, "wait walk a until ok");
        tick(1);
        assertEquals("err syntax col 6: walk is not a read-only command", text(wait));
    }

    @Test
    void waitForTicks() {
        Session session = engine.openSession();
        CompletableFuture<Reply> wait = engine.submit(session, null, "wait 3t");
        tick(2);
        assertFalse(wait.isDone());
        tick(1);
        assertEquals("ok", text(wait));
    }

    @Test
    void sampleCollectsOneRowPerRun() {
        Session session = engine.openSession();
        CompletableFuture<Reply> sample = engine.submit(session, null, "sample counter every 2t x3");
        for (int index = 0; index < 10 && !sample.isDone(); index++) {
            game.time++;
            counter.incrementAndGet();
            tick(1);
        }
        assertEquals("ok samples=3\n  t1 value=1\n  t3 value=3\n  t5 value=5", text(sample));
    }

    @Test
    void sampleRejectsActionsAndHugeCounts() {
        Session session = engine.openSession();
        CompletableFuture<Reply> action = engine.submit(session, null, "sample walk a every 1t x2");
        CompletableFuture<Reply> huge = engine.submit(session, null, "sample counter every 1t x5000");
        tick(2);
        assertEquals("err syntax col 8: walk is not a read-only command", text(action));
        assertEquals("err syntax col 25: a sample is taken x1 to x1000 times, not x5000; sample <query> every "
            + "<duration> x<n> [clock=server|client] | sample <query> every <duration> [x<n>] [clock=server|client] "
            + "while <action>", text(huge));
    }

    @Test
    void aSampleRunsBesideItsAction() {
        Session session = engine.openSession();
        CompletableFuture<Reply> walk = engine.submit(session, null, "sample counter every 1t while walk a 3");
        for (int index = 0; index < 10 && !walk.isDone(); index++) {
            game.time++;
            tick(1);
        }
        // Each sample is taken before the action's tick, so the first shows the state before it started.
        assertEquals("ok samples=3\n  t1 value=0\n  t2 value=1\n  t3 value=2", text(walk));
        CompletableFuture<Reply> capped = engine.submit(session, null, "sample counter every 2t x2 while walk b 6");
        tick(10);
        assertEquals(List.of(3, 5), capped.join().results().get(0).rows().stream()
            .map(row -> ((Number) row.fields().get("value")).intValue()).toList());
    }

    @Test
    void aSampleBesideAFailingActionKeepsItsRows() {
        Session session = engine.openSession();
        CompletableFuture<Reply> walk = engine.submit(session, null,
            "sample counter every 1t while walk a 100 timeout=2t; counter");
        tick(5);
        Result result = walk.join().results().get(0);
        assertEquals("timeout", result.code());
        assertEquals(2, result.rows().size());
        assertEquals("skip", text(walk).lines().reduce((first, second) -> second).orElseThrow());
    }

    @Test
    void aSampleThatCrashesCancelsItsAction() {
        CompletableFuture<Reply> walk = engine.submit(engine.openSession(), null,
            "sample boom every 1t while walk a 3");
        tick(2);
        assertEquals("err internal sample boom samples=0", text(walk));
        assertTrue(log.contains("a cancelled"), log.toString());
    }

    @Test
    void aServerQueryIsSampledOnTheServerClockBesideAClientAction() {
        Session session = engine.openSession();
        // The action starts and takes its first step on the client; the server takes its first sample after that.
        CompletableFuture<Reply> walk = engine.submit(session, null, "sample probe every 1t while walk a 3");
        for (int index = 0; index < 5 && !walk.isDone(); index++) {
            engine.tickServer();
            engine.tickClient();
        }
        assertEquals(2, walk.join().results().get(0).rows().size());
        CompletableFuture<Reply> stopped = engine.submit(session, null, "sample counter every 1t while walk c 100");
        tick(2);
        engine.submit(engine.openSession(), null, "stop").join();
        assertEquals("err cancelled", text(stopped));
    }

    @Test
    void repeatedObservationsReportOnlyChanges() {
        Session session = engine.openSession();
        assertEquals("ok\n  a n=0", text(engine.submit(session, null, "items")));
        assertEquals("ok same", text(engine.submit(session, null, "items")));
        counter.incrementAndGet();
        assertEquals("ok\n  ~ a n=1", text(engine.submit(session, null, "items")));
        assertEquals("ok\n  a n=1", text(engine.submit(session, null, "items full")));
    }

    @Test
    void actionsWaitWhileTheGameLoadsButQueriesAnswer() {
        Session session = engine.openSession();
        game.ready = false;
        CompletableFuture<Reply> walk = engine.submit(session, null, "walk a 1");
        tick(3);
        assertFalse(walk.isDone());
        assertEquals("ok value=0", text(engine.submit(session, null, "counter")));
        game.ready = true;
        tick(1);
        assertEquals("ok", text(walk));
    }

    @Test
    void gateStopsCommandsOutsideAWorld() {
        Session session = engine.openSession();
        game.worldGate = "not_in_world";
        assertEquals("err not_in_world", text(engine.submit(session, null, "world")));
    }

    @Test
    void jsonFormatIsPerSession() {
        Session json = engine.openSession();
        Session text = engine.openSession();
        engine.submit(json, null, "fmt json").join();
        assertEquals(Session.Format.JSON, json.format());
        assertEquals(Session.Format.TEXT, text.format());
    }

    @Test
    void eventsArriveWithTheNextReply() {
        Session session = engine.openSession();
        engine.broadcast(Event.of("hurt", new Fields().bare("amount", 4)));
        assertEquals("ok value=0\n! hurt 4", text(engine.submit(session, null, "counter")));
        assertEquals("ok value=0", text(engine.submit(session, null, "counter")));
    }

    @Test
    void closingASessionCancelsItsScripts() {
        Session session = engine.openSession();
        CompletableFuture<Reply> walk = engine.submit(session, null, "walk a 100");
        tick(1);
        engine.closeSession(session);
        assertEquals("err cancelled", text(walk));
    }

    @Test
    void extensionsAreNamespacedAndIsolated() {
        CommandRegistry registry = new CommandRegistry();
        registry.addExtensions(List.of(
            new BlockpilotApi.Registration("sable", "sable", CommandSpec.query("info", (context, args) -> {
                throw new IllegalStateException("boom");
            })),
            new BlockpilotApi.Registration("sable", "blockpilot", CommandSpec.query("info",
                (context, args) -> Result.ok()))));
        Engine isolated = new Engine(registry, new FakeGame());
        Session session = isolated.openSession();
        String reply = Formatter.text(isolated.submit(session, null, "sable:info; help").join());
        assertTrue(reply.startsWith("err ext_failed sable:info boom\nskip"), reply);
        assertEquals(1, registry.namespace("sable").size());
        assertTrue(Formatter.text(isolated.submit(session, null, "help sable").join()).contains("sable:info"));
    }

    @Test
    void assertPassesOrStopsTheScript() {
        Session session = engine.openSession();
        assertEquals("ok value=0\nerr assert_failed value=0\nskip",
            text(engine.submit(session, null, "assert counter value==0; assert counter value>0; counter")));
        assertEquals("err assert_failed value=0\nok value=0",
            text(engine.submit(session, null, "?assert counter value>0; counter")));
    }

    @Test
    void assertReportsTheQueryError() {
        assertEquals("err assert_failed fail no_item",
            text(engine.submit(engine.openSession(), null, "assert fail ok")));
    }

    @Test
    void anIfThatHoldsRunsItsCommandInPlace() {
        Session session = engine.openSession();
        CompletableFuture<Reply> reply = engine.submit(session, null, "if counter value==0 then walk a 2; counter");
        assertFalse(reply.isDone());
        tick(2);
        assertEquals("ok\nok value=2", text(reply));
        assertEquals(List.of("a", "a"), log);
    }

    @Test
    void anIfThatDoesNotHoldReportsTheObservedValues() {
        CompletableFuture<Reply> reply = engine.submit(engine.openSession(), null,
            "if counter value>5 then walk a 2; counter");
        tick(1);
        assertEquals("ok false value=0\nok value=0", text(reply));
        assertTrue(log.isEmpty());
    }

    @Test
    void aSoftIfMakesItsCommandSoft() {
        assertEquals("err no_item torch\nok value=0",
            text(engine.submit(engine.openSession(), null, "?if counter value==0 then fail; counter")));
    }

    @Test
    void checksRunTheirQueryOnItsThread() {
        CompletableFuture<Reply> reply = engine.submit(engine.openSession(), null, "assert probe value==3");
        tick(1);
        assertFalse(reply.isDone());
        counter.set(3);
        engine.tickServer();
        assertEquals("ok value=3", text(reply));
    }

    @Test
    void checksAreGatedByTheirQuery() {
        game.worldGate = "not_in_world";
        assertEquals("err not_in_world", text(engine.submit(engine.openSession(), null, "assert world ok")));
    }

    @Test
    void requestIdsFollowTheGrammar() {
        Session session = engine.openSession();
        assertEquals("err syntax col 2: missing a request id after #", text(engine.submit(session, "", "status")));
        assertEquals("err syntax col 3: a request id can't hold a tab or ;", text(engine.submit(session, "7;status",
            "")));
        assertEquals("err syntax col 4: a request id can't hold a tab or ;", text(engine.submit(session, "a1\tb",
            "status")));
    }

    @Test
    void aSyntaxErrorRunsNothing() {
        CompletableFuture<Reply> reply = engine.submit(engine.openSession(), null, "walk a 1; nope; walk b x");
        assertTrue(reply.isDone());
        assertEquals("skip\nerr syntax col 1: unknown command nope; try help\nerr syntax col 8: <ticks> (a whole "
            + "number) expected, got x; walk <name> [ticks]", text(reply));
        tick(2);
        assertTrue(log.isEmpty());
    }

    @Test
    void aSoftStepsSyntaxErrorIsItsOwnResult() {
        assertEquals("err syntax col 2: unknown command nope; try help\nok value=0",
            text(engine.submit(engine.openSession(), null, "?nope; counter")));
    }

    @Test
    void constructsRunTheirQueryOnItsThread() {
        CompletableFuture<Reply> reply = engine.submit(engine.openSession(), null, "wait probe until value==2");
        tick(1);
        counter.set(2);
        engine.tickServer();
        assertTrue(text(reply).startsWith("ok "), text(reply));
    }
}
