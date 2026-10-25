package dev.blockpilot.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class OptionsTest {
    @Test
    void defaults() {
        assertEquals(new Options(Path.of("run"), false, 300, 0, false, false, false, "status"),
            Options.parse("status"));
    }

    @Test
    void aLaterGameDirWinsOverTheLaunchersPreset() {
        Options options = Options.parse("--game-dir", "/games/a", "--json", "--game-dir=/games/b", "--timeout=2.5",
            "--wait-ready", "180", "--wait-world", "inv");
        assertEquals(new Options(Path.of("/games/b"), true, 2.5, 180, true, false, false, "inv"), options);
    }

    @Test
    void aScriptMayFollowTheEndOfOptions() {
        assertEquals("--json", Options.parse("--", "--json").script());
    }

    @Test
    void mcpTakesNoScript() {
        assertTrue(Options.parse("--game-dir", "x", "--mcp").mcp());
        assertThrows(IllegalArgumentException.class, () -> Options.parse("--mcp", "status"));
    }

    @Test
    void helpNeedsNoScript() {
        assertTrue(Options.parse("--game-dir", "x", "--help").help());
    }

    @Test
    void refusesWhatItCantRead() {
        for (String[] args : new String[][] {{}, {"a", "b"}, {"--timeout", "soon", "a"}, {"--timeout=-1", "a"},
            {"--json=yes", "a"}, {"--verbose", "a"}, {"a", "--game-dir"}}) {
            assertThrows(IllegalArgumentException.class, () -> Options.parse(args), String.join(" ", args));
        }
    }
}
