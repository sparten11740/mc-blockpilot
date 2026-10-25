package dev.blockpilot.protocol;

import dev.blockpilot.api.Action;
import dev.blockpilot.api.Args;
import dev.blockpilot.api.CommandContext;
import dev.blockpilot.api.CommandException;
import dev.blockpilot.api.CommandSpec;
import dev.blockpilot.api.Duration;
import dev.blockpilot.api.Position;
import dev.blockpilot.api.Result;
import dev.blockpilot.api.Row;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * One request's script being executed. Queries run as soon as the job is stepped; an action keeps the job busy
 * until it finishes, one tick per step. Steps happen on the client thread, or on the server thread for commands
 * that run there; a job only advances on the thread its next command needs.
 */
final class ScriptJob implements CommandContext {
    private static final Logger LOGGER = LoggerFactory.getLogger("Blockpilot");
    static final Duration DEFAULT_TIMEOUT = Duration.seconds(60);

    enum Affinity { CLIENT, SERVER }

    /** An action in progress and its bookkeeping. */
    private record Running(Action action, String name, Affinity affinity, int timeoutTicks, int[] elapsed) {
    }

    private final Engine engine;
    private final Session session;
    private final String id;
    private final List<RawCommand> commands;
    private final List<Result> results = new ArrayList<>();
    private final CompletableFuture<Reply> future = new CompletableFuture<>();
    /**
     * Guards the job's progress. Steps only try to take it: a job busy on one thread (say the client, blocked in a
     * world load that waits for the integrated server) must never make the other thread wait, or they deadlock.
     */
    private final ReentrantLock lock = new ReentrantLock();
    private int index;
    /** The last command whose gate passed; commands up to it may run on either thread without a client tick. */
    private int gatedThrough = -1;
    private Running running;
    private volatile String activity;
    /** A cancel that arrived on the thread already inside this job's step; applied when that step ends. */
    private String pendingCancel;

    ScriptJob(Engine engine, Session session, String id, List<RawCommand> commands) {
        this.engine = engine;
        this.session = session;
        this.id = id;
        this.commands = commands;
    }

    CompletableFuture<Reply> future() {
        return future;
    }

    Session session() {
        return session;
    }

    @Override
    public int sessionId() {
        return session.id();
    }

    @Override
    public Position resolve(Position position) throws CommandException {
        return engine.game().resolve(position);
    }

    /** The running action's name for the HUD, or null. */
    String activity() {
        return activity;
    }

    /** Advances the job as far as it can on this thread; true once it is complete. */
    boolean step(Affinity affinity) {
        // Tasks run while the client thread waits inside a command (a world load) must not re-enter that job.
        if (lock.isHeldByCurrentThread() || !lock.tryLock()) {
            return false;
        }
        try {
            boolean done = advance(affinity);
            if (pendingCancel != null) {
                cancelLocked(pendingCancel);
                return true;
            }
            return done;
        } finally {
            activity = running == null ? null : running.name();
            lock.unlock();
        }
    }

    private boolean advance(Affinity affinity) {
        while (index < commands.size()) {
            if (running != null) {
                if (running.affinity() != affinity) {
                    return false;
                }
                Result result = tickRunning();
                if (result == null) {
                    return false;
                }
                running = null;
                finish(result);
                continue;
            }
            RawCommand command = commands.get(index);
            CommandRegistry.Entry entry = engine.registry().resolve(command.name());
            if (entry == null) {
                finish(Result.error("syntax", "unknown command " + command.name() + "; try help"));
                continue;
            }
            CommandSpec spec = entry.spec();
            Affinity needed = spec.runsOnServer() ? Affinity.SERVER : Affinity.CLIENT;
            // The gate reads client state, so it is checked on the client thread before anything else runs.
            if (gatedThrough < index) {
                if (affinity != Affinity.CLIENT) {
                    return false;
                }
                String gate = engine.game().gate(spec);
                if (gate != null) {
                    finish(Result.error(gate));
                    continue;
                }
                gatedThrough = gateQueriesAfter(index);
            }
            if (needed != affinity) {
                return false;
            }
            boolean full = spec.isDiffable() && command.has("full");
            RawCommand effective = full ? command.without("full") : command;
            try {
                Args args = ArgParser.parse(spec, effective);
                if (spec.kind() == CommandSpec.Kind.QUERY) {
                    Result result = spec.queryHandler().run(this, args);
                    finish(spec.isDiffable() ? diff(entry, effective, result, full) : result);
                } else {
                    Action action = spec.actionHandler().start(this, args);
                    Duration timeout = args.durationOption("timeout");
                    int timeoutTicks = action instanceof Engine.SelfTimed ? Integer.MAX_VALUE
                        : (timeout != null ? timeout : DEFAULT_TIMEOUT).ticks();
                    Affinity actionAffinity = action instanceof Engine.ServerClocked clocked && clocked.onServerClock()
                        ? Affinity.SERVER : needed;
                    running = new Running(action, entry.qualifiedName(), actionAffinity, timeoutTicks, new int[1]);
                }
            } catch (CommandException e) {
                finish(e.toResult());
            } catch (RuntimeException e) {
                finish(crash(entry, e));
            }
        }
        complete();
        return true;
    }

