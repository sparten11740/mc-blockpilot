package dev.blockpilot.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.blockpilot.api.Action;
import dev.blockpilot.api.ArgType;
import dev.blockpilot.api.CommandSpec;
import dev.blockpilot.api.Duration;
import dev.blockpilot.api.Result;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The parser against commands that use every kind of form element; one test per production or error path. */
class ParserTest {
    private static final CommandSpec.QueryHandler QUERY = (context, args) -> Result.ok();
    private static final CommandSpec.ActionHandler ACTION = (context, args) -> (Action) () -> Result.ok();
    private final CommandRegistry registry = new CommandRegistry();
    private final Parser parser = new Parser(registry);

    ParserTest() {
        registry.register(CommandSpec.query("status", QUERY));
        registry.register(CommandSpec.query("look", QUERY).optionalArg("radius", ArgType.INT).diffable());
        registry.register(CommandSpec.query("inv", QUERY).choice("detail", true, "detail").diffable());
        registry.register(CommandSpec.query("block", QUERY).arg("pos", ArgType.POSITION).option("side", "where"));
        registry.register(CommandSpec.query("probe", QUERY).onServer());
        registry.register(CommandSpec.query("note", QUERY).form(form -> form.optionalText("text")));
        registry.register(CommandSpec.action("mine", ACTION)
            .optionalArg("target", ArgType.POSITION, ArgType.TAG, ArgType.PATTERN, ArgType.ID)
            .optionalArg("count", ArgType.COUNT).option("near", "distance"));
        registry.register(CommandSpec.action("mouse", ACTION)
            .form(form -> form.keyword("op", "down", "up").keyword("button", "left", "right", "middle"))
            .form(form -> form.keyword("op", "click").keyword("button", "left", "right", "middle")
                .optionalArg("ticks", ArgType.DURATION))
            .form(form -> form.keyword("op", "move").arg("dx", ArgType.NUMBER).arg("dy", ArgType.NUMBER)
                .optionalArg("ticks", ArgType.DURATION)));
        registry.register(CommandSpec.action("camera", ACTION)
            .form(form -> form.keyword("op", "at").arg("pos", ArgType.POSITION)
                .optional(group -> group.keyword("look", "look").arg("target", ArgType.POSITION, ArgType.ENTITY)))
            .form(form -> form.keyword("op", "reset")));
        registry.register(CommandSpec.action("chat", ACTION).arg("text", ArgType.TEXT));
        registry.register(CommandSpec.action("server", ACTION).arg("command", ArgType.TEXT).option("as", "who")
            .onServer());
        registry.register(CommandSpec.action("create", ACTION).choice("what", false, "world")
            .arg("name", ArgType.WORD).choice("preset", true, "flat", "void").option("seed", "seed")
            .option("rule:*", "game rule"));
        registry.register(CommandSpec.query("nest", QUERY)
            .form(form -> form.optional(outer -> outer.optional(inner -> inner.keyword("x", "x"))
                .arg("n", ArgType.INT))));
        registry.register(CommandSpec.query("solo", QUERY).form(form -> { }).form(form -> form.keyword("op", "a")));
        registry.register(CommandSpec.query("duo", QUERY).form(form -> form.keyword("op", "a")).form(form -> { }));
        registry.register(CommandSpec.query("twin", QUERY)
            .form(form -> form.keyword("op", "go"))
            .form(form -> form.keyword("op", "go").arg("n", ArgType.INT)));
    }

    private Parser.Line line(String script) {
        List<Parser.Line> lines = parser.script(script);
        assertEquals(1, lines.size());
        return lines.get(0);
    }

    private Statement statement(String script) {
        Parser.Line line = line(script);
        assertNull(line.error(), line.error());
        return line.statement();
    }

    private Statement.Call call(String script) {
        return assertInstanceOf(Statement.Call.class, statement(script));
    }

    private String error(String script) {
        Parser.Line line = line(script);
        assertNull(line.statement());
        return line.error();
    }

    @Test
    void aScriptIsALineperStep() {
        List<Parser.Line> lines = parser.script("status; nope;  ?status");
        assertEquals(3, lines.size());
        assertNull(lines.get(0).error());
        assertEquals("col 1: unknown command nope; try help", lines.get(1).error());
        assertTrue(lines.get(2).soft());
        assertEquals(List.of(), parser.script(" ; "));
    }

