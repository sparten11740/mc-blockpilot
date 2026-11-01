package dev.blockpilot.platform.vanilla.mixin;

import dev.blockpilot.platform.ClientPlatform;
import java.util.function.BooleanSupplier;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The integrated server's start, ticks and stop. */
@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin {
    // Once the server is set up, before its first tick.
    @Inject(method = "runServer", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/server/MinecraftServer;buildServerStatus()"
            + "Lnet/minecraft/network/protocol/status/ServerStatus;",
        ordinal = 0))
    private void blockpilot$started(CallbackInfo info) {
        ClientPlatform.hooks().onServerStarted((MinecraftServer) (Object) this);
    }

    @Inject(method = "tickServer", at = @At("TAIL"))
    private void blockpilot$tickEnd(BooleanSupplier haveTime, CallbackInfo info) {
        ClientPlatform.hooks().onServerTickEnd((MinecraftServer) (Object) this);
    }

    @Inject(method = "stopServer", at = @At("HEAD"))
    private void blockpilot$stopping(CallbackInfo info) {
        ClientPlatform.hooks().onServerStopping((MinecraftServer) (Object) this);
    }
}
