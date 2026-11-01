package dev.blockpilot.game.perf;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/** The server time profiler's sections as a tree, as a profiled capture reports them. */
final class SectionTree {
    /** A section under its parent: its share of the parent and of the whole, in percent, and its calls. */
    record Section(String name, double share, double ofWhole, long calls) {
    }

    static final String ROOT = "root";
    /** Sections under this share of the whole are left out, with all below them. */
    private static final double MIN_OF_WHOLE = 0.5;
    private static final int MAX_DEPTH = 8;

    private SectionTree() {
    }

    /**
     * The tree under {@code root}: {@code children} gives a section's children by path, the path's parts joined by
     * {@code separator}; calls are given per tick of the {@code ticks} the profile covers.
     */
    static JsonArray of(Function<String, List<Section>> children, char separator, int ticks) {
        return level(children, separator, ROOT, Math.max(ticks, 1), 1);
    }

    private static JsonArray level(Function<String, List<Section>> children, char separator, String path, int ticks,
        int depth) {
        JsonArray level = new JsonArray();
        for (Section section : children.apply(path)) {
            if (section.ofWhole() < MIN_OF_WHOLE) {
                continue;
            }
            JsonObject node = new JsonObject();
            node.addProperty("name", section.name());
            node.addProperty("share", Perf.round(section.share()));
            node.addProperty("of_whole", Perf.round(section.ofWhole()));
            node.addProperty("per_tick", Perf.round((double) section.calls() / ticks));
            if (depth < MAX_DEPTH) {
                JsonArray below = level(children, separator, path + separator + section.name(), ticks, depth + 1);
                if (!below.isEmpty()) {
                    node.add("sections", below);
                }
            }
            level.add(node);
        }
        return level;
    }

    /** Each section at {@code minOfWhole} percent of the whole or more, as a path from the root, depth first. */
    static List<JsonObject> atLeast(JsonArray tree, double minOfWhole) {
        List<JsonObject> found = new ArrayList<>();
        collect(tree, ROOT, minOfWhole, found);
        return found;
    }

    private static void collect(JsonArray level, String path, double minOfWhole, List<JsonObject> found) {
        for (JsonElement element : level) {
            JsonObject node = element.getAsJsonObject();
            if (node.get("of_whole").getAsDouble() < minOfWhole) {
                continue;
            }
            String nodePath = path + "/" + node.get("name").getAsString();
            JsonObject row = new JsonObject();
            row.addProperty("path", nodePath);
            row.addProperty("of_whole", node.get("of_whole").getAsDouble());
            found.add(row);
            if (node.has("sections")) {
                collect(node.getAsJsonArray("sections"), nodePath, minOfWhole, found);
            }
        }
    }
}
