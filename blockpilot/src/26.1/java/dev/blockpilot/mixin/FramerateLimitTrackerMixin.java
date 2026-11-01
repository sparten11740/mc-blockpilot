package dev.blockpilot.mixin;

import com.mojang.blaze3d.platform.FramerateLimitTracker;
import dev.blockpilot.game.record.Recorder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** No idle frame-rate cap (30, then 10 frames a second without input) while a recording runs; a minimized one stays. */
@Mixin(FramerateLimitTracker.class)
public abstract class FramerateLimitTrackerMixin {
    @Inject(method = "getThrottleReason", at = @At("RETURN"), cancellable = true)
    private void blockpilot$recording(CallbackInfoReturnable<FramerateLimitTracker.FramerateThrottleReason> info) {
        FramerateLimitTracker.FramerateThrottleReason reason = info.getReturnValue();
        if (Recorder.active() && (reason == FramerateLimitTracker.FramerateThrottleReason.SHORT_AFK
            || reason == FramerateLimitTracker.FramerateThrottleReason.LONG_AFK)) {
            info.setReturnValue(FramerateLimitTracker.FramerateThrottleReason.NONE);
        }
    }
}
