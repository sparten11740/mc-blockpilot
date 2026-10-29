package dev.blockpilot.mixin;

import dev.blockpilot.game.input.HumanInput;
import dev.blockpilot.game.input.Injection;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Feeds injected mouse input through vanilla's own handlers (so mods hooking them see it) and reports real mouse
 * input from the player, which takes control back from the agent.
 */
@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin implements Injection.Mouse {
    @Shadow
    private boolean mouseGrabbed;

    @Shadow
    private void onPress(long window, int button, int action, int modifiers) {
    }

    @Shadow
    private void onMove(long window, double x, double y) {
    }

    @Shadow
    private void onScroll(long window, double xOffset, double yOffset) {
    }

    @Override
    public void blockpilot$press(long window, int button, int action, int modifiers) {
        onPress(window, button, action, modifiers);
    }

    @Override
    public void blockpilot$move(long window, double x, double y) {
        onMove(window, x, y);
    }

    @Override
    public void blockpilot$scroll(long window, double xOffset, double yOffset) {
        onScroll(window, xOffset, yOffset);
    }

    @Override
    public void blockpilot$markGrabbed() {
        mouseGrabbed = true;
    }

    @Inject(method = "onPress", at = @At("HEAD"))
    private void blockpilot$onPress(long window, int button, int action, int modifiers, CallbackInfo info) {
        if (!Injection.active()) {
            HumanInput.mouseButton(window, button);
        }
    }

    @Inject(method = "onScroll", at = @At("HEAD"))
    private void blockpilot$onScroll(long window, double xOffset, double yOffset, CallbackInfo info) {
        if (!Injection.active()) {
            HumanInput.scroll(window);
        }
    }

    @Inject(method = "onMove", at = @At("HEAD"))
    private void blockpilot$onMove(long window, double x, double y, CallbackInfo info) {
        if (!Injection.active()) {
            HumanInput.mouseMove(window, x, y);
        }
    }
}
