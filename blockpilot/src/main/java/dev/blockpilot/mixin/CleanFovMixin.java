package dev.blockpilot.mixin;

import dev.blockpilot.game.observe.HudVisibility;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * With clean frames the field of view effects hold still: the game reads their scale through this getter, which then
 * gives a stand-in at 0, while saving options.txt reads the field and keeps the player's value.
 */
@Mixin(Options.class)
abstract class CleanFovMixin {
    @Inject(method = "fovEffectScale", at = @At("HEAD"), cancellable = true)
    private void blockpilot$still(CallbackInfoReturnable<OptionInstance<Double>> info) {
        if (HudVisibility.clean()) {
            info.setReturnValue(HudVisibility.STILL_FOV);
        }
    }
}
