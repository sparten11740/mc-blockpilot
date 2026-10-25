package dev.blockpilot.mixin;

import dev.blockpilot.game.observe.ClientOptions;
import net.minecraft.client.GraphicsPreset;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Each option a graphics preset sets, so an agent's preset change can be undone in full. */
@Mixin(GraphicsPreset.class)
public abstract class GraphicsPresetMixin {
    @Inject(method = "set", at = @At("HEAD"))
    private <T> void blockpilot$setting(OptionsSubScreen screen, OptionInstance<T> option, T value,
        CallbackInfo info) {
        ClientOptions.beforeSideEffect(option);
    }
}
