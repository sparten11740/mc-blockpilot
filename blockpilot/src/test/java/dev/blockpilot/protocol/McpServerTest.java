package dev.blockpilot.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.blockpilot.api.CommandSpec;
import dev.blockpilot.api.Fields;
import dev.blockpilot.api.Result;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class McpServerTest {
    @TempDir
    Path directory;

    private Token token;
    private McpServer mcp;

    @BeforeEach
    void setUp() throws Exception {
        token = Token.loadOrCreate(directory.resolve("token"));
        CommandRegistry registry = new CommandRegistry();
        registry.register(CommandSpec.query("status", (context, args) ->
            Result.ok(new Fields().tag("hp", 20))));
        Engine engine = new Engine(registry, new FakeGame());
        mcp = new McpServer(engine, token, width -> CompletableFuture.completedFuture(new byte[] {1, 2, 3}), "test");
    }

    private HttpCodec.Response post(String sessionId, String body) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("authorization", "Bearer " + token.value());
        if (sessionId != null) {
            headers.put("mcp-session-id", sessionId);
        }
        return mcp.handle(new HttpCodec.Request("POST", "/mcp", headers, body)).join();
    }

    private String initialize() {
        HttpCodec.Response response = post(null, "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\","
            + "\"params\":{\"protocolVersion\":\"2025-06-18\",\"capabilities\":{},\"clientInfo\":{\"name\":\"t\"}}}");
        assertEquals(200, response.status());
        JsonObject result = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("result");
        assertEquals("2025-06-18", result.get("protocolVersion").getAsString());
        String sessionId = response.headers().get("Mcp-Session-Id");
        assertNotNull(sessionId);
        return sessionId;
    }

    @Test
    void initializesAndListsThreeTools() {
        String session = initialize();
        assertEquals(202, post(session, "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}").status());
        JsonArray tools = JsonParser.parseString(post(session,
            "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\"}").body())
            .getAsJsonObject().getAsJsonObject("result").getAsJsonArray("tools");
        assertEquals(3, tools.size());
        assertEquals("run", tools.get(0).getAsJsonObject().get("name").getAsString());
    }

    @Test
    void toolDescriptionsStaySmall() {
        // Roughly four characters per token; the three tools together must stay under 400 tokens.
        assertTrue(McpServer.tools().toString().length() < 1600, "tools/list is " + McpServer.tools().toString().length()
            + " characters");
    }

    @Test
    void runReturnsCompactText() {
        String session = initialize();
        JsonObject result = JsonParser.parseString(post(session, "{\"jsonrpc\":\"2.0\",\"id\":3,"
            + "\"method\":\"tools/call\",\"params\":{\"name\":\"run\",\"arguments\":{\"script\":\"status\"}}}").body())
            .getAsJsonObject().getAsJsonObject("result");
        assertEquals("ok hp20", result.getAsJsonArray("content").get(0).getAsJsonObject().get("text").getAsString());
        assertFalse(result.get("isError").getAsBoolean());
    }

    @Test
    void commandErrorsAreToolErrors() {
        String session = initialize();
        JsonObject result = JsonParser.parseString(post(session, "{\"jsonrpc\":\"2.0\",\"id\":3,"
            + "\"method\":\"tools/call\",\"params\":{\"name\":\"run\",\"arguments\":{\"script\":\"nope\"}}}").body())
            .getAsJsonObject().getAsJsonObject("result");
        assertTrue(result.get("isError").getAsBoolean());
    }

    @Test
    void screenshotIsImageContent() {
        String session = initialize();
        JsonObject image = JsonParser.parseString(post(session, "{\"jsonrpc\":\"2.0\",\"id\":4,"
            + "\"method\":\"tools/call\",\"params\":{\"name\":\"screenshot\",\"arguments\":{}}}").body())
            .getAsJsonObject().getAsJsonObject("result").getAsJsonArray("content").get(0).getAsJsonObject();
        assertEquals("image", image.get("type").getAsString());
        assertEquals("image/png", image.get("mimeType").getAsString());
    }

    @Test
    void rejectsMissingTokenUnknownSessionAndForeignOrigins() throws Exception {
        assertEquals(401, mcp.handle(new HttpCodec.Request("POST", "/mcp", Map.of(), "{}")).join().status());
        assertEquals(404, post("nope", "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"ping\"}").status());
        assertEquals(400, post(null, "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"ping\"}").status());
        Map<String, String> foreign = Map.of("authorization", "Bearer " + token.value(),
            "origin", "https://evil.example");
        assertEquals(403, mcp.handle(new HttpCodec.Request("POST", "/mcp", foreign, "{}")).join().status());
        assertFalse(McpServer.originAllowed("null"));
        assertTrue(McpServer.originAllowed("http://localhost:3000"));
        Map<String, String> auth = Map.of("authorization", "Bearer " + token.value());
        assertEquals(405, mcp.handle(new HttpCodec.Request("GET", "/mcp", auth, "")).join().status());
        assertEquals(404, mcp.handle(new HttpCodec.Request("POST", "/other", auth, "")).join().status());
        assertTrue(Files.readString(token.file()).strip().length() >= 40);
    }

    @Test
    void tokenSurvivesRestart() throws Exception {
        assertEquals(token.value(), Token.loadOrCreate(token.file()).value());
        assertFalse(token.matches("wrong"));
    }
}
