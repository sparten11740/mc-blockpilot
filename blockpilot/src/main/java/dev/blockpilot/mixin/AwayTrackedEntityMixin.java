package dev.blockpilot.mixin;

import dev.blockpilot.game.observe.AwayCamera;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** An entity is sent to a player within its range of the player's away camera (camera at … away). */
@Mixin(targets = "net.minecraft.server.level.ChunkMap$TrackedEntity")
abstract class AwayTrackedEntityMixin {
    @Redirect(method = "updatePlayer", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/server/level/ServerPlayer;position()Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 blockpilot$awayPosition(ServerPlayer player) {
        return AwayCamera.presence(player);
    }
}
