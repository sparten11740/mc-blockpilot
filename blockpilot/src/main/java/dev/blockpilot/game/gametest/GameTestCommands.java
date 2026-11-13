package dev.blockpilot.game.gametest;

import dev.blockpilot.api.Action;
import dev.blockpilot.api.ArgType;
import dev.blockpilot.api.Args;
import dev.blockpilot.api.CommandContext;
import dev.blockpilot.api.CommandException;
import dev.blockpilot.api.CommandSpec;
import dev.blockpilot.api.Fields;
import dev.blockpilot.api.Result;
import dev.blockpilot.api.Row;
import dev.blockpilot.platform.ClientPlatform;
import dev.blockpilot.protocol.CommandRegistry;
//? if <1.21 {
/*import java.util.Collection;
*///?}
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** The game's own GameTests, run on the integrated server, with each test's result as a row. */
public final class GameTestCommands {
    public void register(CommandRegistry registry) {
        registry.register(CommandSpec.action("gametest", this::run).onServer().group("integrated")
            .keyword("op", "run").arg("selector", ArgType.WORD)
            .help("runs the registered GameTests whose ids match the selector, * matching any run of characters: "
                + "namespace:path on 26.x (gametest run mymod:*), class.method on 1.21.1 (gametest run mymod.*); "
                + "they build their structures beside the player, as /test run does, and server test clearall removes "
                + "them. It answers once every test has ended, with a row per test, passed or failed with its "
                + "message, or err no_test when nothing matches. NeoForge runs GameTests only in a development "
                + "environment started with -Dneoforge.enableGameTest=true, and answers err unsupported without it")
            .field("passed", "tests that passed").field("failed", "tests that failed")
            .example("gametest run mymod:*"));
    }

    private Action run(CommandContext context, Args args) throws CommandException {
        MinecraftServer server = Minecraft.getInstance().getSingleplayerServer();
        LocalPlayer local = Minecraft.getInstance().player;
        ServerPlayer player = local == null ? null : server.getPlayerList().getPlayer(local.getUUID());
        if (player == null) {
            throw new CommandException("not_in_world");
        }
        List<String> chosen = Selection.choose(ClientPlatform.gameTestsOff(), () -> GameTests.ids(server),
            args.word("selector"));
        Run run = new Run(chosen.size());
        run.runner = GameTests.start(player, chosen, run,
            (id, structure) -> run.end(id, "failed", "its structure " + structure + " is missing"));
        return run;
    }

    /** Each chosen test's end, heard on the server thread, which ticks the runner and this action alike. */
    private static final class Run implements Action, GameTestListener {
        private final int count;
        private final Map<String, Fields> ended = new TreeMap<>();
        //? if >=1.21 {
        private GameTestRunner runner;
        //?} else {
        /*private Collection<GameTestInfo> runner;
        *///?}

        Run(int count) {
            this.count = count;
        }

        void end(String id, String result, String message) {
            Fields fields = new Fields().bare("test", id).bare("result", result);
            ended.put(id, message == null ? fields : fields.bare("message", message));
        }

        @Override
        public void testStructureLoaded(GameTestInfo info) {
        }

        //? if >=1.21 {
        @Override
        public void testPassed(GameTestInfo info, GameTestRunner runner) {
            end(GameTests.id(info), "passed", null);
        }

        @Override
        public void testFailed(GameTestInfo info, GameTestRunner runner) {
            end(GameTests.id(info), "failed", GameTests.message(info));
        }

        @Override
        public void testAddedForRerun(GameTestInfo original, GameTestInfo copy, GameTestRunner runner) {
            // Runs without retries never rerun a test.
        }
        //?} else {
        /*@Override
        public void testPassed(GameTestInfo info) {
            end(GameTests.id(info), "passed", null);
        }

        @Override
        public void testFailed(GameTestInfo info) {
            end(GameTests.id(info), "failed", GameTests.message(info));
        }
        *///?}

        @Override
        public Result tick() {
            if (ended.size() < count) {
                return null;
            }
            long passed = ended.values().stream().filter(fields -> "passed".equals(fields.get("result"))).count();
            return Result.ok(new Fields().put("passed", passed).put("failed", count - passed))
                .withRows(ended.entrySet().stream().map(entry -> Row.of(entry.getKey(), entry.getValue())).toList());
        }

        @Override
        public void cancel() {
            if (runner != null) {
                //? if >=1.21 {
                runner.stop();
                //?} else {
                /*GameTests.stop();
                *///?}
            }
        }

        @Override
        public String label() {
            return "gametest";
        }
    }
}
