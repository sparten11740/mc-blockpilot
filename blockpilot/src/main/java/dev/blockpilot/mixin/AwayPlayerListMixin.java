package dev.blockpilot.mixin;

import dev.blockpilot.game.observe.AwayCamera;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Sounds and level events within range of a player's away camera reach it (camera at … away). Sable replaces
 * broadcast with one that reads the player's position() for its sub-level aware distance, where vanilla reads getX,
 * getY and getZ; each redirect is optional, and the priority above Sable's lets them into its version.
 */
@Mixin(value = PlayerList.class, priority = 1100)
abstract class AwayPlayerListMixin {
    @Redirect(method = "broadcast", require = 0, at = @At(value = "INVOKE",
        target = "Lnet/minecraft/server/level/ServerPlayer;getX()D"))
    private double blockpilot$awayX(ServerPlayer player) {
        return AwayCamera.presence(player).x;
    }

    @Redirect(method = "broadcast", require = 0, at = @At(value = "INVOKE",
        target = "Lnet/minecraft/server/level/ServerPlayer;getY()D"))
    private double blockpilot$awayY(ServerPlayer player) {
        return AwayCamera.presence(player).y;
    }

    @Redirect(method = "broadcast", require = 0, at = @At(value = "INVOKE",
        target = "Lnet/minecraft/server/level/ServerPlayer;getZ()D"))
    private double blockpilot$awayZ(ServerPlayer player) {
        return AwayCamera.presence(player).z;
    }

    @Redirect(method = "broadcast", require = 0, at = @At(value = "INVOKE",
        target = "Lnet/minecraft/server/level/ServerPlayer;position()Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 blockpilot$awayPosition(ServerPlayer player) {
        return AwayCamera.presence(player);
    }
}
