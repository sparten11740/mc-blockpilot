package dev.blockpilot.protocol;

import dev.blockpilot.api.Event;
import dev.blockpilot.api.Fields;
import dev.blockpilot.api.Row;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * One connected client: an MCP session or a line-protocol connection. Holds its output format, the last rows of
 * each diffable command, and the events it has not been told about yet.
 */
public final class Session {
    public enum Format { TEXT, JSON }

    static final int EVENT_CAPACITY = 50;
    static final int LOG_EVENTS_PER_SECOND = 20;

    private final int id;
    private volatile Format format = Format.TEXT;
    private final Map<String, List<Row>> lastRows = new HashMap<>();
    private final Deque<Event> events = new ArrayDeque<>();
    private int droppedEvents;
    private volatile Consumer<Event> pushTarget;
    private volatile boolean pushing;
    /** The lowest log level delivered as events, as a rank (see {@link #logRank}); none by default. */
    private volatile int logLevel = Integer.MAX_VALUE;
    private long logWindowStart;
    private int logInWindow;
    private int logDropped;

    public Session(int id) {
        this.id = id;
    }

    public int id() {
        return id;
    }

    public Format format() {
        return format;
    }

    public void setFormat(Format value) {
        format = value;
    }

    /** The rows this session last saw for a diffable command, keyed by the command and its arguments. */
    public synchronized List<Row> swapRows(String key, List<Row> rows) {
        return lastRows.put(key, rows);
    }

    /** Where pushed events go; only line-protocol connections have one. */
    public void setPushTarget(Consumer<Event> target) {
        pushTarget = target;
    }

    public boolean canPush() {
        return pushTarget != null;
    }

    /** {@code sub on}: events are written out as they happen instead of with the next reply. */
    public void setPushing(boolean value) {
        pushing = value;
    }

    /** {@code sub log <level>}: log entries at or above the level arrive as events; null stops them. */
    public void setLogLevel(String level) {
        logLevel = level == null ? Integer.MAX_VALUE : logRank(level);
    }

    /** A log level's rank, lowest first: trace, debug, info, warn, error; anything else ranks highest. */
    public static int logRank(String level) {
        return switch (level.toUpperCase(Locale.ROOT)) {
            case "TRACE" -> 0;
            case "DEBUG" -> 1;
            case "INFO" -> 2;
            case "WARN" -> 3;
            case "ERROR" -> 4;
            default -> 5;
        };
    }

    /** Log events below the session's level are dropped, and at most 20 per second are kept. */
    private synchronized boolean acceptLog(Event event) {
        Object level = event.fields().get("level");
        if (level == null || logRank(level.toString()) < logLevel) {
            return false;
        }
        long now = System.currentTimeMillis();
        if (now - logWindowStart >= 1000) {
            int dropped = logDropped;
            logWindowStart = now;
            logInWindow = 0;
            logDropped = 0;
            if (dropped > 0) {
                deliver(Event.of("log_dropped", new Fields().bare("count", dropped)));
            }
        }
        if (logInWindow >= LOG_EVENTS_PER_SECOND) {
            logDropped++;
            return false;
        }
        logInWindow++;
        return true;
    }

    public void deliver(Event event) {
        if (event.name().equals("log") && !acceptLog(event)) {
            return;
        }
        Consumer<Event> target = pushTarget;
        if (pushing && target != null) {
            target.accept(event);
            return;
        }
        synchronized (this) {
            if (events.size() >= EVENT_CAPACITY) {
                events.removeFirst();
                droppedEvents++;
            }
            events.addLast(event);
        }
    }

    /**
     * The buffered events, oldest first, plus a {@code dropped} marker when the buffer overflowed and a
     * {@code log_dropped} marker for log entries the rate limit held back since the last report.
     */
    public synchronized List<Event> drainEvents() {
        List<Event> drained = new ArrayList<>(events.size() + 2);
        if (droppedEvents > 0) {
            drained.add(Event.of("dropped", new Fields().bare("count", droppedEvents)));
            droppedEvents = 0;
        }
        drained.addAll(events);
        events.clear();
        if (logDropped > 0) {
            drained.add(Event.of("log_dropped", new Fields().bare("count", logDropped)));
            logDropped = 0;
        }
        return drained;
    }
}
