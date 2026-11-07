package dev.blockpilot.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.blockpilot.api.Action;
import dev.blockpilot.api.ArgType;
import dev.blockpilot.api.CommandException;
import dev.blockpilot.api.CommandSpec;
import dev.blockpilot.api.Event;
import dev.blockpilot.api.Fields;
import dev.blockpilot.api.Result;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Actions run when a script ends, however it ends, before its reply. */
class EndingsTest {
    private final FakeGame game = new FakeGame();
    private final List<String> log = new ArrayList<>();
    private Engine engine;

    @BeforeEach
    void setUp() {
        CommandRegistry registry = new CommandRegistry();
        // tidy <name> [ticks]: registers an ending that answers after that many ticks; ticks 0 never answers,
        // and a name starting with "self" times itself, as Blockpilot's own endings may.
        registry.register(CommandSpec.action("tidy", (context, args) -> {
            String name = args.word("name");
            int ticks = args.integer("ticks", 2);
            context.atEnd(name.startsWith("self") ? new SelfTimedEnding(name, ticks) : new Ending(name, ticks));
            return () -> Result.ok();
        }).arg("name", ArgType.WORD).optionalArg("ticks", ArgType.INT));
        registry.register(CommandSpec.action("idle", (context, args) -> () -> {
            log.add("idle");
            return null;
        }));
        registry.register(CommandSpec.action("step", (context, args) -> () -> {
            log.add("step");
            return Result.ok();
        }));
        registry.register(CommandSpec.query("fail", (context, args) -> {
            throw new CommandException("no_item", "torch");
        }));
        registry.register(CommandSpec.action("quiet", (context, args) -> {
            context.atEnd(new Action() {
                @Override
                public Result tick() {
                    return Result.skip();
                }

                @Override
                public String label() {
                    return "quiet";
                }
            });
            return () -> Result.ok();
        }));
        engine = new Engine(registry, game);
    }

    private class Ending implements Action {
        private final String name;
        private final int ticks;
        private int ticked;

        Ending(String name, int ticks) {
            this.name = name;
            this.ticks = ticks;
        }

        @Override
        public Result tick() {
            log.add(name);
            return ticks > 0 && ++ticked >= ticks ? Result.ok(new Fields().bare("name", name)) : null;
        }

        @Override
        public void cancel() {
            log.add(name + " cancelled");
        }

        @Override
        public String label() {
            return "tidy";
        }
    }

    private final class SelfTimedEnding extends Ending implements Engine.SelfTimed {
        SelfTimedEnding(String name, int ticks) {
            super(name, ticks);
        }
    }

    private void tick(int times) {
        for (int index = 0; index < times; index++) {
            game.clientTicks++;
            engine.tickClient();
        }
    }

    private static List<String> events(CompletableFuture<Reply> reply) {
        assertTrue(reply.isDone(), "no reply yet");
        return reply.join().events().stream().map(EndingsTest::text).toList();
    }

    private static String text(Event event) {
        return event.name() + " " + event.fields().text();
    }

    @Test
    void anEndingRunsAfterTheLastStepBeforeTheReply() {
        CompletableFuture<Reply> reply = engine.submit(engine.openSession(), null, "tidy a; step");
        // The ending's first tick comes on the step the last statement ends on.
        tick(1);
        assertFalse(reply.isDone());
        tick(1);
        assertEquals(List.of("tidy a"), events(reply));
        assertEquals(List.of("step", "a", "a"), log);
        assertEquals("ok\nok\n! tidy a", Formatter.text(reply.join()));
    }

    @Test
    void anEndingRunsAfterAFailingStep() {
        CompletableFuture<Reply> reply = engine.submit(engine.openSession(), null, "tidy a 1; fail; step");
        tick(3);
        assertEquals("ok\nerr no_item torch\nskip\n! tidy a", Formatter.text(reply.join()));
    }

    @Test
    void endingsRunInTheOrderGiven() {
        CompletableFuture<Reply> reply = engine.submit(engine.openSession(), null, "tidy a 1; tidy b 1");
        tick(4);
        assertEquals(List.of("tidy a", "tidy b"), events(reply));
    }

    @Test
    void anEndingThatAnswersSkipLeavesNoEvent() {
        CompletableFuture<Reply> reply = engine.submit(engine.openSession(), null, "quiet");
        tick(2);
        assertEquals(List.of(), events(reply));
    }

    @Test
    void anEndingThatNeverAnswersIsCancelledAfterAMinuteUnlessItTimesItself() {
        CompletableFuture<Reply> reply = engine.submit(engine.openSession(), null, "tidy a 0");
        tick(1200);
        assertFalse(reply.isDone());
        tick(2);
        assertEquals(List.of("tidy err timeout 60.0s"), events(reply));
        assertTrue(log.contains("a cancelled"));
        CompletableFuture<Reply> self = engine.submit(engine.openSession(), null, "tidy self 1300");
        tick(1250);
        assertFalse(self.isDone());
        tick(60);
        assertEquals(List.of("tidy self"), events(self));
    }

    @Test
    void aStoppedScriptRunsItsEndingsWhileTheQueueGoesOn() {
        CompletableFuture<Reply> stopped = engine.submit(engine.openSession(), null, "tidy a 3; idle");
        tick(2);
        engine.submit(engine.openSession(), null, "stop").join();
        assertFalse(stopped.isDone());
        CompletableFuture<Reply> next = engine.submit(engine.openSession(), null, "step");
        tick(1);
        assertTrue(next.isDone(), "the script after the stopped one waited for its ending");
        tick(3);
        assertEquals("ok\nerr cancelled\n! tidy a", Formatter.text(stopped.join()));
    }

    @Test
    void aClosedSessionsScriptStillRunsItsEndings() {
        Session session = engine.openSession();
        CompletableFuture<Reply> closed = engine.submit(session, null, "tidy a 3; idle");
        tick(2);
        engine.closeSession(session);
        tick(4);
        assertTrue(closed.isDone());
        assertEquals(3, log.stream().filter("a"::equals).count());
    }
}
