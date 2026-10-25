package dev.blockpilot.mixin;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.IOException;
import java.nio.channels.WritableByteChannel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** The PNG encoder behind {@code writeToFile}, to encode into memory. */
@Mixin(NativeImage.class)
public interface NativeImageInvoker {
    @Invoker("writeToChannel")
    boolean blockpilot$writeToChannel(WritableByteChannel output) throws IOException;
}
