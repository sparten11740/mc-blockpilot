package dev.blockpilot.game.perf;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.blockpilot.game.perf.SectionTree.Section;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SectionTreeTest {
    private static final char SEPARATOR = '\u001e';
    private static final Map<String, List<Section>> PROFILE = Map.of(
        "root", List.of(new Section("tick", 95, 95, 200), new Section("nextTickWait", 5, 5, 200)),
        "root" + SEPARATOR + "tick", List.of(new Section("levels", 90, 85.5, 200),
            new Section("unspecified", 9.8, 9.3, 200), new Section("autoSave", 0.2, 0.19, 1)),
        "root" + SEPARATOR + "tick" + SEPARATOR + "levels", List.of(new Section("unspecified", 94.6, 80.9, 200)));

    private static JsonArray tree() {
        return SectionTree.of(path -> PROFILE.getOrDefault(path, List.of()), SEPARATOR, 200);
    }

    @Test
    void sectionsNestWithTheirSharesAndCallsPerTick() {
        JsonObject tick = tree().get(0).getAsJsonObject();
        assertEquals("tick", tick.get("name").getAsString());
        assertEquals(95.0, tick.get("of_whole").getAsDouble());
        assertEquals(1.0, tick.get("per_tick").getAsDouble());
        JsonObject levels = tick.getAsJsonArray("sections").get(0).getAsJsonObject();
        assertEquals("unspecified", levels.getAsJsonArray("sections").get(0).getAsJsonObject().get("name")
            .getAsString());
    }

    @Test
    void sectionsUnderHalfAPercentAreLeftOut() {
        JsonArray underTick = tree().get(0).getAsJsonObject().getAsJsonArray("sections");
        assertEquals(2, underTick.size());
        assertEquals("unspecified", underTick.get(1).getAsJsonObject().get("name").getAsString());
    }

    @Test
    void rowsArePathsFromTheRootAtTheGivenShare() {
        List<String> paths = SectionTree.atLeast(tree(), 5).stream().map(row -> row.get("path").getAsString())
            .toList();
        assertEquals(List.of("root/tick", "root/tick/levels", "root/tick/levels/unspecified", "root/tick/unspecified",
            "root/nextTickWait"), paths);
    }
}
