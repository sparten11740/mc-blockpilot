package dev.blockpilot.mixin;

import org.spongepowered.asm.mixin.Mixin;
//? if >=1.21 {
import net.minecraft.client.Minecraft;
//?} else {
/*import dev.blockpilot.game.ServerTicking;
import net.minecraft.client.Timer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
*///?}

/**
 * On 1.20.1 the client ticks at the rate of Blockpilot's tick rate port, as 1.20.3's client follows its integrated
 * server's rate, so slow motion slows the client too. Empty on later versions.
 */
//? if >=1.21 {
@Mixin(Minecraft.class)
//?} else {
/*@Mixin(Timer.class)
*///?}
public abstract class TickRateClientMixin {
    //? if <1.21 {
    /*@Shadow
    @Final
    @Mutable
    private float msPerTick;

    @Inject(method = "advanceTime", at = @At("HEAD"))
    private void blockpilot$rate(long gameTime, CallbackInfoReturnable<Integer> info) {
        msPerTick = ServerTicking.clientMillisPerTick();
    }
    *///?}
}
