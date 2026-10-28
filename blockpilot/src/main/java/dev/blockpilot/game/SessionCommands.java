package dev.blockpilot.game;

import dev.blockpilot.api.Action;
import dev.blockpilot.api.ArgType;
import dev.blockpilot.api.Args;
import dev.blockpilot.api.CommandContext;
import dev.blockpilot.api.CommandException;
import dev.blockpilot.api.CommandSpec;
import dev.blockpilot.api.Fields;
import dev.blockpilot.api.Result;
import dev.blockpilot.api.Row;
import dev.blockpilot.protocol.CommandRegistry;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
//? if >=26.1 {
/*import net.minecraft.client.multiplayer.ClientLevel;
*///?}
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.LevelSummary;
import net.minecraft.world.level.storage.WorldData;

/** Getting into and out of games: listing saves and servers, joining, leaving, quitting, respawning. */
final class SessionCommands {
    private static final int QUIT_DELAY_TICKS = 5;

    void register(CommandRegistry registry) {
        registry.register(CommandSpec.query("worlds", (context, args) -> worlds()).group("session")
            .help("singleplayer saves, most recently played first").example("worlds"));
        registry.register(CommandSpec.query("servers", (context, args) -> servers()).group("session")
            .help("the multiplayer server list").example("servers"));
        registry.register(CommandSpec.action("join", this::join).group("session")
            .form(form -> form.keyword("kind", "world").text("name"))
            .form(form -> form.keyword("kind", "server").arg("name", ArgType.INT, ArgType.WORD))
            .option("tick", "frozen: freeze a world's server before its first tick, so it reads as saved until "
                + "tick unfreeze or tick step", List.of("frozen"))
            .help("joins a save folder or a server (list index or address); returns once the player can act")
            .example("join world New World"));
        registry.register(CommandSpec.action("leave", (context, args) -> leave()).group("session")
            .help("saves and quits singleplayer or disconnects, back to the title screen").example("leave"));
        registry.register(CommandSpec.action("quit", (context, args) -> quit()).group("session")
            .help("leaves the world as leave does, answers with the game's process id, then closes the game")
            .field("pid", "the game's process id; it exits shortly after the reply").example("quit"));
        registry.register(CommandSpec.action("respawn", (context, args) -> respawn()).needsWorld().group("session")
            .help("respawns from the death screen").example("respawn"));
    }

    private static Minecraft minecraft() {
        return Minecraft.getInstance();
    }

    static List<LevelSummary> summaries() throws CommandException {
        var source = minecraft().getLevelSource();
        try {
            return source.loadLevelSummaries(source.findLevelCandidates()).join();
        } catch (Exception e) {
            throw new CommandException("internal", "could not list saves: " + e.getMessage());
        }
    }

    private Result worlds() throws CommandException {
        List<LevelSummary> saves = new ArrayList<>(summaries());
        saves.sort(Comparator.comparingLong(LevelSummary::getLastPlayed).reversed());
        List<Row> rows = new ArrayList<>();
        // Vanilla leaves out saves whose lock is held, which includes the world loaded right now.
        MinecraftServer server = minecraft().getSingleplayerServer();
        if (server != null) {
            String folder = Snapshots.folderOf(server);
            WorldData data = server.getWorldData();
            rows.add(Row.of(folder, world(folder, data.getLevelName(), data.getGameType(), data.isAllowCommands(),
                System.currentTimeMillis()).put("loaded", true, "loaded")));
            saves.removeIf(save -> save.getLevelId().equals(folder));
        }
        for (LevelSummary save : saves) {
            rows.add(Row.of(save.getLevelId(), world(save.getLevelId(), save.getLevelName(), save.getGameMode(),
                save.hasCommands(), save.getLastPlayed())));
        }
        return Result.ok().withRows(rows);
    }

    private static Fields world(String folder, String name, GameType mode, boolean cheats, long lastPlayed) {
        Fields fields = new Fields().put("folder", folder, "\"" + folder + "\"");
        if (!name.equals(folder)) {
            fields.put("name", name, "name:\"" + name + "\"");
        }
        fields.bare("mode", mode.getName());
        if (cheats) {
            fields.put("cheats", true, "cheats");
        }
        if (TestWorlds.isTestWorld(folder)) {
            fields.put("test", true, "test");
        }
        return fields.hidden("last_played", lastPlayed);
    }

    private Result servers() {
        ServerList list = new ServerList(minecraft());
        list.load();
        List<Row> rows = new ArrayList<>();
        for (int index = 0; index < list.size(); index++) {
            ServerData server = list.get(index);
            rows.add(Row.of(String.valueOf(index), new Fields().bare("index", index)
                .put("name", server.name, "\"" + server.name + "\"").bare("address", server.ip)));
        }
        return Result.ok().withRows(rows);
    }

