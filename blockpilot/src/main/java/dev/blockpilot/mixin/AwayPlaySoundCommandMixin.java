package dev.blockpilot.mixin;

import dev.blockpilot.game.observe.AwayCamera;
import net.minecraft.server.commands.PlaySoundCommand;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** playsound measures from a player's away camera, and its minimum volume plays next to it (camera at … away). */
@Mixin(PlaySoundCommand.class)
abstract class AwayPlaySoundCommandMixin {
    @Redirect(method = "playSound", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/server/level/ServerPlayer;getX()D"))
    private static double blockpilot$awayX(ServerPlayer player) {
        return AwayCamera.presence(player).x;
    }

    @Redirect(method = "playSound", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/server/level/ServerPlayer;getY()D"))
    private static double blockpilot$awayY(ServerPlayer player) {
        return AwayCamera.presence(player).y;
    }

    @Redirect(method = "playSound", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/server/level/ServerPlayer;getZ()D"))
    private static double blockpilot$awayZ(ServerPlayer player) {
        return AwayCamera.presence(player).z;
    }
}
