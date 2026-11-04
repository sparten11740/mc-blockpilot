package dev.blockpilot.game.observe;

import net.minecraft.client.Minecraft;

/** Whether the game hides its HUD, as F1 does. From 26.2 the HUD only offers to toggle it. Client thread. */
public final class HudVisibility {
    private HudVisibility() {
    }

    public static boolean hidden() {
        //? if >=26.2 {
        /*return Minecraft.getInstance().gui.hud.isHidden();
        *///?} else {
        return Minecraft.getInstance().options.hideGui;
        //?}
    }

    public static void setHidden(boolean hidden) {
        //? if >=26.2 {
        /*if (hidden() != hidden) {
            Minecraft.getInstance().gui.hud.toggle();
        }
        *///?} else {
        Minecraft.getInstance().options.hideGui = hidden;
        //?}
    }
}
