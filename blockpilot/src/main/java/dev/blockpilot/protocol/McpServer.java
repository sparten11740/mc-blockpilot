package dev.blockpilot.protocol;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import dev.blockpilot.api.Result;
import dev.blockpilot.wire.Json;
import dev.blockpilot.wire.McpTools;
import java.net.URI;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MCP over Streamable HTTP at {@code /mcp}: initialize, ping, tools/list and tools/call, answered with plain JSON
 * (no server-sent events). The tool list and server details come from {@link McpTools}, which the stdio server shares.
 */
public final class McpServer {
    /** Captures the next rendered frame as PNG, scaled to the width. */
    public interface Screenshots {
        CompletableFuture<byte[]> capture(int width);
    }

    private final Engine engine;
    private final Token token;
    private final Screenshots screenshots;
    private final String version;
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();

    public McpServer(Engine engine, Token token, Screenshots screenshots, String version) {
        this.engine = engine;
        this.token = token;
        this.screenshots = screenshots;
        this.version = version;
    }

    public CompletableFuture<HttpCodec.Response> handle(HttpCodec.Request request) {
        String path = request.path().split("\\?", 2)[0];
        if (!path.equals("/mcp")) {
            return done(HttpCodec.Response.of(404, "text/plain", "Blockpilot serves MCP at /mcp"));
        }
        if (!originAllowed(request.header("origin"))) {
            return done(HttpCodec.Response.of(403, "text/plain", "Origin not allowed"));
        }
        String authorization = request.header("authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")
            || !token.matches(authorization.substring("Bearer ".length()))) {
            return done(HttpCodec.Response.of(401, "text/plain", "Missing or wrong bearer token")
                .header("WWW-Authenticate", "Bearer"));
        }
        String sessionId = request.header("mcp-session-id");
        switch (request.method()) {
            case "GET" -> {
                return done(HttpCodec.Response.of(405, "text/plain", "No server-initiated stream; use POST")
                    .header("Allow", "POST, DELETE"));
            }
            case "DELETE" -> {
                Session session = sessionId == null ? null : sessions.remove(sessionId);
                if (session != null) {
                    engine.closeSession(session);
                }
                return done(HttpCodec.Response.of(session == null ? 404 : 204, null, null));
            }
            case "POST" -> {
                return post(request, sessionId);
            }
            default -> {
                return done(HttpCodec.Response.of(405, "text/plain", "Use POST").header("Allow", "POST, DELETE"));
            }
        }
    }

    private CompletableFuture<HttpCodec.Response> post(HttpCodec.Request request, String sessionId) {
        JsonElement body;
        try {
            body = JsonParser.parseString(request.body());
        } catch (JsonSyntaxException e) {
            return done(json(200, error(null, -32700, "Parse error")));
        }
        List<JsonObject> messages = new ArrayList<>();
        if (body.isJsonArray()) {
            body.getAsJsonArray().forEach(element -> messages.add(element.getAsJsonObject()));
        } else if (body.isJsonObject()) {
            messages.add(body.getAsJsonObject());
        } else {
            return done(json(200, error(null, -32600, "Invalid request")));
        }
        boolean initializing = messages.stream().anyMatch(message -> "initialize".equals(method(message)));
        Session session = sessionId == null ? null : sessions.get(sessionId);
        String newSessionId = null;
        if (initializing) {
            newSessionId = UUID.randomUUID().toString();
            session = engine.openSession();
            sessions.put(newSessionId, session);
        } else if (sessionId == null) {
            return done(json(400, error(null, -32600, "Missing Mcp-Session-Id; initialize first")));
        } else if (session == null) {
            return done(HttpCodec.Response.of(404, "application/json",
                error(null, -32001, "Unknown session; initialize again").toString()));
        }
        List<CompletableFuture<JsonObject>> answers = new ArrayList<>();
        for (JsonObject message : messages) {
            if (message.has("id")) {
                answers.add(answer(session, message));
            }
        }
        String responseSession = newSessionId;
        if (answers.isEmpty()) {
            return done(HttpCodec.Response.of(202, null, null));
        }
        return CompletableFuture.allOf(answers.toArray(CompletableFuture[]::new)).thenApply(ignored -> {
            JsonElement payload;
            if (body.isJsonArray()) {
                JsonArray array = new JsonArray();
                answers.forEach(answer -> array.add(answer.join()));
                payload = array;
            } else {
                payload = answers.get(0).join();
            }
            HttpCodec.Response response = json(200, payload);
            return responseSession == null ? response : response.header("Mcp-Session-Id", responseSession);
        });
    }

    private CompletableFuture<JsonObject> answer(Session session, JsonObject message) {
        JsonElement id = message.get("id");
        JsonObject params = message.has("params") && message.get("params").isJsonObject()
            ? message.getAsJsonObject("params") : new JsonObject();
        String method = method(message);
        return switch (method == null ? "" : method) {
            case "initialize" -> CompletableFuture.completedFuture(success(id, initialize(params)));
            case "ping" -> CompletableFuture.completedFuture(success(id, new JsonObject()));
            case "tools/list" -> CompletableFuture.completedFuture(success(id, tools()));
            case "tools/call" -> call(session, params).thenApply(result -> success(id, result));
            default -> CompletableFuture.completedFuture(error(id, -32601, "Method not found: " + method));
        };
    }

    private JsonObject initialize(JsonObject params) {
        String requested = params.has("protocolVersion") ? params.get("protocolVersion").getAsString() : null;
        return JsonParser.parseString(Json.write(McpTools.initializeResult(requested, version))).getAsJsonObject();
    }

    static JsonObject tools() {
        return JsonParser.parseString(Json.write(McpTools.toolsList())).getAsJsonObject();
    }

    private CompletableFuture<JsonObject> call(Session session, JsonObject params) {
        String name = params.has("name") ? params.get("name").getAsString() : "";
        JsonObject arguments = params.has("arguments") && params.get("arguments").isJsonObject()
            ? params.getAsJsonObject("arguments") : new JsonObject();
        switch (name) {
            case "run" -> {
                String script = arguments.has("script") ? arguments.get("script").getAsString() : "";
                return engine.submit(session, null, script).thenApply(reply ->
                    textContent(Formatter.render(reply, session.format()), !reply.allOk()));
            }
            case "help" -> {
                String topic = arguments.has("topic") ? arguments.get("topic").getAsString() : null;
                Result result = engine.help().answer(topic);
                return CompletableFuture.completedFuture(textContent(
                    Formatter.render(new Reply(null, List.of(result), List.of()), session.format()), !result.isOk()));
            }
            case "screenshot" -> {
                int width = arguments.has("width") ? arguments.get("width").getAsInt() : 512;
                return screenshots.capture(Math.max(16, Math.min(1280, width))).handle((png, failure) -> {
                    if (failure != null) {
                        return textContent("err screenshot " + failure.getMessage(), true);
                    }
                    JsonObject image = new JsonObject();
                    image.addProperty("type", "image");
                    image.addProperty("data", Base64.getEncoder().encodeToString(png));
                    image.addProperty("mimeType", "image/png");
                    JsonArray content = new JsonArray();
                    content.add(image);
                    JsonObject result = new JsonObject();
                    result.add("content", content);
                    return result;
                });
            }
            default -> {
                return CompletableFuture.completedFuture(textContent("Unknown tool " + name, true));
            }
        }
    }

    private static JsonObject textContent(String text, boolean isError) {
        JsonObject item = new JsonObject();
        item.addProperty("type", "text");
        item.addProperty("text", text);
        JsonArray content = new JsonArray();
        content.add(item);
        JsonObject result = new JsonObject();
        result.add("content", content);
        result.addProperty("isError", isError);
        return result;
    }

    /**
     * Browsers send an Origin; only pages served from this machine may talk to the endpoint (DNS rebinding). Tools
     * and CLIs send none. The opaque origin {@code null} (sandboxed frames, local files) is refused.
     */
    static boolean originAllowed(String origin) {
        if (origin == null) {
            return true;
        }
        try {
            String host = URI.create(origin).getHost();
            return host != null && (host.equals("localhost") || host.equals("127.0.0.1") || host.equals("[::1]")
                || host.equals("::1"));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static String method(JsonObject message) {
        return message.has("method") ? message.get("method").getAsString() : null;
    }

    private static JsonObject success(JsonElement id, JsonObject result) {
        JsonObject response = new JsonObject();
        response.addProperty("jsonrpc", "2.0");
        response.add("id", id);
        response.add("result", result);
        return response;
    }

    private static JsonObject error(JsonElement id, int code, String message) {
        JsonObject response = new JsonObject();
        response.addProperty("jsonrpc", "2.0");
        response.add("id", id);
        JsonObject error = new JsonObject();
        error.addProperty("code", code);
        error.addProperty("message", message);
        response.add("error", error);
        return response;
    }

    private static HttpCodec.Response json(int status, JsonElement payload) {
        return HttpCodec.Response.of(status, "application/json", payload.toString());
    }

    private static CompletableFuture<HttpCodec.Response> done(HttpCodec.Response response) {
        return CompletableFuture.completedFuture(response);
    }

    /** Closes every MCP session, e.g. when the endpoint shuts down. */
    public void closeAll() {
        sessions.values().forEach(engine::closeSession);
        sessions.clear();
    }
}
