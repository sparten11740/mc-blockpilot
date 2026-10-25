package dev.blockpilot.protocol;

import dev.blockpilot.api.Action;
import dev.blockpilot.api.CommandContext;
import dev.blockpilot.api.CommandException;
import dev.blockpilot.api.CommandSpec;
import dev.blockpilot.api.Duration;
import dev.blockpilot.api.Fields;
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
 * One request's parsed script being executed. Queries run as soon as the job is stepped; an action keeps the job busy
 * until it finishes, one tick per step. Steps happen on the client thread, or on the server thread for commands
 * that run there; a job only advances on the thread its next statement needs.
 */
final class ScriptJob implements CommandContext {
    private static final Logger LOGGER = LoggerFactory.getLogger("Blockpilot");
    static final Duration DEFAULT_TIMEOUT = Duration.seconds(60);

    enum Affinity { CLIENT, SERVER }

    /** An action in progress; {@code entry} is null for a construct's action. */
    private record Running(Action action, String name, CommandRegistry.Entry entry, Affinity affinity,
        int timeoutTicks, int[] elapsed) {
    }

    /** Where a statement runs: the command whose gate applies (if any), whether it acts, and on which thread. */
    private record Placement(CommandSpec gated, boolean action, Affinity affinity) {
    }

    private final Engine engine;
    private final Session session;
    private final String id;
    /** An if that holds puts its statement in its own place. */
    private final List<Parser.Line> lines;
    private final List<Result> results = new ArrayList<>();
    private final CompletableFuture<Reply> future = new CompletableFuture<>();
    /**
     * Guards the job's progress. Steps only try to take it: a job busy on one thread (say the client, blocked in a
     * world load that waits for the integrated server) must never make the other thread wait, or they deadlock.
     */
    private final ReentrantLock lock = new ReentrantLock();
    private int index;
    /** The last statement whose gate passed; statements up to it may run on either thread without a client tick. */
    private int gatedThrough = -1;
    private Running running;
    private volatile String activity;
    /** A cancel that arrived on the thread already inside this job's step; applied when that step ends. */
    private String pendingCancel;

    ScriptJob(Engine engine, Session session, String id, List<Parser.Line> lines) {
        this.engine = engine;
        this.session = session;
        this.id = id;
        this.lines = new ArrayList<>(lines);
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
        while (index < lines.size()) {
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
            Parser.Line line = lines.get(index);
            if (line.error() != null) {
                // Only a soft step's syntax error gets this far; the engine refuses scripts with other ones.
                finish(Result.error("syntax", line.error()));
                continue;
            }
            Statement statement = line.statement();
            Placement placement = placement(statement);
            // The gate reads client state, so it is checked on the client thread before anything else runs.
            if (gatedThrough < index) {
                if (affinity != Affinity.CLIENT) {
                    return false;
                }
                String gate = gate(placement);
                if (gate != null) {
                    finish(Result.error(gate));
                    continue;
                }
                gatedThrough = gateQueriesAfter(index);
            }
            if (placement.affinity() != affinity) {
                return false;
            }
            try {
                run(statement);
            } catch (CommandException e) {
                finish(e.toResult());
            } catch (RuntimeException e) {
                finish(crash(name(statement), statement instanceof Statement.Call call ? call.entry() : null, e));
            }
        }
        complete();
        return true;
    }

    private static Placement placement(Statement statement) {
        return switch (statement) {
            case Statement.Call call -> new Placement(call.entry().spec(),
                call.entry().spec().kind() == CommandSpec.Kind.ACTION, affinity(call));
            case Statement.Assert check -> new Placement(check.query().entry().spec(), false, affinity(check.query()));
            case Statement.If check -> new Placement(check.query().entry().spec(), false, affinity(check.query()));
            case Statement.WaitUntil wait -> new Placement(wait.query().entry().spec(), true, Affinity.CLIENT);
            case Statement.Sample sample -> new Placement(sample.query().entry().spec(), true, Affinity.CLIENT);
            case Statement.WaitTicks wait -> new Placement(null, true, Affinity.CLIENT);
            case Statement.WaitIdle wait -> new Placement(null, true, Affinity.CLIENT);
        };
    }

    private static Affinity affinity(Statement.Call call) {
        return call.entry().spec().runsOnServer() ? Affinity.SERVER : Affinity.CLIENT;
    }

    private static String name(Statement statement) {
        return switch (statement) {
            case Statement.Call call -> call.entry().qualifiedName();
            case Statement.WaitTicks wait -> "wait";
            case Statement.WaitIdle wait -> "wait";
            case Statement.WaitUntil wait -> "wait";
            case Statement.Sample sample -> "sample";
            case Statement.Assert check -> "assert";
            case Statement.If check -> "if";
        };
    }

    private String gate(Placement placement) {
        String gate = placement.action() ? engine.game().actionGate() : null;
        return gate != null || placement.gated() == null ? gate : engine.game().gate(placement.gated());
    }

