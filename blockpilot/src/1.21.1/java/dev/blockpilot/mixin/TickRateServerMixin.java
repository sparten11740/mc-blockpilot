package dev.blockpilot.mixin;

import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
//? if <1.21 {
/*import dev.blockpilot.game.ServerTicking;
import java.util.function.BooleanSupplier;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
*///?}

/**
 * Blockpilot's port of the tick rate manager on 1.20.1, which has none (see ServerTicking): whether each server tick
 * runs the game, and the time the server loop gives a tick. Empty on later versions.
 */
@Mixin(MinecraftServer.class)
public abstract class TickRateServerMixin {
    //? if <1.21 {
    /*@Inject(method = "tickServer", at = @At("HEAD"))
    private void blockpilot$startTick(BooleanSupplier hasTimeLeft, CallbackInfo info) {
        ServerTicking.startTick();
    }

    // The loop's 50 ms a tick: added to the next tick's time (the third 50 in runServer), and the most the tasks
    // between ticks may run past it (the fourth).
    @ModifyConstant(method = "runServer", constant = @Constant(longValue = 50L, ordinal = 2))
    private long blockpilot$tickMillis(long millis) {
        return ServerTicking.millisThisTick();
    }

    @ModifyConstant(method = "runServer", constant = @Constant(longValue = 50L, ordinal = 3))
    private long blockpilot$taskMillis(long millis) {
        return ServerTicking.millisPerTick();
    }
    *///?}
}
