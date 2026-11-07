package dev.blockpilot.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.blockpilot.Specs;
import dev.blockpilot.api.Action;
import dev.blockpilot.api.ArgType;
import dev.blockpilot.api.CommandSpec;
import dev.blockpilot.api.Result;
import dev.blockpilot.api.Row;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class GrammarTest {
    private static final CommandSpec.QueryHandler QUERY = (context, args) -> Result.ok();
    private static final CommandSpec.ActionHandler ACTION = (context, args) -> (Action) () -> Result.ok();

    private static List<String> productions(CommandSpec spec) {
        return productions(spec.name(), spec);
    }

    private static List<String> productions(String qualifiedName, CommandSpec spec) {
        return Grammar.command(new CommandRegistry.Entry(qualifiedName, "", "test", spec.freeze()));
    }

    @Test
    void theCoreGrammarIsTheSpecs() throws IOException {
        assertEquals(Specs.grammar(), Grammar.CORE);
    }

    @Test
    void theCoreGrammarIsPrintedAProductionOrCommentALine() throws IOException {
        List<String> lines = Grammar.coreLines();
        long productions = Specs.grammar().lines().filter(line -> line.matches("[a-z][a-z0-9-]*\\s+::=.*")).count();
        long comments = Specs.grammar().lines().filter(line -> line.strip().startsWith("/*")).count();
        assertEquals(productions + comments, lines.size());
        for (String line : lines) {
            assertTrue(line.matches("/\\*.*\\*/|[a-z][a-z0-9-]* ::= .*"), line);
            assertTrue(!line.replaceAll("\"[^\"]*\"", "\"\"").contains("  "), line);
        }
        assertTrue(lines.get(0).startsWith("/* Read as the parser reads it: alternatives are tried in order and the "
            + "first that matches is taken;"));
        assertTrue(lines.contains("request ::= ( \"#\" request-id ( #x20 line-script )? | line-script ) line-end"));
        assertTrue(lines.contains("field-path ::= identifier ( \".\" ( identifier | digits ) )* /* [C] */"));
        assertTrue(lines.contains("token-char ::= \"\\;\" | [^#x20#x9#xA#xD;]"));
    }

    @Test
    void theEnginesCommandsArePinned() {
        CommandRegistry registry = new CommandRegistry();
        new Engine(registry, new FakeGame());
        List<String> lines = new ArrayList<>();
        registry.all().forEach(entry -> lines.addAll(Grammar.command(entry)));
        assertEquals(List.of(
            "help-command ::= \"help\" word?",
            "fmt-command ::= \"fmt\" ( \"text\" | \"json\" )",
            "sub-command ::= \"sub\" ( ( \"on\" | \"off\" ) | \"log\" ( \"error\" | \"warn\" | \"info\" | \"off\" ) )",
            "stop-command ::= \"stop\""), lines);
    }

    @Test
    void formsBecomeOneProductionAndOptionsAnother() {
        assertEquals(List.of(
            "mouse-command ::= \"mouse\" ( ( \"down\" | \"up\" ) ( \"left\" | \"right\" ) | \"move\" number number "
                + "duration? )",
            "mouse-option ::= \"timeout=\" ( duration | seconds )"), productions(CommandSpec.action("mouse", ACTION)
            .form(form -> form.keyword("op", "down", "up").keyword("button", "left", "right"))
            .form(form -> form.keyword("op", "move").arg("dx", ArgType.NUMBER).arg("dy", ArgType.NUMBER)
                .optionalArg("ticks", ArgType.DURATION))));
        assertEquals(List.of("camera-command ::= \"camera\" \"at\" position ( \"look\" ( position | entity ) )?",
            "camera-option ::= \"timeout=\" ( duration | seconds )"), productions(CommandSpec.action("camera", ACTION)
            .form(form -> form.keyword("op", "at").arg("pos", ArgType.POSITION).optional(group -> group
                .keyword("look", "look").arg("target", ArgType.POSITION, ArgType.ENTITY)))));
    }

    @Test
    void aRepeatedGroupIsStarred() {
        assertEquals(List.of("path-command ::= \"path\" position ( \"via\" position )*"),
            productions(CommandSpec.query("path", QUERY).form(form -> form.arg("pos", ArgType.POSITION)
                .repeated(4, via -> via.keyword("through", "via").arg("via", ArgType.POSITION)))));
    }

    @Test
    void slotsPrintTheirWordsAndTypes() {
        assertEquals(List.of("say-command ::= \"say\" \"team\"? text"), productions(CommandSpec.query("say", QUERY)
            .optionalKeyword("to", "team").arg("message", ArgType.TEXT)));
        assertEquals(List.of("note-command ::= \"note\" text?"), productions(CommandSpec.query("note", QUERY)
            .form(form -> form.optionalText("text"))));
        assertEquals(List.of("swap-command ::= \"swap\" ( \"head\" | \"feet\" | integer ) ( \"offhand\" | id )?"),
            productions(CommandSpec.query("swap", QUERY).form(form -> form.arg("a", List.of("head", "feet"),
                ArgType.INT).optionalArg("b", List.of("offhand"), ArgType.ID))));
        assertEquals(List.of("quote-command ::= \"quote\" 'say\"hi'"), productions(CommandSpec.query("quote", QUERY)
            .keyword("op", "say\"hi")));
    }

    @Test
    void emptyFormsMakeTheRestOptional() {
        assertEquals(List.of("solo-command ::= \"solo\" ( \"a\" )?"), productions(CommandSpec.query("solo", QUERY)
            .form(form -> { }).form(form -> form.keyword("op", "a"))));
        assertEquals(List.of("duo-command ::= \"duo\" ( \"a\" | \"b\" )?"), productions(CommandSpec.query("duo",
            QUERY).form(form -> form.keyword("op", "a")).form(form -> { }).form(form -> form.keyword("op", "b"))));
        assertEquals(List.of("none-command ::= \"none\""), productions(CommandSpec.query("none", QUERY)
            .form(form -> { }).form(form -> { })));
    }

    @Test
    void optionsListFullTheDeclaredOnesAndTimeout() {
        assertEquals(List.of("create-command ::= \"create\" \"world\" word",
            "create-option ::= \"full\" | \"seed=\" option-value | \"rule:\" identifier \"=\" option-value | "
                + "\"side=\" ( \"client\" | \"server\" ) | \"near=\" number | \"timeout=\" ( duration | seconds )"),
            productions(CommandSpec.action("create", ACTION).keyword("what", "world").arg("name", ArgType.WORD)
                .diffable().option("seed", "seed").option("rule:*", "a rule").option("side", "where",
                    List.of("client", "server")).option("near", "how near", ArgType.NUMBER)));
        assertEquals(List.of("build-command ::= \"build\"", "build-option ::= \"full\""),
            productions(CommandSpec.query("build", QUERY).diffable()));
    }

    @Test
    void extensionCommandsAreNamedWithADot() {
        assertEquals(List.of("mymod.build-command ::= \"mymod:build\" position"),
            productions("mymod:build", CommandSpec.query("build", QUERY).arg("at", ArgType.POSITION)));
    }

    @Test
    void helpPrintsTheCoreGrammarThenTheCommands() {
        CommandRegistry registry = new CommandRegistry();
        new Engine(registry, new FakeGame());
        List<Row> rows = new Help(registry).answer("grammar").rows();
        List<String> lines = rows.stream().map(row -> (String) row.fields().asMap().get("ebnf")).toList();
        List<String> core = Grammar.coreLines();
        assertEquals(core, lines.subList(0, core.size()));
        assertEquals(Grammar.COMMANDS_COMMENT, lines.get(core.size()));
        assertEquals("help-command ::= \"help\" word?", lines.get(core.size() + 1));
        assertEquals("help", rows.get(core.size() + 1).key());
        assertEquals(core.size() + 5, rows.size());
    }
}
