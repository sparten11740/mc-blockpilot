package dev.blockpilot.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.Map;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Every key the game has a name for, which vanilla keeps package-private. */
@Mixin(InputConstants.Key.class)
public interface InputKeyAccessor {
    @Accessor("NAME_MAP")
    static Map<String, InputConstants.Key> blockpilot$names() {
        throw new AssertionError();
    }
}
