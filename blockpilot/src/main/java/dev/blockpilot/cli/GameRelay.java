package dev.blockpilot.cli;

import java.io.IOException;
import java.io.PrintStream;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Sends the stdio server's tool calls to the game that {@code endpoint.json} names at the moment of each call, over
 * the game's own {@code /mcp}. A new game (another pid or port, as after a restart) gets a new session. A call is
 * sent again only when the game cannot have run it: the session was unknown (404) or the connection was refused.
 */
final class GameRelay implements AutoCloseable {
    private final Path gameDir;
    private final String version;
    private final PrintStream log;
    private Session session;

    /** One session with one game; its own HTTP client, so no pooled connection outlives the game it went to. */
    private record Session(long pid, int port, String token, HttpClient http, String id) {
    }

    private static final class Unreachable extends Exception {
        Unreachable(String reason) {
            super(reason);
        }
    }

    GameRelay(Path gameDir, String version, PrintStream log) {
        this.gameDir = gameDir;
        this.version = version;
        this.log = log;
    }

    /** The JSON-RPC response to a {@code tools/call} with that id, as one line of JSON. */
    String call(Object id, Map<?, ?> params, String protocolVersion) {
        String request = Json.write(rpc(id, "tools/call", params));
        boolean retried = false;
        while (true) {
            Session current;
            try {
                current = session(protocolVersion);
            } catch (Unreachable e) {
                return toolError(id, "blockpilot: no game is running in " + gameDir + ": " + e.getMessage()
                    + ". Start it, then call again.");
            }
            HttpResponse<String> response;
            try {
                response = post(current, request);
            } catch (ConnectException e) {
                drop(current);
                if (!retried) {
                    retried = true;
                    continue;
                }
                return toolError(id, "blockpilot: the game in " + gameDir + " refused the connection. "
                    + "Start it, then call again.");
            } catch (IOException e) {
                drop(current);
                return toolError(id, "blockpilot: the game closed during the call, which may have run in part ("
                    + e.getMessage() + ")");
            }
            if (response.statusCode() == 404 && !retried) {
                drop(current);
                retried = true;
                continue;
            }
            if (response.statusCode() != 200) {
                return toolError(id, "blockpilot: the game answered HTTP " + response.statusCode() + ": "
                    + response.body());
            }
            return response.body();
        }
    }

    /** Ends the game session, if any; the game would otherwise keep it until it exits. */
    @Override
    public synchronized void close() {
        if (session == null) {
            return;
        }
        try {
            session.http().send(request(session, session.id()).timeout(Duration.ofSeconds(2)).DELETE().build(),
                HttpResponse.BodyHandlers.discarding());
        } catch (IOException e) {
            // The game is gone already.
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        session.http().shutdownNow();
        session = null;
    }

    private synchronized Session session(String protocolVersion) throws Unreachable {
        EndpointFile endpoint;
        try {
            endpoint = EndpointFile.read(gameDir);
        } catch (IOException e) {
            throw new Unreachable(e.getMessage());
        }
        if (session != null && session.pid() == endpoint.pid() && session.port() == endpoint.port()) {
            return session;
        }
        if (session != null) {
            session.http().shutdown();
            session = null;
        }
        HttpClient http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(5)).build();
        Session opening = new Session(endpoint.pid(), endpoint.port(), endpoint.token(), http, null);
        try {
            Map<String, Object> client = new LinkedHashMap<>();
            client.put("name", "blockpilot-stdio");
            client.put("version", version);
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("protocolVersion", protocolVersion);
            params.put("capabilities", Map.of());
            params.put("clientInfo", client);
            HttpResponse<String> answer = post(opening, Json.write(rpc(0, "initialize", params)));
            String id = answer.headers().firstValue("Mcp-Session-Id").orElse(null);
            if (answer.statusCode() != 200 || id == null) {
                throw new Unreachable("its endpoint answered initialize with HTTP " + answer.statusCode());
            }
            session = new Session(endpoint.pid(), endpoint.port(), endpoint.token(), http, id);
            post(session, "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}");
            log.println("blockpilot: connected to the game in " + gameDir + " (pid " + endpoint.pid() + ", port "
                + endpoint.port() + ")");
            return session;
        } catch (IOException e) {
            session = null;
            http.shutdownNow();
            throw new Unreachable("its endpoint did not answer: " + e.getMessage());
        }
    }

    private synchronized void drop(Session failed) {
        if (session == failed) {
            session = null;
            failed.http().shutdown();
        }
    }

    private HttpResponse<String> post(Session target, String body) throws IOException {
        HttpRequest.Builder request = request(target, target.id())
            .header("Content-Type", "application/json")
            .header("Accept", "application/json, text/event-stream")
            .POST(HttpRequest.BodyPublishers.ofString(body));
        try {
            return target.http().send(request.build(), HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted", e);
        }
    }

    private static HttpRequest.Builder request(Session target, String sessionId) {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + target.port() + "/mcp"))
            .header("Authorization", "Bearer " + target.token());
        return sessionId == null ? request : request.header("Mcp-Session-Id", sessionId);
    }

    static Map<String, Object> rpc(Object id, String method, Object params) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("jsonrpc", "2.0");
        message.put("id", id);
        message.put("method", method);
        message.put("params", params);
        return message;
    }

    static String toolError(Object id, String text) {
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("type", "text");
        content.put("text", text);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("content", List.of(content));
        result.put("isError", true);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("jsonrpc", "2.0");
        response.put("id", id);
        response.put("result", result);
        return Json.write(response);
    }
}
