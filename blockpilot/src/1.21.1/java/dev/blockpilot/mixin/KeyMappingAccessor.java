package dev.blockpilot.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The bound key and pending clicks of a key mapping, which vanilla keeps private. */
@Mixin(KeyMapping.class)
public interface KeyMappingAccessor {
    @Accessor("key")
    InputConstants.Key blockpilot$key();

    @Accessor("clickCount")
    int blockpilot$clickCount();

    @Accessor("clickCount")
    void blockpilot$setClickCount(int value);

    /** Sets the held state directly; {@code setDown} flips toggle mappings (toggle sneak/sprint) instead. */
    @Accessor("isDown")
    void blockpilot$setHeld(boolean value);
}
