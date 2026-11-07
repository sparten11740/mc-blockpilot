package dev.blockpilot.game.safety;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.blockpilot.protocol.AllowAllPolicy;
import dev.blockpilot.protocol.ConfiguredPolicy;
import dev.blockpilot.protocol.Connection;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TrustTest {
    private static final UUID SELF = UUID.randomUUID();
    private static final UUID STEVE = UUID.randomUUID();
    private static final UUID MALLORY = UUID.randomUUID();

    private static Connection connection(boolean unverified) {
        return new Connection(SELF, unverified, Map.of(SELF, "Agent", STEVE, "Steve", MALLORY, "Mallory"));
    }

    private static ConfiguredPolicy policy(List<String> names) {
        return new ConfiguredPolicy(names, Set.of(), false, false, List.of());
    }

    @Test
    void nobodyButTheAgentsOwnPlayerByDefault() {
        ConfiguredPolicy policy = policy(List.of());
        assertFalse(policy.trusts(STEVE, connection(false)));
        assertTrue(policy.trusts(SELF, connection(false)));
        assertEquals("withheld from=Steve", Trust.withheld(STEVE, "Steve"));
    }

    @Test
    void trustedNamesMatchIgnoringCase() {
        ConfiguredPolicy policy = policy(List.of("steve"));
        assertTrue(policy.trusts(STEVE, connection(false)));
        assertFalse(policy.trusts(MALLORY, connection(false)));
    }

    @Test
    void nobodyElseWhereAccountsArentVerified() {
        ConfiguredPolicy policy = policy(List.of("Steve"));
        assertFalse(policy.trusts(STEVE, connection(true)));
        assertTrue(policy.trusts(SELF, connection(true)));
    }

    @Test
    void aDevelopmentRunTrustsTheListWhereAccountsArentVerified() {
        ConfiguredPolicy policy = new ConfiguredPolicy(List.of("Steve"), Set.of(), false, true, List.of());
        assertTrue(policy.trusts(STEVE, connection(true)));
        assertFalse(policy.trusts(MALLORY, connection(true)));
    }

    @Test
    void allowingEverythingTrustsEveryoneEvenUnverified() {
        assertTrue(AllowAllPolicy.INSTANCE.trusts(MALLORY, connection(true)));
        assertFalse(policy(List.of()).trusts(MALLORY, connection(true)));
    }

    @Test
    void anUnknownSenderIsNamedByUuid() {
        UUID stranger = UUID.randomUUID();
        assertFalse(policy(List.of("Steve")).trusts(stranger, connection(false)));
        assertEquals("withheld from=" + stranger, Trust.withheld(stranger, null));
    }

    @Test
    void anOfflineServersUuidGivesTheAccountAway() {
        UUID offline = UUID.nameUUIDFromBytes("OfflinePlayer:Steve".getBytes(StandardCharsets.UTF_8));
        assertTrue(Trust.offlineAccount(offline, "Steve"));
        assertFalse(Trust.offlineAccount(UUID.randomUUID(), "Steve"));
    }

    @Test
    void aNewListReplacesTheOldAndKeepsTheNamesAsWritten() {
        ConfiguredPolicy policy = policy(List.of("STEVE"));
        assertEquals(List.of("STEVE"), policy.trustedNames());
        assertTrue(policy.trusts(STEVE, connection(false)));
        assertTrue(connection(false).online("steve"));
        assertFalse(policy(List.of()).trusts(STEVE, connection(false)));
    }
}
