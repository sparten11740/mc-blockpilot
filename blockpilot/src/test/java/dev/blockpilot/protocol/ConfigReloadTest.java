package dev.blockpilot.protocol;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigReloadTest {
    @TempDir
    Path directory;

    private final List<Config> applied = new ArrayList<>();

    private ConfigReload reload(String text) throws Exception {
        Path file = directory.resolve("config.json");
        Files.writeString(file, text);
        return new ConfigReload(file, applied::add);
    }

    private void write(String text) throws Exception {
        Files.writeString(directory.resolve("config.json"), text);
    }

    @Test
    void anUnchangedFileAppliesNothing() throws Exception {
        ConfigReload reload = reload("{\"trustedPlayers\": [\"Steve\"]}");
        reload.check();
        assertTrue(applied.isEmpty());
    }

    @Test
    void aChangeIsApplied() throws Exception {
        ConfigReload reload = reload("{}");
        write("{\"trustedPlayers\": [\"Steve\"], \"agentCommands\": \"play\"}");
        reload.check();
        assertEquals(1, applied.size());
        assertEquals(List.of("Steve"), applied.get(0).trustedPlayers);
        assertTrue(applied.get(0).playOnly());
    }

    @Test
    void twoQuickChangesAreBothSeen() throws Exception {
        ConfigReload reload = reload("{}");
        write("{\"trustedPlayers\": [\"Steve\"]}");
        reload.check();
        write("{}");
        reload.check();
        assertEquals(List.of(), applied.get(1).trustedPlayers);
    }

    @Test
    void aBrokenFileKeepsTheLastGoodSettingsUntilFixed() throws Exception {
        ConfigReload reload = reload("{}");
        write("{\"trustedPlayers\": [\"Steve\"");
        reload.check();
        reload.check();
        assertTrue(applied.isEmpty());
        write("{\"trustedPlayers\": [\"Steve\"]}");
        reload.check();
        assertEquals(List.of("Steve"), applied.get(0).trustedPlayers);
    }

    @Test
    void aTrailingCommaReloadsWithoutTheNullName() throws Exception {
        ConfigReload reload = reload("{}");
        write("{\"trustedPlayers\": [\"Steve\",]}");
        reload.check();
        assertEquals(List.of("Steve"), applied.get(0).trustedPlayers);
    }

    @Test
    void aFailingApplyStaysInsideTheReload() throws Exception {
        Path file = directory.resolve("config.json");
        Files.writeString(file, "{}");
        ConfigReload reload = new ConfigReload(file, read -> {
            throw new IllegalStateException("broken apply");
        });
        Files.writeString(file, "{\"trustedPlayers\": [\"Steve\"]}");
        assertDoesNotThrow(reload::check);
        assertDoesNotThrow(reload::check);
    }

    @Test
    void readingDoesntWriteTheFile() throws Exception {
        ConfigReload reload = reload("{}");
        write("{\"trustedPlayers\": [\"Steve\"]}");
        reload.check();
        assertEquals("{\"trustedPlayers\": [\"Steve\"]}", Files.readString(directory.resolve("config.json")));
    }
}
