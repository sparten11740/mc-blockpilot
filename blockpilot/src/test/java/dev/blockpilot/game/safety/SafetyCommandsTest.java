package dev.blockpilot.game.safety;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.blockpilot.api.Result;
import dev.blockpilot.api.Row;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SafetyCommandsTest {
    @Test
    void listsTheTrustedPlayersOnlineOrOffline() {
        Trust trust = new Trust(List.of("Steve", "Alex"));
        trust.update(UUID.randomUUID(), false, Map.of(UUID.randomUUID(), "steve"));
        Result result = new SafetyCommands(trust).trust();
        assertEquals(2, result.fields().get("count"));
        assertEquals(List.of("Steve online", "Alex offline"), result.rows().stream().map(Row::text).toList());
    }

    @Test
    void saysWhenTheServerDoesntVerifyAccounts() {
        Trust trust = new Trust(List.of());
        trust.update(UUID.randomUUID(), true, Map.of());
        Result result = new SafetyCommands(trust).trust();
        assertEquals(true, result.fields().get("unverified"));
        assertEquals(0, result.fields().get("count"));
    }
}
