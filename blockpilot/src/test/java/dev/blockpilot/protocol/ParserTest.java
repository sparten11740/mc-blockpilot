package dev.blockpilot.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.blockpilot.api.Action;
import dev.blockpilot.api.ArgType;
import dev.blockpilot.api.Args;
import dev.blockpilot.api.CommandSpec;
import dev.blockpilot.api.Duration;
import dev.blockpilot.api.Fields;
import dev.blockpilot.api.Position;
import dev.blockpilot.api.Result;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * The parser against commands that use every kind of form element: one test per production or error path, the
 * spec's scenarios asserted on every field they mention, and a fuzz test. Expected columns come from the script text.
 */
class ParserTest {
    private static final CommandSpec.QueryHandler QUERY = (context, args) -> Result.ok();
    private static final CommandSpec.ActionHandler ACTION = (context, args) -> (Action) () -> Result.ok();
    private final CommandRegistry registry = new CommandRegistry();
    private final Parser parser = new Parser(registry);

    ParserTest() {
        registry.register(CommandSpec.query("status", QUERY));
        registry.register(CommandSpec.query("look", QUERY).optionalArg("radius", ArgType.INT).diffable());
        registry.register(CommandSpec.query("inv", QUERY).optionalKeyword("detail", "detail").diffable());
        registry.register(CommandSpec.query("list", QUERY).optionalArg("filter", ArgType.ID).diffable());
        registry.register(CommandSpec.query("block", QUERY).arg("pos", ArgType.POSITION).option("side", "where"));
        registry.register(CommandSpec.query("probe", QUERY).onServer());
        registry.register(CommandSpec.query("note", QUERY).form(form -> form.optionalText("text")));
        registry.register(CommandSpec.action("mine", ACTION)
            .optionalArg("target", ArgType.POSITION, ArgType.TAG, ArgType.PATTERN, ArgType.ID, ArgType.BLOCK_STATE)
            .optionalArg("count", ArgType.COUNT).option("near", "distance", ArgType.NUMBER));
        registry.register(CommandSpec.action("mouse", ACTION)
            .form(form -> form.keyword("op", "down", "up").keyword("button", "left", "right", "middle"))
            .form(form -> form.keyword("op", "click").keyword("button", "left", "right", "middle")
                .optionalArg("ticks", ArgType.DURATION))
            .form(form -> form.keyword("op", "move").arg("dx", ArgType.NUMBER).arg("dy", ArgType.NUMBER)
                .optionalArg("ticks", ArgType.DURATION)));
        registry.register(CommandSpec.action("click", ACTION)
            .form(form -> form.arg("at", ArgType.POINT, ArgType.WIDGET).optionalKeyword("button", "left", "right"))
            .form(form -> form.arg("slot", ArgType.MENU_SLOT).optionalKeyword("mode", "left", "right", "shift")));
        registry.register(CommandSpec.action("turn", ACTION).arg("yaw", ArgType.NUMBER).arg("pitch", ArgType.NUMBER)
            .optionalArg("ticks", ArgType.DURATION));
        registry.register(CommandSpec.action("camera", ACTION)
            .form(form -> form.keyword("op", "at").arg("pos", ArgType.POSITION)
                .optional(group -> group.keyword("look", "look").arg("target", ArgType.POSITION, ArgType.ENTITY)))
            .form(form -> form.keyword("op", "reset")));
        registry.register(CommandSpec.action("chat", ACTION).arg("text", ArgType.TEXT));
        registry.register(CommandSpec.action("say", ACTION).optionalKeyword("to", "team").arg("message", ArgType.TEXT)
            .option("loud", "louder", List.of("yes")));
        registry.register(CommandSpec.action("server", ACTION).arg("command", ArgType.TEXT).option("as", "who")
            .onServer());
        registry.register(CommandSpec.action("create", ACTION).keyword("what", "world")
            .arg("name", ArgType.WORD).optionalKeyword("preset", "flat", "void").option("seed", "seed")
            .option("rule:*", "game rule"));
        registry.register(CommandSpec.query("nest", QUERY)
            .form(form -> form.optional(outer -> outer.keyword("x", "x")
                .optional(inner -> inner.arg("n", ArgType.INT)))));
        registry.register(CommandSpec.query("solo", QUERY).form(form -> { }).form(form -> form.keyword("op", "a")));
        registry.register(CommandSpec.query("duo", QUERY).form(form -> form.keyword("op", "a")).form(form -> { }));
        registry.register(CommandSpec.query("twin", QUERY)
            .form(form -> form.keyword("op", "go"))
            .form(form -> form.keyword("op", "go").arg("n", ArgType.INT)));
        registry.register(CommandSpec.query("tri", QUERY)
            .form(form -> form.arg("n", ArgType.INT))
            .form(form -> form.arg("p", ArgType.POSITION))
            .form(form -> form.arg("n", ArgType.INT).arg("m", ArgType.INT)));
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

    /**
     * The one-step script's error is at the first occurrence of {@code at} (or, when null, where a token after the
     * last would start) and starts with the hint, followed by nothing or {@code ;} and the usage.
     */
    private void assertErrorAt(String script, String at, String hint) {
        int column = at == null ? script.length() + 2 : script.indexOf(at) + 1;
        assertTrue(at == null || column > 0, at + " isn't in " + script);
        String error = error(script);
        String expected = "col " + column + ": " + hint;
        assertTrue(error.equals(expected) || error.startsWith(expected + ";"), "expected " + expected + "… but was "
            + error);
    }

    private static Args.Value value(String text, ArgType type) {
        return new Args.Value(text, type);
    }

    private static void assertArgs(Statement.Call call, Map<String, Args.Value> arguments,
        Map<String, Args.Value> options) {
        assertEquals(arguments, call.args().arguments());
        assertEquals(options, call.args().options());
    }

    private static Statement.Call then(Statement statement) {
        return assertInstanceOf(Statement.Call.class, assertInstanceOf(Statement.If.class, statement).then());
    }

    @Test
    void aScriptIsALinePerStep() {
        List<Parser.Line> lines = parser.script("status; nope;  ?status");
        assertEquals(3, lines.size());
        assertEquals("status", assertInstanceOf(Statement.Call.class, lines.get(0).statement()).entry()
            .qualifiedName());
        assertEquals("col 1: unknown command nope; try help", lines.get(1).error());
        assertTrue(lines.get(2).soft());
        assertEquals(List.of(), parser.script(" ; "));
    }

    @Test
    void aStepStartingWithAQuestionMarkIsSoft() {
        assertTrue(line("?status").soft());
        assertFalse(line("status").soft());
        Parser.Line unknown = line("?nope");
        assertTrue(unknown.soft());
        assertEquals("col 2: unknown command nope; try help", unknown.error());
        // Spec scenario "Blank after the question mark".
        List<Parser.Line> lines = parser.script("? frobnicate; status");
        assertTrue(lines.get(0).soft());
        assertEquals("col 1: a statement must follow ? directly", lines.get(0).error());
        assertNull(lines.get(1).error());
    }

    @Test
    void callsBindTheirArgumentsWithTheMatchedType() {
        Statement.Call mine = call("mine #logs x6");
        assertEquals("mine", mine.entry().qualifiedName());
        assertArgs(mine, Map.of("target", value("#logs", ArgType.TAG), "count", value("x6", ArgType.COUNT)), Map.of());
        assertEquals(6, mine.args().count("count", 1));
        assertEquals(ArgType.POSITION, call("mine ~0,-1,0").args().type("target"));
        assertEquals(ArgType.PATTERN, call("mine *ore*").args().type("target"));
        assertEquals(ArgType.ID, call("mine create:cogwheel").args().type("target"));
        assertArgs(call("mine x6"), Map.of("count", value("x6", ArgType.COUNT)), Map.of());
        assertArgs(call("mine"), Map.of(), Map.of());
    }

    @Test
    void aBlockStateIsOneTokenWhereASlotTakesIt() {
        assertArgs(call("mine oak_log[axis=y,waterlogged=false] x2 near=3"), Map.of("target",
            value("oak_log[axis=y,waterlogged=false]", ArgType.BLOCK_STATE), "count", value("x2", ArgType.COUNT)),
            Map.of("near", value("3", ArgType.NUMBER)));
        assertEquals(ArgType.BLOCK_STATE, call("mine #logs[axis=y]").args().type("target"));
        assertEquals(ArgType.BLOCK_STATE, call("mine *_stairs[half=top]").args().type("target"));
    }

    @Test
    void aBlockStateDoesNotBreakOutOfItsStepOrSlot() {
        // A semicolon still ends the step, and a blank the token.
        List<Parser.Line> lines = parser.script("mine oak_log[axis=y];status");
        assertEquals(2, lines.size());
        assertEquals("oak_log[axis=y]", assertInstanceOf(Statement.Call.class, lines.get(0).statement()).args()
            .word("target"));
        assertEquals("status", assertInstanceOf(Statement.Call.class, lines.get(1).statement()).entry()
            .qualifiedName());
        assertErrorAt("mine oak_log [axis=y]", "[axis=y]", "<count> (a count x<n>) expected, got [axis=y]");
        // A slot that doesn't take block states refuses one; malformed brackets, or an = after them, are none.
        assertErrorAt("list oak_log[axis=y]", "oak_log", "<filter> (an id like stone or create:cogwheel) expected, "
            + "got oak_log[axis=y]");
        for (String token : List.of("oak_log[axis=y", "oak_log[axis=y]]", "oak_log[axis==y]", "oak_log[Axis=y]",
            "oak_log[axis=y]=1")) {
            assertTrue(error("mine " + token).startsWith("col 6: "), token);
        }
        // Nor does one pass for an option's value.
        assertErrorAt("mine #logs near=oak_log[axis=y]", "near", "near= takes a number, got oak_log[axis=y]");
    }

    @Test
    void formsAreChosenByWhatMatches() {
        // Spec scenario "Form chosen by keyword".
        assertArgs(call("mouse move 30 -10 4t"), Map.of("op", value("move", ArgType.KEYWORD), "dx", value("30",
            ArgType.NUMBER), "dy", value("-10", ArgType.NUMBER), "ticks", value("4t", ArgType.DURATION)), Map.of());
        assertErrorAt("mouse move 30 up", "up", "<dy> (a number) expected, got up");
        assertEquals("col 7: down|up or click or move expected, got jump; mouse down|up left|right|middle | "
            + "mouse click left|right|middle [ticks] | mouse move <dx> <dy> [ticks]", error("mouse jump"));
        assertErrorAt("mouse down", null, "missing left|right|middle");
        assertErrorAt("twin x", "x", "go expected, got x");
        assertErrorAt("twin go 5x", "5x", "<n> (a whole number) expected, got 5x");
        assertEquals("col 6: a expected, got b; solo | solo a", error("solo b"));
        assertEquals("col 5: a expected, got b; duo a | duo", error("duo b"));
        assertEquals("col 6: unknown option bogus= (takes none); twin go | twin go <n>", error("twin bogus=1"));
    }

    @Test
    void expectationsMergeAsASet() {
        assertEquals("col 5: <n> (a whole number) or <p> (a position x,y,z or ~dx,dy,dz) expected, got x; tri <n> | "
            + "tri <p> | tri <n> <m>", error("tri x"));
    }

    @Test
    void aSlotTakesTheFirstOfItsTypesThatMatches() {
        // Spec scenario "Slot with several types".
        assertArgs(call("click w3"), Map.of("at", value("w3", ArgType.WIDGET)), Map.of());
        assertArgs(call("click 120,40"), Map.of("at", value("120,40", ArgType.POINT)), Map.of());
        assertArgs(call("click s5 shift"), Map.of("slot", value("s5", ArgType.MENU_SLOT), "mode", value("shift",
            ArgType.KEYWORD)), Map.of());
    }

    @Test
    void errorsNameTheOptionalSlotsSkippedThere() {
        // Spec scenario "Optional slot that doesn't fit".
        assertErrorAt("turn 90 20 10", "10", "<ticks> (a duration like 20t or 3s) expected, got 10");
        assertErrorAt("mouse click left 4", "4", "<ticks> (a duration like 20t or 3s) expected, got 4");
        assertErrorAt("mine 1,2", "1,2", "<target> (a position x,y,z or ~dx,dy,dz or a tag like #logs or a pattern "
            + "like *ore* or an id like stone or create:cogwheel or a block with states like oak_log[axis=y]) or "
            + "<count> (a count x<n>) expected, got 1,2");
        assertErrorAt("turn 90", null, "missing <pitch> (a number)");
    }

    @Test
    void optionalGroupsAreTakenWhenTheirFirstPartFits() {
        assertArgs(call("camera at 1,2,3 look e5"), Map.of("op", value("at", ArgType.KEYWORD), "pos", value("1,2,3",
            ArgType.POSITION), "look", value("look", ArgType.KEYWORD), "target", value("e5", ArgType.ENTITY)),
            Map.of());
        assertFalse(call("camera at 1,2,3").args().has("look"));
        assertEquals("reset", call("camera reset").args().word("op"));
        assertEquals("col 22: missing <target> (a position x,y,z or ~dx,dy,dz or an entity e<id>); camera at <pos> "
            + "[look <target>]", error("camera at 1,2,3 look"));
        assertErrorAt("camera at 1,2,3 lok e5", "lok", "look expected, got lok");
        assertArgs(call("nest x 5"), Map.of("x", value("x", ArgType.KEYWORD), "n", value("5", ArgType.INT)), Map.of());
        assertFalse(call("nest x").args().has("n"));
        assertErrorAt("nest 5", "5", "x expected, got 5");
        assertArgs(call("nest"), Map.of(), Map.of());
    }

    @Test
    void optionsStandAnywhereOnceEach() {
        // Spec scenario "Option position doesn't matter".
        Statement.Call first = call("mine timeout=90 #logs x6");
        Statement.Call last = call("mine #logs x6 timeout=90");
        assertEquals(first.args().arguments(), last.args().arguments());
        assertEquals(first.args().options(), last.args().options());
        assertArgs(call("mine timeout=90 #logs near=3 x6"), Map.of("target", value("#logs", ArgType.TAG), "count",
            value("x6", ArgType.COUNT)), Map.of("timeout", value("90", ArgType.SECONDS), "near", value("3",
            ArgType.NUMBER)));
        // Spec scenario "Option given twice".
        assertErrorAt("mine #logs timeout=9 timeout=90", "timeout=90", "timeout= given twice");
        assertErrorAt("status foo=1", "foo=1", "unknown option foo= (takes none)");
        // Only actions take timeout=.
        assertErrorAt("look timeout=5", "timeout", "unknown option timeout= (takes none)");
        assertErrorAt("block 1,2,3 bogus=1", "bogus", "unknown option bogus= (takes side=)");
        assertErrorAt("mine rule:x=1", "rule", "unknown option rule:x= (takes near=, timeout=)");
        assertErrorAt("create bogus=1 world w1", "bogus", "unknown option bogus= (takes seed=, rule:<name>=, "
            + "timeout=)");
    }

    @Test
    void optionFamiliesKeepTheirOrder() {
        // Spec scenario "Family of options".
        Statement.Call create = call("create world w1 rule:doDaylightCycle=false rule:keepInventory=true seed=5");
        assertEquals(List.of("doDaylightCycle", "keepInventory"), List.copyOf(create.args().family("rule")
            .keySet()));
        assertEquals("false", create.args().family("rule").get("doDaylightCycle"));
        assertEquals("5", create.args().option("seed"));
    }

    @Test
    void optionValuesAreCheckedBeforeRunning() {
        // Spec scenario "Option value checked before running".
        List<Parser.Line> lines = parser.script("status; mine near=abc");
        assertNull(lines.get(0).error());
        assertEquals("col 6: near= takes a number, got abc; mine [target] [count] [near=number]",
            lines.get(1).error());
        assertErrorAt("mine timeout=-5", "timeout", "timeout= takes a duration like 20t or 3s or seconds like 30 or "
            + "0.5, got -5");
        assertErrorAt("mine timeout=foo", "timeout", "timeout= takes a duration like 20t or 3s or seconds like 30 "
            + "or 0.5, got foo");
        assertEquals(ArgType.DURATION, call("mine timeout=5s").args().optionType("timeout"));
        assertEquals(ArgType.TEXT, call("block 1,2,3 side=anything").args().optionType("side"));
        assertErrorAt("say loud=no hi", "loud", "loud= takes yes, got no");
    }

    @Test
    void textTakesTheRestOfTheStepAsWritten() {
        assertEquals("hello  world", call("chat hello  world  ").args().text("text"));
        assertEquals(ArgType.TEXT, call("chat hi").args().type("text"));
        // Spec scenario "Text that looks like an option".
        assertEquals("a=b", call("chat a=b").args().text("text"));
        assertEquals("a=b c", call("chat a=b c").args().text("text"));
        Statement.Call server = call("server as=player time set day");
        assertEquals("player", server.args().option("as"));
        assertEquals("time set day", server.args().text("command"));
        assertErrorAt("chat", null, "missing <text> (text)");
        assertFalse(call("note").args().has("text"));
        // Declared options stand before the text; once it starts, they are text.
        assertEquals(Map.of("timeout", value("5", ArgType.SECONDS)), call("chat timeout=5 hi").args().options());
        assertEquals("hi timeout=5", call("chat hi timeout=5").args().text("text"));
        assertErrorAt("chat timeout=5", null, "missing <text> (text)");
    }

    @Test
    void textMayStartAfterOptionalParts() {
        assertArgs(call("say a=b"), Map.of("message", value("a=b", ArgType.TEXT)), Map.of());
        assertArgs(call("say team a=b"), Map.of("to", value("team", ArgType.KEYWORD), "message", value("a=b",
            ArgType.TEXT)), Map.of());
        assertArgs(call("say loud=yes team hi"), Map.of("to", value("team", ArgType.KEYWORD), "message", value("hi",
            ArgType.TEXT)), Map.of("loud", value("yes", ArgType.KEYWORD)));
    }

    @Test
    void anEscapedSemicolonStaysInTheStep() {
        // Spec scenario "Escaped semicolon".
        List<Parser.Line> lines = parser.script("chat a\\; b; status");
        assertEquals(2, lines.size());
        assertEquals("a; b", assertInstanceOf(Statement.Call.class, lines.get(0).statement()).args().text("text"));
        assertEquals("status", assertInstanceOf(Statement.Call.class, lines.get(1).statement()).entry()
            .qualifiedName());
        // Columns count the escape as written.
        assertErrorAt("if note a\\;b ok", null, "missing then");
    }

    @Test
    void blanksAreSpacesAndTabsOnly() {
        assertEquals("　", call("chat 　").args().text("text"));
        assertEquals("hi　", call("chat hi　").args().text("text"));
        assertEquals("\f", call("chat \f ").args().text("text"));
        assertEquals("a\u000Bb", then(statement("if status ok then chat a\u000Bb")).args().text("text"));
    }

    @Test
    void diffableCommandsTakeFullAnywhere() {
        assertTrue(call("look full").full());
        assertFalse(call("look 5").full());
        Statement.Call first = call("look full 5");
        assertTrue(first.full());
        assertEquals(5, first.args().integer("radius", 0));
        assertTrue(call("look 5 full").full());
        assertTrue(call("inv full detail").full());
        assertTrue(call("inv detail full").full());
        // A slot never takes it, not even one that takes ids.
        Statement.Call list = call("list full");
        assertTrue(list.full());
        assertFalse(list.args().has("filter"));
        assertTrue(error("look full full").startsWith("col 11: full given twice;"));
        assertErrorAt("look full x", "x", "<radius> (a whole number) expected, got x");
        assertErrorAt("status full", "full", "unexpected full");
        assertEquals("col 8: unexpected extra; status", error("status extra"));
        assertEquals("col 6: <radius> (a whole number) expected, got x; look [radius] [full]", error("look x"));
    }

    @Test
    void waits() {
        assertEquals(new Duration(5), assertInstanceOf(Statement.WaitTicks.class, statement("wait 5t")).duration());
        assertEquals(new Duration(40), assertInstanceOf(Statement.WaitTicks.class, statement("wait 2s")).duration());
        assertInstanceOf(Statement.WaitIdle.class, statement("wait idle"));
        assertErrorAt("wait 5t x", "x", "unexpected x");
        assertErrorAt("wait", null, "missing a duration, idle or <query> until <predicate>");
        assertTrue(error("wait 5t x").endsWith("; wait <duration> | wait idle | wait <query> until <predicate> "
            + "[and …] [stable=<duration>] [every=<duration>] [timeout=<duration>] [clock=server|client]"));
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
        assertEquals(1, assertInstanceOf(Statement.WaitUntil.class, statement("wait status until ok stable=0t"))
            .stable());
    }

    @Test
    void aConstructInAScript() {
        // Spec scenario "A construct in a script".
        List<Parser.Line> lines = parser.script("wait block ~0,-1,0 until id==air timeout=5s; status");
        Statement.WaitUntil wait = assertInstanceOf(Statement.WaitUntil.class, lines.get(0).statement());
        assertEquals("block", wait.query().entry().qualifiedName());
        assertEquals(Position.relative(0, -1, 0), wait.query().args().position("pos"));
        assertTrue(wait.condition().holds(Result.ok(new Fields().put("id", "air"))));
        assertFalse(wait.condition().holds(Result.ok(new Fields().put("id", "dirt"))));
        assertEquals(List.of("id"), List.copyOf(wait.condition().observed(Result.ok(new Fields().put("id", "air")
            .put("be", "chest"))).asMap().keySet()));
        assertEquals(new Duration(100), wait.timeout());
        assertEquals("status", assertInstanceOf(Statement.Call.class, lines.get(1).statement()).entry()
            .qualifiedName());
    }

    @Test
    void waitErrors() {
        assertErrorAt("wait status until ok clock=server", "clock", "clock=server doesn't fit status, which runs on "
            + "the client");
        assertErrorAt("wait probe until ok clock=client", "clock", "clock=client doesn't fit probe, which runs on "
            + "the server");
        assertErrorAt("wait status until ok clock=moon", "clock", "clock= takes server|client, got moon");
        assertErrorAt("wait status until ok stable=foo", "stable", "stable= takes a duration like 20t or 3s, got foo");
        assertErrorAt("wait status until ok timeout=foo", "timeout", "timeout= takes a duration like 20t or 3s or "
            + "seconds like 30 or 0.5, got foo");
        assertErrorAt("wait status until ok timeout=-5", "timeout", "timeout= takes a duration like 20t or 3s or "
            + "seconds like 30 or 0.5, got -5");
        assertErrorAt("wait status until ok every=0t", "every", "every= takes 1t or more");
        assertErrorAt("wait status until ok bogus=1", "bogus", "unknown option bogus= (takes clock=, every=, stable=, "
            + "timeout=)");
        assertErrorAt("wait status until ok stable=1t stable=2t", "stable=2t", "stable= given twice");
        assertErrorAt("wait status until ok extra", "extra", "unexpected extra");
        assertErrorAt("wait block 1,2,3", null, "missing until");
        // Spec scenario "Action used as condition".
        assertErrorAt("wait mine 1,2,3 until ok", "mine", "mine is not a read-only command");
        assertErrorAt("wait nope until ok", "nope", "unknown command nope; try help");
        // Spec scenario "Ordering against text".
        assertErrorAt("wait status until hp<max_hp", "hp<", "< compares with a number or $field, not max_hp");
    }

    @Test
    void waitHintsAtLikelyMistakes() {
        // Spec scenario "Duration without a unit".
        assertErrorAt("wait 5", "5", "a duration needs a unit: 5t or 5s");
        assertErrorAt("wait 500ms", "500ms", "a duration like 20t or 3s expected, got 500ms");
        assertErrorAt("wait status timeout=5s until ok", "timeout", "timeout= belongs to wait, after the predicates");
        assertErrorAt("wait status until ok every 5t", "every", "every is an option here: every=5t");
        assertErrorAt("wait status every 5t until ok", "every", "every is an option here: every=5t");
        assertErrorAt("wait status until ok every", "every", "every is an option here: every=…");
        assertErrorAt("wait status until hp < 10", "hp", "a predicate is one token, without blanks: hp<10");
        assertErrorAt("wait status until hp=10", "hp", "predicates compare with ==: hp==10");
        // Spec scenario "Assert inside wait".
        assertErrorAt("wait assert status hp>5 until ok", "assert", "assert can't be a query; constructs run only "
            + "as statements");
    }

    @Test
    void predicates() {
        Statement.Assert both = assertInstanceOf(Statement.Assert.class, statement("assert status a==1 and b<2"));
        assertTrue(both.condition().holds(Result.ok(new Fields().put("a", 1).put("b", 1))));
        assertFalse(both.condition().holds(Result.ok(new Fields().put("a", 1).put("b", 3))));
        assertFalse(both.condition().holds(Result.ok(new Fields().put("a", 2).put("b", 1))));
        assertEquals(List.of("a", "b"), List.copyOf(both.condition().observed(Result.ok(new Fields().put("a", 1)
            .put("b", 1).put("c", 0))).asMap().keySet()));
        Statement.Assert okAnd = assertInstanceOf(Statement.Assert.class, statement("assert status ok and a==1"));
        assertFalse(okAnd.condition().holds(Result.error("nope")));
        assertTrue(okAnd.condition().holds(Result.ok(new Fields().put("a", 1))));
        Statement.Assert sideBySide = assertInstanceOf(Statement.Assert.class, statement("assert status a==1 b==2"));
        assertFalse(sideBySide.condition().holds(Result.ok(new Fields().put("a", 1).put("b", 3))));
        // and is not a stop word, so it belongs to the query, which takes no arguments.
        assertErrorAt("assert status and a==1", "and", "unexpected and");
        assertErrorAt("assert status a==1 and", null, "missing a predicate (field==value, field<n or ok)");
        assertErrorAt("assert status a==1 and junk", "junk", "a predicate (field==value, field<n or ok) expected, "
            + "got junk");
        assertErrorAt("assert status", null, "missing a predicate (field==value, field<n or ok)");
        assertErrorAt("assert status hp>5 junk", "junk", "unexpected junk");
        assertErrorAt("assert status hp<max_hp", "hp", "< compares with a number or $field, not max_hp");
        assertErrorAt("assert status a==b|c", "a==", "a value can't hold $ < > = ! & or |, got b|c");
    }

    @Test
    void predicateMistakesGetHints() {
        // Spec scenario "Predicate with a single equals sign".
        assertErrorAt("assert block ~0,-1,0 id=air", "id", "predicates compare with ==: id==air");
        assertErrorAt("if block 1,2,3 id=air then chat x", "id", "predicates compare with ==: id==air");
        assertErrorAt("assert status hp>5 food=3", "food", "predicates compare with ==: food==3");
        assertErrorAt("if status hp < 10 then chat low", "<", "a predicate is one token, without blanks: hp<10");
        assertErrorAt("assert status hp <10", "<10", "a predicate is one token, without blanks: hp<10");
        assertErrorAt("assert status hp< 10", "hp<", "a predicate is one token, without blanks: hp<10");
        assertErrorAt("assert status a==1 hp >= 3", "hp >=", "a predicate is one token, without blanks: hp>=3");
        // A declared option of the query isn't a predicate, and nothing after a stop word is taken for one.
        assertErrorAt("assert block 1,2,3 side=server hp", "hp", "unexpected hp");
        assertErrorAt("assert status hp", "hp", "unexpected hp");
        assertEquals("<3", then(statement("if status ok then chat <3")).args().text("text"));
        assertErrorAt("assert status every=5t ok", "every", "predicates compare with ==: every==5t");
        for (String odd : List.of("assert status 5< 3", "assert status 5 < 3", "assert status hp <", "assert status "
            + "hp= 3", "assert status 1=2", "assert status a==1 junk b==2")) {
            assertTrue(error(odd).contains(": unexpected "), odd + " gave " + error(odd));
        }
        assertErrorAt("if status then <3", "then", "a predicate (field==value, field<n or ok) expected, got then");
    }

    @Test
    void queriesEndAtTheFirstStopWord() {
        Statement.Assert text = assertInstanceOf(Statement.Assert.class, statement("assert note hi there ok"));
        assertEquals("hi there", text.query().args().text("text"));
        assertTrue(assertInstanceOf(Statement.Assert.class, statement("assert inv full count>3")).query().full());
        assertErrorAt("assert", null, "missing a query (a read-only command)");
        assertErrorAt("assert ok", "ok", "a query (a read-only command) expected, got ok");
        for (String construct : List.of("wait", "sample", "assert", "if")) {
            assertErrorAt("assert " + construct + " status ok", construct + " status", construct + " can't be a query; "
                + "constructs run only as statements");
        }
    }

    @Test
    void samples() {
        Statement.Sample sample = assertInstanceOf(Statement.Sample.class, statement("sample status every 1t x20"));
        assertEquals("status", sample.queries().get(0).entry().qualifiedName());
        assertEquals(new Duration(1), sample.every());
        assertEquals(20, sample.count());
        assertEquals(1000, assertInstanceOf(Statement.Sample.class, statement("sample probe every 2t x1000 "
            + "clock=server")).count());
        assertErrorAt("sample status every x20", "x20", "a duration like 20t or 3s expected, got x20");
        assertErrorAt("sample status every", null, "missing a duration like 20t or 3s");
        assertErrorAt("sample status every 1t", null, "missing a count x<n> or while <action|wait>");
        assertErrorAt("sample status every 0t x5", "0t", "a sample is taken every 1t or more");
        assertErrorAt("sample status every 1t x0", "x0", "a sample is taken x1 to x1000 times, not x0");
        // Spec scenario "Too many samples".
        assertErrorAt("sample status every 1t x1001", "x1001", "a sample is taken x1 to x1000 times, not x1001");
        assertErrorAt("sample status every 1t x5 clock=server", "clock", "clock=server doesn't fit status, which "
            + "runs on the client");
        assertErrorAt("sample status every 1t x5 extra", "extra", "unexpected extra");
        assertEquals("col 15: unexpected 1t; status", error("sample status 1t x5"));
        assertErrorAt("sample status until", "until", "every expected, got until");
        assertErrorAt("sample status", null, "missing every");
        assertErrorAt("sample status every=5t x10", "every=", "sample takes every and its duration as words: every "
            + "5t");
        assertErrorAt("sample status clock=client every 1t x5", "clock", "clock= belongs to sample, after the count");
    }

    @Test
    void aSampleTakesSeveralQueriesJoinedByAnd() {
        Statement.Sample three = assertInstanceOf(Statement.Sample.class, statement("sample block ~0,-1,0 side=where "
            + "and look 3 and status every 20t x3"));
        assertEquals(List.of("block", "look", "status"), three.queries().stream()
            .map(query -> query.entry().qualifiedName()).toList());
        assertEquals(3, three.queries().get(1).args().integer("radius", 0));
        assertEquals(3, three.count());
        // A bare and ends a sampled query's text, while the query of assert takes it.
        Statement.Sample note = assertInstanceOf(Statement.Sample.class, statement("sample note a b and status every "
            + "1t x2"));
        assertEquals("a b", note.queries().get(0).args().text("text"));
        assertEquals("a and b", assertInstanceOf(Statement.Assert.class, statement("assert note a and b ok")).query()
            .args().text("text"));
        assertEquals(500, assertInstanceOf(Statement.Sample.class, statement("sample status and status every 1t "
            + "x500")).count());
        // Spec scenario "Too many rows".
        assertErrorAt("sample status and status every 1t x600", "x600", "a sample holds at most 1000 rows, not 1200: "
            + "x600 times 2 queries");
        assertErrorAt("sample status and", null, "missing a query (a read-only command)");
        assertErrorAt("sample status and every 1t x5", "every", "a query (a read-only command) expected, got every");
        assertErrorAt("sample status and mine every 1t x5", "mine", "mine is not a read-only command");
        assertErrorAt("sample status and wait 1t every 1t x5", "wait", "wait can't be a query; constructs run only as "
            + "statements");
    }

    @Test
    void aSampleOfBothSidesRunsOnTheClientClock() {
        Statement.Sample mixed = assertInstanceOf(Statement.Sample.class, statement("sample probe and status every "
            + "1t x5 clock=client"));
        assertEquals("status", mixed.lead().entry().qualifiedName());
        assertEquals("probe", assertInstanceOf(Statement.Sample.class, statement("sample probe and probe every 1t x5 "
            + "clock=server")).lead().entry().qualifiedName());
        // Spec scenario "Server clock asked for".
        assertErrorAt("sample status and probe every 1t x5 clock=server", "clock", "clock=server doesn't fit status, "
            + "which runs on the client");
        assertErrorAt("sample probe and probe every 1t x5 clock=client", "clock", "clock=client doesn't fit probe, "
            + "which runs on the server");
    }

    @Test
    void aSideServerQueryIsServerSideForClocks() {
        Statement.Sample server = assertInstanceOf(Statement.Sample.class, statement("sample block 1,2,3 side=server "
            + "every 1t x5 clock=server"));
        assertTrue(server.lead().runsOnServer());
        assertErrorAt("sample block 1,2,3 every 1t x5 clock=server", "clock", "clock=server doesn't fit block, which "
            + "runs on the client");
        assertErrorAt("sample block 1,2,3 side=server every 1t x5 clock=client", "clock", "clock=client doesn't fit "
            + "block, which runs on the server");
        assertEquals("status", assertInstanceOf(Statement.Sample.class, statement("sample status and block 1,2,3 "
            + "side=server every 1t x5")).lead().entry().qualifiedName());
        assertInstanceOf(Statement.WaitUntil.class, statement("wait block 1,2,3 side=server until id==air "
            + "clock=server"));
    }

    @Test
    void aSampleCanRunBesideAnAction() {
        Statement.Sample walk = assertInstanceOf(Statement.Sample.class, statement("sample status every 2t while "
            + "mine #logs x3"));
        assertEquals(1000, walk.count());
        Statement.Call mine = assertInstanceOf(Statement.Call.class, walk.during());
        assertEquals("mine", mine.entry().qualifiedName());
        assertEquals(3, mine.args().count("count", 1));
        Statement.Sample capped = assertInstanceOf(Statement.Sample.class, statement("sample status every 2t x5 "
            + "clock=client while mine"));
        assertEquals(5, capped.count());
        // Without a count, the runs stop where the rows reach 1000.
        assertEquals(333, assertInstanceOf(Statement.Sample.class, statement("sample status and look and inv every 2t "
            + "while mine")).count());
        assertNull(assertInstanceOf(Statement.Sample.class, statement("sample status every 2t x5")).during());
        assertErrorAt("sample status every 2t while", null, "missing an action or wait after while");
        assertErrorAt("sample status every 2t while assert status hp>0", "assert", "while takes an action or a wait, "
            + "not assert");
        // Spec scenario "Something other than an action after while".
        assertErrorAt("sample status every 2t while look", "look", "while takes an action or a wait; look is a query");
        assertErrorAt("sample status every 2t while nope", "nope", "unknown command nope; try help");
        assertErrorAt("sample status every 2t clock=server while mine", "clock", "clock=server doesn't fit status, "
            + "which runs on the client");
        assertErrorAt("sample status every 2t extra while mine", "extra", "unexpected extra");
    }

    @Test
    void aSampleCanRunBesideAWait() {
        assertEquals(new Statement.WaitTicks(Duration.parse("200t")), assertInstanceOf(Statement.Sample.class,
            statement("sample status every 20t while wait 200t")).during());
        assertInstanceOf(Statement.WaitIdle.class, assertInstanceOf(Statement.Sample.class,
            statement("sample status every 2t while wait idle")).during());
        Statement.WaitUntil until = assertInstanceOf(Statement.WaitUntil.class, assertInstanceOf(Statement.Sample.class,
            statement("sample status every 2t x10 while wait status until hp<10 timeout=5s")).during());
        assertEquals("status", until.query().entry().qualifiedName());
        assertErrorAt("sample status every 2t while wait", null, "missing a duration, idle or <query> until "
            + "<predicate>");
    }

    @Test
    void conditionals() {
        Statement.If plain = assertInstanceOf(Statement.If.class, statement("if status hp<10 then mine stone"));
        assertEquals("status", plain.query().entry().qualifiedName());
        assertTrue(plain.condition().holds(Result.ok(new Fields().put("hp", 5))));
        Statement.Call then = then(plain);
        assertEquals("mine", then.entry().qualifiedName());
        assertEquals(value("stone", ArgType.ID), then.args().arguments().get("target"));
        assertFalse(plain.soft());
        assertFalse(line("if status hp<10 then mine stone").soft());
        assertTrue(assertInstanceOf(Statement.If.class, statement("if status hp<10 then ?mine stone")).soft());
        Parser.Line softIf = line("?if status hp<10 then mine stone");
        assertTrue(softIf.soft());
        assertFalse(assertInstanceOf(Statement.If.class, softIf.statement()).soft());
        Statement.If nested = assertInstanceOf(Statement.If.class,
            statement("if status ok then if status ok then chat both hold"));
        assertEquals("both hold", then(nested.then()).args().text("text"));
        // The statement after then isn't cut at stop words.
        assertEquals("until then ok", then(statement("if status ok then chat until then ok")).args().text("text"));
        assertErrorAt("if status hp<10 then", null, "missing a statement after then");
        assertErrorAt("if status hp<10 mine", "mine", "then expected, got mine");
        assertErrorAt("if status ok then ? mine", "? mine", "a statement must follow ? directly");
    }

    @Test
    void anIfNestsAtMostSixteenDeep() {
        assertInstanceOf(Statement.If.class, statement("if status ok then ".repeat(16) + "status"));
        assertErrorAt("if status ok then ".repeat(17) + "status", "if status ok then status", "if nests at most 16 "
            + "deep");
        String deep = "if status ok then ".repeat(20_000) + "status";
        assertTrue(error(deep).startsWith("col " + ("if status ok then ".length() * 16 + 1) + ": if nests at most "
            + "16 deep"));
    }

    @Test
    void examplesParseAgainstTheirCommand() throws Parser.SyntaxError {
        CommandRegistry.Entry mine = registry.resolve("mine");
        assertEquals("#logs", Parser.example(mine, "mine #logs x3").args().word("target"));
        assertTrue(assertThrows(Parser.SyntaxError.class, () -> Parser.example(mine, "mine 1,2")).getMessage()
            .startsWith("col 6: <target> "));
        assertEquals("col 1: an example starts with mine, not status", assertThrows(Parser.SyntaxError.class,
            () -> Parser.example(mine, "status #logs")).getMessage());
        assertEquals("col 1: an example is one step", assertThrows(Parser.SyntaxError.class,
            () -> Parser.example(mine, "mine; mine")).getMessage());
        assertThrows(Parser.SyntaxError.class, () -> Parser.example(mine, " "));
    }

    @Test
    void theConstructsHelpExamplesParse() {
        for (Construct construct : Construct.ALL) {
            assertNull(line(construct.example()).error(), construct.example());
        }
    }

    @Test
    void theParserNeverThrowsAndEveryErrorHasAColumn() {
        String[] pieces = {"wait", "sample", "assert", "if", "then", "until", "every", "ok", "and", "status", "look",
            "mine", "chat", "say", "camera", "at", "full", "x6", "5", "5t", "1,2,3", "~0,-1,0", "e5", "#logs", "a==1",
            "hp<5", "hp<$max", "id=air", "timeout=5", "clock=server", "near=x", "?", "\\;", ";", "\n", " ", "\t",
            "　", "\f", "=", "<", "$", "x", "-", ".", ":", "*"};
        Pattern error = Pattern.compile("col [1-9]\\d*: .+", Pattern.DOTALL);
        Random random = new Random(1703);
        for (int run = 0; run < 20_000; run++) {
            StringBuilder script = new StringBuilder();
            for (int piece = random.nextInt(12); piece >= 0; piece--) {
                script.append(pieces[random.nextInt(pieces.length)]).append(random.nextInt(3) == 0 ? "" : " ");
            }
            for (Parser.Line line : parser.script(script.toString())) {
                assertTrue(line.statement() != null ^ line.error() != null, script.toString());
                if (line.error() != null) {
                    assertTrue(error.matcher(line.error()).matches(), script + " gave " + line.error());
                }
            }
        }
    }
}
