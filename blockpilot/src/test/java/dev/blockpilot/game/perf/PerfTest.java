package dev.blockpilot.game.perf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

/** The capture summary, fed the way the instrumentation mixins feed it. */
class PerfTest {
    @Test
    void idleHooksRecordNothing() {
        Perf.particleTickEnd("flame");
        Perf.stageEnd("after_sky");
        Perf.entityRenderEnd("cow");
        Perf.particleRendered("flame", 5);
        Perf.start("idle");
        JsonObject client = Perf.stop(new JsonObject(), Map.of()).getAsJsonObject("client");
        assertEquals(0, client.getAsJsonArray("entity_renderers").size());
        assertEquals(0, client.getAsJsonObject("particles").getAsJsonArray("renders").size());
        assertEquals(0, client.getAsJsonArray("render_stages").size());
    }

    @Test
    void aRenderInTwoStepsCountsAsOneCall() {
        Perf.start("steps");
        Perf.renderStart();
        Perf.renderStepEnd("chest");
        Perf.renderStart();
        Perf.renderEnd("chest");
        Perf.renderStart();
        Perf.renderStepEnd("sign");
        Perf.entityRenderStart();
        Perf.entityRenderStepEnd("cow");
        Perf.entityRenderStart();
        Perf.entityRenderEnd("cow");
        JsonObject client = Perf.stop(new JsonObject(), Map.of()).getAsJsonObject("client");
        Map<String, Integer> renderers = new TreeMap<>();
        client.getAsJsonArray("block_entity_renderers").forEach(row -> renderers.put(
            row.getAsJsonObject().get("type").getAsString(), row.getAsJsonObject().get("calls").getAsInt()));
        assertEquals(Map.of("chest", 1, "sign", 0), renderers);
        assertEquals(1, client.getAsJsonArray("entity_renderers").get(0).getAsJsonObject().get("calls").getAsInt());
    }

    @Test
    void summarizesWhatTheHooksRecorded() {
        Perf.start("unit");
        for (int tick = 0; tick < 20; tick++) {
            Perf.serverTick(2_000_000, 1000 + tick);
            Perf.blockEntityStart(false);
            Perf.blockEntityEnd(false, "hopper");
            Perf.entityStart(false);
            Perf.entityEnd(false, "pig");
        }
        Perf.renderStart();
        Perf.renderEnd("chest");
        Perf.sectionRebuilt();
        Perf.packet(true, "custom_payload/neoforge:custom_time_packet");
        Perf.packetBytes(true, "custom_payload/neoforge:custom_time_packet", 12);
        Perf.packet(false, "move_player_pos");
        Perf.entityRenderStart();
        Perf.entityRenderEnd("cow");
        Perf.particleTickStart();
        Perf.particleTickEnd("flame");
        Perf.particleRendered("flame", 3_000_000);
        Perf.particleRendered("end_rod", 1_000_000);
        Perf.stageStart();
        Perf.stageEnd("after_particles");
        JsonObject summary = Perf.stop(new JsonObject(), Map.of("flame", 3, "end_rod", 7));

        assertFalse(Perf.active);
        assertEquals("unit", summary.get("name").getAsString());
        JsonObject server = summary.getAsJsonObject("server");
        assertEquals(20, server.get("ticks").getAsInt());
        assertEquals(2.0, server.getAsJsonObject("mspt").get("max").getAsDouble());
        JsonObject hopper = server.getAsJsonArray("block_entities").get(0).getAsJsonObject();
        assertEquals("hopper", hopper.get("type").getAsString());
        assertEquals(20, hopper.get("calls").getAsInt());
        assertEquals("pig", server.getAsJsonArray("entities").get(0).getAsJsonObject().get("type").getAsString());
        JsonObject client = summary.getAsJsonObject("client");
        assertEquals("chest", client.getAsJsonArray("block_entity_renderers").get(0).getAsJsonObject().get("type")
            .getAsString());
        assertEquals(1, client.getAsJsonObject("sections").get("rebuilds").getAsInt());
        assertEquals("cow", client.getAsJsonArray("entity_renderers").get(0).getAsJsonObject().get("type")
            .getAsString());
        JsonObject particles = client.getAsJsonObject("particles");
        assertEquals("flame", particles.getAsJsonArray("renders").get(0).getAsJsonObject().get("type").getAsString());
        assertEquals(3.0, particles.getAsJsonArray("renders").get(0).getAsJsonObject().get("total_ms").getAsDouble());
        assertEquals("flame", particles.getAsJsonArray("ticks").get(0).getAsJsonObject().get("type").getAsString());
        assertEquals(List.of("end_rod", "flame"), List.copyOf(particles.getAsJsonObject("live").keySet()));
        assertEquals(7, particles.getAsJsonObject("live").get("end_rod").getAsInt());
        assertEquals("after_particles", client.getAsJsonArray("render_stages").get(0).getAsJsonObject().get("type")
            .getAsString());
        JsonObject payload = summary.getAsJsonObject("network").getAsJsonArray("clientbound").get(0).getAsJsonObject();
        assertEquals("custom_payload/neoforge:custom_time_packet", payload.get("type").getAsString());
        assertEquals(1, payload.get("count").getAsInt());
        assertEquals(12, payload.get("bytes").getAsInt());
        assertEquals("move_player_pos", summary.getAsJsonObject("network").getAsJsonArray("serverbound").get(0)
            .getAsJsonObject().get("type").getAsString());
    }

