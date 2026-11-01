package dev.blockpilot.mixin;

import com.mojang.blaze3d.audio.DeviceList;
import com.mojang.blaze3d.audio.Library;
import dev.blockpilot.game.record.AudioCapture;
import java.nio.IntBuffer;
import org.lwjgl.openal.ALC10;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * While a recording wants the game's sound, the engine plays through a loopback device: {@link AudioCapture} keeps the
 * device the engine opened to play the mix on, and the engine reports that device as its own, so its device checks
 * see no change.
 */
@Mixin(Library.class)
public abstract class LibraryMixin {
    @Shadow
    private long currentDevice;

    @Inject(method = "openDeviceOrFallback", at = @At("RETURN"), cancellable = true)
    private static void blockpilot$device(String preferredDevice, String systemDefaultDevice,
        CallbackInfoReturnable<Long> info) {
        info.setReturnValue(AudioCapture.device(info.getReturnValue()));
    }

    @Redirect(method = "init", at = @At(value = "INVOKE",
        target = "Lorg/lwjgl/openal/ALC10;alcCreateContext(JLjava/nio/IntBuffer;)J"))
    private long blockpilot$context(long device, IntBuffer attributes) {
        return AudioCapture.isLoopback(device) ? AudioCapture.context(device)
            : ALC10.alcCreateContext(device, attributes);
    }

    @Inject(method = "init", at = @At("RETURN"))
    private void blockpilot$started(String preferredDevice, DeviceList currentDevices, boolean useHrtf,
        CallbackInfo info) {
        AudioCapture.started(currentDevice);
    }

    @Inject(method = "cleanup", at = @At("HEAD"))
    private void blockpilot$closing(CallbackInfo info) {
        AudioCapture.closing(currentDevice);
    }

    @Inject(method = "currentDeviceName", at = @At("HEAD"), cancellable = true)
    private void blockpilot$deviceName(CallbackInfoReturnable<String> info) {
        String name = AudioCapture.deviceName();
        if (name != null) {
            info.setReturnValue(name);
        }
    }

    @Inject(method = "isCurrentDeviceDisconnected", at = @At("HEAD"), cancellable = true)
    private void blockpilot$disconnected(CallbackInfoReturnable<Boolean> info) {
        if (AudioCapture.isLoopback(currentDevice)) {
            info.setReturnValue(AudioCapture.monitorLost());
        }
    }
}
