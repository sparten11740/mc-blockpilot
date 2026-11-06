package dev.blockpilot.wire;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What an MCP client learns before its first tool call, the same from the game's HTTP endpoint and from the stdio
 * server, which answers it while no game runs. Three tools keep the per-request schema cost small; the command
 * reference itself is fetched with {@code help} when needed.
 */
public final class McpTools {
    public static final List<String> PROTOCOL_VERSIONS =
        List.of("2025-11-25", "2025-06-18", "2025-03-26", "2024-11-05");

    static final String RUN_DESCRIPTION = """
        Runs Blockpilot commands in the Minecraft client. Script: commands separated by ; (one result line each, \
        stops at the first err; ?cmd goes on past its own err). Positions x,y,z or ~dx,dy,dz (relative); \
        entities e<id> or @<name> (a tag or custom name); counts x<n>; durations 20t / 3s; options name=value.
        Common: status; look [r]; find <block|#tag|entity> [r]; block <pos>; inv; screen; goto <pos|e<id>>; \
        mine <pos|block> [x<n>]; place <item> <pos>; use [pos|e<id>]; attack <e<id>>; select <item>; \
        craft <item> [x<n>]; chat <text>; cmd <command>; mouse down|up|click <button>; drag <button> <dx> <dy> [Nt]; \
        key press <key>; wait <Nt>; wait <query> until <field><op><value>; assert <query> <field><op><value>; \
        if <query> <field><op><value> then <cmd>; stop; fmt json|text.
        Use help, help <command> or help errors for the full reference.""";

    private static final String INSTRUCTIONS = "Controls the running Minecraft client. Use run with short command "
        + "scripts; call help (or run \"help <command>\") for the reference.";

    private McpTools() {
    }

    /** The answer to {@code initialize}: the client's protocol version when supported, else the newest. */
    public static Map<String, Object> initializeResult(String requestedVersion, String serverVersion) {
        Map<String, Object> serverInfo = new LinkedHashMap<>();
        serverInfo.put("name", "blockpilot");
        serverInfo.put("version", serverVersion);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("protocolVersion",
            PROTOCOL_VERSIONS.contains(requestedVersion) ? requestedVersion : PROTOCOL_VERSIONS.get(0));
        result.put("capabilities", Map.of("tools", Map.of("listChanged", false)));
        result.put("serverInfo", serverInfo);
        result.put("instructions", INSTRUCTIONS);
        return result;
    }

    public static Map<String, Object> toolsList() {
        return Map.of("tools", List.of(
            tool("run", RUN_DESCRIPTION, "script", "string", "Commands separated by ;", true),
            tool("screenshot", "PNG of the next rendered frame. Use only when text output is not enough.",
                "width", "integer", "Pixel width, default 512, max 1280", false),
            tool("help", "The command reference: no topic lists commands; a command, group or 'errors' gives details.",
                "topic", "string", "Command, group, namespace or errors", false)));
    }

    private static Map<String, Object> tool(String name, String description, String parameter, String type,
        String parameterDescription, boolean required) {
        Map<String, Object> property = new LinkedHashMap<>();
        property.put("type", type);
        property.put("description", parameterDescription);
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", Map.of(parameter, property));
        if (required) {
            schema.put("required", List.of(parameter));
        }
        Map<String, Object> tool = new LinkedHashMap<>();
        tool.put("name", name);
        tool.put("description", description);
        tool.put("inputSchema", schema);
        return tool;
    }
}
