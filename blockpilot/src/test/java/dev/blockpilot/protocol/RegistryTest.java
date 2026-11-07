package dev.blockpilot.protocol;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.blockpilot.api.ArgType;
import dev.blockpilot.api.BlockpilotApi;
import dev.blockpilot.api.CommandSpec;
import dev.blockpilot.api.Result;
import java.util.List;
import org.junit.jupiter.api.Test;

class RegistryTest {
    @Test
    void builtInExamplesParse() {
        // The engine registers its built-ins (help, fmt, sub, stop, wait, sample); each example must parse.
        assertDoesNotThrow(() -> new Engine(new CommandRegistry(), new FakeGame()));
    }

    @Test
    void aRepeatedGroupCantTakeWhatFollowsIt() {
        CommandSpec greedy = CommandSpec.query("greedy", (context, args) -> Result.ok())
            .form(form -> form.repeated(3, group -> group.arg("a", ArgType.INT)).optionalArg("b", ArgType.INT));
        assertThrows(IllegalArgumentException.class, () -> new CommandRegistry().register(greedy));
        CommandSpec inner = CommandSpec.query("inner", (context, args) -> Result.ok())
            .form(form -> form.repeated(3, group -> group.keyword("k", "via").optionalArg("n", ArgType.ID)));
        assertThrows(IllegalArgumentException.class, () -> new CommandRegistry().register(inner),
            "an optional id after via could take the next via");
    }

    @Test
    void brokenExamplesAreRejected() {
        CommandSpec broken = CommandSpec.query("look", (context, args) -> Result.ok())
            .optionalArg("radius", ArgType.INT).example("look twelve");
        assertThrows(IllegalStateException.class, () -> new CommandRegistry().register(broken));
        CommandSpec look = CommandSpec.query("look", (context, args) -> Result.ok())
            .optionalArg("radius", ArgType.INT).example("look 12");
        assertNull(CommandRegistry.exampleProblem(new CommandRegistry.Entry("look", "", "blockpilot", look, true)));
        assertEquals("col 1: an example starts with mod:look, not look",
            CommandRegistry.exampleProblem(new CommandRegistry.Entry("mod:look", "mod", "mod", look, false)));
    }

    @Test
    void aDisabledNamespaceIsGoneAndHelpSaysWhy() {
        CommandRegistry registry = new CommandRegistry();
        registry.addExtensions(List.of(new BlockpilotApi.Registration("sable", "blockpilot",
            CommandSpec.query("list", (context, args) -> Result.ok()))));
        Engine engine = new Engine(registry, new FakeGame());
        registry.disable("sable", "off: Sable is not installed");
        assertNull(registry.resolve("sable:list"));
        assertEquals(List.of(), registry.namespace("sable"));
        assertTrue(registry.all().stream().noneMatch(entry -> entry.namespace().equals("sable")));
        assertEquals("ok\n  sable off: Sable is not installed",
            Formatter.text(engine.submit(engine.openSession(), null, "help sable").join()));
        assertEquals("err syntax col 1: unknown command sable:list; try help",
            Formatter.text(engine.submit(engine.openSession(), null, "sable:list").join()));
        assertFalse(Formatter.text(engine.submit(engine.openSession(), null, "help").join()).contains("sable"));
    }
}
