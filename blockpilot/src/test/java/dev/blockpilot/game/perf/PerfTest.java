package dev.blockpilot.game.perf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
            Perf.spikes(ticks, labels).toString());
        double[] many = new double[30];
        java.util.Arrays.fill(many, 70);
        assertEquals(Perf.MAX_SPIKES, Perf.spikes(many, new long[30]).size());
    }

    @Test
    void theTimelineIsInTheFileButNotTheReply() {
        Perf.start("timeline");
        Perf.serverTick(3_000_000, 500);
        Perf.serverTick(90_000_000, 501);
        JsonObject summary = Perf.stop(new JsonObject(), Map.of());
        JsonObject server = summary.getAsJsonObject("server");
        assertEquals("{\"tick\":[500,501],\"ms\":[3.0,90.0]}", server.get("timeline").toString());
        assertEquals("[{\"tick\":501,\"ms\":90.0}]", server.get("spikes").toString());
        assertEquals(90.0, server.getAsJsonObject("mspt").get("p99").getAsDouble());
        JsonObject reply = PerfCommands.forReply(summary);
        assertFalse(reply.getAsJsonObject("server").has("timeline"));
        assertTrue(server.has("timeline"));
        assertTrue(reply.getAsJsonObject("server").has("spikes"));
    }
}