    @Test
    void softMarkers() {
        assertTrue(line("?status").soft());
        assertFalse(line("status").soft());
        Parser.Line unknown = line("?nope");
        assertTrue(unknown.soft());
        assertEquals("col 2: unknown command nope; try help", unknown.error());
        Parser.Line bare = line("? status");
        assertFalse(bare.soft());
        assertEquals("col 1: a statement must follow ? directly", bare.error());
    }

    @Test
    void callsBindTheirArgumentsWithTheMatchedType() {
        Statement.Call mine = call("mine #logs x6");
        assertEquals("mine", mine.entry().qualifiedName());
        assertEquals("#logs", mine.args().word("target"));
        assertEquals(ArgType.TAG, mine.args().kind("target"));
        assertEquals(ArgType.COUNT, mine.args().kind("count"));
        assertEquals(ArgType.POSITION, call("mine ~0,-1,0").args().kind("target"));
        assertEquals(ArgType.PATTERN, call("mine *ore*").args().kind("target"));
        assertEquals(ArgType.ID, call("mine create:cogwheel").args().kind("target"));
        assertFalse(call("mine").args().has("target"));
    }

    @Test
    void formsAreChosenByWhatMatches() {
        assertEquals("move", call("mouse move 30 -10 4t").args().word("op"));
        assertEquals("right", call("mouse click right").args().word("button"));
        assertEquals("col 15: <dy> (a number) expected, got up; mouse move <dx> <dy> [ticks]",
            error("mouse move 30 up"));
        assertEquals("col 7: down|up or click or move expected, got jump; mouse down|up left|right|middle | "
            + "mouse click left|right|middle [ticks] | mouse move <dx> <dy> [ticks]", error("mouse jump"));
        assertEquals("col 12: missing left|right|middle; mouse down|up left|right|middle",
            error("mouse down"));
        assertEquals("col 6: go expected, got x; twin go | twin go <n>", error("twin x"));
        // A form's own message and another's expectation at the same token aren't merged; the first form's wins.
        assertEquals("col 6: unexpected b; solo", error("solo b"));
        assertEquals("col 5: a expected, got b; duo a", error("duo b"));
    }

    @Test
    void optionalGroupsAreTakenWhenTheirFirstPartFits() {
        Statement.Call looking = call("camera at 1,2,3 look e5");
        assertEquals(ArgType.ENTITY, looking.args().kind("target"));
        assertFalse(call("camera at 1,2,3").args().has("look"));
        assertEquals("reset", call("camera reset").args().word("op"));
        assertEquals("col 22: missing <target> (a position x,y,z or ~dx,dy,dz or an entity e<id>); camera at <pos> "
            + "[look <target>]", error("camera at 1,2,3 look"));
        assertEquals("x", call("nest x 5").args().word("x"));
        assertEquals("col 6: unexpected 5; nest [[x] <n>]", error("nest 5"));
        assertFalse(call("nest").args().has("n"));
    }

    @Test
    void optionsStandAnywhereOnceEach() {
        Statement.Call mine = call("mine timeout=90 #logs near=3 x6");
        assertEquals(Map.of("timeout", "90", "near", "3"), mine.args().options());
        assertEquals("col 22: timeout= given twice; mine [target] [count] [near=…]",
            error("mine #logs timeout=9 timeout=90"));
        assertEquals("col 8: unknown option foo= (takes none); status", error("status foo=1"));
        // Only actions take timeout=.
        assertEquals("col 6: unknown option timeout= (takes none); look [radius]", error("look timeout=5"));
        assertEquals("col 13: unknown option bogus= (takes side=); block <pos> [side=…]",
            error("block 1,2,3 bogus=1"));
        assertEquals("col 6: unknown option rule:x= (takes near=, timeout=); mine [target] [count] [near=…]",
            error("mine rule:x=1"));
        assertEquals("col 8: unknown option bogus= (takes timeout=); camera at <pos> [look <target>] | "
            + "camera reset", error("camera bogus=1"));
        Statement.Call create = call("create world w1 flat rule:doDaylightCycle=false seed=5");
        assertEquals(Map.of("doDaylightCycle", "false"), create.args().family("rule"));
        assertEquals("5", create.args().option("seed"));
    }

