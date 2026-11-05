package dev.blockpilot.mixin;

import com.mojang.blaze3d.platform.InputConstants;
//? if <26.3 {
import com.mojang.blaze3d.platform.Window;
//?}
//? if >=26.3 {
/*import dev.blockpilot.game.input.Focus;
*///?}
import dev.blockpilot.game.input.InputState;
//? if >=26.3 {
/*import org.lwjgl.sdl.SDLMouse;
*///?}
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
//? if >=26.3 {
/*import org.spongepowered.asm.mixin.injection.Redirect;
*///?}
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Code that polls the keyboard directly (vanilla and mods) sees keys an agent holds as pressed. From 26.3 it also keeps
 * the player's pointer where it is while an agent drives a window in the background.
 */
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

    //? if >=26.3 {
    /*// SDL moves the real pointer whatever has focus; GLFW, up to 26.2, ignored this for a window without it.
    @Redirect(method = {"grabMouse", "releaseMouse"}, at = @At(value = "INVOKE",
        target = "Lorg/lwjgl/sdl/SDLMouse;SDL_WarpMouseInWindow(JFF)V"))
    private static void blockpilot$warpOnlyWithFocus(long window, float x, float y) {
        if (Focus.reallyFocused()) {
            SDLMouse.SDL_WarpMouseInWindow(window, x, y);
        }
    }
    *///?}
}
