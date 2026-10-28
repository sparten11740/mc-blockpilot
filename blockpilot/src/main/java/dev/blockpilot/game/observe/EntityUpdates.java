package dev.blockpilot.game.observe;

import dev.blockpilot.api.Fields;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * The entity updates the client applied since the end of its previous tick, per entity and kind. Only the client
 * thread uses it: the packet handlers after their thread check, queries, and the end of the tick, which clears it.
 */
public final class EntityUpdates {
    public enum Kind { MOVE, TELEPORT, MOTION, DATA }

    public static final EntityUpdates CLIENT = new EntityUpdates();
    private static final Kind[] KINDS = Kind.values();

    private final Map<Integer, int[]> counts = new HashMap<>();

    public void count(int entity, Kind kind) {
        counts.computeIfAbsent(entity, id -> new int[KINDS.length])[kind.ordinal()]++;
    }

    /** Adds {@code net} with the kinds that arrived for the entity, as {@code net move3 data1}; nothing if none did. */
    public void addTo(Fields fields, int entity) {
        int[] entityCounts = counts.get(entity);
        if (entityCounts == null) {
            return;
        }
        Map<String, Integer> net = new LinkedHashMap<>();
        StringBuilder text = new StringBuilder("net");
        for (Kind kind : KINDS) {
            int count = entityCounts[kind.ordinal()];
            if (count > 0) {
                String name = kind.name().toLowerCase(Locale.ROOT);
                net.put(name, count);
                text.append(' ').append(name).append(count);
            }
        }
        fields.put("net", net, text.toString());
    }

    public void clear() {
        counts.clear();
    }
}
