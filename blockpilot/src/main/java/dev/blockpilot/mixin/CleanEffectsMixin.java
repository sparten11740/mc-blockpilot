package dev.blockpilot.mixin;

import dev.blockpilot.game.observe.HudVisibility;
import net.minecraft.client.Minecraft;
//? if >=1.21 {
import net.minecraft.core.Holder;
//?}
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * With clean frames, the local player has no night vision, blindness, darkness or nausea while a frame is drawn, so
 * they don't tint, fog or warp a capture. The client tick still sees them, so a blind player can't sprint, and so do
 * the server's copy of the player and every other entity.
 */
@Mixin(LivingEntity.class)
abstract class CleanEffectsMixin {
    //? if >=1.21 {
    @Inject(method = "hasEffect", at = @At("HEAD"), cancellable = true)
    private void blockpilot$cleanHas(Holder<MobEffect> effect, CallbackInfoReturnable<Boolean> info) {
        if (hidden(effect)) {
            info.setReturnValue(false);
        }
    }

    @Inject(method = "getEffect", at = @At("HEAD"), cancellable = true)
    private void blockpilot$cleanGet(Holder<MobEffect> effect, CallbackInfoReturnable<MobEffectInstance> info) {
        if (hidden(effect)) {
            info.setReturnValue(null);
        }
    }

    private boolean hidden(Holder<MobEffect> effect) {
        return HudVisibility.clean() && !HudVisibility.inClientTick() && (Object) this == Minecraft.getInstance().player
            && (effect == MobEffects.NIGHT_VISION || effect == MobEffects.BLINDNESS || effect == MobEffects.DARKNESS
            //? if >=1.21.11 {
            /*|| effect == MobEffects.NAUSEA);
            *///?} else {
            || effect == MobEffects.CONFUSION);
            //?}
    }
    //?} else {
    /*@Inject(method = "hasEffect", at = @At("HEAD"), cancellable = true)
    private void blockpilot$cleanHas(MobEffect effect, CallbackInfoReturnable<Boolean> info) {
        if (hidden(effect)) {
            info.setReturnValue(false);
        }
    }

    @Inject(method = "getEffect", at = @At("HEAD"), cancellable = true)
    private void blockpilot$cleanGet(MobEffect effect, CallbackInfoReturnable<MobEffectInstance> info) {
        if (hidden(effect)) {
            info.setReturnValue(null);
        }
    }

    private boolean hidden(MobEffect effect) {
        return HudVisibility.clean() && !HudVisibility.inClientTick() && (Object) this == Minecraft.getInstance().player
            && (effect == MobEffects.NIGHT_VISION || effect == MobEffects.BLINDNESS || effect == MobEffects.DARKNESS
            || effect == MobEffects.CONFUSION);
    }
    *///?}
}
