package dev.blockpilot.mixin;

import dev.blockpilot.game.perf.Perf;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Counts, during a performance capture, the entities added to server levels, spawned or loaded, per server tick. */
@Mixin(PersistentEntitySectionManager.class)
public abstract class PersistentEntitySectionManagerMixin {
    @Inject(method = "addEntity", at = @At("HEAD"))
    private void blockpilot$added(EntityAccess entity, boolean loaded, CallbackInfoReturnable<Boolean> info) {
        Perf.entityAdded();
    }
}
