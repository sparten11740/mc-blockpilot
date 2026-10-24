package dev.blockpilot.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.blockpilot.api.Action;
import dev.blockpilot.api.BlockpilotApi;
import dev.blockpilot.api.CommandSpec;
import dev.blockpilot.api.Fields;
import dev.blockpilot.api.Result;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
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

        // Events and JSON output.
        engine.submit(session, null, "fmt json").join();
        BlockpilotApi.emit("sample", "assembled", new Fields().put("uuid", "abc", "uuid=abc"));
        JsonObject reply = JsonParser.parseString(Formatter.json(engine.submit(session, null, "sample:info").join()))
            .getAsJsonObject();
        assertEquals(0, reply.getAsJsonArray("results").get(0).getAsJsonObject().get("speed").getAsInt());
        assertEquals("sample:assembled", reply.getAsJsonArray("events").get(0).getAsJsonObject().get("event")
            .getAsString());
        assertTrue(Formatter.text(engine.submit(session, null, "help sample:").join()).contains("sample:info"));
    }
}
