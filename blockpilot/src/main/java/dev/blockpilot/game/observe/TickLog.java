package dev.blockpilot.game.observe;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.ToLongFunction;

/** The last entries stamped with a tick, oldest first, remembering up to which tick it has dropped entries. */
final class TickLog<T> {
    private final int capacity;
    private final ToLongFunction<T> tick;
    private final Deque<T> entries = new ArrayDeque<>();
    private Long droppedThrough;

    TickLog(int capacity, ToLongFunction<T> tick) {
        this.capacity = capacity;
        this.tick = tick;
    }

    void add(T entry) {
        if (entries.size() >= capacity) {
            droppedThrough = tick.applyAsLong(entries.removeFirst());
        }
        entries.addLast(entry);
    }

    List<T> since(long since) {
        List<T> out = new ArrayList<>();
        for (T entry : entries) {
            if (tick.applyAsLong(entry) >= since) {
                out.add(entry);
            }
        }
        return out;
    }

    /** Empties the log, forgetting what it dropped too, as for another world's ticks. */
    void clear() {
        entries.clear();
        droppedThrough = null;
    }

    /** The tick of the oldest entry kept, or null when there is none. */
    Long oldest() {
        return entries.isEmpty() ? null : tick.applyAsLong(entries.getFirst());
    }

    /** The tick of the oldest entry kept, when entries from {@code since} on were dropped; else null. */
    Long from(long since) {
        return droppedThrough != null && since <= droppedThrough ? tick.applyAsLong(entries.getFirst()) : null;
    }
}
