package dev.blockpilot.mixin;

import com.mojang.blaze3d.platform.InputConstants;
//? if <26.3 {
import com.mojang.blaze3d.platform.Window;
//?}
import dev.blockpilot.game.input.InputState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Code that polls the keyboard directly (vanilla and mods) sees keys an agent holds as pressed. */
@Mixin(InputConstants.class)
public abstract class InputConstantsMixin {
    @Inject(method = "isKeyDown", at = @At("HEAD"), cancellable = true)
    //? if >=26.3 {
    /*private static void blockpilot$heldKey(int key, CallbackInfoReturnable<Boolean> info) {
    *///?} else {
    private static void blockpilot$heldKey(Window window, int key, CallbackInfoReturnable<Boolean> info) {
    //?}
        if (InputState.isRawKeyHeld(key)) {
            info.setReturnValue(true);
        }
    }
}
