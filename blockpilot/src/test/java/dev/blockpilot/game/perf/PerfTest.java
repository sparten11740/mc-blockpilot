package dev.blockpilot.game.perf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

/** The capture summary, fed the way the instrumentation mixins feed it. */
class PerfTest {
    @Test
    void summarizesWhatTheHooksRecorded() {
        Perf.start("unit");
        for (int tick = 0; tick < 20; tick++) {
            Perf.serverTick(2_000_000);
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
        JsonObject summary = Perf.stop(new JsonObject());

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
        JsonObject payload = summary.getAsJsonObject("network").getAsJsonArray("clientbound").get(0).getAsJsonObject();
        assertEquals("custom_payload/neoforge:custom_time_packet", payload.get("type").getAsString());
        assertEquals(1, payload.get("count").getAsInt());
        assertEquals(12, payload.get("bytes").getAsInt());
        assertEquals("move_player_pos", summary.getAsJsonObject("network").getAsJsonArray("serverbound").get(0)
            .getAsJsonObject().get("type").getAsString());
    }
}
