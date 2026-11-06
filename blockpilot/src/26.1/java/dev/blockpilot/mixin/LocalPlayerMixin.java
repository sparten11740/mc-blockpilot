package dev.blockpilot.mixin;

import dev.blockpilot.game.observe.AwayCamera;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * While a camera is away (camera at … away), the player stands where it was once its own chunk has left the client.
 * 1.21.1 skips the tick then by itself; 26.x ticks the player as soon as the client has loaded, and it would fall.
 */
@Mixin(LocalPlayer.class)
abstract class LocalPlayerMixin {
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void blockpilot$standWhileAway(CallbackInfo info) {
        LocalPlayer player = (LocalPlayer) (Object) this;
        if (AwayCamera.active() && !player.level().hasChunkAt(player.getBlockX(), player.getBlockZ())) {
            info.cancel();
        }
    }
}
