package dev.blockpilot.mixin;

import dev.blockpilot.game.input.HumanInput;
import dev.blockpilot.game.input.Injection;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Injected key and character input through vanilla's handlers, and real key presses as a human take-over. */
@Mixin(KeyboardHandler.class)
public abstract class KeyboardHandlerMixin implements Injection.Keyboard {
    @Shadow
    private void keyPress(long window, int action, KeyEvent event) {
    }

    @Shadow
    private void charTyped(long window, CharacterEvent event) {
    }

    @Override
    public void blockpilot$keyPress(long window, int key, int otherKeyCode, int action, int modifiers) {
        keyPress(window, action, new KeyEvent(key, otherKeyCode, modifiers));
    }

    @Override
    public void blockpilot$charTyped(long window, int codePoint, int modifiers) {
        //? if >=26.1 {
        /*charTyped(window, new CharacterEvent(codePoint));
        *///?} else {
        charTyped(window, new CharacterEvent(codePoint, modifiers));
        //?}
    }

    @Inject(method = "keyPress", at = @At("HEAD"))
    private void blockpilot$onKeyPress(long window, int action, KeyEvent event, CallbackInfo info) {
        if (!Injection.active()) {
            HumanInput.key(window, event.key(), action);
        }
    }
}
