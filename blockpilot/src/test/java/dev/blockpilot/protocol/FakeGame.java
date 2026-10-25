package dev.blockpilot.protocol;

import dev.blockpilot.api.CommandSpec;
import dev.blockpilot.api.Position;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A game for engine tests: client tasks run at once, server tasks wait in {@link #serverTasks} until the test runs
 * them, positions resolve around 0,64,0, releases are counted.
 */
final class FakeGame implements GameBridge {
    final AtomicInteger releases = new AtomicInteger();
    final List<Runnable> serverTasks = new ArrayList<>();
    /** The gate's answer for commands that need a world; null lets them run. */
    String worldGate;
    /** The gate's answer for commands that need the integrated server; null lets them run. */
    String serverGate;
    /** The game time the fake reports; tests advance it. */
    long time;
    /** Whether the fake has finished loading; queued actions wait while false. */
    boolean ready = true;

    @Override
    public void runOnClient(Runnable task) {
        task.run();
    }

    @Override
    public void runOnServer(Runnable task) {
        serverTasks.add(task);
    }

    @Override
    public boolean ready() {
        return ready;
    }

    /** The action gate's answer; null lets actions run. */
    String actionGate;

    @Override
    public String actionGate() {
        return actionGate;
    }

    @Override
    public String gate(CommandSpec spec) {
        if (spec.needsIntegratedServer() && serverGate != null) {
            return serverGate;
        }
        return spec.needsWorldLoaded() ? worldGate : null;
    }

    @Override
    public Position resolve(Position position) {
        return position.resolve(0, 64, 0);
    }

    @Override
    public long gameTime() {
        return time;
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
