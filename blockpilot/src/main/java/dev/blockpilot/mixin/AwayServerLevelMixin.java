package dev.blockpilot.mixin;

import dev.blockpilot.game.observe.AwayCamera;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
//? if >=1.21.11 {
/*import net.minecraft.world.phys.Vec3;
*///?}

/** Particles and explosions within range of a player's away camera reach it (camera at … away). */
@Mixin(ServerLevel.class)
abstract class AwayServerLevelMixin {
    // The private overload every other sends through; a bare name would select only the first.
    @Redirect(method = "sendParticles(Lnet/minecraft/server/level/ServerPlayer;ZDDD"
        + "Lnet/minecraft/network/protocol/Packet;)Z", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/server/level/ServerPlayer;blockPosition()Lnet/minecraft/core/BlockPos;"))
    private BlockPos blockpilot$awayParticles(ServerPlayer player) {
        return AwayCamera.presenceBlock(player);
    }

    //? if <1.21.11 {
    @Redirect(method = "explode", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/server/level/ServerPlayer;distanceToSqr(DDD)D"))
    private double blockpilot$awayExplosion(ServerPlayer player, double x, double y, double z) {
        return AwayCamera.presence(player).distanceToSqr(x, y, z);
    }
    //?} else {
    /*@Redirect(method = "explode", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/server/level/ServerPlayer;distanceToSqr(Lnet/minecraft/world/phys/Vec3;)D"))
    private double blockpilot$awayExplosion(ServerPlayer player, Vec3 center) {
        return AwayCamera.presence(player).distanceToSqr(center);
    }
    *///?}
}
