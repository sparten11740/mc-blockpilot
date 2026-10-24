package dev.blockpilot.protocol;

import dev.blockpilot.api.CommandSpec;
import dev.blockpilot.api.Position;
import java.util.concurrent.atomic.AtomicInteger;

/** A game for engine tests: tasks run at once, positions resolve around 0,64,0, releases are counted. */
final class FakeGame implements GameBridge {
    final AtomicInteger releases = new AtomicInteger();
    /** The gate's answer for commands that need a world; null lets them run. */
    String worldGate;

    @Override
    public void runOnClient(Runnable task) {
        task.run();
    }

    @Override
    public String gate(CommandSpec spec) {
        return spec.needsWorldLoaded() ? worldGate : null;
    }

    @Override
    public Position resolve(Position position) {
        return position.resolve(0, 64, 0);
    }

    @Override
    public void releaseInputs() {
        releases.incrementAndGet();
    }

    @Override
    public void releaseInputs(int sessionId) {
        releases.incrementAndGet();
    }
}
