package dev.blockpilot.cli;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * MCP over stdio: one JSON-RPC message per line each way. An MCP client launches it once per session, so its tools
 * exist before the game does and stay through every restart; {@code initialize}, {@code ping} and {@code tools/list}
 * are answered here, tool calls go to the game through a {@link GameRelay}. Only protocol messages go to stdout.
 */
final class StdioServer {
    private final BufferedReader in;
    private final PrintStream out;
    private final GameRelay relay;
    private final String version;
    private final ExecutorService calls = Executors.newCachedThreadPool(task -> {
        Thread thread = new Thread(task, "blockpilot-mcp-call");
        thread.setDaemon(true);
        return thread;
    });
    private volatile String protocolVersion = McpTools.PROTOCOL_VERSIONS.get(0);

    StdioServer(BufferedReader in, PrintStream out, GameRelay relay, String version) {
        this.in = in;
        this.out = out;
        this.relay = relay;
        this.version = version;
    }

    /** Serves until stdin closes, then lets running calls finish and ends the game session. */
    int run() throws IOException {
        String line;
        while ((line = in.readLine()) != null) {
            if (!line.isBlank()) {
                handle(line);
            }
        }
        calls.shutdown();
        try {
            calls.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        relay.close();
        return 0;
    }

    private void handle(String line) {
        Object message;
        try {
            message = Json.parse(line);
        } catch (IllegalArgumentException | StackOverflowError e) {
            send(error(null, -32700, "Parse error"));
            return;
        }
        if (message instanceof List<?>) {
            send(error(null, -32600, "Batches are not supported; send one message per line"));
            return;
        }
        if (!(message instanceof Map<?, ?> request) || !(request.get("method") instanceof String method)) {
            // Responses would answer requests of ours, and this server sends none.
            if (!(message instanceof Map<?, ?> response && response.containsKey("id")
                && (response.containsKey("result") || response.containsKey("error")))) {
                send(error(null, -32600, "Invalid request"));
            }
            return;
        }
        if (!request.containsKey("id")) {
            return;
        }
        Object id = request.get("id");
        Map<?, ?> params = request.get("params") instanceof Map<?, ?> given ? given : Map.of();
        switch (method) {
            case "initialize" -> {
                Map<String, Object> result = McpTools.initializeResult(
                    params.get("protocolVersion") instanceof String requested ? requested : null, version);
                protocolVersion = (String) result.get("protocolVersion");
                send(success(id, result));
            }
            case "ping" -> send(success(id, Map.of()));
            case "tools/list" -> send(success(id, McpTools.toolsList()));
            case "tools/call" -> calls.execute(() -> {
                String answer;
                try {
                    answer = relay.call(id, params, protocolVersion);
                } catch (RuntimeException e) {
                    answer = GameRelay.toolError(id, "blockpilot: " + e);
                }
                send(answer);
            });
            default -> send(error(id, -32601, "Method not found: " + method));
        }
    }

    /** Raw newlines can only be whitespace between JSON tokens, so dropping them keeps one message per line. */
    private synchronized void send(String message) {
        out.print(message.replace("\r", "").replace("\n", "") + "\n");
        out.flush();
    }

    private static String success(Object id, Object result) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("jsonrpc", "2.0");
        response.put("id", id);
        response.put("result", result);
        return Json.write(response);
    }

    private static String error(Object id, int code, String text) {
        Map<String, Object> problem = new LinkedHashMap<>();
        problem.put("code", code);
        problem.put("message", text);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("jsonrpc", "2.0");
        response.put("id", id);
        response.put("error", problem);
        return Json.write(response);
    }
}
