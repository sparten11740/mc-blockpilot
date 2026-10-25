package dev.blockpilot.protocol;

import dev.blockpilot.api.Action;
import dev.blockpilot.api.ArgType;
import dev.blockpilot.api.Args;
import dev.blockpilot.api.CommandContext;
import dev.blockpilot.api.CommandException;
import dev.blockpilot.api.CommandSpec;
import dev.blockpilot.api.Duration;
import dev.blockpilot.api.Event;
import dev.blockpilot.api.Fields;
import dev.blockpilot.api.Result;
import dev.blockpilot.api.Row;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Runs scripts for every session. Scripts made only of queries run right away; scripts with an action go through
 * one FIFO queue shared by all sessions, whose head advances one step per tick. Ticks come from the client and,
 * for commands that run there, from the integrated server.
 */
public final class Engine {
    /** An action that enforces its own time limit (the engine's default timeout does not apply). */
    public interface SelfTimed {
    }

    /** An action that may ask to be ticked by the integrated server instead of the client. */
    public interface ServerClocked {
        boolean onServerClock();
    }

    private final CommandRegistry registry;
    private final GameBridge game;
    private final Help help;
    private final Map<Integer, Session> sessions = new ConcurrentHashMap<>();
    private final AtomicInteger nextSessionId = new AtomicInteger(1);
    private final ConcurrentLinkedDeque<ScriptJob> queue = new ConcurrentLinkedDeque<>();
    private final List<ScriptJob> free = new CopyOnWriteArrayList<>();
    private final Parser parser;

    public Engine(CommandRegistry registry, GameBridge game) {
        this.registry = registry;
        this.game = game;
        this.help = new Help(registry);
        this.parser = new Parser(registry);
        registerBuiltIns();
    }

    CommandRegistry registry() {
        return registry;
    }

    GameBridge game() {
        return game;
    }

    public Help help() {
        return help;
    }

    public Session openSession() {
        Session session = new Session(nextSessionId.getAndIncrement());
        sessions.put(session.id(), session);
        return session;
    }

    /** Ends a session: its queued and running scripts are cancelled. */
    public void closeSession(Session session) {
        sessions.remove(session.id());
        cancelWhere(job -> job.session() == session, "cancelled", true);
        game.releaseInputs(session.id());
    }

    public int sessionCount() {
        return sessions.size();
    }

    /**
     * Parses the script and queues it (with an action) or runs it (queries only). A syntax error in a step that isn't
     * soft runs nothing: that step answers {@code err syntax} and every other one {@code skip}.
     */
    public CompletableFuture<Reply> submit(Session session, String id, String script) {
        List<Parser.Line> lines = parser.script(script);
        if (lines.isEmpty()) {
            return CompletableFuture.completedFuture(new Reply(id,
                List.of(Result.error("syntax", "empty script")), session.drainEvents()));
        }
        if (lines.stream().anyMatch(line -> line.error() != null && !line.soft())) {
            List<Result> results = lines.stream().map(line -> line.error() != null && !line.soft()
                ? Result.error("syntax", line.error()) : Result.skip()).toList();
            return CompletableFuture.completedFuture(new Reply(id, results, session.drainEvents()));
        }
        ScriptJob job = new ScriptJob(this, session, id, lines);
        if (job.hasAction()) {
            queue.addLast(job);
        } else {
            free.add(job);
            game.runOnClient(() -> stepFree(ScriptJob.Affinity.CLIENT));
        }
        return job.future();
    }

    /** Called at the end of every client tick. */
    public void tickClient() {
        tick(ScriptJob.Affinity.CLIENT);
    }

    /** Called at the end of every integrated-server tick, on the server thread. */
    public void tickServer() {
        tick(ScriptJob.Affinity.SERVER);
    }

    private void tick(ScriptJob.Affinity affinity) {
        stepFree(affinity);
        if (!game.ready()) {
            // Loading a world while the game still loads its resources never finishes, so actions wait.
            return;
        }
        ScriptJob head = queue.peekFirst();
        while (head != null && head.step(affinity)) {
            queue.remove(head);
            head = queue.peekFirst();
        }
    }

