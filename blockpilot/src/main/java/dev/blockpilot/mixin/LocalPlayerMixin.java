package dev.blockpilot.mixin;

import dev.blockpilot.game.observe.AwayCamera;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * While a camera is away (camera at … away), the player stands where it was. 1.21.1 ticks the player only while its
 * chunk is loaded and 26.x as soon as the client has loaded, and on both it sank a little while the camera went away,
 * so the tick is skipped for as long as a camera is away.
 */
@Mixin(LocalPlayer.class)
abstract class LocalPlayerMixin {
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void blockpilot$standWhileAway(CallbackInfo info) {
        if (AwayCamera.active()) {
            info.cancel();
        }
    }
}
