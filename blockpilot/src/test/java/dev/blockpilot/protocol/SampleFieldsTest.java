package dev.blockpilot.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.blockpilot.api.CommandException;
import dev.blockpilot.api.CommandSpec;
import dev.blockpilot.api.Fields;
import dev.blockpilot.api.Result;
import dev.blockpilot.api.Row;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** {@code sample … fields=}: rows keep only the fields asked for, besides their tick, query and err. */
class SampleFieldsTest {
    private final FakeGame game = new FakeGame();
    private Engine engine;

    @BeforeEach
    void setUp() {
        CommandRegistry registry = new CommandRegistry();
        registry.register(CommandSpec.query("wide", (context, args) -> Result.ok(new Fields().put("a", 1).put("b", 2)
            .put("c", 3))));
        registry.register(CommandSpec.query("narrow", (context, args) -> Result.ok(new Fields().put("b", 5))));
        registry.register(CommandSpec.query("listing", (context, args) -> Result.ok().withRows(List.of(
            Row.of("x", new Fields().bare("name", "x"))))));
        registry.register(CommandSpec.query("broken", (context, args) -> {
            throw new CommandException("no_item", "torch");
        }));
        engine = new Engine(registry, game);
    }

    private String sample(String script) {
        CompletableFuture<Reply> reply = engine.submit(engine.openSession(), null, script);
        for (int index = 0; index < 10 && !reply.isDone(); index++) {
            game.time++;
            game.clientTicks++;
            engine.tickClient();
        }
        assertTrue(reply.isDone(), "no reply yet");
        return Formatter.text(reply.join());
    }

    @Test
    void aRowKeepsOnlyTheFieldsAskedFor() {
        assertEquals("ok samples=2\n  t1 c1 a=1 c=3\n  t2 c2 a=1 c=3", sample("sample wide every 1t x2 fields=a,c"));
    }

    @Test
    void withSeveralQueriesARowKeepsItsQueryAndAFieldItLacksIsAbsent() {
        assertEquals("ok samples=1\n  t1 c1 wide b=2\n  t1 c1 narrow b=5\n  t1 c1 listing",
            sample("sample wide and narrow and listing every 1t x1 fields=b"));
    }

    @Test
    void aFailedQueryKeepsItsError() {
        assertEquals("ok samples=1\n  t1 c1 err no_item", sample("sample broken every 1t x1 fields=a"));
    }

    @Test
    void theCountOfARowListStaysWhenAskedFor() {
        assertEquals("ok samples=1\n  t1 c1 count=1", sample("sample listing every 1t x1 fields=count"));
    }

    @Test
    void aNameThatCantBeAFieldIsASyntaxError() {
        assertTrue(sample("sample wide every 1t x1 fields=a,Hp!").startsWith(
            "err syntax col 25: fields= takes field names separated by commas, not Hp!;"));
        assertTrue(sample("sample wide every 1t x1 fields=a,").startsWith(
            "err syntax col 25: fields= takes field names separated by commas, not ;"));
    }

    @Test
    void fieldsAndClockComeInEitherOrderAndBeforeWhile() {
        assertTrue(sample("sample wide every 1t x1 fields=b clock=client").matches("ok samples=1\n  t\\d+ c\\d+ b=2"));
        assertTrue(sample("sample wide every 1t x1 clock=client fields=b").matches("ok samples=1\n  t\\d+ c\\d+ b=2"));
        assertTrue(sample("sample wide every 1t fields=b while wait 2t").matches("ok samples=\\d+\n(  t\\d+ c\\d+ b=2\n?)+"),
            sample("sample wide every 1t fields=b while wait 2t"));
    }

    @Test
    void jsonRowsAreFilteredToo() {
        CompletableFuture<Reply> reply = engine.submit(engine.openSession(), null, "sample wide every 1t x1 fields=c");
        for (int index = 0; index < 5 && !reply.isDone(); index++) {
            game.clientTicks++;
            engine.tickClient();
        }
        String json = Formatter.json(reply.join());
        assertTrue(json.contains("\"c\":3") && !json.contains("\"a\":") && !json.contains("\"b\":"), json);
    }
}
