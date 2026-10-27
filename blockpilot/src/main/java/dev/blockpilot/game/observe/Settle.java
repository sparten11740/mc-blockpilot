package dev.blockpilot.game.observe;

import dev.blockpilot.api.Action;
import dev.blockpilot.api.Duration;
import dev.blockpilot.api.Fields;
import dev.blockpilot.api.Numbers;
import dev.blockpilot.api.Result;
import dev.blockpilot.protocol.Engine;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ChunkTrackingView;

/**
 * Waits until every chunk the server sends around the player has arrived and the section compile queue has stayed
 * empty, with the same number of sections rendered, for 3 ticks (from the second tick on, once the renderer has
 * scheduled what came into view or a change made dirty).
 */
final class Settle implements Action, Engine.SelfTimed {
    private static final Duration TIMEOUT = Duration.seconds(20);
    private static final int QUIET_TICKS = 3;
    private final String label;
    private final Supplier<Fields> answer;
    private final int timeoutTicks;
    private int ticks;
    private int quiet;
    private int lastRendered = -1;

    /**
     * Answers with the fields plus {@code settled=}, or {@code unsettled pending=} (and {@code missing=}) at the
     * timeout (null: 20s).
     */
    Settle(String label, Supplier<Fields> answer, Duration timeout) {
        this.label = label;
        this.answer = answer;
        this.timeoutTicks = (timeout == null ? TIMEOUT : timeout).ticks();
    }

    @Override
    public Result tick() {
        ticks++;
        var renderer = Minecraft.getInstance().levelRenderer;
        SectionRenderDispatcher dispatcher = renderer.getSectionRenderDispatcher();
        if (Minecraft.getInstance().level == null || dispatcher == null) {
            return Result.ok(answer.get());
        }
        int rendered = renderer.countRenderedSections();
        int missing = missingChunks(Minecraft.getInstance());
        boolean idle = ticks >= 2 && missing == 0 && dispatcher.isQueueEmpty() && rendered == lastRendered;
        lastRendered = rendered;
        quiet = idle ? quiet + 1 : 0;
        double seconds = new Duration(ticks).seconds();
        if (quiet >= QUIET_TICKS) {
            return Result.ok(answer.get().put("settled", seconds, "settled=" + Numbers.format(seconds) + "s"));
        }
        if (ticks >= timeoutTicks) {
            //? if >=26.1 {
            /*int pending = dispatcher.getCompileQueueSize();
            *///?} else {
            int pending = dispatcher.getToBatchCount() + dispatcher.getToUpload();
            //?}
            Fields fields = answer.get().put("unsettled", true, "unsettled")
                .put("pending", pending, "pending=" + pending);
            return Result.ok(missing > 0 ? fields.put("missing", missing, "missing=" + missing) : fields);
        }
        return null;
    }

    /**
     * The chunks within the view distance of the player's chunk that the client hasn't received. The server sends
     * these and a ring beyond them, as it generates them, which in new terrain takes many seconds.
     */
    private static int missingChunks(Minecraft minecraft) {
        if (minecraft.player == null) {
            return 0;
        }
        int centerX = SectionPos.blockToSectionCoord(minecraft.player.getBlockX());
        int centerZ = SectionPos.blockToSectionCoord(minecraft.player.getBlockZ());
        int distance = minecraft.options.getEffectiveRenderDistance();
        int missing = 0;
        for (int x = centerX - distance; x <= centerX + distance; x++) {
            for (int z = centerZ - distance; z <= centerZ + distance; z++) {
                if (ChunkTrackingView.isInViewDistance(centerX, centerZ, distance, x, z)
                    && minecraft.level.getChunkSource().getChunkNow(x, z) == null) {
                    missing++;
                }
            }
        }
        return missing;
    }

    @Override
    public String label() {
        return label;
    }
}
