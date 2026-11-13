package dev.blockpilot.mixin;

import dev.blockpilot.game.observe.Observations;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
//? if >=1.21.11 {
/*import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
*///?} else {
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//?}

/** Logs every sound the client starts, right after it is resolved (so volume and pitch are known), even muted. */
@Mixin(SoundEngine.class)
public abstract class SoundEngineMixin {
    @Inject(method = "play", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/resources/sounds/SoundInstance;getSound()Lnet/minecraft/client/resources/sounds/Sound;"))
    //? if >=1.21.11 {
    /*private void blockpilot$played(SoundInstance sound, CallbackInfoReturnable<SoundEngine.PlayResult> info) {
    *///?} else {
    private void blockpilot$played(SoundInstance sound, CallbackInfo info) {
    //?}
        Observations.sound(sound);
    }
}
