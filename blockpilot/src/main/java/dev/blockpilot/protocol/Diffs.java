package dev.blockpilot.protocol;

import dev.blockpilot.api.Result;
import dev.blockpilot.api.Row;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Compares two runs of a keyed list: rows added, removed (by key) and changed. */
public final class Diffs {
    private Diffs() {
    }

    public static Result.Diff between(List<Row> previous, List<Row> current) {
        Map<String, Row> before = new LinkedHashMap<>();
        for (Row row : previous) {
            before.put(row.key(), row);
        }
        List<Row> added = new ArrayList<>();
        List<Row> changed = new ArrayList<>();
        for (Row row : current) {
            Row old = before.remove(row.key());
            if (old == null) {
                added.add(row);
            } else if (!old.fields().equals(row.fields())) {
                changed.add(row);
            }
        }
        return new Result.Diff(added, new ArrayList<>(before.keySet()), changed);
    }
}