    @Test
    void textTakesTheRestOfTheStep() {
        assertEquals("hello  world", call("chat hello  world  ").args().text("text"));
        assertEquals(ArgType.TEXT, call("chat hi").args().kind("text"));
        assertEquals("a=b c", call("chat a=b c").args().text("text"));
        assertEquals("hi; there", call("chat hi\\; there").args().text("text"));
        Statement.Call server = call("server as=player time set day");
        assertEquals("player", server.args().option("as"));
        assertEquals("time set day", server.args().text("command"));
        assertEquals("col 6: missing <text> (text); chat <text>", error("chat"));
        assertFalse(call("note").args().has("text"));
    }

    @Test
    void diffableCommandsTakeFull() {
        assertTrue(call("look full").full());
        assertFalse(call("look 5").full());
        assertTrue(call("inv detail full").full());
        assertEquals("col 11: unexpected x; look [radius]", error("look full x"));
        assertEquals("col 6: unexpected x; look [radius]", error("look x"));
        assertEquals("col 8: unexpected full; status", error("status full"));
        assertEquals("col 8: unexpected extra; status", error("status extra"));
    }

    @Test
    void waits() {
        assertEquals(new Duration(5), assertInstanceOf(Statement.WaitTicks.class, statement("wait 5t")).duration());
        assertEquals(new Duration(40), assertInstanceOf(Statement.WaitTicks.class, statement("wait 2s")).duration());
        assertInstanceOf(Statement.WaitIdle.class, statement("wait idle"));
        assertEquals("col 9: unexpected x", error("wait 5t x"));
        assertEquals("col 6: missing a duration, idle or <query> until <predicate>", error("wait"));
        Statement.WaitUntil until = assertInstanceOf(Statement.WaitUntil.class,
            statement("wait block 1,2,3 side=server until id==air"));
        assertEquals("server", until.query().args().option("side"));
        assertEquals(1, until.stable());
        assertEquals(1, until.every());
        assertNull(until.timeout());
        Statement.WaitUntil options = assertInstanceOf(Statement.WaitUntil.class,
            statement("wait probe until value>1 stable=20t every=5t timeout=30 clock=server"));
        assertEquals(20, options.stable());
        assertEquals(5, options.every());
        assertEquals(new Duration(600), options.timeout());
        assertEquals(new Duration(100), assertInstanceOf(Statement.WaitUntil.class,
            statement("wait status until ok timeout=5s clock=client")).timeout());
        assertEquals(1, assertInstanceOf(Statement.WaitUntil.class, statement("wait status until ok every=0t"))
            .every());
    }

    @Test
    void waitErrors() {
        assertEquals("col 22: clock=server needs a server-side query; status runs on the client",
            error("wait status until ok clock=server"));
        assertEquals("col 22: clock= takes server or client, not moon", error("wait status until ok clock=moon"));
        assertEquals("col 22: stable= expects a duration like 20t or 3s, not foo",
            error("wait status until ok stable=foo"));
        assertEquals("col 22: timeout= expects a duration like 20t or 3s, not foo",
            error("wait status until ok timeout=foo"));
        assertEquals("col 22: timeout= expects a duration like 20t or 3s, not -5",
            error("wait status until ok timeout=-5"));
        assertEquals("col 22: unknown option bogus= (takes clock=, every=, stable=, timeout=)",
            error("wait status until ok bogus=1"));
        assertEquals("col 32: stable= given twice", error("wait status until ok stable=1t stable=2t"));
        assertEquals("col 22: unexpected extra", error("wait status until ok extra"));
        assertEquals("col 18: missing until", error("wait block 1,2,3"));
        assertEquals("col 6: mine is not a read-only command", error("wait mine 1,2,3 until ok"));
        assertEquals("col 6: unknown command nope; try help", error("wait nope until ok"));
    }

