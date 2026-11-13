package dev.blockpilot.mixin;

import net.minecraft.server.level.ServerChunkCache;
import org.spongepowered.asm.mixin.Mixin;
//? if <1.21 {
/*import dev.blockpilot.game.ServerTicking;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
*///?}

/** A frozen server spawns no mobs on 1.20.1, as with 1.20.3's tick rate manager. Empty on later versions. */
@Mixin(ServerChunkCache.class)
public abstract class FrozenSpawnMixin {
    //? if <1.21 {
    /*@Redirect(method = "tickChunks", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/NaturalSpawner;"
        + "spawnForChunk(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/chunk/LevelChunk;"
        + "Lnet/minecraft/world/level/NaturalSpawner$SpawnState;ZZZ)V"))
    private void blockpilot$spawn(ServerLevel level, LevelChunk chunk, NaturalSpawner.SpawnState state,
        boolean friendlies, boolean enemies, boolean persistent) {
        if (ServerTicking.runsNormally()) {
            NaturalSpawner.spawnForChunk(level, chunk, state, friendlies, enemies, persistent);
        }
    }
    *///?}
}
