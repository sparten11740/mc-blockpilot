package dev.blockpilot.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigTest {
    @TempDir
    Path directory;

    @Test
    void writesDefaultsWhenMissing() throws Exception {
        Path file = directory.resolve("blockpilot/config.json");
        Config config = Config.load(file);
        assertEquals(25599, config.port);
        assertEquals("127.0.0.1", config.bind);
        assertTrue(Files.readString(file).contains("\"portTries\": 10"));
    }

    @Test
    void readsSavedValues() throws Exception {
        Path file = directory.resolve("config.json");
        Files.writeString(file, "{\"port\": 26000, \"maxRows\": 8}");
        Config config = Config.load(file);
        assertEquals(26000, config.port);
        assertEquals(8, config.maxRows);
        assertEquals("127.0.0.1", config.bind);
    }

    @Test
    void fallsBackOnBrokenFiles() throws Exception {
        Path file = directory.resolve("config.json");
        Files.writeString(file, "{ not json");
        assertEquals(25599, Config.load(file).port);
    }

    @Test
    void trustsNobodyAndAllowsEveryCommandByDefault() {
        Config config = Config.load(directory.resolve("config.json"));
        assertEquals(List.of(), config.trustedPlayers);
        assertFalse(SafetyPolicy.from(config, false).playOnly());
    }

    @Test
    void aBrokenFileFailsClosedAndIsLeftAlone() throws Exception {
        Path file = directory.resolve("config.json");
        String broken = "{\"agentCommands\": \"play\", \"trustedPlayers\": [\"Steve\"";
        Files.writeString(file, broken);
        Config config = Config.load(file);
        assertTrue(SafetyPolicy.from(config, false).playOnly());
        assertEquals(List.of(), config.trustedPlayers);
        assertEquals(broken, Files.readString(file));
    }

    @Test
    void aTrailingCommaOrANullNameIsDropped() throws Exception {
        Path file = directory.resolve("config.json");
        Files.writeString(file, "{\"trustedPlayers\": [\"Steve\",]}");
        assertEquals(List.of("Steve"), Config.load(file).trustedPlayers);
        Files.writeString(file, "{\"trustedPlayers\": [null, \" \"]}");
        assertEquals(List.of(), Config.load(file).trustedPlayers);
        Files.writeString(file, "{\"playSlashCommands\": [null, \" \", \"fill\"]}");
        assertEquals(List.of("fill"), Config.load(file).playSlashCommands);
    }

    @Test
    void allowingEverythingIsOffByDefaultAndInABrokenFile() throws Exception {
        Path file = directory.resolve("config.json");
        assertFalse(Config.load(file).dangerouslyAllowAll);
        Files.writeString(file, "{\"dangerouslyAllowAll\": true, ");
        Config broken = Config.load(file);
        assertFalse(broken.dangerouslyAllowAll);
        assertTrue(SafetyPolicy.from(broken, false).playOnly());
    }

    @Test
    void allowingEverythingIgnoresThePlayProfile() throws Exception {
        Path file = directory.resolve("config.json");
        Files.writeString(file, "{\"dangerouslyAllowAll\": true, \"agentCommands\": \"play\"}");
        assertFalse(SafetyPolicy.from(Config.load(file), false).playOnly());
    }

    @Test
    void anUnknownCommandProfileHoldsAgentsToPlaying() throws Exception {
        Path file = directory.resolve("config.json");
        Files.writeString(file, "{\"agentCommands\": \"everything\", \"trustedPlayers\": null}");
        Config config = Config.load(file);
        assertTrue(SafetyPolicy.from(config, false).playOnly());
        assertEquals(List.of(), config.trustedPlayers);
    }
}
