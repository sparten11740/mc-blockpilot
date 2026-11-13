package dev.blockpilot.mixin;

import dev.blockpilot.game.observe.AwayCamera;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
//? if <1.21 {
/*import net.minecraft.util.Mth;
*///?}
//? if <1.21.11 {
import net.minecraft.world.entity.Entity;
//?} else {
/*import net.minecraft.world.phys.Vec3;
*///?}

/** Chunk loading, ticking, sending and spawning centre on a player's away camera (camera at … away). */
@Mixin(ChunkMap.class)
abstract class AwayChunkMapMixin {
    @Redirect(method = {"updatePlayerStatus", "updatePlayerPos", "move"}, at = @At(value = "INVOKE",
        target = "Lnet/minecraft/core/SectionPos;of(Lnet/minecraft/world/level/entity/EntityAccess;)"
            + "Lnet/minecraft/core/SectionPos;"))
    private SectionPos blockpilot$awaySection(EntityAccess entity) {
        return entity instanceof ServerPlayer player && AwayCamera.awayFrom(player)
            ? AwayCamera.presenceSection(player) : SectionPos.of(entity);
    }

    //? if >=1.21 {
    @Redirect(method = "updateChunkTracking", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/server/level/ServerPlayer;chunkPosition()Lnet/minecraft/world/level/ChunkPos;"))
    private ChunkPos blockpilot$awayChunk(ServerPlayer player) {
        return AwayCamera.awayFrom(player) ? AwayCamera.presenceChunk(player) : player.chunkPosition();
    }
    //?} else {
    /*// 1.20.1 picks the chunks a player sees around the block the player stands on.
    @Redirect(method = {"updatePlayerStatus", "move"}, at = @At(value = "INVOKE",
        target = "Lnet/minecraft/server/level/ServerPlayer;getBlockX()I"))
    private int blockpilot$awayX(ServerPlayer player) {
        return AwayCamera.awayFrom(player) ? Mth.floor(AwayCamera.presence(player).x) : player.getBlockX();
    }

    @Redirect(method = {"updatePlayerStatus", "move"}, at = @At(value = "INVOKE",
        target = "Lnet/minecraft/server/level/ServerPlayer;getBlockZ()I"))
    private int blockpilot$awayZ(ServerPlayer player) {
        return AwayCamera.awayFrom(player) ? Mth.floor(AwayCamera.presence(player).z) : player.getBlockZ();
    }
    *///?}

    //? if <1.21.11 {
    @Redirect(method = "playerIsCloseEnoughForSpawning", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/server/level/ChunkMap;euclideanDistanceSquared(Lnet/minecraft/world/level/ChunkPos;"
            + "Lnet/minecraft/world/entity/Entity;)D"))
    private double blockpilot$awaySpawnDistance(ChunkPos chunk, Entity entity) {
        var at = entity instanceof ServerPlayer player ? AwayCamera.presence(player) : entity.position();
        double x = SectionPos.sectionToBlockCoord(chunk.x, 8) - at.x;
        double z = SectionPos.sectionToBlockCoord(chunk.z, 8) - at.z;
        return x * x + z * z;
    }
    //?} else {
    /*@Redirect(method = "playerIsCloseEnoughForSpawning", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/server/level/ServerPlayer;position()Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 blockpilot$awaySpawnDistance(ServerPlayer player) {
        return AwayCamera.presence(player);
    }
    *///?}
}
