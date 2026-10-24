package dev.blockpilot.protocol;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.blockpilot.api.ArgType;
import dev.blockpilot.api.CommandSpec;
import dev.blockpilot.api.Result;
import org.junit.jupiter.api.Test;

class RegistryTest {
    @Test
    void builtInExamplesParse() {
        // The engine registers its built-ins (help, fmt, sub, stop, wait, sample); each example must parse.
        assertDoesNotThrow(() -> new Engine(new CommandRegistry(), new FakeGame()));
    }

    @Test
    void brokenExamplesAreRejected() {
        CommandSpec broken = CommandSpec.query("look", (context, args) -> Result.ok())
            .optionalArg("radius", ArgType.INT).example("look twelve");
        assertThrows(IllegalStateException.class, () -> new CommandRegistry().register(broken));
        assertNull(CommandRegistry.exampleProblem(CommandSpec.query("look", (context, args) -> Result.ok())
            .optionalArg("radius", ArgType.INT).example("look 12")));
    }
}
