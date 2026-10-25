package dev.blockpilot.protocol;

import java.util.LinkedHashMap;
import java.util.Map;

/** The stable error codes, with what each means; {@code help errors} prints this table. */
public final class Errors {
    public static final Map<String, String> CODES = new LinkedHashMap<>();

    static {
        CODES.put("syntax", "unknown command or malformed arguments; the hint says what was expected");
        CODES.put("skip", "not an error: the command did not run because an earlier one failed");
        CODES.put("not_in_world", "the command needs a loaded world");
        CODES.put("no_server", "the command needs the integrated server (singleplayer or LAN host)");
        CODES.put("no_cheats", "the command needs a world that allows commands (cheats on)");
        CODES.put("disabled", "agent control is switched off with the toggle key");
        CODES.put("interrupted", "the player pressed a key or moved the mouse; agent actions stop");
        CODES.put("cancelled", "stopped by stop, or the session closed");
        CODES.put("timeout", "the action or wait ran out of time; the last values are attached");
        CODES.put("assert_failed", "the assert's predicates did not hold; the observed values are attached");
        CODES.put("no_item", "the item is not in the inventory");
        CODES.put("no_entity", "no entity with that reference is loaded");
        CODES.put("no_block", "no matching block found");
        CODES.put("no_path", "no walkable path to the target through loaded chunks");
        CODES.put("stuck", "the player stopped making progress (goto along its path, move by distance)");
        CODES.put("out_of_reach", "the target is beyond reach and the command does not walk");
        CODES.put("no_recipe", "no crafting recipe for that item");
        CODES.put("missing", "ingredients missing for the recipe");
        CODES.put("need_table", "the recipe needs a crafting table within 4 blocks");
        CODES.put("no_screen", "the command needs an open screen (GUI)");
        CODES.put("too_large", "the requested region or output exceeds the limit");
        CODES.put("unloaded", "the position is in a chunk that is not loaded");
        CODES.put("join_failed", "joining the world or server failed; the reason is attached");
        CODES.put("exists", "a world with that name already exists");
        CODES.put("no_snapshot", "this world has no snapshot with that name (snapshot list)");
        CODES.put("not_test_world", "only worlds made by create world can be deleted");
        CODES.put("not_frozen", "tick step needs the server frozen first (tick freeze)");
        CODES.put("busy", "a performance capture is already running (perf stop first)");
        CODES.put("no_capture", "no performance capture is running (perf start first)");
        CODES.put("command_failed", "the server command failed; its message is attached");
        CODES.put("ext_failed", "an extension command threw an exception; it is logged");
        CODES.put("internal", "Blockpilot hit an unexpected error; it is logged");
    }

    private Errors() {
    }
}
