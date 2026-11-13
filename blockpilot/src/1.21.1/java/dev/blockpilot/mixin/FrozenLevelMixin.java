package dev.blockpilot.mixin;

import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
//? if <1.21 {
/*import dev.blockpilot.game.ServerTicking;
import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.raid.Raids;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.dimension.end.EndDragonFight;
import net.minecraft.world.ticks.LevelTicks;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
*///?}

/**
 * What a frozen level skips on 1.20.1, as 1.20.3's tick rate manager has it skip: weather, time, block and fluid
 * ticks, random ticks, spawning, raids, block events and all entities but players and what they ride. Chunks still
 * load and changes still reach clients. Block entities and spawning have mixins of their own. Empty on later versions.
 */
@Mixin(ServerLevel.class)
public abstract class FrozenLevelMixin {
    //? if <1.21 {
    /*@Redirect(method = "tick", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/level/border/WorldBorder;tick()V"))
    private void blockpilot$border(WorldBorder border) {
        if (ServerTicking.runsNormally()) {
            border.tick();
        }
    }

    @Inject(method = "advanceWeatherCycle", at = @At("HEAD"), cancellable = true)
    private void blockpilot$weather(CallbackInfo info) {
        skipWhenFrozen(info);
    }

    @Inject(method = "tickTime", at = @At("HEAD"), cancellable = true)
    private void blockpilot$time(CallbackInfo info) {
        skipWhenFrozen(info);
    }

    @Redirect(method = "tick", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/ticks/LevelTicks;tick(JILjava/util/function/BiConsumer;)V"))
    private <T> void blockpilot$scheduledTicks(LevelTicks<T> ticks, long time, int max, BiConsumer<BlockPos, T> run) {
        if (ServerTicking.runsNormally()) {
            ticks.tick(time, max, run);
        }
    }

    @Redirect(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/raid/Raids;tick()V"))
    private void blockpilot$raids(Raids raids) {
        if (ServerTicking.runsNormally()) {
            raids.tick();
        }
    }

    @Inject(method = "runBlockEvents", at = @At("HEAD"), cancellable = true)
    private void blockpilot$blockEvents(CallbackInfo info) {
        skipWhenFrozen(info);
    }

    @Inject(method = "tickChunk", at = @At("HEAD"), cancellable = true)
    private void blockpilot$randomTicks(LevelChunk chunk, int randomTickSpeed, CallbackInfo info) {
        skipWhenFrozen(info);
    }

    @Inject(method = "tickCustomSpawners", at = @At("HEAD"), cancellable = true)
    private void blockpilot$customSpawners(boolean enemies, boolean friendlies, CallbackInfo info) {
        skipWhenFrozen(info);
    }

    @Redirect(method = "tick", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/level/dimension/end/EndDragonFight;tick()V"))
    private void blockpilot$dragonFight(EndDragonFight fight) {
        if (ServerTicking.runsNormally()) {
            fight.tick();
        }
    }

    @Inject(method = "tickNonPassenger", at = @At("HEAD"), cancellable = true)
    private void blockpilot$entity(Entity entity, CallbackInfo info) {
        if (ServerTicking.frozen(entity)) {
            info.cancel();
        }
    }

    private static void skipWhenFrozen(CallbackInfo info) {
        if (!ServerTicking.runsNormally()) {
            info.cancel();
        }
    }
    *///?}
}
