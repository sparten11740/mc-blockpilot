package dev.blockpilot.mixin;

import dev.blockpilot.game.safety.SlashCommands;
import net.minecraft.client.multiplayer.ClientPacketListener;
//? if >=26.1 {
/*import net.minecraft.client.gui.screens.Screen;
*///?}
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//? if <26.1 {
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
//?}

/**
 * Every way a slash command leaves the client: typed into the chat screen, and clicked in a chat message, which goes
 * out without passing sendCommand.
 */
@Mixin(ClientPacketListener.class)
public abstract class SlashCommandMixin {
    @Inject(method = "sendCommand", at = @At("HEAD"), cancellable = true)
    private void blockpilot$typed(String command, CallbackInfo info) {
        if (SlashCommands.refuse(command)) {
            info.cancel();
        }
    }

    //? if >=26.1 {
    /*@Inject(method = "sendUnattendedCommand", at = @At("HEAD"), cancellable = true)
    private void blockpilot$clicked(String command, Screen screen, CallbackInfo info) {
        if (SlashCommands.refuse(command)) {
            info.cancel();
        }
    }
    *///?} else {
    @Inject(method = "sendUnsignedCommand", at = @At("HEAD"), cancellable = true)
    private void blockpilot$clicked(String command, CallbackInfoReturnable<Boolean> info) {
        if (SlashCommands.refuse(command)) {
            info.setReturnValue(true);
        }
    }
    //?}
}
