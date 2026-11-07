package dev.blockpilot.game.gametest;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestException;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestInstance;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.gametest.framework.RetryOptions;
import net.minecraft.gametest.framework.StructureGridSpawner;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * GameTests as 26.x holds them: test instances in a registry loaded from data packs with the world, named
 * {@code namespace:path}.
 */
final class GameTests {
    private GameTests() {
    }

    static Collection<String> ids(MinecraftServer server) {
        return server.registryAccess().lookupOrThrow(Registries.TEST_INSTANCE).listElementIds()
            .map(key -> key.identifier().toString()).toList();
    }

    /** Starts the tests beside the player, telling the listener as each ends; null when none could start. */
    static GameTestRunner start(ServerPlayer player, List<String> ids, GameTestListener listener,
            BiConsumer<String, String> missingStructure) {
        MinecraftServer server = player.level().getServer();
        Registry<GameTestInstance> registry = server.registryAccess().lookupOrThrow(Registries.TEST_INSTANCE);
        List<GameTestInfo> infos = new ArrayList<>();
        for (String id : ids) {
            Holder.Reference<GameTestInstance> test = registry.get(Identifier.parse(id)).orElseThrow();
            //? if >=26.3 {
            /*ServerLevel level = server.getLevel(test.value().dimension());
            boolean hasStructure = level != null && level.getStructureTemplateManager().get(test.value().structure())
                .isPresent();
            *///?} else {
            ServerLevel level = player.level();
            boolean hasStructure = level.getStructureManager().get(test.value().structure()).isPresent();
            //?}
            // The runner would throw on the server thread for it; /test leaves such a test out too.
            if (!hasStructure) {
                missingStructure.accept(id, test.value().structure().toString());
                continue;
            }
            GameTestInfo info = new GameTestInfo(test, Rotation.NONE, level, RetryOptions.noRetries());
            info.addListener(listener);
            infos.add(info);
        }
        if (infos.isEmpty()) {
            return null;
        }
        //? if >=26.3 {
        /*GameTestRunner runner = GameTestRunner.Builder.fromInfo(infos, server)
            .newStructureSpawner(new StructureGridSpawner(dimension -> corner(player, server.getLevel(dimension)),
                GameTestRunner.DEFAULT_TESTS_PER_ROW, false))
            .build();
        *///?} else {
        GameTestRunner runner = GameTestRunner.Builder.fromInfo(infos, player.level())
            .newStructureSpawner(new StructureGridSpawner(corner(player, player.level()),
                GameTestRunner.DEFAULT_TESTS_PER_ROW, false))
            .build();
        //?}
        runner.start();
        return runner;
    }

    /** Where /test run puts the tests: on the surface, three blocks south of the player. */
    private static BlockPos corner(ServerPlayer player, ServerLevel level) {
        BlockPos at = player.blockPosition();
        int surface = level.getHeightmapPos(Heightmap.Types.WORLD_SURFACE, at).getY();
        return new BlockPos(at.getX(), surface, at.getZ() + 3);
    }

    static String id(GameTestInfo info) {
        return info.id().toString();
    }

    static String message(GameTestInfo info) {
        GameTestException error = info.getError();
        return error == null ? "" : error.getDescription().getString();
    }
}
