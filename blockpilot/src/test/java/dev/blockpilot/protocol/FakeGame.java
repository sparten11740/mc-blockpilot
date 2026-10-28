package dev.blockpilot.protocol;

import dev.blockpilot.api.CommandException;
import dev.blockpilot.api.CommandSpec;
import dev.blockpilot.api.Position;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * A game for engine tests: client tasks run at once, server tasks wait in {@link #serverTasks} until the test runs
 * them, calls on the server run at once and are counted, positions resolve around 0,64,0, releases are counted.
 */
final class FakeGame implements GameBridge {
    final AtomicInteger releases = new AtomicInteger();
    final List<Runnable> serverTasks = new ArrayList<>();
    int serverCalls;
    /** Whether a call on the server is running, for queries that note where they ran. */
    boolean inServerCall;
    /** The error code calls on the server fail with; null lets them run. */
    String serverCallFailure;
    /** What else happens while a call on the server runs, say the server ending a tick. */
    Runnable duringServerCall = () -> { };
    /** The gate's answer for commands that need a world; null lets them run. */
    String worldGate;
    /** The gate's answer for commands that need the integrated server; null lets them run. */
    String serverGate;
    /** The game time the fake reports; tests advance it. */
    long time;
    /** Whether the fake has finished loading; queued actions wait while false. */
    boolean ready = true;
    /** Whether the fake's integrated server is paused. */
    boolean serverPaused;
    /** What the fake says kept the client busy, or null. */
    String stallHint;
    /** The client ticks the fake reports; the tests' tick helper advances it. */
    long clientTicks;
    /** The server load the fake reports, in ms. */
    int serverLoad;

    @Override
    public void runOnClient(Runnable task) {
        task.run();
    }

    @Override
    public void runOnServer(Runnable task) {
        serverTasks.add(task);
    }

    @Override
    public <T> T callOnServer(Supplier<T> task) throws CommandException {
        serverCalls++;
        if (serverCallFailure != null) {
            throw new CommandException(serverCallFailure);
        }
        inServerCall = true;
        duringServerCall.run();
        try {
            return task.get();
        } finally {
            inServerCall = false;
        }
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
    public boolean serverPaused() {
        return serverPaused;
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
    public long clientTicks() {
        return clientTicks;
    }

    @Override
    public int serverLoad() {
        return serverLoad;
    }

    @Override
    public String stallHint() {
        return stallHint;
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
