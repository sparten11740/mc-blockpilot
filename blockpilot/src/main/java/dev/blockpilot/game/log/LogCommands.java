package dev.blockpilot.game.log;

import dev.blockpilot.api.ArgType;
import dev.blockpilot.api.Args;
import dev.blockpilot.api.CommandContext;
import dev.blockpilot.api.CommandException;
import dev.blockpilot.api.CommandSpec;
import dev.blockpilot.api.Fields;
import dev.blockpilot.api.Result;
import dev.blockpilot.api.Row;
import dev.blockpilot.protocol.CommandRegistry;
import dev.blockpilot.protocol.Session;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** {@code log}: the game's recent log, filtered, from a mark on, with condensed stack traces. */
public final class LogCommands {
    public void register(CommandRegistry registry) {
        registry.register(CommandSpec.query("log", this::log).group("observe")
            .optionalArg("op", ArgType.WORD).optionalArg("seq", ArgType.INT)
            .option("level", "lowest level shown: error, warn (default), info")
            .option("since", "a mark: only entries logged after it")
            .option("grep", "only entries whose message contains this text (case-insensitive)")
            .option("logger", "only loggers whose name starts with this").option("limit", "newest n (default 50)")
            .help("log [level=] [since=] [grep=] [logger=] [limit=]: recent log entries (INFO and above are kept, "
                + "5000 at most), oldest first; log mark: the current position for since=; log detail <seq>: "
                + "the full stack trace of an entry. sub log <level> streams them as events")
            .field("mark", "the newest entry's sequence number").field("count", "matching entries")
            .example("log since=1200 level=warn"));
    }

    private Result log(CommandContext context, Args args) throws CommandException {
        String op = args.word("op");
        if ("mark".equals(op)) {
            return Result.ok(new Fields().put("mark", LogCapture.mark(), "mark=" + LogCapture.mark()));
        }
        if ("detail".equals(op)) {
            if (!args.has("seq")) {
                throw new CommandException("syntax", "log detail <seq>");
            }
            LogCapture.Entry entry = LogCapture.get(args.integer("seq", 0));
            if (entry == null) {
                throw new CommandException("syntax", "no log entry " + args.word("seq") + " (only the last "
                    + LogCapture.CAPACITY + " are kept)");
            }
            Fields fields = describe(entry).put("trace", entry.fullTrace(), entry.fullTrace() == null ? ""
                : entry.fullTrace().replace("\n", " ⏎ "));
            return Result.ok(fields);
        }
        if (op != null) {
            throw new CommandException("syntax", "log [mark | detail <seq>] [level=] [since=] [grep=] [logger=]");
        }
        int minimum = Session.logRank(args.option("level") == null ? "warn" : args.option("level"));
        long since = (long) args.numberOption("since", 0);
        String grep = args.option("grep") == null ? null : args.option("grep").toLowerCase(Locale.ROOT);
        String logger = args.option("logger");
        List<LogCapture.Entry> matching = new ArrayList<>();
        for (LogCapture.Entry entry : LogCapture.since(since)) {
            if (Session.logRank(entry.level()) >= minimum && (grep == null || entry.message().toLowerCase(Locale.ROOT)
                .contains(grep)) && (logger == null || entry.logger().startsWith(logger))) {
                matching.add(entry);
            }
        }
        int limit = args.intOption("limit", 50);
        List<LogCapture.Entry> shown = matching.size() <= limit ? matching
            : matching.subList(matching.size() - limit, matching.size());
        List<Row> rows = new ArrayList<>();
        for (LogCapture.Entry entry : shown) {
            rows.add(Row.of(String.valueOf(entry.seq()), describe(entry)));
        }
        return Result.ok(new Fields().put("mark", LogCapture.mark(), "mark=" + LogCapture.mark())
            .put("count", matching.size(), "count=" + matching.size())).withRows(rows, matching.size() - shown.size());
    }

    private static Fields describe(LogCapture.Entry entry) {
        Fields fields = new Fields().put("seq", entry.seq(), "#" + entry.seq()).bare("level", entry.level())
            .put("thread", entry.thread(), "[" + entry.thread() + "]").bare("logger", entry.logger())
            .bare("message", entry.message());
        if (entry.trace() != null) {
            fields.put("exception", entry.trace(), "— " + entry.trace());
        }
        return fields;
    }
}
