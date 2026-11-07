package dev.blockpilot.game.gametest;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRegistry;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.gametest.framework.RetryOptions;
import net.minecraft.gametest.framework.StructureGridSpawner;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.Heightmap;

/** GameTests as 1.21.1 holds them: test functions in a static registry, named {@code <class>.<method>}. */
final class GameTests {
    /** Blockpilot's own tests, in the source set only the 1.21.1 dev runs include; the published jar has none. */
    private static final String DEV_TESTS = "dev.blockpilot.gametest.BlockpilotSmokeTests";
    private static boolean lookedForDevTests;

    private GameTests() {
    }

    /** The server goes unused here, but 26.x's adapter needs it, and the two share their methods. */
    static Collection<String> ids(MinecraftServer server) {
        addDevTests();
        return GameTestRegistry.getAllTestFunctions().stream().map(TestFunction::testName).toList();
    }

    /** Starts the tests beside the player, telling the listener as each ends; null when none could start. */
    static GameTestRunner start(ServerPlayer player, List<String> ids, GameTestListener listener,
            BiConsumer<String, String> missingStructure) {
        ServerLevel level = player.serverLevel();
        List<GameTestInfo> infos = new ArrayList<>();
        for (String id : ids) {
            TestFunction test = GameTestRegistry.getTestFunction(id);
            ResourceLocation structure = ResourceLocation.parse(test.structureName());
            // The runner would throw on the server thread for it; /test leaves such a test out too.
            if (level.getStructureManager().get(structure).isEmpty()) {
                missingStructure.accept(id, structure.toString());
                continue;
            }
            GameTestInfo info = new GameTestInfo(test, Rotation.NONE, level, RetryOptions.noRetries());
            info.addListener(listener);
            infos.add(info);
        }
        if (infos.isEmpty()) {
            return null;
        }
        // Where /test run puts them: on the surface, three blocks south of the player.
        BlockPos at = player.blockPosition();
        BlockPos corner = new BlockPos(at.getX(), level.getHeightmapPos(Heightmap.Types.WORLD_SURFACE, at).getY() + 1,
            at.getZ() + 3);
        GameTestRunner runner = GameTestRunner.Builder.fromInfo(infos, level)
            .newStructureSpawner(new StructureGridSpawner(corner, GameTestRunner.DEFAULT_TESTS_PER_ROW, false))
            .build();
        runner.start();
        return runner;
    }

    static String id(GameTestInfo info) {
        return info.getTestName();
    }

    static String message(GameTestInfo info) {
        Throwable error = info.getError();
        if (error == null) {
            return "";
        }
        return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
    }

    /** Adds Blockpilot's own tests once, where the dev runs include them. */
    private static void addDevTests() {
        if (lookedForDevTests) {
            return;
        }
        lookedForDevTests = true;
        try {
            Class.forName(DEV_TESTS).getMethod("addTo", Collection.class)
                .invoke(null, GameTestRegistry.getAllTestFunctions());
        } catch (ClassNotFoundException e) {
            // Not a dev run.
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Blockpilot's dev GameTests didn't load", e);
        }
    }
}
