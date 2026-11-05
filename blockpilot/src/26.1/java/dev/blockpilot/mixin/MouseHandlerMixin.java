package dev.blockpilot.mixin;

import dev.blockpilot.game.input.HumanInput;
import dev.blockpilot.game.input.Injection;
//? if >=26.3 {
/*import net.minecraft.client.Minecraft;
*///?}
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
//? if >=26.3 {
/*import org.spongepowered.asm.mixin.Unique;
*///?}
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
    private void onButton(long window, MouseButtonInfo button, int action) {
    }

    //? if >=26.3 {
    /*@Shadow
    private double xpos;

    @Shadow
    private double ypos;

    @Unique
    private boolean blockpilot$agentMovedLast;

    @Shadow
    private void onMove(long window, double x, double y, double xRelative, double yRelative) {
    }
    *///?} else {
    @Shadow
    private void onMove(long window, double x, double y) {
    }
    //?}

    @Shadow
    private void onScroll(long window, double xOffset, double yOffset) {
    }

    @Override
    public void blockpilot$press(long window, int button, int action, int modifiers) {
        onButton(window, new MouseButtonInfo(button, modifiers), action);
    }

    @Override
    public void blockpilot$move(long window, double x, double y) {
        //? if >=26.3 {
        /*blockpilot$agentMovedLast = true;
        // A grabbed mouse turns the camera by the relative movement, which the window system reports with the place.
        onMove(window, x, y, x - xpos, y - ypos);
        *///?} else {
        onMove(window, x, y);
        //?}
    }

    @Override
    public void blockpilot$scroll(long window, double xOffset, double yOffset) {
        onScroll(window, xOffset, yOffset);
    }

    @Override
    public void blockpilot$markGrabbed() {
        mouseGrabbed = true;
    }

    //? if >=26.3 {
    /*// Every new screen rereads the OS pointer, which would forget where an agent put the cursor; 26.2 keeps it.
    @Inject(method = "resyncMousePosition", at = @At("HEAD"), cancellable = true)
    private void blockpilot$keepAgentsCursor(CallbackInfo info) {
        if (blockpilot$agentMovedLast) {
            info.cancel();
        }
    }

    *///?}
    @Inject(method = "onButton", at = @At("HEAD"))
    private void blockpilot$onPress(long window, MouseButtonInfo button, int action, CallbackInfo info) {
        if (!Injection.active()) {
            HumanInput.mouseButton(window, button.button());
        }
    }

    @Inject(method = "onScroll", at = @At("HEAD"))
    private void blockpilot$onScroll(long window, double xOffset, double yOffset, CallbackInfo info) {
        if (!Injection.active()) {
            HumanInput.scroll(window);
        }
    }

    @Inject(method = "onMove", at = @At("HEAD"))
    //? if >=26.3 {
    /*private void blockpilot$onMove(long window, double x, double y, double xRelative, double yRelative,
        CallbackInfo info) {
    *///?} else {
    private void blockpilot$onMove(long window, double x, double y, CallbackInfo info) {
    //?}
        if (!Injection.active()) {
            //? if >=26.3 {
            /*blockpilot$agentMovedLast = false;
            var gameWindow = Minecraft.getInstance().getWindow();
            // SDL's relative mode keeps a grabbed mouse inside the window, so at an edge its place stops changing.
            if (mouseGrabbed && HumanInput.atEdge(x, y, gameWindow.getScreenWidth(), gameWindow.getScreenHeight())) {
                HumanInput.mouseMoveBy(window, x, y, xRelative, yRelative);
                return;
            }
            *///?}
            HumanInput.mouseMove(window, x, y);
        }
    }
}
