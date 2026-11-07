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
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The engine's side of agent safety, against a fake game with stand-ins for the commands it judges. */
class SafetyEngineTest {
    private final List<String> ran = new ArrayList<>();
    private final FakeGame game = new FakeGame();
    private final AtomicReference<SafetyPolicy> safety = new AtomicReference<>(ConfiguredPolicy.DEFAULT);
    private Set<String> allowed = Set.of();
    private boolean playOnly;
    private List<String> slashCommands = List.of();
    private Engine engine;

    @BeforeEach
    void setUp() {
        CommandRegistry registry = new CommandRegistry();
        new SafetyCommands(safety::get, new Trust(), false).register(registry);
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
        engine.setSafetyPolicy(safety::get);
    }

    /** The settings this test has set, as one policy the way the client swaps it in. */
    private void apply() {
        safety.set(new ConfiguredPolicy(List.of(), allowed, playOnly, false, slashCommands));
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
        playOnly = true;
        apply();
        assertEquals("skip\nerr not_allowed cmd say", run("jump; cmd say hi"));
        assertEquals("err not_allowed server", run("server time query gametime"));
        assertEquals(List.of(), ran);
    }

    @Test
    void aRefusedCommandInsideIfIsRefused() {
        playOnly = true;
        apply();
        assertEquals("err not_allowed cmd say", run("if status ok then cmd say hi"));
        assertEquals(List.of(), ran);
    }

    @Test
    void aPlayOnlyAgentSendsTheSlashCommandsListedForIt() {
        playOnly = true;
        slashCommands = List.of("Fill");
        apply();
        assertEquals("ok", run("cmd /minecraft:FILL ~ ~ ~ ~ ~ ~ stone"));
        assertEquals("err not_allowed cmd op", run("cmd op Mallory"));
        assertEquals("err not_allowed cmd execute", run("cmd execute run fill ~ ~ ~ ~ ~ ~ stone"));
        assertEquals(List.of("cmd"), ran);
    }

    @Test
    void worldFilesAndQuittingAreRefusedByWhatTheyDo() {
        playOnly = true;
        apply();
        assertEquals("err not_allowed create world", run("create world mymod-test"));
        assertEquals("err not_allowed delete world", run("delete world mymod-test"));
        assertEquals("err not_allowed quit", run("quit"));
        assertEquals(List.of(), ran);
    }

    @Test
    void perfOnlyReads() {
        playOnly = true;
        apply();
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
        playOnly = true;
        apply();
        assertEquals("err not_allowed sable:tp", run("sable:tp"));
        assertEquals(List.of(), ran);
    }

    @Test
    void underAllEveryExtensionRuns() {
        assertEquals("ok\nok\nok", run("mymod:gear; claimed:grab; sable:pull"));
        assertEquals(List.of("gear", "grab", "pull"), ran);
    }

    @Test
    void anExtensionThatIsntAllowedIsRefusedUntilItIs() {
        playOnly = true;
        apply();
        assertEquals("err not_allowed mymod:gear", run("mymod:gear"));
        assertEquals(List.of(), ran);
        allowed = Set.of("mymod");
        apply();
        assertEquals("ok", run("mymod:gear"));
    }

    @Test
    void blockpilotsOwnNamespacesNeedNoAllowing() {
        playOnly = true;
        apply();
        assertEquals("ok", run("sable:info"));
    }

    @Test
    void claimingToBeBlockpilotThroughTheApiAllowsNothing() {
        playOnly = true;
        apply();
        assertEquals("err not_allowed claimed:grab", run("claimed:grab"));
    }

    @Test
    void anotherModsCommandBesideBuiltInOnesIsRefused() {
        playOnly = true;
        apply();
        assertEquals("err not_allowed sable:pull", run("sable:pull"));
        assertEquals(List.of(), ran);
    }

    @Test
    void anExtensionThatIsntAllowedHasItsEventsDelivered() {
        playOnly = true;
        apply();
        Session session = engine.openSession();
        engine.broadcastExtension(Event.of("mymod:assembled", new Fields()));
        engine.broadcastExtension(Event.of("sable:assembled", new Fields()));
        assertEquals("ok count=0\n! mymod:assembled\n! sable:assembled",
            Formatter.text(engine.submit(session, null, "trust").join()));
    }

    @Test
    void helpMarksAnExtensionThatIsntAllowedOnlyUnderPlay() {
        assertTrue(!run("help").contains("not allowed"), run("help"));
        playOnly = true;
        apply();
        String help = run("help");
        assertTrue(help.contains("mymod (extension, not allowed: add it to allowedExtensions"), help);
        assertTrue(help.contains("claimed (extension, not allowed"), help);
        String grammar = run("help grammar");
        assertTrue(grammar.contains("sable.tp-command"), grammar);
        assertTrue(!grammar.contains("sable.pull") && !grammar.contains("mymod.gear"), grammar);
    }

    @Test
    void allowingEverythingUsesEveryExtensionAndItsEvents() {
        safety.set(AllowAllPolicy.INSTANCE);
        assertEquals("ok", run("mymod:gear"));
        assertEquals("ok", run("claimed:grab"));
        Session session = engine.openSession();
        engine.broadcastExtension(Event.of("mymod:assembled", new Fields()));
        assertEquals("ok count=0 dangerously_allow_all\n! mymod:assembled",
            Formatter.text(engine.submit(session, null, "trust").join()));
        playOnly = true;
        apply();
        assertEquals("err not_allowed mymod:gear", run("mymod:gear"));
    }

    @Test
    void playingAndExtensionsGoOn() {
        allowed = Set.of("mymod");
        apply();
        playOnly = true;
        apply();
        assertEquals("ok\nok", run("goto 0,64,0; mymod:gear"));
        assertEquals(List.of("goto", "gear"), ran);
    }
}