    @Test
    void spikesAreTheTicksOverFiftyMillisecondsLongestFirst() {
        double[] ticks = {10, 60, 180, 55, 50};
        long[] labels = {101, 102, 103, 104, 105};
        assertEquals("[{\"tick\":103,\"ms\":180.0},{\"tick\":102,\"ms\":60.0},{\"tick\":104,\"ms\":55.0}]",
            Perf.spikes(ticks, labels, new int[5][4]).toString());
        double[] many = new double[30];
        java.util.Arrays.fill(many, 70);
        assertEquals(Perf.MAX_SPIKES, Perf.spikes(many, new long[30], new int[30][4]).size());
    }

    @Test
    void blockChangesAreRankedByRateWithTheirBlockEntityAndSubLevel() {
        Perf.blockChanged("stone", null, null);
        Perf.start("changes");
        Perf.serverTick(1_000_000, 1);
        for (int change = 0; change < 3; change++) {
            Perf.blockChanged("oak_leaves", null, null);
        }
        for (int change = 0; change < 5; change++) {
            Perf.blockChanged("mymod:thruster", "mymod:thruster", "ship");
        }
        Perf.blockChanged("stone", null, null);
        JsonArray changes = Perf.stop(new JsonObject(), Map.of()).getAsJsonObject("server")
            .getAsJsonArray("block_changes");
        assertEquals(3, changes.size());
        JsonObject thruster = changes.get(0).getAsJsonObject();
        assertEquals("mymod:thruster", thruster.get("block").getAsString());
        assertEquals("mymod:thruster", thruster.get("be").getAsString());
        assertEquals("ship", thruster.get("sub").getAsString());
        assertEquals(5, thruster.get("count").getAsInt());
        JsonObject leaves = changes.get(1).getAsJsonObject();
        assertEquals("oak_leaves", leaves.get("block").getAsString());
        assertFalse(leaves.has("be") || leaves.has("sub"));
        assertEquals(1, changes.get(2).getAsJsonObject().get("count").getAsInt());
    }

    @Test
    void aSampleOfSpawnsIsTracedAndSourcesRankedByShare() {
        assertFalse(Perf.traceSpawn("flame", false));
        Perf.start("sources");
        int traced = 0;
        for (int spawn = 0; spawn < 64; spawn++) {
            traced += Perf.traceSpawn("flame", false) ? 1 : 0;
        }
        // The first spawn and every 32nd after it, per side.
        assertEquals(2, traced);
        assertTrue(Perf.traceSpawn("flame", true));
        for (int sample = 0; sample < 6; sample++) {
            Perf.spawnedBy("large_smoke", "mymod", "com.example.Burner", "client");
        }
        Perf.spawnedBy("large_smoke", null, null, "server");
        Perf.spawnedBy("large_smoke", "minecraft", null, "client");
        Perf.spawnedBy("large_smoke", "other", "com.example.Chimney", "client");
        Perf.spawnedBy("flame", "mymod", "com.example.Torch", "server_side");
        JsonArray sources = Perf.stop(new JsonObject(), Map.of()).getAsJsonObject("client").getAsJsonObject("particles")
            .getAsJsonArray("sources");
        assertEquals(4, sources.size());
        JsonObject burner = sources.get(0).getAsJsonObject();
        assertEquals("large_smoke", burner.get("type").getAsString());
        assertEquals("mymod", burner.get("mod").getAsString());
        assertEquals("com.example.Burner", burner.get("class").getAsString());
        assertEquals("client", burner.get("via").getAsString());
        assertEquals(0.67, burner.get("share").getAsDouble());
        assertEquals("flame", sources.get(3).getAsJsonObject().get("type").getAsString());
        assertEquals("server_side", sources.get(3).getAsJsonObject().get("via").getAsString());
    }

    @Test
    void aTicksWorkGoesWithItsTimeOneTickLaterAndZeroCountsAreLeftOut() {
        Perf.start("work");
        // The end of tick 1: its time isn't read yet (the hook reads the tick before), its work is held back.
        Perf.chunkLoaded();
        Perf.chunkLoaded();
        Perf.chunkGenerated();
        Perf.chunkSent();
        Perf.serverTick(1_000_000, 0);
        // The end of tick 2: tick 1's time comes, with tick 1's work.
        Perf.entityAdded();
        Perf.serverTick(120_000_000, 1);
        Perf.serverTick(2_000_000, 2);
        JsonObject server = Perf.stop(new JsonObject(), Map.of()).getAsJsonObject("server");
        assertEquals("[{\"tick\":1,\"ms\":120.0,\"chunks_generated\":1,\"chunks_loaded\":1,\"chunks_sent\":1}]",
            server.get("spikes").toString());
        assertEquals("{\"generated\":1,\"loaded\":1,\"sent\":1}", server.get("chunks").toString());
        assertEquals(1, server.get("entities_added").getAsInt());
        assertEquals("[0,0,1]", server.getAsJsonObject("timeline").get("entities_added").toString());
    }

    @Test
    void theTimelineIsInTheFileButNotTheReply() {
        Perf.start("timeline");
        Perf.serverTick(3_000_000, 500);
        Perf.serverTick(90_000_000, 501);
        JsonObject summary = Perf.stop(new JsonObject(), Map.of());
        JsonObject server = summary.getAsJsonObject("server");
        assertEquals("{\"tick\":[500,501],\"ms\":[3.0,90.0],\"chunks_generated\":[0,0],\"chunks_loaded\":[0,0],"
            + "\"chunks_sent\":[0,0],\"entities_added\":[0,0]}", server.get("timeline").toString());
        assertEquals("[{\"tick\":501,\"ms\":90.0}]", server.get("spikes").toString());
        assertEquals(90.0, server.getAsJsonObject("mspt").get("p99").getAsDouble());
        JsonObject reply = PerfCommands.forReply(summary);
        assertFalse(reply.getAsJsonObject("server").has("timeline"));
        assertTrue(server.has("timeline"));
        assertTrue(reply.getAsJsonObject("server").has("spikes"));
    }
}
