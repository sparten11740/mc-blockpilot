package dev.blockpilot.protocol;

import dev.blockpilot.api.Event;
import dev.blockpilot.api.Fields;
import dev.blockpilot.api.Row;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * One connected client: an MCP session or a line-protocol connection. Holds its output format, the last rows of
 * each diffable command, and the events it has not been told about yet.
 */
public final class Session {
    public enum Format { TEXT, JSON }

    static final int EVENT_CAPACITY = 50;

    private final int id;
    private volatile Format format = Format.TEXT;
    private final Map<String, List<Row>> lastRows = new HashMap<>();
    private final Deque<Event> events = new ArrayDeque<>();
    private int droppedEvents;
    private volatile Consumer<Event> pushTarget;
    private volatile boolean pushing;

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

    public void deliver(Event event) {
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

    /** The buffered events, oldest first, plus a {@code dropped} marker when the buffer overflowed. */
    public synchronized List<Event> drainEvents() {
        List<Event> drained = new ArrayList<>(events.size() + 1);
        if (droppedEvents > 0) {
            drained.add(Event.of("dropped", new Fields().bare("count", droppedEvents)));
            droppedEvents = 0;
        }
        drained.addAll(events);
        events.clear();
        return drained;
    }
}
