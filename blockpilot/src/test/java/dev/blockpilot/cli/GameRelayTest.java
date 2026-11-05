package dev.blockpilot.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.blockpilot.wire.Json;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The relay against stand-ins for the game's /mcp; each request is logged as "method session". */
class GameRelayTest {
    private static final String RESULT = "{\"jsonrpc\":\"2.0\",\"id\":7,\"result\":{\"content\":[],\"isError\":false}}";

    @TempDir
    Path gameDir;

    private final List<String> requests = Collections.synchronizedList(new ArrayList<>());
    private final List<HttpServer> servers = new ArrayList<>();
    private GameRelay relay;

    @AfterEach
    void stop() {
        if (relay != null) {
            relay.close();
        }
        servers.forEach(server -> server.stop(0));
    }

    @Test
    void relaysCallsInOneSession() throws IOException {
        game("a", Behavior.ANSWER);
        assertEquals(RESULT, call());
        assertEquals(RESULT, call());
        assertEquals(List.of("a initialize -", "a notifications/initialized a1", "a tools/call a1",
            "a tools/call a1"), requests);
    }

    @Test
    void aRestartedGameGetsANewSessionWithoutAnError() throws IOException {
        game("a", Behavior.ANSWER);
        assertEquals(RESULT, call());
        game("b", Behavior.ANSWER);
        assertEquals(RESULT, call());
        assertEquals(List.of("a initialize -", "a notifications/initialized a1", "a tools/call a1",
            "b initialize -", "b notifications/initialized b1", "b tools/call b1"), requests);
    }

    @Test
    void anUnknownSessionIsReopenedAndTheCallSentOnceMore() throws IOException {
        game("a", Behavior.FORGET_FIRST_SESSION);
        assertEquals(RESULT, call());
        assertEquals(List.of("a initialize -", "a notifications/initialized a1", "a tools/call a1",
            "a initialize -", "a notifications/initialized a2", "a tools/call a2"), requests);
    }

    @Test
    void aRefusedConnectionMeansNoGame() throws IOException {
        game("a", Behavior.ANSWER);
        assertEquals(RESULT, call());
        servers.remove(0).stop(0);
        String answer = call();
        assertTrue(answer.contains("\"isError\":true") && answer.contains("no game is running in "), answer);
        assertEquals(1, requests.stream().filter(request -> request.contains("tools/call")).count());
    }

    @Test
    void aConnectionThatBreaksAfterTheRequestIsNotRepeated() throws IOException {
        game("a", Behavior.HANG_UP_ON_CALLS);
        String answer = call();
        assertTrue(answer.contains("\"isError\":true") && answer.contains("closed during the call"), answer);
        assertEquals(1, requests.stream().filter(request -> request.contains("tools/call")).count());
    }

    @Test
    void closingEndsTheGameSession() throws IOException {
        game("a", Behavior.ANSWER);
        call();
        relay.close();
        assertEquals("a DELETE a1", requests.get(requests.size() - 1));
    }

    private enum Behavior { ANSWER, FORGET_FIRST_SESSION, HANG_UP_ON_CALLS }

    /** Starts a stand-in game called name and points endpoint.json at it, as a game's start would. */
    private void game(String name, Behavior behavior) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicInteger sessions = new AtomicInteger();
        server.createContext("/mcp", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            String session = exchange.getRequestHeaders().getFirst("Mcp-Session-Id");
            String method = exchange.getRequestMethod().equals("POST")
                ? (String) ((Map<?, ?>) Json.parse(body)).get("method") : exchange.getRequestMethod();
            requests.add(name + " " + method + " " + (session == null ? "-" : session));
            if (!"Bearer secret".equals(exchange.getRequestHeaders().getFirst("Authorization"))) {
                reply(exchange, 401, "");
            } else if (method.equals("initialize")) {
                exchange.getResponseHeaders().add("Mcp-Session-Id", name + sessions.incrementAndGet());
                reply(exchange, 200, "{\"jsonrpc\":\"2.0\",\"id\":0,\"result\":{}}");
            } else if (!method.equals("tools/call")) {
                reply(exchange, 202, "");
            } else if (behavior == Behavior.HANG_UP_ON_CALLS) {
                exchange.close();
            } else if (behavior == Behavior.FORGET_FIRST_SESSION && session.equals(name + "1")) {
                reply(exchange, 404, "");
            } else {
                reply(exchange, 200, RESULT);
            }
        });
        server.start();
        servers.add(server);
        Files.writeString(gameDir.resolve("token"), "secret\n");
        Files.createDirectories(gameDir.resolve("blockpilot"));
        Files.writeString(gameDir.resolve("blockpilot/endpoint.json"), Json.write(Map.of("pid",
            ProcessHandle.current().pid(), "port", server.getAddress().getPort(), "token_file",
            gameDir.resolve("token").toString())));
        if (relay == null) {
            relay = new GameRelay(gameDir, "test", new PrintStream(new ByteArrayOutputStream(), true,
                StandardCharsets.UTF_8));
        }
    }

    private String call() {
        return relay.call(Json.parse("7"), Map.of("name", "run", "arguments", Map.of("script", "status")),
            "2025-06-18");
    }

    private static void reply(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
