package dev.blockpilot.platform.vanilla.mixin;

import dev.blockpilot.platform.vanilla.VanillaPlatform;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Adds the mod's key mappings before the options read their keys from options.txt. */
@Mixin(Options.class)
public abstract class OptionsMixin {
    @Mutable
    @Final
    @Shadow
    public KeyMapping[] keyMappings;

    @Inject(method = "load()V", at = @At("HEAD"))
    private void blockpilot$addKeyMappings(CallbackInfo info) {
        keyMappings = VanillaPlatform.withKeyMappings(keyMappings);
    }
}
