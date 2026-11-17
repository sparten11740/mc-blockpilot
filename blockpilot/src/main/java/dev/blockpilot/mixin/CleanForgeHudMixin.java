package dev.blockpilot.mixin;

import dev.blockpilot.game.observe.HudVisibility;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * With clean frames, 1.20.1 Forge's HUD is skipped too: it replaces the game's and draws its overlays, and other mods',
 * without calling the method {@link CleanHudMixin} cancels. Applies only where Forge's HUD exists. Not remapped, as no
 * refmap entry is written for it: the method is named as in development and, as {@code Gui}'s override, by its SRG
 * name in a released jar.
 */
@Pseudo
@Mixin(targets = "net.minecraftforge.client.gui.overlay.ForgeGui", remap = false)
abstract class CleanForgeHudMixin {
    @Inject(method = {"render(Lnet/minecraft/client/gui/GuiGraphics;F)V",
        "m_280421_(Lnet/minecraft/client/gui/GuiGraphics;F)V"}, at = @At("HEAD"), cancellable = true)
    private void blockpilot$clean(CallbackInfo info) {
        if (HudVisibility.clean()) {
            info.cancel();
        }
    }
}
