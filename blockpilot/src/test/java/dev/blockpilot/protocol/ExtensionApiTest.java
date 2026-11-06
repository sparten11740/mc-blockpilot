package dev.blockpilot.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.blockpilot.api.Action;
import dev.blockpilot.api.ArgType;
import dev.blockpilot.api.Args;
import dev.blockpilot.api.BlockpilotApi;
import dev.blockpilot.api.CommandSpec;
import dev.blockpilot.api.Fields;
import dev.blockpilot.api.Result;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * A sample extension, registered through the public API the way another mod would, exercised through the engine.
 * BlockpilotApi is process-wide state, so everything runs in one test.
 */
class ExtensionApiTest {
    @Test
    void sampleExtensionBehavesLikeBuiltIns() {
        AtomicInteger speed = new AtomicInteger(5);
        AtomicBoolean cancelled = new AtomicBoolean();
        BlockpilotApi.register("sample", CommandSpec.query("info", (context, args) ->
            Result.ok(new Fields().put("speed", speed.get(), "speed=" + speed.get()))).field("speed", "blocks/tick")
            .example("sample:info"));
        BlockpilotApi.register("sample", CommandSpec.action("spin", (context, args) -> new Action() {
            @Override
            public Result tick() {
                return null;
            }

            @Override
            public void cancel() {
                cancelled.set(true);
            }
        }));
        // Server-thread commands, which record whether they ran inside a server tick.
        AtomicBoolean inServerTick = new AtomicBoolean();
        List<String> calls = new ArrayList<>();
        BlockpilotApi.register("sample", CommandSpec.query("depth", (context, args) -> {
            calls.add("depth " + inServerTick.get());
            return Result.ok(new Fields().put("depth", 3, "depth=3"));
        }).onServer().field("depth", "blocks"));
        BlockpilotApi.register("sample", CommandSpec.action("build", (context, args) -> {
            calls.add("start " + inServerTick.get());
            int[] remaining = {2};
            return () -> {
                calls.add("tick " + inServerTick.get());
                return --remaining[0] <= 0 ? Result.ok() : null;
            };
        }).onServer());

        GuideSnippets.register();
        // A repeated group, read back in order.
        BlockpilotApi.register("test", CommandSpec.query("path", (context, args) -> {
            List<String> via = args.values("via").stream().map(Args.Value::text).toList();
            return Result.ok(new Fields().put("via", via.size(), "via=" + String.join(";", via)));
        }).form(form -> form.arg("pos", ArgType.POSITION).repeated(4, group -> group.keyword("through", "via")
            .arg("via", ArgType.POSITION))));
        // An integration compiled against the blockpilot-api 2.0.0 jar of the v1.0.0 release.
        try {
            Class.forName("example.fixture.ApiFixture").getMethod("register").invoke(null);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("the 2.0.0 fixture didn't register", e);
        }

        CommandRegistry registry = new CommandRegistry();
        FakeGame game = new FakeGame();
        Engine engine = new Engine(registry, game);
        List<BlockpilotApi.Registration> registrations = BlockpilotApi.freeze(engine::broadcast);
        registry.addExtensions(registrations);

        // Registering after loading finished is refused.
        BlockpilotApi.register("late", CommandSpec.query("x", (context, args) -> Result.ok()));
        assertTrue(BlockpilotApi.freeze(engine::broadcast).stream()
            .noneMatch(registration -> registration.namespace().equals("late")));

        Session session = engine.openSession();
        assertEquals("ok speed=5", Formatter.text(engine.submit(session, null, "sample:info").join()));
        CompletableFuture<Reply> flaps = engine.submit(session, null, "mymod:flaps set 15 20t side=left");
        engine.tickClient();
        assertEquals("ok", Formatter.text(flaps.join()));
        assertEquals("err syntax col 20: side= takes left|right, got up; mymod:flaps set <degrees> [ticks] "
            + "[side=left|right]", Formatter.text(engine.submit(session, null, "mymod:flaps set 15 side=up")
            .join()));

        // wait … until works on extension queries.
        CompletableFuture<Reply> wait = engine.submit(session, null, "wait sample:info until speed<1 timeout=1s");
        for (int tick = 0; tick < 5 && !wait.isDone(); tick++) {
            speed.decrementAndGet();
            engine.tickClient();
        }
        assertTrue(Formatter.text(wait.join()).startsWith("ok"), Formatter.text(wait.join()));

        // stop cancels extension actions through their cancel hook.
        CompletableFuture<Reply> spin = engine.submit(session, null, "sample:spin");
        engine.tickClient();
        assertFalse(spin.isDone());
        engine.submit(engine.openSession(), null, "stop").join();
        assertEquals("err cancelled", Formatter.text(spin.join()));
        assertTrue(cancelled.get());

        // A server-thread action starts and ticks only on server ticks, once the gate passed on a client tick.
        Runnable tickBoth = () -> {
            game.time++;
            engine.tickClient();
            inServerTick.set(true);
            engine.tickServer();
            inServerTick.set(false);
        };
        CompletableFuture<Reply> build = engine.submit(session, null, "sample:build");
        for (int tick = 0; tick < 5 && !build.isDone(); tick++) {
            tickBoth.run();
        }
        assertEquals("ok", Formatter.text(build.join()));
        assertEquals(List.of("start true", "tick true", "tick true"), calls);

        // Server-clock sampling of a server-thread extension query: rows 40 server ticks apart.
        calls.clear();
        CompletableFuture<Reply> sample = engine.submit(session, null, "sample sample:depth every 40t x3 clock=server");
        for (int tick = 0; tick < 200 && !sample.isDone(); tick++) {
            tickBoth.run();
        }
        String samples = Formatter.text(sample.join());
        assertTrue(samples.startsWith("ok samples=3"), samples);
        List<Long> ticks = new ArrayList<>();
        Matcher matcher = Pattern.compile("t(\\d+) depth=3").matcher(samples);
        while (matcher.find()) {
            ticks.add(Long.parseLong(matcher.group(1)));
        }
        assertEquals(List.of(40L, 40L), List.of(ticks.get(1) - ticks.get(0), ticks.get(2) - ticks.get(1)));
        assertTrue(calls.stream().allMatch(call -> call.equals("depth true")), calls.toString());

        // Without an integrated server the gate refuses, and the handler is never called.
        calls.clear();
        game.serverGate = "no_server";
        CompletableFuture<Reply> refused = engine.submit(session, null, "sample:build");
        tickBoth.run();
        assertEquals("err no_server", Formatter.text(refused.join()));
        assertEquals(List.of(), calls);
        game.serverGate = null;

        // The examples in docs/extending.md work as documented.
        assertEquals("ok extended=true", Formatter.text(engine.submit(session, null, "mymod:gear").join()));
        CompletableFuture<Reply> guideBuild = engine.submit(session, null, "mymod:build hangar");
        for (int tick = 0; tick < 5 && !guideBuild.isDone(); tick++) {
            tickBoth.run();
        }
        assertEquals("ok blocks=42", Formatter.text(guideBuild.join()));
        GuideSnippets.emit("abc");
        assertEquals("ok extended=true\n! mymod:assembled uuid=abc",
            Formatter.text(engine.submit(session, null, "mymod:gear").join()));

        // Events and JSON output.
        engine.submit(session, null, "fmt json").join();
        BlockpilotApi.emit("sample", "assembled", new Fields().put("uuid", "abc", "uuid=abc"));
        JsonObject reply = JsonParser.parseString(Formatter.json(engine.submit(session, null, "sample:info").join()))
            .getAsJsonObject();
        assertEquals(0, reply.getAsJsonArray("results").get(0).getAsJsonObject().get("speed").getAsInt());
        assertEquals("sample:assembled", reply.getAsJsonArray("events").get(0).getAsJsonObject().get("event")
            .getAsString());
        assertTrue(Formatter.text(engine.submit(session, null, "help sample:").join()).contains("sample:info"));

        engine.submit(session, null, "fmt text").join();
        assertEquals("ok via=", Formatter.text(engine.submit(session, null, "test:path 1,2,3").join()));
        assertEquals("ok via=4,5,6;7,8,9",
            Formatter.text(engine.submit(session, null, "test:path 1,2,3 via 4,5,6 via 7,8,9").join()));
        String tooMany = Formatter.text(engine.submit(session, null,
            "test:path 1,2,3 via 1,1,1 via 2,2,2 via 3,3,3 via 4,4,4 via 5,5,5").join());
        assertTrue(tooMany.startsWith("err syntax col 57: at most 4 via groups"), tooMany);
        assertTrue(Formatter.text(engine.submit(session, null, "help test:path").join())
            .contains("test:path <pos> [via <via>]…"));
        assertEquals("ok hello ann", Formatter.text(engine.submit(session, null, "fixture:greet ann").join()));
        assertEquals("ok HELLO ann", Formatter.text(engine.submit(session, null, "fixture:greet ann loud").join()));
    }
}
