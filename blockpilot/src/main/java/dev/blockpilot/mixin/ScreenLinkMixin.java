package dev.blockpilot.mixin;

import dev.blockpilot.game.safety.PlayGuard;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.ClickEvent;
//? if >=1.21.11 {
/*import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
*///?} else {
import java.util.Set;
import net.minecraft.network.chat.Style;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
//?}
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;

/**
 * Where vanilla acts on a clicked text's click event, in chat and books alike: a play-only agent's click on one that
 * opens a link or a file, or copies to the clipboard, is refused before any confirm screen, and also with the
 * chat-links prompt switched off.
 */
@Mixin(Screen.class)
public abstract class ScreenLinkMixin {
    //? if >=1.21.11 {
    /*@Inject(method = "defaultHandleClickEvent", at = @At("HEAD"), cancellable = true)
    private static void blockpilot$clicked(ClickEvent event, Minecraft minecraft, Screen screen, CallbackInfo info) {
        if ((event instanceof ClickEvent.OpenUrl || event instanceof ClickEvent.OpenFile
            || event instanceof ClickEvent.CopyToClipboard) && PlayGuard.refuseLink()) {
            info.cancel();
        }
    }
    *///?} else {
    private static final Set<ClickEvent.Action> LEAVING = Set.of(ClickEvent.Action.OPEN_URL,
        ClickEvent.Action.OPEN_FILE, ClickEvent.Action.COPY_TO_CLIPBOARD);

    @Inject(method = "handleComponentClicked", at = @At("HEAD"), cancellable = true)
    private void blockpilot$clicked(Style style, CallbackInfoReturnable<Boolean> info) {
        ClickEvent event = style == null ? null : style.getClickEvent();
        if (event != null && LEAVING.contains(event.getAction()) && PlayGuard.refuseLink()) {
            info.setReturnValue(false);
        }
    }
    //?}
}
