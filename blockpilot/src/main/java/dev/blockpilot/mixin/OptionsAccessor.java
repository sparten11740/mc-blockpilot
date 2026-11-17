package dev.blockpilot.mixin;

import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The field of view effect option itself, which {@code fovEffectScale()} stands in for while frames are clean. */
@Mixin(Options.class)
public interface OptionsAccessor {
    @Accessor("fovEffectScale")
    OptionInstance<Double> blockpilot$fovEffectScale();
}