    private Action join(CommandContext context, Args args) throws CommandException {
        String name = args.word("name");
        boolean world = args.word("kind").equals("world");
        if (args.hasOption("tick") && !world) {
            throw new CommandException("syntax", "tick=frozen freezes a singleplayer world; a server can't be frozen");
        }
        if (minecraft().level != null) {
            disconnect();
        }
        if (world) {
            boolean exists = summaries().stream().anyMatch(save -> save.getLevelId().equals(name));
            if (!exists) {
                throw new CommandException("join_failed", "no save folder " + name + "; see worlds");
            }
            ServerCommands.freezeOnStart(args.hasOption("tick") ? name : null);
            minecraft().createWorldOpenFlows().openWorld(name, () -> minecraft().setScreen(new TitleScreen()));
        } else {
            String address = name;
            ServerList list = new ServerList(minecraft());
            list.load();
            ServerData data = null;
            int index = args.type("name") == ArgType.INT ? args.integer("name", -1) : -1;
            if (index >= 0 && index < list.size()) {
                data = list.get(index);
                address = data.ip;
            }
            if (data == null) {
                data = new ServerData(address, address, ServerData.Type.OTHER);
            }
            ConnectScreen.startConnecting(new JoinMultiplayerScreen(new TitleScreen()), minecraft(),
                ServerAddress.parseString(address), data, false, null);
        }
        return new Joined(name);
    }

    /** Waits until the player is in the world and can act, or the connection fails. */
    private record Joined(String name) implements Action {
        @Override
        public Result tick() throws CommandException {
            Minecraft minecraft = minecraft();
            if (minecraft.screen instanceof DisconnectedScreen disconnected) {
                ServerCommands.freezeOnStart(null);
                throw new CommandException("join_failed", describe(disconnected));
            }
            if (minecraft.player != null && minecraft.level != null
                && (minecraft.screen == null || !Screens.type(minecraft.screen).equals("loading"))) {
                ServerCommands.freezeOnStart(null);
                return Result.ok(new Fields().put("joined", name, "joined " + name));
            }
            return null;
        }

        @Override
        public void cancel() {
            ServerCommands.freezeOnStart(null);
        }

        @Override
        public String label() {
            return "join";
        }
    }

    /** The title and message lines of a disconnect screen. */
    private static String describe(DisconnectedScreen screen) {
        List<String> parts = new ArrayList<>();
        parts.add(screen.getTitle().getString());
        screen.children().forEach(child -> {
            if (child instanceof AbstractWidget widget && !(widget instanceof Button)) {
                String text = widget.getMessage().getString();
                // The title is one of the widgets too.
                if (!text.isBlank() && !parts.contains(text)) {
                    parts.add(text);
                }
            }
        });
        return String.join(": ", parts);
    }

    /** Set while a world exit Blockpilot started is pending, so it isn't taken for the player leaving. */
    private static boolean leaving;

    /**
     * Leaves the current game the way the pause menu's button does (saving singleplayer worlds). Used by Blockpilot's
     * own world switches, which keep agent option changes.
     */
    static void disconnect() {
        Minecraft minecraft = minecraft();
        boolean local = minecraft.isLocalServer();
        if (minecraft.level != null) {
            leaving = true;
            //? if >=26.1 {
            /*minecraft.level.disconnect(ClientLevel.DEFAULT_QUIT_MESSAGE);
            *///?} else {
            minecraft.level.disconnect();
            //?}
        }
        //? if >=26.1 {
        /*if (local) {
            minecraft.disconnectWithSavingScreen();
        } else {
            minecraft.disconnectWithProgressScreen();
        }
        *///?} else {
        if (local) {
            minecraft.disconnect(new GenericMessageScreen(Component.translatable("menu.savingLevel")));
        } else {
            minecraft.disconnect();
        }
        //?}
        minecraft.setScreen(new TitleScreen());
    }

    private Action leave() throws CommandException {
        if (minecraft().level == null) {
            throw new CommandException("not_in_world");
        }
        disconnect();
        BlockpilotClient.restoreAgentChanges("the agent left the world");
        return () -> minecraft().level == null ? Result.ok() : null;
    }

    private Action quit() {
        if (minecraft().level != null) {
            disconnect();
            BlockpilotClient.restoreAgentChanges("the agent quit the game");
        }
        return () -> {
            if (minecraft().level != null) {
                return null;
            }
            // The reply goes out on the endpoint's threads; closing a few ticks later lets it leave first.
            BlockpilotClient.get().closeAfter(QUIT_DELAY_TICKS);
            return Result.ok(new Fields().put("pid", ProcessHandle.current().pid()));
        };
    }

    /** Called when the world has gone; true when Blockpilot left it (and the flag is cleared). */
    static boolean leftByBlockpilot() {
        boolean ours = leaving;
        leaving = false;
        return ours;
    }

    private Action respawn() throws CommandException {
        var player = minecraft().player;
        if (!player.isDeadOrDying()) {
            throw new CommandException("syntax", "the player is alive");
        }
        player.respawn();
        return () -> {
            var current = minecraft().player;
            if (current != null && !current.isDeadOrDying()) {
                minecraft().setScreen(null);
                return Result.ok();
            }
            return null;
        };
    }
}