    @Test
    void predicates() {
        Statement.Assert both = assertInstanceOf(Statement.Assert.class, statement("assert status a==1 and b<2"));
        assertTrue(both.condition().holds(Result.ok(new dev.blockpilot.api.Fields().put("a", 1).put("b", 1))));
        assertInstanceOf(Statement.Assert.class, statement("assert status ok and a==1"));
        assertInstanceOf(Statement.Assert.class, statement("assert status a==1 b==2"));
        // and is not a stop word, so it belongs to the query, which takes no arguments.
        assertEquals("col 15: unexpected and; status", error("assert status and a==1"));
        assertEquals("col 20: unexpected and", error("assert status a==1 and"));
        assertEquals("col 15: missing a predicate (field==value, field<n or ok)", error("assert status"));
        assertEquals("col 20: unexpected junk", error("assert status hp>5 junk"));
        assertEquals("col 15: < compares with a number or $field, not max_hp", error("assert status hp<max_hp"));
        assertEquals("col 15: a value can't hold $ < > = ! & or |, got b|c", error("assert status a==b|c"));
    }

    @Test
    void queriesEndAtTheFirstStopWord() {
        Statement.Assert text = assertInstanceOf(Statement.Assert.class, statement("assert note hi there ok"));
        assertEquals("hi there", text.query().args().text("text"));
        assertTrue(assertInstanceOf(Statement.Assert.class, statement("assert inv full count>3")).query().full());
        assertEquals("col 8: missing a query (a read-only command)", error("assert"));
        assertEquals("col 8: a query (a read-only command) expected, got ok", error("assert ok"));
    }

    @Test
    void samples() {
        Statement.Sample sample = assertInstanceOf(Statement.Sample.class, statement("sample status every 1t x20"));
        assertEquals(new Duration(1), sample.every());
        assertEquals(20, sample.count());
        assertInstanceOf(Statement.Sample.class, statement("sample probe every 2t x5 clock=server"));
        assertEquals("col 21: a duration like 20t or 3s expected, got x20", error("sample status every x20"));
        assertEquals("col 24: missing a count x<n>", error("sample status every 1t"));
        assertEquals("col 27: clock=server needs a server-side query; status runs on the client",
            error("sample status every 1t x5 clock=server"));
        assertEquals("col 15: unexpected 1t; status", error("sample status 1t x5"));
        assertEquals("col 15: every expected, got until", error("sample status until"));
        assertEquals("col 15: missing every", error("sample status"));
    }

    @Test
    void conditionals() {
        Statement.If plain = assertInstanceOf(Statement.If.class, statement("if status hp<10 then mine stone"));
        assertInstanceOf(Statement.Call.class, plain.then());
        assertFalse(plain.soft());
        assertTrue(assertInstanceOf(Statement.If.class, statement("if status hp<10 then ?mine stone")).soft());
        Statement.If nested = assertInstanceOf(Statement.If.class,
            statement("if status ok then if status ok then chat both hold"));
        assertEquals("both hold", assertInstanceOf(Statement.Call.class,
            assertInstanceOf(Statement.If.class, nested.then()).then()).args().text("text"));
        assertEquals("col 22: missing a statement after then", error("if status hp<10 then"));
        assertEquals("col 17: then expected, got mine", error("if status hp<10 mine"));
        assertEquals("col 19: a statement must follow ? directly", error("if status ok then ? mine"));
        assertEquals("col 1: unknown command nope; try help", error("nope ok"));
    }

    @Test
    void examplesParseAgainstTheirCommand() throws Parser.SyntaxError {
        CommandRegistry.Entry mine = registry.resolve("mine");
        assertEquals("#logs", Parser.example(mine, "mine #logs x3").args().word("target"));
        Parser.SyntaxError wrong = assertThrows(Parser.SyntaxError.class, () -> Parser.example(mine, "mine 1,2"));
        assertEquals("col 6: unexpected 1,2; mine [target] [count] [near=…]", wrong.getMessage());
        assertThrows(Parser.SyntaxError.class, () -> Parser.example(mine, "mine; mine"));
        assertThrows(Parser.SyntaxError.class, () -> Parser.example(mine, " "));
    }

    @Test
    void theConstructsHelpExamplesParse() {
        for (Construct construct : Construct.ALL) {
            assertNull(line(construct.example()).error(), construct.example());
        }
    }
}