    private void stepFree(ScriptJob.Affinity affinity) {
        for (ScriptJob job : free) {
            if (job.step(affinity)) {
                free.remove(job);
            }
        }
    }

    /** The running action's name for the HUD, or null when the queue is idle. */
    public String activity() {
        ScriptJob head = queue.peekFirst();
        return head == null ? null : head.activity();
    }

    /** Cancels every queued and running script with the code; returns how many were stopped. */
    public int stopAll(String code) {
        int stopped = cancelWhere(job -> true, code, false);
        game.releaseInputs();
        return stopped;
    }

    /** Cancels matching queued scripts, and with {@code queries} also scripts of only queries still pending. */
    private int cancelWhere(java.util.function.Predicate<ScriptJob> filter, String code, boolean queries) {
        int stopped = 0;
        for (ScriptJob job : new ArrayList<>(queue)) {
            if (filter.test(job)) {
                job.cancel(code);
                queue.remove(job);
                stopped++;
            }
        }
        for (ScriptJob job : free) {
            if (queries && filter.test(job)) {
                job.cancel(code);
                free.remove(job);
            }
        }
        return stopped;
    }

    /** Delivers an event to every session. */
    public void broadcast(Event event) {
        for (Session session : sessions.values()) {
            session.deliver(event);
        }
    }

    private void registerBuiltIns() {
        registry.register(CommandSpec.query("help", (context, args) -> help.answer(args.text("topic")))
            .group("meta").optionalArg("topic", ArgType.WORD)
            .help("lists commands; help <command|group|namespace|errors> for details").example("help goto"));
        registry.register(CommandSpec.query("fmt", (context, args) -> {
            Session session = sessions.get(context.sessionId());
            session.setFormat(args.word("format").equals("json") ? Session.Format.JSON : Session.Format.TEXT);
            return Result.ok();
        }).group("meta").keyword("format", "text", "json")
            .help("sets this session's reply format; text is compact, json is one object per reply")
            .example("fmt json"));
        registry.register(CommandSpec.query("sub", (context, args) -> {
            Session session = sessions.get(context.sessionId());
            if (args.word("state").equals("log")) {
                session.setLogLevel(args.word("level").equals("off") ? null : args.word("level"));
                return Result.ok();
            }
            if (!session.canPush()) {
                throw new CommandException("syntax", "sub on|off only works on the line protocol");
            }
            session.setPushing(args.word("state").equals("on"));
            return Result.ok();
        }).group("meta")
            .form(form -> form.keyword("state", "on", "off"))
            .form(form -> form.keyword("state", "log").keyword("level", "error", "warn", "info", "off"))
            .help("on|off: (line protocol) push events as they happen instead of with the next reply; log <level>: "
                + "log entries at or above the level arrive as ! log events (max 20/s)").example("sub log error"));
        registry.register(CommandSpec.query("stop", (context, args) -> {
            int stopped = stopAll("cancelled");
            return Result.ok(new Fields().put("stopped", stopped, "stopped " + stopped));
        }).group("meta").help("cancels the running and queued actions of every session and releases held input")
            .field("stopped", "number of scripts cancelled").example("stop"));
    }

    static final int MAX_SAMPLES = 1000;

    /** {@code sample}'s action: fails with {@code too_large} past {@link #MAX_SAMPLES} runs. */
    Action sample(CommandContext context, Statement.Sample sample) throws CommandException {
        if (sample.count() > MAX_SAMPLES) {
            throw new CommandException("too_large", String.valueOf(MAX_SAMPLES));
        }
        Statement.Call query = sample.query();
        return new Sampler(context, query.entry().spec(), query.args(), Math.max(1, sample.every().ticks()),
            sample.count(), query.entry().spec().runsOnServer());
    }

    Action waitTicks(Statement.WaitTicks wait) {
        return new WaitTicks(wait.duration().ticks());
    }