    private void run(Statement statement) throws CommandException {
        switch (statement) {
            case Statement.Call call -> runCall(call);
            case Statement.Assert check -> finish(assertion(check.condition(), check.query(), query(check.query())));
            case Statement.If check -> conditional(check);
            case Statement.WaitTicks wait -> start(engine.waitTicks(wait), "wait", null, null);
            case Statement.WaitIdle wait -> start(() -> Result.ok(), "wait", null, null);
            case Statement.WaitUntil wait -> start(engine.waitUntil(this, wait), "wait", null, null);
            case Statement.Sample sample -> start(engine.sample(this, sample), "sample", null, null);
        }
    }

    private void runCall(Statement.Call call) throws CommandException {
        CommandSpec spec = call.entry().spec();
        if (spec.kind() == CommandSpec.Kind.QUERY) {
            Result result = spec.queryHandler().run(this, call.args());
            finish(spec.isDiffable() ? diff(call, result) : result);
        } else {
            start(spec.actionHandler().start(this, call.args()), call.entry().qualifiedName(), call.entry(),
                call.args().durationOption("timeout"));
        }
    }

    private void start(Action action, String name, CommandRegistry.Entry entry, Duration timeout) {
        int timeoutTicks = action instanceof Engine.SelfTimed ? Integer.MAX_VALUE
            : (timeout != null ? timeout : DEFAULT_TIMEOUT).ticks();
        Affinity affinity = action instanceof Engine.ServerClocked clocked && clocked.onServerClock()
            ? Affinity.SERVER : entry != null && entry.spec().runsOnServer() ? Affinity.SERVER : Affinity.CLIENT;
        running = new Running(action, name, entry, affinity, timeoutTicks, new int[1]);
    }

    /** A query's result; its own errors are results too, for predicates like {@code ok} to look at. */
    private Result query(Statement.Call query) {
        try {
            return query.entry().spec().queryHandler().run(this, query.args());
        } catch (CommandException e) {
            return e.toResult();
        }
    }

    private static Result assertion(Condition condition, Statement.Call query, Result result) {
        Fields observed = condition.observed(result);
        if (condition.holds(result)) {
            return Result.ok(observed);
        }
        String argument = result.isOk() ? null : query.entry().qualifiedName() + " " + result.code();
        return Result.error("assert_failed", argument).with(observed);
    }

    /** An if that holds puts its statement in its place, to be gated and run next. */
    private void conditional(Statement.If check) {
        Result result = query(check.query());
        if (check.condition().holds(result)) {
            lines.set(index, new Parser.Line(check.then(), lines.get(index).soft() || check.soft(), null));
            gatedThrough = index - 1;
            return;
        }
        Fields fields = new Fields().put("if", false, "false");
        if (!result.isOk()) {
            fields.put("error", result.code(), "error=" + result.code());
        }
        finish(Result.ok(fields).with(check.condition().observed(result)));
    }

    /**
     * Gates the queries that follow {@code from} up to the next other statement (or the first gate that fails), so a
     * run of server-side queries is answered in one server tick instead of one per client tick. Queries change
     * nothing, so their gates still hold when they run. Returns the last gated index.
     */
    private int gateQueriesAfter(int from) {
        int last = from;
        while (last + 1 < lines.size() && lines.get(last + 1).statement() instanceof Statement.Call call
            && call.entry().spec().kind() == CommandSpec.Kind.QUERY
            && engine.game().gate(call.entry().spec()) == null) {
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
            running.action().cancel();
            engine.game().releaseInputs();
            return crash(running.name(), running.entry(), e);
        }
        int elapsed = ++running.elapsed()[0];
        if (elapsed >= running.timeoutTicks()) {
            running.action().cancel();
            return Result.error("timeout", new Duration(elapsed).seconds() + "s");
        }
        return null;
    }

    private Result diff(Statement.Call call, Result result) {
        List<Row> rows = result.rows();
        if (!result.isOk() || rows == null) {
            return result;
        }
        String key = call.entry().qualifiedName() + " " + call.args().arguments() + call.args().options();
        List<Row> previous = session.swapRows(key, rows);
        if (previous == null || call.full()) {
            return result;
        }
        return result.withDiff(Diffs.between(previous, rows));
    }

    private Result crash(String name, CommandRegistry.Entry entry, RuntimeException e) {
        LOGGER.error("Command {} failed", name, e);
        String message = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
        if (entry != null && !entry.namespace().isEmpty()) {
            return Result.error("ext_failed", name + " " + message);
        }
        return Result.error("internal", name + " " + message);
    }

    private void finish(Result result) {
        results.add(result);
        boolean soft = lines.get(index).soft();
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
        while (index < lines.size()) {
            results.add(Result.skip());
            index++;
        }
    }

    /** Stops the job: the running action is cancelled and every statement not yet done fails with the code. */
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
        if (index < lines.size()) {
            // A cancelled script stops even at a soft (?) statement.
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
        for (Parser.Line line : lines) {
            if (line.statement() != null && isAction(line.statement())) {
                return true;
            }
        }
        return false;
    }

    /** An action, or an if whose statement is one, so the script queues like other scripts with actions. */
    private static boolean isAction(Statement statement) {
        return switch (statement) {
            case Statement.If check -> isAction(check.then());
            case Statement.Assert check -> false;
            default -> placement(statement).action();
        };
    }
}
