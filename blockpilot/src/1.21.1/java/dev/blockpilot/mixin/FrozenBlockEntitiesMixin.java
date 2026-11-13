package dev.blockpilot.mixin;

import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
//? if <1.21 {
/*import dev.blockpilot.game.ServerTicking;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
*///?}

/** A frozen server level's block entities wait on 1.20.1, as with 1.20.3's tick rate manager. Empty later. */
@Mixin(Level.class)
public abstract class FrozenBlockEntitiesMixin {
    //? if <1.21 {
    /*@Inject(method = "tickBlockEntities", at = @At("HEAD"), cancellable = true)
    private void blockpilot$blockEntities(CallbackInfo info) {
        if (!((Level) (Object) this).isClientSide() && !ServerTicking.runsNormally()) {
            info.cancel();
        }
    }
    *///?}
}
