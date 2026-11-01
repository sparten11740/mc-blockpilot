package dev.blockpilot.game.perf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import dev.blockpilot.game.perf.StackSamples.Frame;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StackSamplesTest {
    private static final Map<String, String> MODS = Map.of("dev.mod.", "mymod", "dev.blockpilot.", "blockpilot",
        "net.minecraft.", "minecraft");
    private static final Frame TICK = frame("net.minecraft.server.MinecraftServer", "tickServer");

    private static Frame frame(String className, String method) {
        return new Frame(className, method, false);
    }

    private static StackSamples samples() {
        return new StackSamples(className -> MODS.entrySet().stream().filter(mod -> className.startsWith(mod.getKey()))
            .map(Map.Entry::getValue).findFirst().orElse(null));
    }

    @Test
    void theInnermostModFrameNamesTheMod() {
        assertEquals("mymod", samples().modOf(List.of(frame("java.util.HashMap", "get"),
            frame("dev.mod.Physics", "step"), frame("net.neoforged.bus.EventBus", "post"), TICK)));
    }

    @Test
    void framesNoModOwnsArePassedOver() {
        assertEquals("mymod", samples().modOf(List.of(frame("org.lib.Solver", "solve"),
            frame("dev.mod.Physics", "step"), TICK)));
    }

    @Test
    void withoutAModFrameItIsMinecraftOrJava() {
        assertEquals("minecraft", samples().modOf(List.of(frame("java.util.HashMap", "get"), TICK)));
        assertEquals("java", samples().modOf(List.of(frame("java.lang.Thread", "sleep"))));
    }

    @Test
    void blockpilotCountsAsAMod() {
        assertEquals("blockpilot", samples().modOf(List.of(frame("dev.blockpilot.game.perf.Perf", "serverTick"),
            TICK)));
    }

    @Test
    void theSummaryRanksModsAndTopFramesByShare() {
        StackSamples samples = samples();
        List<Frame> physics = List.of(new Frame("dev.mod.Rapier", "step", true), frame("dev.mod.Physics", "tick"),
            TICK);
        samples.add(physics);
        samples.add(physics);
        samples.add(physics);
        samples.add(List.of(frame("java.util.HashMap", "get"), TICK));
        samples.skip();
        JsonObject summary = samples.summary(10);
        assertEquals(4, summary.get("samples").getAsInt());
        assertEquals(1, summary.get("outside_ticks").getAsInt());
        JsonObject mod = summary.getAsJsonArray("mods").get(0).getAsJsonObject();
        assertEquals("mymod", mod.get("mod").getAsString());
        assertEquals(75.0, mod.get("share").getAsDouble());
        JsonObject top = summary.getAsJsonArray("frames").get(0).getAsJsonObject();
        assertEquals("dev.mod.Rapier.step", top.get("frame").getAsString());
        assertTrue(top.get("native").getAsBoolean());
        assertEquals("mymod", top.get("mod").getAsString());
        JsonObject second = summary.getAsJsonArray("frames").get(1).getAsJsonObject();
        assertEquals("java", second.get("mod").getAsString());
        assertFalse(second.has("native"));
    }
}
