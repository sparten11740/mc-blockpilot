package dev.blockpilot.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.blockpilot.api.ArgType;
import dev.blockpilot.api.Args;
import dev.blockpilot.api.CommandException;
import dev.blockpilot.api.CommandSpec;
import dev.blockpilot.api.Fields;
import dev.blockpilot.api.Result;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** {@code @<name>} entities through the engine: handlers see ids, wherever and however often their command runs. */
class EntityNamesTest {
    private final FakeGame game = new FakeGame();
    private Engine engine;

    @BeforeEach
    void setUp() {
        CommandRegistry registry = new CommandRegistry();
        registry.register(CommandSpec.query("who", (context, args) -> Result.ok(id(args)))
            .arg("target", ArgType.ENTITY).option("near", "an entity", ArgType.ENTITY));
        registry.register(CommandSpec.action("chase", (context, args) -> {
            Fields fields = id(args);
            return () -> Result.ok(fields);
        }).arg("target", ArgType.ENTITY));
        registry.register(CommandSpec.query("seen", (context, args) -> Result.ok(id(args))).onServer()
            .arg("target", ArgType.ENTITY));
        registry.register(CommandSpec.query("pair", (context, args) -> Result.ok(new Fields().bare("ids",
            args.word("target") + "+" + args.values("other").stream().map(Args.Value::text)
                .collect(Collectors.joining(","))))).form(form -> form.arg("target", ArgType.ENTITY)
            .repeated(4, group -> group.keyword("with", "with").arg("other", ArgType.ENTITY))));
        registry.register(CommandSpec.query("say", (context, args) -> Result.ok(new Fields().bare("said",
            args.text("message")))).arg("message", ArgType.TEXT));
        engine = new Engine(registry, game);
        game.entities.putAll(Map.of("pip", 7, "star", 12));
    }

    private static Fields id(Args args) {
        Fields fields = new Fields().put("id", args.entity("target").id());
        if (args.hasOption("near")) {
            fields.put("near", args.option("near"));
        }
        return fields;
    }

    private CompletableFuture<Reply> run(String script) {
        return engine.submit(engine.openSession(), null, script);
    }

    private static String text(CompletableFuture<Reply> reply) {
        assertTrue(reply.isDone(), "no reply yet");
        return Formatter.text(reply.join());
    }

    private void tick(int times) {
        for (int index = 0; index < times; index++) {
            game.clientTicks++;
            engine.tickClient();
        }
    }

    @Test
    void aQuerySeesTheIdAndAnIdStaysAsWritten() {
        assertEquals("ok id=7\nok id=12", text(run("who @pip; who e12")));
        assertEquals(List.of("pip"), game.lookups);
    }

    @Test
    void anOptionTakenAsAnEntityIsResolvedToo() {
        assertEquals("ok id=7 near=e12", text(run("who @pip near=@star")));
    }

    @Test
    void anActionSeesTheId() {
        CompletableFuture<Reply> chase = run("chase @star");
        tick(1);
        assertEquals("ok id=12", text(chase));
    }

    @Test
    void aServerQueryLooksTheNameUpOnTheServersTick() {
        CompletableFuture<Reply> seen = run("seen @pip");
        tick(1);
        assertTrue(game.lookups.isEmpty());
        engine.tickServer();
        assertEquals("ok id=7", text(seen));
        assertEquals(List.of("pip"), game.lookups);
    }

    @Test
    void everyValueOfARepeatedSlotIsResolvedOncePerName() {
        assertEquals("ok e7+e12,e7", text(run("pair @pip with @star with @pip")));
        assertEquals(List.of("pip", "star"), game.lookups);
    }

    @Test
    void textKeepsItsAt() {
        assertEquals("ok hello @pip", text(run("say hello @pip")));
        assertTrue(game.lookups.isEmpty());
    }

    @Test
    void anUnknownNameFailsTheStep() {
        assertEquals("err no_entity @daisy\nskip", text(run("who @daisy; who @pip")));
    }

    @Test
    void waitLooksTheNameUpUntilItAppears() {
        CompletableFuture<Reply> wait = run("wait who @daisy until ok timeout=5s");
        tick(3);
        assertFalse(wait.isDone());
        game.entities.put("daisy", 31);
        tick(1);
        assertTrue(text(wait).startsWith("ok "), text(wait));
        assertEquals(4, game.lookups.size());
    }

    @Test
    void aSampleLooksTheNameUpOnEachRunWhereItsQueryRuns() {
        CompletableFuture<Reply> sample = run("sample who @pip and seen @star every 1t x2");
        for (int index = 0; index < 4 && !sample.isDone(); index++) {
            game.time++;
            tick(1);
        }
        assertEquals("ok samples=2\n  t1 c1 who id=7\n  t1 c1 seen id=12\n  t2 c2 who id=7\n  t2 c2 seen id=12",
            text(sample));
        assertEquals(List.of("pip", "star (server call)", "pip", "star (server call)"), game.lookups);
    }

    @Test
    void argumentsWithoutNamesAreLeftAsTheyAre() throws CommandException {
        Args args = new Args(Map.of("target", new Args.Value("e3", ArgType.ENTITY)), Map.of());
        assertSame(args, EntityNames.resolve(args, game));
    }
}
