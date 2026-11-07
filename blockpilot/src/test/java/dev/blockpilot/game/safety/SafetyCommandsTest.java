package dev.blockpilot.game.safety;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.blockpilot.api.Result;
import dev.blockpilot.api.Row;
import dev.blockpilot.protocol.AllowAllPolicy;
import dev.blockpilot.protocol.ConfiguredPolicy;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SafetyCommandsTest {
    @Test
    void listsTheTrustedPlayersOnlineOrOffline() {
        Trust trust = new Trust();
        trust.update(UUID.randomUUID(), false, Map.of(UUID.randomUUID(), "steve"));
        ConfiguredPolicy policy = new ConfiguredPolicy(List.of("Steve", "Alex"), Set.of(), false, false);
        Result result = new SafetyCommands(() -> policy, trust, false).trust();
        assertEquals(2, result.fields().get("count"));
        assertEquals(List.of("Steve online", "Alex offline"), result.rows().stream().map(Row::text).toList());
    }

    @Test
    void saysWhenTheServerDoesntVerifyAccounts() {
        Trust trust = new Trust();
        trust.update(UUID.randomUUID(), true, Map.of());
        Result result = new SafetyCommands(() -> ConfiguredPolicy.DEFAULT, trust, false).trust();
        assertEquals(true, result.fields().get("unverified"));
        assertEquals(0, result.fields().get("count"));
        assertEquals(null, result.fields().get("trust_unverified"));
    }

    @Test
    void saysWhenEverythingIsAllowed() {
        Result result = new SafetyCommands(() -> AllowAllPolicy.INSTANCE, new Trust(), false).trust();
        assertEquals(true, result.fields().get("dangerously_allow_all"));
    }

    @Test
    void saysWhenADevelopmentRunTrustsUnverifiedServers() {
        Trust trust = new Trust();
        trust.update(UUID.randomUUID(), true, Map.of());
        assertEquals(true, new SafetyCommands(() -> ConfiguredPolicy.DEFAULT, trust, true).trust().fields()
            .get("trust_unverified"));
    }
}
