package dev.blockpilot.game.safety;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TrustTest {
    private static final UUID SELF = UUID.randomUUID();
    private static final UUID STEVE = UUID.randomUUID();
    private static final UUID MALLORY = UUID.randomUUID();

    private static Trust trust(List<String> names, boolean unverified) {
        Trust trust = new Trust(names, false);
        trust.update(SELF, unverified, Map.of(SELF, "Agent", STEVE, "Steve", MALLORY, "Mallory"));
        return trust;
    }

    @Test
    void nobodyButTheAgentsOwnPlayerByDefault() {
        Trust trust = trust(List.of(), false);
        assertFalse(trust.trusts(STEVE));
        assertTrue(trust.trusts(SELF));
        assertEquals("withheld from=Steve", trust.withheld(STEVE, "Steve"));
        assertNull(trust.withheld(SELF, "Agent"));
    }

    @Test
    void trustedNamesMatchIgnoringCase() {
        Trust trust = trust(List.of("steve"), false);
        assertTrue(trust.trusts(STEVE));
        assertNull(trust.withheld(STEVE, "Steve"));
        assertEquals("withheld from=Mallory", trust.withheld(MALLORY, "Mallory"));
    }

    @Test
    void nobodyElseWhereAccountsArentVerified() {
        Trust trust = trust(List.of("Steve"), true);
        assertFalse(trust.trusts(STEVE));
        assertTrue(trust.trusts(SELF));
    }

    @Test
    void aDevelopmentRunTrustsTheListWhereAccountsArentVerified() {
        Trust trust = new Trust(List.of("Steve"), true);
        trust.update(SELF, true, Map.of(STEVE, "Steve", MALLORY, "Mallory"));
        assertTrue(trust.trusts(STEVE));
        assertFalse(trust.trusts(MALLORY));
    }

    @Test
    void anUnknownSenderIsNamedByUuid() {
        Trust trust = trust(List.of("Steve"), false);
        UUID stranger = UUID.randomUUID();
        assertEquals("withheld from=" + stranger, trust.withheld(stranger, null));
    }

    @Test
    void anOfflineServersUuidGivesTheAccountAway() {
        UUID offline = UUID.nameUUIDFromBytes("OfflinePlayer:Steve".getBytes(StandardCharsets.UTF_8));
        assertTrue(Trust.offlineAccount(offline, "Steve"));
        assertFalse(Trust.offlineAccount(UUID.randomUUID(), "Steve"));
    }

    @Test
    void aNewListReplacesTheOldAndKeepsTheNamesAsWritten() {
        Trust trust = trust(List.of(), false);
        trust.set(List.of("STEVE"));
        assertEquals(List.of("STEVE"), trust.names());
        assertTrue(trust.trusts(STEVE));
        assertTrue(trust.online("steve"));
        trust.set(List.of());
        assertFalse(trust.trusts(STEVE));
    }
}
