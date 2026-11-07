package dev.blockpilot.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class SafetyPolicyTest {
    @Test
    void theConfigFileChoosesThePolicy() {
        Config config = new Config();
        assertSame(ConfiguredPolicy.class, SafetyPolicy.from(config, false).getClass());
        config.dangerouslyAllowAll = true;
        assertSame(AllowAllPolicy.INSTANCE, SafetyPolicy.from(config, false));
    }

    /** Every policy is immutable and swapped whole, so a reader never sees play-only beside "safety off". */
    @Test
    void aReaderOnAnotherThreadAlwaysSeesOneWholePolicy() throws InterruptedException {
        SafetyPolicy configured = new ConfiguredPolicy(List.of("Steve"), Set.of("mymod"), true, false);
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
