package dev.blockpilot.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class CommandSpecTest {
    private static final CommandSpec.QueryHandler QUERY = (context, args) -> Result.ok();
    private static final CommandSpec.ActionHandler ACTION = (context, args) -> () -> Result.ok();

    private static String rejection(Runnable declaration) {
        return assertThrows(IllegalArgumentException.class, declaration::run).getMessage();
    }

    @Test
    void commandsListOneUsagePerForm() {
        CommandSpec mouse = CommandSpec.action("mouse", ACTION)
            .form(form -> form.keyword("op", "down", "up").keyword("button", "left", "right"))
            .form(form -> form.keyword("op", "move").arg("dx", ArgType.NUMBER).arg("dy", ArgType.NUMBER)
                .optionalArg("ticks", ArgType.DURATION));
        assertEquals(List.of("mouse down|up left|right", "mouse move <dx> <dy> [ticks]"), mouse.usages("mouse"));
        CommandSpec create = CommandSpec.action("create", ACTION).keyword("what", "world").arg("name", ArgType.WORD)
            .option("seed", "world seed").option("rule:*", "a game rule").option("near", "distance", ArgType.NUMBER)
            .option("side", "where", List.of("client", "server")).option("hold", "how long", List.of("off"),
                ArgType.DURATION);
        assertEquals(List.of("create world <name> [seed=…] [rule:<name>=…] [near=number] [side=client|server] "
            + "[hold=off|duration]"), create.usages("create"));
        CommandSpec stop = CommandSpec.query("stop", QUERY);
        assertEquals(List.of("stop"), stop.usages("stop"));
        assertEquals(1, stop.forms().size());
        CommandSpec look = CommandSpec.query("look", QUERY).optionalKeyword("detail", "detail")
            .optionalArg("radius", ArgType.INT);
        assertEquals(List.of("look [detail] [radius]"), look.usages("look"));
    }

    @Test
    void namesFollowTheGrammar() {
        assertEquals("a command name is [a-z][a-z0-9_]*, not Gear", rejection(() -> CommandSpec.query("Gear", QUERY)));
        rejection(() -> CommandSpec.action("max-speed", ACTION));
        rejection(() -> CommandSpec.query(null, QUERY));
        assertThrows(NullPointerException.class, () -> CommandSpec.query("a", null));
        assertThrows(NullPointerException.class, () -> CommandSpec.action("a", null));
        assertEquals("a field is named by an identifier, not max-hp",
            rejection(() -> CommandSpec.query("a", QUERY).field("max-hp", "hit points")));
        rejection(() -> CommandSpec.query("a", QUERY).field(null, "x"));
        CommandSpec.query("a", QUERY).field("maxHp", "hit points").field("pos", "where");
    }

    @Test
    void optionNamesFollowTheGrammar() {
        assertEquals("an option is named <identifier> or <identifier>:<identifier>, or <identifier>:* for a family, "
            + "not max-speed", rejection(() -> CommandSpec.query("a", QUERY).option("max-speed", "how fast")));
        rejection(() -> CommandSpec.query("a", QUERY).option("a:b:*", "x"));
        rejection(() -> CommandSpec.query("a", QUERY).option("-:*", "x"));
        rejection(() -> CommandSpec.query("a", QUERY).option(null, "x"));
        CommandSpec.query("a", QUERY).option("rule:*", "x").option("sable:mode", "x").option("_x", "x");
    }

    @Test
    void optionsAreDeclaredOnce() {
        assertEquals("a declares near= twice",
            rejection(() -> CommandSpec.query("a", QUERY).option("near", "x").option("near", "y", ArgType.INT)));
        assertEquals("a: every action takes timeout= already",
            rejection(() -> CommandSpec.action("a", ACTION).option("timeout", "x")));
        CommandSpec.query("a", QUERY).option("timeout", "x");
        assertEquals("grep=: free text can't be combined with other types",
            rejection(() -> CommandSpec.query("a", QUERY).option("grep", "x", List.of("all"), ArgType.TEXT)));
        assertEquals("near=: number takes every integer, so integer must come first",
            rejection(() -> CommandSpec.query("a", QUERY).option("near", "x", ArgType.NUMBER, ArgType.INT)));
        assertEquals("hud= lists a word twice",
            rejection(() -> CommandSpec.query("a", QUERY).option("hud", "x", List.of("off", "off"))));
    }

    @Test
    void optionsAreFoundByNameFamilyOrTheActionTimeout() {
        CommandSpec create = CommandSpec.action("create", ACTION).option("seed", "x").option("rule:*", "y");
        assertEquals("seed", create.findOption("seed").name());
        assertEquals("rule:*", create.findOption("rule:keepInventory").name());
        assertTrue(create.findOption("rule:keepInventory").isFamily());
        assertSame(CommandSpec.TIMEOUT, create.findOption("timeout"));
        assertNull(create.findOption("other:thing"));
        assertNull(create.findOption("bogus"));
        assertNull(CommandSpec.query("look", QUERY).findOption("timeout"));
        assertEquals(List.of("seed", "rule:*"), List.copyOf(create.options().keySet()));
        CommandSpec.Option near = CommandSpec.query("a", QUERY).option("near", "x", ArgType.NUMBER).options()
            .get("near");
        assertEquals("a number", near.expectation());
        assertEquals("number", near.valueUsage());
        assertEquals("a duration like 20t or 3s or seconds like 30 or 0.5", CommandSpec.TIMEOUT.expectation());
        assertFalse(near.isFamily());
        assertThrows(NullPointerException.class, () -> new CommandSpec.Option("a", null, near.value()));
        assertThrows(NullPointerException.class, () -> new CommandSpec.Option("a", "x", null));
    }

    @Test
    void partsOnTheSpecAndFormsDontMix() {
        assertEquals("a declares its forms with form(…); add the part there", assertThrows(
            IllegalStateException.class, () -> CommandSpec.query("a", QUERY).form(form -> form.keyword("op", "list"))
                .arg("n", ArgType.INT)).getMessage());
        assertEquals("a has parts declared on the spec; declare every form with form(…)", assertThrows(
            IllegalStateException.class, () -> CommandSpec.query("a", QUERY).arg("n", ArgType.INT)
                .form(form -> form.keyword("op", "list"))).getMessage());
        assertThrows(IllegalStateException.class, () -> CommandSpec.query("a", QUERY).form(form -> { })
            .optionalKeyword("x", "x"));
        assertThrows(IllegalStateException.class, () -> CommandSpec.query("a", QUERY).form(form -> { })
            .keyword("x", "x"));
        assertThrows(IllegalStateException.class, () -> CommandSpec.query("a", QUERY).form(form -> { })
            .optionalArg("x", ArgType.INT));
    }

    @Test
    void freezingChecksTheFormsAndStopsChanges() {
        CommandSpec spec = CommandSpec.action("find", ACTION).form(form -> form.keyword("op", "all"))
            .form(form -> form.optionalArg("radius", ArgType.INT).arg("limit", ArgType.INT));
        assertEquals("find (form 2): optional [radius] would take a token meant for <limit>; give them distinct "
            + "types or group them as [<a> [b]]", assertThrows(IllegalArgumentException.class, spec::freeze)
            .getMessage());
        CommandSpec frozen = CommandSpec.query("look", QUERY).optionalArg("radius", ArgType.INT).freeze();
        assertSame(frozen, frozen.freeze());
        assertThrows(IllegalStateException.class, () -> frozen.forms().get(0).arg("late", ArgType.WORD));
        assertThrows(UnsupportedOperationException.class, () -> frozen.forms().clear());
        assertThrows(UnsupportedOperationException.class, () -> frozen.options().clear());
        assertThrows(UnsupportedOperationException.class, () -> frozen.fields().clear());
        List<Runnable> changes = List.of(() -> frozen.group("x"), () -> frozen.arg("a", ArgType.INT),
            () -> frozen.optionalArg("a", ArgType.INT), () -> frozen.keyword("a", "a"),
            () -> frozen.optionalKeyword("a", "a"), () -> frozen.form(form -> { }), () -> frozen.option("a", "x"),
            () -> frozen.option("a", "x", ArgType.INT), () -> frozen.option("a", "x", List.of("y")),
            () -> frozen.field("a", "x"), () -> frozen.help("x"), () -> frozen.example("look"), frozen::diffable,
            frozen::needsWorld, frozen::needsServer, frozen::onServer);
        for (Runnable change : changes) {
            assertEquals("look is registered and can't change",
                assertThrows(IllegalStateException.class, change::run).getMessage());
        }
        assertEquals(1, CommandSpec.query("stop", QUERY).freeze().forms().size());
    }

    @Test
    void theSpecReportsWhatWasDeclared() {
        CommandSpec spec = CommandSpec.query("probe", QUERY).group("server").help("probes").example("probe")
            .field("value", "the value").diffable().onServer();
        assertEquals("probe", spec.name());
        assertEquals(CommandSpec.Kind.QUERY, spec.kind());
        assertEquals("server", spec.group());
        assertEquals("probes", spec.help());
        assertEquals("probe", spec.example());
        assertEquals("the value", spec.fields().get("value"));
        assertTrue(spec.isDiffable());
        assertTrue(spec.runsOnServer());
        assertTrue(spec.needsIntegratedServer());
        assertTrue(spec.needsWorldLoaded());
        assertSame(QUERY, spec.queryHandler());
        assertNull(spec.actionHandler());
        CommandSpec act = CommandSpec.action("go", ACTION).needsWorld();
        assertSame(ACTION, act.actionHandler());
        assertNull(act.queryHandler());
        assertTrue(act.needsWorldLoaded());
        assertFalse(act.needsIntegratedServer());
        assertFalse(act.runsOnServer());
        assertFalse(act.isDiffable());
        assertEquals("misc", act.group());
        assertEquals("", act.help());
        assertNull(act.example());
    }
}
