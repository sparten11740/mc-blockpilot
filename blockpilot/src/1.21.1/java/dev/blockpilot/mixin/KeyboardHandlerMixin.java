package dev.blockpilot.mixin;

import dev.blockpilot.game.input.HumanInput;
import dev.blockpilot.game.input.Injection;
import net.minecraft.client.KeyboardHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Injected key and character input through vanilla's handlers, and real key presses as a human take-over. */
@Mixin(KeyboardHandler.class)
public abstract class KeyboardHandlerMixin implements Injection.Keyboard {
    @Shadow
    private void charTyped(long window, int codePoint, int modifiers) {
    }

    @Override
    public void blockpilot$charTyped(long window, int codePoint, int modifiers) {
        charTyped(window, codePoint, modifiers);
    }

    @Inject(method = "keyPress", at = @At("HEAD"))
    private void blockpilot$onKeyPress(long window, int key, int scanCode, int action, int modifiers,
        CallbackInfo info) {
        if (!Injection.active()) {
            HumanInput.key(window, key, action);
        }
    }
}
