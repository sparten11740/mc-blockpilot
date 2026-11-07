package dev.blockpilot.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.blockpilot.api.Action;
import dev.blockpilot.api.ArgType;
import dev.blockpilot.api.BlockpilotApi;
import dev.blockpilot.api.CommandSpec;
import dev.blockpilot.api.Event;
import dev.blockpilot.api.Fields;
import dev.blockpilot.api.Result;
import dev.blockpilot.game.safety.SafetyCommands;
import dev.blockpilot.game.safety.Trust;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
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
        registry.register(CommandSpec.action("press", (context, args) -> {
            ran.add("press");
            return new Action() {
                @Override
                public Result tick() {
                    Refusal.raise("not_allowed", "cmd");
                    return Result.ok();
                }

                @Override
                public void cancel() {
                    ran.add("press cancelled");
                }
            };
        }));
        registry.register(CommandSpec.action("type", (context, args) -> {
            Refusal.raise("not_allowed", "cmd");
            return () -> Result.ok();
        }));
        // As the Sable compat module adds its commands: built in.
        registry.addBuiltIn(List.of(new BlockpilotApi.Registration("sable", "blockpilot", action("tp").onServer()),
            new BlockpilotApi.Registration("sable", "blockpilot",
                CommandSpec.query("info", (context, args) -> Result.ok()))));
        // Through the API, as other mods register, whatever registrant they name.
        registry.addExtensions(List.of(new BlockpilotApi.Registration("mymod", "mymod", action("gear")),
            new BlockpilotApi.Registration("claimed", "blockpilot", action("grab")),
            new BlockpilotApi.Registration("sable", "sable", action("pull"))));
        engine = new Engine(registry, game);
        engine.setOwnEventNamespaces(List.of("sable"));
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
        try {
            return Formatter.text(reply.get(10, TimeUnit.SECONDS));
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            throw new AssertionError(script + " didn't answer", e);
        }
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
    void aCommandHeldBackOnItsWayFailsTheStepThatSentIt() {
        assertEquals("err not_allowed cmd\nskip", run("press; jump"));
        assertEquals(List.of("press", "press cancelled"), ran);
        assertEquals("err not_allowed cmd", run("type"));
    }

    @Test
    void aStaleRefusalFailsNothing() {
        Refusal.raise("not_allowed", "cmd");
        assertEquals("ok", run("jump"));
    }

    @Test
    void anExtensionCommandOnTheIntegratedServerIsRefused() {
        engine.setPlayOnly(true);
        assertEquals("err not_allowed sable:tp", run("sable:tp"));
        assertEquals(List.of(), ran);
    }

    @Test
    void anExtensionThatIsntAllowedIsRefusedUntilItIs() {
        assertEquals("err not_allowed mymod:gear", run("mymod:gear"));
        assertEquals(List.of(), ran);
        engine.setAllowedExtensions(List.of("mymod"));
        assertEquals("ok", run("mymod:gear"));
    }

    @Test
    void blockpilotsOwnNamespacesNeedNoAllowing() {
        assertEquals("ok", run("sable:info"));
    }

    @Test
    void claimingToBeBlockpilotThroughTheApiAllowsNothing() {
        assertEquals("err not_allowed claimed:grab", run("claimed:grab"));
    }

    @Test
    void anotherModsCommandBesideBuiltInOnesIsRefused() {
        assertEquals("err not_allowed sable:pull", run("sable:pull"));
        assertEquals(List.of(), ran);
    }

    @Test
    void anExtensionThatIsntAllowedHasItsEventsDropped() {
        Session session = engine.openSession();
        engine.broadcastExtension(Event.of("mymod:assembled", new Fields()));
        engine.broadcastExtension(Event.of("sable:assembled", new Fields()));
        assertEquals("ok count=0\n! sable:assembled", Formatter.text(engine.submit(session, null, "trust").join()));
    }

    @Test
    void helpMarksAnExtensionThatIsntAllowed() {
        String help = run("help");
        assertTrue(help.contains("mymod (extension, not allowed: add it to allowedExtensions"), help);
        assertTrue(help.contains("claimed (extension, not allowed"), help);
        String grammar = run("help grammar");
        assertTrue(grammar.contains("sable.tp-command"), grammar);
        assertTrue(!grammar.contains("sable.pull") && !grammar.contains("mymod.gear"), grammar);
    }

    @Test
    void playingAndExtensionsGoOn() {
        engine.setAllowedExtensions(List.of("mymod"));
        engine.setPlayOnly(true);
        assertEquals("ok\nok", run("goto 0,64,0; mymod:gear"));
        assertEquals(List.of("goto", "gear"), ran);
    }
}
