package dev.blockpilot.game.perf;

import dev.blockpilot.game.Ids;
import dev.blockpilot.game.sable.SableSupport;
import dev.blockpilot.game.sable.SubLevels;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * What a block change the server sends to clients counts under, on the server thread: its block, the block entity
 * type when it has one, and with Sable the sub-level whose plot holds it. A plot's sections belong to one sub-level,
 * so the sub-level is looked up once per section and capture. The cache is keyed by dimension, so it holds no level
 * after the player leaves a world.
 */
public final class BlockChanges {
    private static final Map<ResourceKey<Level>, Map<Long, String>> SUB_LEVELS = new HashMap<>();
    private static int capture = -1;

    private BlockChanges() {
    }

    public static void changed(Level level, BlockPos pos) {
        if (capture != Perf.capture()) {
            SUB_LEVELS.clear();
            capture = Perf.capture();
        }
        BlockState state = level.getBlockState(pos);
        BlockEntity blockEntity = state.hasBlockEntity() ? level.getBlockEntity(pos) : null;
        String sub = SUB_LEVELS.computeIfAbsent(level.dimension(), key -> new HashMap<>())
            .computeIfAbsent(SectionPos.asLong(pos), section -> subLevel(level, pos));
        Perf.blockChanged(Ids.of(state), blockEntity == null ? null : Ids.of(blockEntity.getType()),
            sub.isEmpty() ? null : sub);
    }

    private static String subLevel(Level level, BlockPos pos) {
        try {
            SubLevels.Info info = SableSupport.subLevels().at(level, pos);
            return info == null ? "" : info.name();
        } catch (RuntimeException e) {
            // A failing Sable call must not break the server's tick; the change then counts as in the world.
            return "";
        }
    }
}