    Action waitUntil(CommandContext context, Statement.WaitUntil wait) {
        Statement.Call query = wait.query();
        Duration timeout = wait.timeout() == null ? ScriptJob.DEFAULT_TIMEOUT : wait.timeout();
        return new WaitUntil(context, query.entry().spec(), query.args(), wait.condition(), wait.stable(),
            wait.every(), timeout.ticks(), query.entry().spec().runsOnServer());
    }

    /** {@code sample}: runs a query every N ticks and collects one row per run. */
    private final class Sampler implements Action, SelfTimed, ServerClocked {
        private final CommandContext context;
        private final CommandSpec query;
        private final Args args;
        private final int interval;
        private final int count;
        private final boolean serverClock;
        private final List<Row> rows = new ArrayList<>();
        private int elapsed;

        Sampler(CommandContext context, CommandSpec query, Args args, int interval, int count, boolean serverClock) {
            this.context = context;
            this.query = query;
            this.args = args;
            this.interval = interval;
            this.count = count;
            this.serverClock = serverClock;
        }

        @Override
        public boolean onServerClock() {
            return serverClock;
        }

        @Override
        public Result tick() {
            if (elapsed++ % interval == 0) {
                long time = game.gameTime();
                Fields fields = new Fields().put("tick", time, "t" + time);
                Result result;
                String gate = serverClock ? null : game.gate(query);
                try {
                    result = gate != null ? Result.error(gate) : query.queryHandler().run(context, args);
                } catch (CommandException e) {
                    result = e.toResult();
                }
                if (!result.isOk()) {
                    fields.put("err", result.code(), "err " + result.code());
                }
                result.fields().list().forEach(field -> fields.put(field.name(), field.value(), field.text()));
                if (result.rows() != null && result.fields().get("count") == null) {
                    fields.put("count", result.rows().size(), "count=" + result.rows().size());
                }
                rows.add(Row.of(String.valueOf(rows.size()), fields));
            }
            return rows.size() >= count ? Result.ok(new Fields().put("samples", rows.size(), "samples=" + rows.size()))
                .withRows(rows) : null;
        }

        @Override
        public String label() {
            return "sample";
        }
    }

    /** {@code wait <Nt|Ns>}. */
    private static final class WaitTicks implements Action, SelfTimed {
        private int remaining;

        WaitTicks(int ticks) {
            remaining = ticks;
        }

        @Override
        public Result tick() {
            return --remaining <= 0 ? Result.ok() : null;
        }

        @Override
        public String label() {
            return "wait";
        }
    }

    /** {@code wait <query> until …}: re-runs the query until the condition holds long enough. */
    private final class WaitUntil implements Action, SelfTimed, ServerClocked {
        private final CommandContext context;
        private final CommandSpec query;
        private final Args args;
        private final Condition condition;
        private final int stable;
        private final int every;
        private final int timeout;
        private final boolean serverClock;
        private int elapsed;
        private int matches;
        private Result last;

        WaitUntil(CommandContext context, CommandSpec query, Args args, Condition condition, int stable, int every,
            int timeout, boolean serverClock) {
            this.context = context;
            this.query = query;
            this.args = args;
            this.condition = condition;
            this.stable = stable;
            this.every = every;
            this.timeout = timeout;
            this.serverClock = serverClock;
        }

        @Override
        public boolean onServerClock() {
            return serverClock;
        }

        @Override
        public Result tick() {
            if (elapsed % every == 0) {
                String gate = serverClock ? null : game.gate(query);
                try {
                    last = gate != null ? Result.error(gate) : query.queryHandler().run(context, args);
                } catch (CommandException e) {
                    last = e.toResult();
                }
                matches = condition.holds(last) ? matches + every : 0;
                if (matches >= stable) {
                    Fields fields = new Fields().bare("elapsed", new Duration(elapsed).seconds() + "s");
                    if (serverClock) {
                        long time = game.gameTime();
                        fields.put("tick", time, "t" + time);
                    }
                    return Result.ok(fields).with(condition.observed(last));
                }
            }
            elapsed++;
            if (elapsed >= timeout) {
                return Result.error("timeout", new Duration(elapsed).seconds() + "s").with(condition.observed(last));
            }
            return null;
        }

        @Override
        public String label() {
            return "wait";
        }
    }
}
