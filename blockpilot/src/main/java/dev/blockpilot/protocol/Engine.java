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

    public Engine(CommandRegistry registry, GameBridge game) {
        this.registry = registry;
        this.game = game;
        this.help = new Help(registry);
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

    public CompletableFuture<Reply> submit(Session session, String id, String script) {
        List<RawCommand> commands = ScriptParser.parse(script);
        if (commands.isEmpty()) {
            return CompletableFuture.completedFuture(new Reply(id,
                List.of(Result.error("syntax", "empty script")), session.drainEvents()));
        }
        ScriptJob job = new ScriptJob(this, session, id, commands);
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
            .group("meta").optionalArg("topic", ArgType.TEXT)
            .help("lists commands; help <command|group|namespace|errors> for details").example("help goto"));
        registry.register(CommandSpec.query("fmt", (context, args) -> {
            Session session = sessions.get(context.sessionId());
            session.setFormat(args.word("format").equals("json") ? Session.Format.JSON : Session.Format.TEXT);
            return Result.ok();
        }).group("meta").choice("format", false, "text", "json")
            .help("sets this session's reply format; text is compact, json is one object per reply")
            .example("fmt json"));
        registry.register(CommandSpec.query("sub", (context, args) -> {
            Session session = sessions.get(context.sessionId());
            if (!session.canPush()) {
                throw new CommandException("syntax", "sub only works on the line protocol");
            }
            session.setPushing(args.word("state").equals("on"));
            return Result.ok();
        }).group("meta").choice("state", false, "on", "off")
            .help("line protocol: push events as they happen instead of with the next reply").example("sub on"));
        registry.register(CommandSpec.query("stop", (context, args) -> {
            int stopped = stopAll("cancelled");
            return Result.ok(new Fields().put("stopped", stopped, "stopped " + stopped));
        }).group("meta").help("cancels the running and queued actions of every session and releases held input")
            .field("stopped", "number of scripts cancelled").example("stop"));
        registry.register(CommandSpec.action("wait", this::startWait).group("meta").arg("what", ArgType.TEXT)
            .help("wait <Nt|Ns> | wait idle | wait <query> until <field><op><value> [and …] "
                + "[stable=<Nt>] [every=<Nt>] [timeout=<duration>]; ops == != < <= > >=, or ok")
            .example("wait block ~1,0,0 until id==air timeout=5s"));
        registry.register(CommandSpec.action("sample", this::startSample).group("meta").arg("what", ArgType.TEXT)
            .help("sample <query> every <Nt> x<n> [clock=client|server]: runs the read-only query n times (max "
                + "1000), every N ticks, one row per run with the game tick and the query's fields")
            .example("sample status every 1t x20"));
    }

    static final int MAX_SAMPLES = 1000;

    private Action startSample(CommandContext context, Args args) throws CommandException {
        List<RawCommand.Token> tokens = RawCommand.of("sample " + args.text("what")).tokens();
        int every = -1;
        for (int index = 0; index < tokens.size(); index++) {
            if (tokens.get(index).text().equals("every")) {
                every = index;
                break;
            }
        }
        if (every <= 0) {
            throw new CommandException("syntax", "sample <query> every <Nt> x<n> [clock=client|server]");
        }
        StringBuilder query = new StringBuilder();
        for (int index = 0; index < every; index++) {
            query.append(index == 0 ? "" : " ").append(tokens.get(index).text());
        }
        RawCommand queryCommand = RawCommand.of(query.toString());
        CommandRegistry.Entry entry = registry.resolve(queryCommand.name());
        if (entry == null || entry.spec().kind() != CommandSpec.Kind.QUERY) {
            throw new CommandException("syntax", queryCommand.name() + " is not a read-only command");
        }
        Args queryArgs = ArgParser.parse(entry.spec(), queryCommand);
        // The inner query is gated here, on the client thread; server-clock runs skip the per-run client gate.
        String gate = game.gate(entry.spec());
        if (gate != null) {
            throw new CommandException(gate);
        }
        Duration interval = null;
        int count = 0;
        boolean serverClock = false;
        for (int index = every + 1; index < tokens.size(); index++) {
            String token = tokens.get(index).text();
            if (token.matches("x\\d+")) {
                count = Integer.parseInt(token.substring(1));
            } else if (token.equals("clock=server") || token.equals("clock=client")) {
                serverClock = token.endsWith("server");
            } else if (Duration.parse(token) != null && interval == null) {
                interval = Duration.parse(token);
            } else {
                throw new CommandException("syntax", "unexpected " + token + " in sample … every <Nt> x<n>");
            }
        }
        if (interval == null || count <= 0) {
            throw new CommandException("syntax", "sample <query> every <Nt> x<n>");
        }
        if (count > MAX_SAMPLES) {
            throw new CommandException("too_large", String.valueOf(MAX_SAMPLES));
        }
        if (serverClock && !entry.spec().runsOnServer()) {
            throw new CommandException("syntax", "clock=server needs a server-side query; "
                + queryCommand.name() + " runs on the client");
        }
        return new Sampler(context, entry.spec(), queryArgs, Math.max(1, interval.ticks()), count,
            serverClock || entry.spec().runsOnServer());
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
                long time = game.gameTime(serverClock);
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

    private Action startWait(CommandContext context, Args args) throws CommandException {
        RawCommand what = RawCommand.of("wait " + args.text("what"));
        List<RawCommand.Token> tokens = what.tokens();
        if (tokens.size() == 1) {
            String token = tokens.get(0).text();
            if (token.equals("idle")) {
                return () -> Result.ok();
            }
            Duration duration = Duration.parse(token);
            if (duration != null) {
                return new WaitTicks(duration.ticks());
            }
        }
        int until = -1;
        for (int index = 0; index < tokens.size(); index++) {
            if (tokens.get(index).text().equals("until")) {
                until = index;
                break;
            }
        }
        if (until <= 0) {
            throw new CommandException("syntax", "wait <Nt|Ns>, wait idle or wait <query> until <predicate>");
        }
        StringBuilder query = new StringBuilder();
        for (int index = 0; index < until; index++) {
            query.append(index == 0 ? "" : " ").append(tokens.get(index).text());
        }
        RawCommand queryCommand = RawCommand.of(query.toString());
        CommandRegistry.Entry entry = registry.resolve(queryCommand.name());
        if (entry == null) {
            throw new CommandException("syntax", "unknown command " + queryCommand.name());
        }
        if (entry.spec().kind() != CommandSpec.Kind.QUERY) {
            throw new CommandException("syntax", queryCommand.name() + " is not a read-only command");
        }
        Args queryArgs = ArgParser.parse(entry.spec(), queryCommand);
        String gate = game.gate(entry.spec());
        if (gate != null) {
            throw new CommandException(gate);
        }
        List<String> predicates = new ArrayList<>();
        Map<String, String> options = new java.util.LinkedHashMap<>();
        for (int index = until + 1; index < tokens.size(); index++) {
            String token = tokens.get(index).text();
            int equals = token.indexOf('=');
            boolean option = equals > 0 && token.charAt(equals - 1) != '!' && token.charAt(equals - 1) != '<'
                && token.charAt(equals - 1) != '>' && !token.startsWith("==", equals)
                && List.of("stable", "every", "timeout", "clock").contains(token.substring(0, equals));
            if (option) {
                options.put(token.substring(0, equals), token.substring(equals + 1));
            } else {
                predicates.add(token);
            }
        }
        Args waitOptions = new Args(Map.of(), options);
        Duration stable = waitOptions.durationOption("stable");
        Duration every = waitOptions.durationOption("every");
        Duration timeout = waitOptions.durationOption("timeout");
        boolean serverClock = "server".equals(options.get("clock"));
        if (serverClock && !entry.spec().runsOnServer()) {
            throw new CommandException("syntax", "clock=server needs a server-side query; "
                + queryCommand.name() + " runs on the client");
        }
        return new WaitUntil(context, entry.spec(), queryArgs, Condition.parse(predicates),
            stable == null ? 1 : Math.max(1, stable.ticks()), every == null ? 1 : Math.max(1, every.ticks()),
            (timeout == null ? ScriptJob.DEFAULT_TIMEOUT : timeout).ticks(), entry.spec().runsOnServer());
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
                    return Result.ok(new Fields().bare("elapsed", new Duration(elapsed).seconds() + "s"))
                        .with(condition.observed(last));
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
