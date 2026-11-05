package dev.blockpilot.mixin;

import dev.blockpilot.game.load.Load;
import dev.blockpilot.game.perf.Perf;
import java.util.function.BooleanSupplier;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Adds the server load an agent set, inside the time the server tallies for a tick, and marks when a tick runs. */
@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin {
    @Inject(method = "tickChildren", at = @At("HEAD"))
    private void blockpilot$load(BooleanSupplier hasTimeLeft, CallbackInfo info) {
        Load.serverTick();
    }

    @Inject(method = "tickServer", at = @At("HEAD"))
    private void blockpilot$tickStarted(BooleanSupplier hasTimeLeft, CallbackInfo info) {
        Perf.serverTicking(true);
    }

    @Inject(method = "tickServer", at = @At("RETURN"))
    private void blockpilot$tickEnded(BooleanSupplier hasTimeLeft, CallbackInfo info) {
        Perf.serverTicking(false);
    }
}
