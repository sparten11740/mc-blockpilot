package dev.blockpilot.mixin;

import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The volume and pitch a sound was played with, before its resource scales them. */
@Mixin(AbstractSoundInstance.class)
public interface AbstractSoundInstanceAccessor {
    @Accessor("volume")
    float blockpilot$volume();

    @Accessor("pitch")
    float blockpilot$pitch();
}
