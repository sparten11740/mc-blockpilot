package dev.blockpilot.game.perf;

import com.google.gson.JsonObject;
import dev.blockpilot.platform.ClientPlatform;
import java.util.Arrays;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BooleanSupplier;

/**
 * Samples a thread's stack on its own thread, 20 times a second on average. The gaps vary from 35 to 65 ms, so the
 * samples don't keep landing on the same phase of a 50 ms tick.
 */
final class StackSampler {
    private static final int MIN_GAP_MS = 35;
    private static final int MAX_GAP_MS = 65;
    /** How many top frames the summary ranks. */
    private static final int TOP_FRAMES = 20;

    private final Thread target;
    private final BooleanSupplier ticking;
    private final StackSamples samples = new StackSamples(ClientPlatform::modOf);
    private final Thread sampler;
    private volatile boolean running = true;

    /** Starts sampling {@code target}, keeping the samples taken while {@code ticking} holds. */
    StackSampler(Thread target, BooleanSupplier ticking) {
        this.target = target;
        this.ticking = ticking;
        sampler = new Thread(this::run, "Blockpilot stack sampler");
        sampler.setDaemon(true);
        sampler.start();
    }

    private void run() {
        while (running) {
            try {
                Thread.sleep(ThreadLocalRandom.current().nextInt(MIN_GAP_MS, MAX_GAP_MS + 1));
            } catch (InterruptedException e) {
                return;
            }
            if (!running) {
                return;
            }
            if (!ticking.getAsBoolean()) {
                samples.skip();
                continue;
            }
            samples.add(Arrays.stream(target.getStackTrace()).map(frame -> new StackSamples.Frame(frame.getClassName(),
                frame.getMethodName(), frame.isNativeMethod())).toList());
        }
    }

    /** Stops sampling and gives the summary. */
    JsonObject stop() {
        running = false;
        sampler.interrupt();
        try {
            sampler.join(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return samples.summary(TOP_FRAMES);
    }
}
