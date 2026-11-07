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
        CODES.put("paused", "the singleplayer game is paused, so commands that need server ticks can't run");
        CODES.put("disabled", "agent control is switched off with the toggle key");
        CODES.put("interrupted", "the player pressed a key or moved the mouse; agent actions stop. input= names it: "
            + "key with key=<name>, button with button=<left|right|middle|n>, scroll, or move with dx= dy= (window "
            + "pixels); the interrupted event carries the same");
        CODES.put("cancelled", "stopped by stop, or the session closed");
        CODES.put("stalled", "the game's client thread finished no tick for 30s outside Blockpilot's own commands, "
            + "e.g. flooded with chat; the script was cancelled, and the reason says how long and why if known");
        CODES.put("timeout", "the action or wait ran out of time; the last values are attached");
        CODES.put("assert_failed", "the assert's predicates did not hold; the observed values are attached");
        CODES.put("no_item", "the item is not in the inventory");
        CODES.put("no_entity", "no entity with that reference is loaded");
        CODES.put("no_block", "no matching block found");
        CODES.put("no_path", "no walkable path to the target through loaded chunks");
        CODES.put("stuck", "the player stopped making progress (goto along its path, move by distance)");
        CODES.put("out_of_reach", "the target is beyond reach and the command does not walk");
        CODES.put("unbreakable", "mine: the player can't break the block (bedrock outside creative, adventure mode); "
            + "it is named");
        CODES.put("obstructed", "use at a block aboard a sub-level found something else in the way; it is named");
        CODES.put("no_recipe", "no crafting recipe for that item");
        CODES.put("missing", "ingredients missing for the recipe");
        CODES.put("need_table", "the recipe needs a crafting table within 4 blocks");
        CODES.put("no_screen", "the command needs an open screen (GUI)");
        CODES.put("too_large", "the requested region or output exceeds the limit");
        CODES.put("unloaded", "the position is in a chunk that is not loaded");
        CODES.put("join_failed", "joining the world or server failed; the reason is attached");
        CODES.put("exists", "a world, or a recording, with that name already exists");
        CODES.put("no_test", "gametest: no registered GameTest matches the selector");
        CODES.put("no_snapshot", "this world has no snapshot with that name (snapshot list)");
        CODES.put("not_test_world", "only worlds made by create world can be deleted");
        CODES.put("not_frozen", "tick step needs the server frozen first (tick freeze)");
        CODES.put("busy", "a performance capture or a recording is already running (perf stop or record stop first)");
        CODES.put("no_capture", "no performance capture or recording is running (perf start or record start first)");
        CODES.put("record_failed", "ffmpeg failed while recording, e.g. on a full disk; its last lines are attached, "
            + "and the partial file is removed");
        CODES.put("needs_restart", "config: the value takes effect only after a game restart, or for a server "
            + "config after the world loads its file again; edit the file instead");
        CODES.put("unsupported", "the command needs something this loader or machine lacks (config: NeoForge's "
            + "config system; record: ffmpeg with an H.264 encoder, and for sound a running sound engine on OpenAL "
            + "Soft)");
        CODES.put("command_failed", "the server command failed; its message is attached");
        CODES.put("ext_failed", "an extension command threw an exception; it is logged");
        CODES.put("no_sublevel", "no sub-level has that UUID or name, or here found none");
        CODES.put("no_room", "sable:tp found no spot with room for the player near that block aboard");
        CODES.put("not_aboard", "sable:tp: the client never showed the player standing on the spot aboard; what it "
            + "shows instead is attached");
        CODES.put("ambiguous", "several sub-levels have that name; their UUIDs are attached");
        CODES.put("sable", "a call into Sable failed; a missing method turns the Sable integration off until restart");
        CODES.put("internal", "Blockpilot hit an unexpected error; it is logged");
    }

    private Errors() {
    }
}
