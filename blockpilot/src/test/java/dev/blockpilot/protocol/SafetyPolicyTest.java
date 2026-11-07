package dev.blockpilot.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class SafetyPolicyTest {
    private static ConfiguredPolicy policy(boolean playOnly, Set<String> allowed, List<String> slashCommands) {
        return new ConfiguredPolicy(List.of(), allowed, playOnly, false, slashCommands);
    }

    @Test
    void aSlashCommandsRootIsItsFirstWordAsTheGameReadsIt() {
        assertEquals("fill", SafetyPolicy.rootOf("/fill ~ ~ ~ ~ ~ ~ stone"));
        assertEquals("fill", SafetyPolicy.rootOf("minecraft:fill ~ ~ ~ ~ ~ ~ stone"));
        assertEquals("fill", SafetyPolicy.rootOf("FILL"));
        assertEquals("op", SafetyPolicy.rootOf("  /Minecraft:Op  Steve"));
        assertEquals("", SafetyPolicy.rootOf(""));
        assertEquals("", SafetyPolicy.rootOf("/"));
    }

    @Test
    void aPlayOnlyAgentSendsOnlyListedRoots() {
        SafetyPolicy play = policy(true, Set.of(), List.of("Fill"));
        assertTrue(play.slashCommandAllowed("fill"));
        assertFalse(play.slashCommandAllowed("op"));
        assertFalse(play.slashCommandAllowed(""));
        assertTrue(policy(false, Set.of(), List.of()).slashCommandAllowed("op"));
        assertTrue(AllowAllPolicy.INSTANCE.slashCommandAllowed("op"));
    }

    @Test
    void theConfigFileChoosesThePolicy() {
        Config config = new Config();
        config.agentCommands = "play";
        config.playSlashCommands = List.of("fill");
        SafetyPolicy configured = SafetyPolicy.from(config, false);
        assertSame(ConfiguredPolicy.class, configured.getClass());
        assertTrue(configured.slashCommandAllowed("fill"));
        assertFalse(configured.slashCommandAllowed("op"));
        config.dangerouslyAllowAll = true;
        assertSame(AllowAllPolicy.INSTANCE, SafetyPolicy.from(config, false));
    }

    /** Every policy is immutable and swapped whole, so a reader never sees play-only beside "safety off". */
    @Test
    void aReaderOnAnotherThreadAlwaysSeesOneWholePolicy() throws InterruptedException {
        SafetyPolicy configured = policy(true, Set.of("mymod"), List.of());
        AtomicReference<SafetyPolicy> policy = new AtomicReference<>(configured);
        AtomicBoolean done = new AtomicBoolean();
        AtomicInteger torn = new AtomicInteger();
        AtomicInteger reads = new AtomicInteger();
        Thread reader = new Thread(() -> {
            while (!done.get()) {
                SafetyPolicy now = policy.get();
                if (now.playOnly() == !now.indicatorNote().isEmpty()) {
                    torn.incrementAndGet();
                }
                reads.incrementAndGet();
            }
        });
        reader.start();
        for (int swap = 0; swap < 200_000; swap++) {
            policy.set(swap % 2 == 0 ? AllowAllPolicy.INSTANCE : configured);
        }
        done.set(true);
        reader.join(10_000);
        assertEquals(0, torn.get(), "reads: " + reads.get());
    }
}