    /**
     * Gates the queries that follow {@code from} up to the next action (or the first gate that fails), so a run of
     * server-side queries is answered in one server tick instead of one per client tick. Queries change nothing, so
     * their gates still hold when they run; what follows an action is gated after it. Returns the last gated index.
     */
    private int gateQueriesAfter(int from) {
        int last = from;
        while (last + 1 < commands.size()) {
            CommandRegistry.Entry next = engine.registry().resolve(commands.get(last + 1).name());
            boolean query = next != null && next.spec().kind() == CommandSpec.Kind.QUERY;
            if (!query || engine.game().gate(next.spec()) != null) {
                break;
            }
            last++;
        }
        return last;
    }

    private Result tickRunning() {
        try {
            Result result = running.action().tick();
            if (result != null) {
                return result;
            }
        } catch (CommandException e) {
            return e.toResult();
        } catch (RuntimeException e) {
            CommandRegistry.Entry entry = engine.registry().resolve(running.name());
            running.action().cancel();
            engine.game().releaseInputs();
            return crash(entry, e);
        }
        int elapsed = ++running.elapsed()[0];
        if (elapsed >= running.timeoutTicks()) {
            running.action().cancel();
            return Result.error("timeout", new Duration(elapsed).seconds() + "s");
        }
        return null;
    }

    private Result diff(CommandRegistry.Entry entry, RawCommand command, Result result, boolean full) {
        List<Row> rows = result.rows();
        if (!result.isOk() || rows == null) {
            return result;
        }
        String key = entry.qualifiedName() + " " + command.argumentText();
        List<Row> previous = session.swapRows(key, rows);
        if (previous == null || full) {
            return result;
        }
        return result.withDiff(Diffs.between(previous, rows));
    }

    private Result crash(CommandRegistry.Entry entry, RuntimeException e) {
        String name = entry == null ? "?" : entry.qualifiedName();
        LOGGER.error("Command {} failed", name, e);
        String message = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
        if (entry != null && !entry.namespace().isEmpty()) {
            return Result.error("ext_failed", name + " " + message);
        }
        return Result.error("internal", name + " " + message);
    }

    private void finish(Result result) {
        results.add(result);
        boolean soft = commands.get(index).soft();
        index++;
        if (!result.isOk() && !soft) {
            // A failing script must not leave keys or buttons held that its earlier commands pressed.
            if (hasAction()) {
                engine.game().releaseInputs(session.id());
            }
            skipRest();
        }
    }

    private void skipRest() {
        while (index < commands.size()) {
            results.add(Result.skip());
            index++;
        }
    }

    /** Stops the job: the running action is cancelled and every command not yet done fails with the code. */
    void cancel(String code) {
        if (lock.isHeldByCurrentThread()) {
            // Called from a task the client runs while it waits inside this job's own step: apply it afterwards.
            pendingCancel = code;
            return;
        }
        lock.lock();
        try {
            cancelLocked(code);
        } finally {
            lock.unlock();
        }
    }

    private void cancelLocked(String code) {
        if (future.isDone()) {
            return;
        }
        if (running != null) {
            Action action = running.action();
            if (running.affinity() == Affinity.SERVER) {
                // Server-thread actions start and tick on the server thread, so they are cancelled there too.
                engine.game().runOnServer(action::cancel);
            } else {
                action.cancel();
            }
            running = null;
            activity = null;
        }
        if (index < commands.size()) {
            // A cancelled script stops even at a soft (?) command.
            results.add(Result.error(code));
            index++;
            skipRest();
        }
        complete();
    }

    private void complete() {
        if (!future.isDone()) {
            future.complete(new Reply(id, List.copyOf(results), session.drainEvents()));
        }
    }

    boolean hasAction() {
        for (RawCommand command : commands) {
            CommandRegistry.Entry entry = engine.registry().resolve(command.name());
            if (entry != null && entry.spec().kind() == CommandSpec.Kind.ACTION) {
                return true;
            }
        }
        return false;
    }
}
