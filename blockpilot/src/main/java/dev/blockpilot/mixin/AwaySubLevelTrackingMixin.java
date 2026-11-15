package dev.blockpilot.mixin;

import dev.blockpilot.game.observe.AwayCamera;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Sable sends a player the sub-levels within its tracking range of the player; with a camera away (camera at … away),
 * of the camera instead, as for chunks and entities. Shifting the sub-level's position by the player's offset from the
 * camera leaves Sable's own test, and its configured range, to decide. Skipped without Sable.
 */
@Pseudo
@Mixin(targets = "dev.ryanhcode.sable.sublevel.system.SubLevelTrackingSystem", remap = false)
abstract class AwaySubLevelTrackingMixin {
    @ModifyVariable(method = "shouldLoad", at = @At("HEAD"), argsOnly = true, require = 0)
    private Vector3dc blockpilot$fromAwayCamera(Vector3dc position, Player player, Vector3dc unchanged) {
        if (!(player instanceof ServerPlayer server) || !AwayCamera.awayFrom(server)) {
            return position;
        }
        Vec3 camera = AwayCamera.presence(server);
        return new Vector3d(position).add(player.getX() - camera.x, player.getY() - camera.y, player.getZ() - camera.z);
    }
}
