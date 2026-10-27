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

/**
 * Waits until the section compile queue has stayed empty, with the same number of sections rendered, for 3 ticks
 * (from the second tick on, once the renderer has scheduled what came into view or a change made dirty).
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

    /** Answers with the fields plus {@code settled=}, or {@code unsettled pending=} at the timeout (null: 20s). */
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
        boolean idle = ticks >= 2 && dispatcher.isQueueEmpty() && rendered == lastRendered;
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
            return Result.ok(answer.get().put("unsettled", true, "unsettled")
                .put("pending", pending, "pending=" + pending));
        }
        return null;
    }

    @Override
    public String label() {
        return label;
    }
}
