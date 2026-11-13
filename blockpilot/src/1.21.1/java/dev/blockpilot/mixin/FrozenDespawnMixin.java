package dev.blockpilot.mixin;

import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
//? if <1.21 {
/*import dev.blockpilot.game.ServerTicking;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
*///?}

/**
 * A frozen server despawns no mobs on 1.20.1, as with 1.20.3's tick rate manager, where the level checks despawning
 * only for entities that tick. Empty on later versions.
 */
@Mixin(Mob.class)
public abstract class FrozenDespawnMixin {
    //? if <1.21 {
    /*@Inject(method = "checkDespawn", at = @At("HEAD"), cancellable = true)
    private void blockpilot$despawn(CallbackInfo info) {
        if (ServerTicking.frozen((Mob) (Object) this)) {
            info.cancel();
        }
    }
    *///?}
}
