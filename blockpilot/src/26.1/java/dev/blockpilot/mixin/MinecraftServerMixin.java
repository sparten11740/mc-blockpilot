package dev.blockpilot.mixin;

import dev.blockpilot.game.load.Load;
import java.util.function.BooleanSupplier;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Adds the server load an agent set, inside the time the server tallies for a tick. */
@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin {
    @Inject(method = "tickChildren", at = @At("HEAD"))
    private void blockpilot$load(BooleanSupplier hasTimeLeft, CallbackInfo info) {
        Load.serverTick();
    }
}
