package dev.blockpilot.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The stdio server driven through {@code --mcp} with no game in its directory. */
class StdioServerTest {
    @TempDir
    Path gameDir;

    @Test
    void answersInitializePingAndToolsListWithoutAGame() {
        Map<Object, Map<?, ?>> answers = serve(
            "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{\"protocolVersion\":\"2025-06-18\","
                + "\"capabilities\":{},\"clientInfo\":{\"name\":\"test\"}}}",
            "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}",
            "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"ping\"}",
            "{\"jsonrpc\":\"2.0\",\"id\":\"list\",\"method\":\"tools/list\"}");
        assertEquals(3, answers.size());
        Map<?, ?> initialized = result(answers.get(Json.parse("1")));
        assertEquals("2025-06-18", initialized.get("protocolVersion"));
        assertEquals(Map.of("name", "blockpilot", "version", "9.9.9"), initialized.get("serverInfo"));
        assertEquals(Map.of(), result(answers.get(Json.parse("2"))));
        assertEquals(Json.parse(Json.write(McpTools.toolsList())), result(answers.get("list")));
    }

    @Test
    void aToolCallWithoutAGameIsAToolErrorAndTheServerKeepsGoing() {
        Map<Object, Map<?, ?>> answers = serve(
            "{\"jsonrpc\":\"2.0\",\"id\":\"call\",\"method\":\"tools/call\",\"params\":{\"name\":\"run\","
                + "\"arguments\":{\"script\":\"status\"}}}",
            "{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"ping\"}");
        Map<?, ?> called = result(answers.get("call"));
        assertEquals(Boolean.TRUE, called.get("isError"));
        String text = (String) ((Map<?, ?>) ((List<?>) called.get("content")).get(0)).get("text");
        assertTrue(text.startsWith("blockpilot: no game is running in " + gameDir + ": "), text);
        assertEquals(Map.of(), result(answers.get(Json.parse("3"))));
    }

    @Test
    void refusesBatchesJunkAndUnknownMethodsAndIgnoresResponses() {
        List<Map<?, ?>> messages = messages(
            "[{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"ping\"}]",
            "not json",
            "{\"jsonrpc\":\"2.0\",\"id\":5,\"method\":\"resources/list\"}",
            "{\"jsonrpc\":\"2.0\",\"id\":9,\"result\":{}}");
        assertEquals(List.of(-32600, -32700, -32601), messages.stream()
            .map(message -> ((Number) ((Map<?, ?>) message.get("error")).get("code")).intValue()).toList());
    }

    private static Map<?, ?> result(Map<?, ?> answer) {
        return (Map<?, ?>) answer.get("result");
    }

    private Map<Object, Map<?, ?>> serve(String... lines) {
        Map<Object, Map<?, ?>> byId = new LinkedHashMap<>();
        messages(lines).forEach(message -> byId.put(message.get("id"), message));
        return byId;
    }

    /** Every stdout line, each checked to be a JSON-RPC response with exactly one of result or error. */
    private List<Map<?, ?>> messages(String... lines) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        String previous = System.getProperty(Launcher.VERSION_PROPERTY);
        System.setProperty(Launcher.VERSION_PROPERTY, "9.9.9");
        try {
            int code = Main.run(new String[] {"--game-dir", gameDir.toString(), "--mcp"},
                new ByteArrayInputStream(String.join("\n", lines).getBytes(StandardCharsets.UTF_8)),
                new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8));
            assertEquals(0, code);
        } finally {
            if (previous == null) {
                System.clearProperty(Launcher.VERSION_PROPERTY);
            } else {
                System.setProperty(Launcher.VERSION_PROPERTY, previous);
            }
        }
        List<Map<?, ?>> messages = new ArrayList<>();
        for (String line : out.toString(StandardCharsets.UTF_8).split("\n", -1)) {
            if (line.isEmpty()) {
                continue;
            }
            Map<?, ?> message = (Map<?, ?>) Json.parse(line);
            assertEquals("2.0", message.get("jsonrpc"), line);
            assertTrue(message.containsKey("id") && message.containsKey("result") != message.containsKey("error"),
                line);
            messages.add(message);
        }
        return messages;
    }
}
