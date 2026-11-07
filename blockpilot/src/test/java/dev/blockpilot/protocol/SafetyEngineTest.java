package dev.blockpilot.protocol;

import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.blockpilot.game.safety.SafetyCommands;
import dev.blockpilot.game.safety.Trust;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The engine's side of agent safety, against a fake game. */
class SafetyEngineTest {
    private Engine engine;

    @BeforeEach
    void setUp() {
        CommandRegistry registry = new CommandRegistry();
        new SafetyCommands(new Trust(List.of(), false)).register(registry);
        engine = new Engine(registry, new FakeGame());
    }

    private String run(String script) {
        return Formatter.text(engine.submit(engine.openSession(), null, script).join());
    }

    @Test
    void trustOnlyReads() {
        assertTrue(run("trust add Mallory").startsWith("err syntax"), run("trust add Mallory"));
        assertTrue(run("trust").startsWith("ok count=0"), run("trust"));
    }
}
