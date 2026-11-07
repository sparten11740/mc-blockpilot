package dev.blockpilot.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.blockpilot.api.ArgType;
import dev.blockpilot.api.BlockpilotApi;
import dev.blockpilot.api.CommandSpec;
import dev.blockpilot.api.Result;
import dev.blockpilot.game.safety.SafetyCommands;
import dev.blockpilot.game.safety.Trust;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The engine's side of agent safety, against a fake game with stand-ins for the commands it judges. */
class SafetyEngineTest {
    private final List<String> ran = new ArrayList<>();
    private final FakeGame game = new FakeGame();
    private Engine engine;

    @BeforeEach
    void setUp() {
        CommandRegistry registry = new CommandRegistry();
        new SafetyCommands(new Trust(List.of(), false)).register(registry);
        registry.register(CommandSpec.query("status", (context, args) -> Result.ok()));
        registry.register(action("jump"));
        registry.register(action("goto").arg("to", ArgType.POSITION));
        registry.register(action("cmd").arg("command", ArgType.TEXT));
        registry.register(action("server").arg("command", ArgType.TEXT));
        registry.register(action("quit"));
        registry.register(action("create").keyword("what", "world").arg("name", ArgType.WORD));
        registry.register(action("delete").keyword("what", "world").arg("folder", ArgType.TEXT));
        registry.register(action("perf").form(form -> form.keyword("op", "start", "stop", "status"))
            .form(form -> form.arg("for", ArgType.DURATION)));
        registry.addExtensions(List.of(new BlockpilotApi.Registration("mymod", "mymod",
            action("gear").freeze())));
        engine = new Engine(registry, game);
    }

    private CommandSpec action(String name) {
        return CommandSpec.action(name, (context, args) -> {
            ran.add(name);
            return () -> Result.ok();
        });
    }

    private String run(String script) {
        CompletableFuture<Reply> reply = engine.submit(engine.openSession(), null, script);
        for (int tick = 0; tick < 5 && !reply.isDone(); tick++) {
            game.clientTicks++;
            engine.tickClient();
        }
        return Formatter.text(reply.join());
    }

    @Test
    void trustOnlyReads() {
        assertTrue(run("trust add Mallory").startsWith("err syntax"), run("trust add Mallory"));
        assertTrue(run("trust").startsWith("ok count=0"), run("trust"));
    }

    @Test
    void everythingRunsByDefault() {
        assertEquals("ok\nok", run("jump; cmd say hi"));
        assertEquals(List.of("jump", "cmd"), ran);
    }

    @Test
    void aPlayOnlyScriptWithARefusedCommandRunsNoStep() {
        engine.setPlayOnly(true);
        assertEquals("skip\nerr not_allowed cmd", run("jump; cmd say hi"));
        assertEquals("err not_allowed server", run("server time query gametime"));
        assertEquals(List.of(), ran);
    }

    @Test
    void aRefusedCommandInsideIfIsRefused() {
        engine.setPlayOnly(true);
        assertEquals("err not_allowed cmd", run("if status ok then cmd say hi"));
        assertEquals(List.of(), ran);
    }

    @Test
    void worldFilesAndQuittingAreRefusedByWhatTheyDo() {
        engine.setPlayOnly(true);
        assertEquals("err not_allowed create world", run("create world mymod-test"));
        assertEquals("err not_allowed delete world", run("delete world mymod-test"));
        assertEquals("err not_allowed quit", run("quit"));
        assertEquals(List.of(), ran);
    }

    @Test
    void perfOnlyReads() {
        engine.setPlayOnly(true);
        assertEquals("err not_allowed perf", run("perf 1s"));
        assertEquals("err not_allowed perf", run("perf start"));
        assertEquals("ok", run("perf status"));
    }

    @Test
    void playingAndExtensionsGoOn() {
        engine.setPlayOnly(true);
        assertEquals("ok\nok", run("goto 0,64,0; mymod:gear"));
        assertEquals(List.of("goto", "gear"), ran);
    }
}
