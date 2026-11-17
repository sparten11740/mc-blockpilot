package dev.blockpilot.game.observe;

import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;

/**
 * Whether the game hides its HUD, as F1 does, and whether frames are clean, as {@code option hud} and captures set
 * them ({@link HudLayers}). From 26.2 the HUD only offers to toggle it. Client thread.
 */
public final class HudVisibility {
    /** What the game reads as the field of view effect scale while frames are clean. */
    public static final OptionInstance<Double> STILL_FOV = new OptionInstance<>("options.fovEffectScale",
        OptionInstance.noTooltip(), (caption, value) -> caption, OptionInstance.UnitDouble.INSTANCE, 0.0, value -> {
        });
    private static final HudLayers LAYERS = new HudLayers(new HudLayers.Switch() {
        @Override
        public boolean hidden() {
            return HudVisibility.hidden();
        }

        @Override
        public void setHidden(boolean hidden) {
            HudVisibility.setHidden(hidden);
        }
    });

    private HudVisibility() {
    }

    /**
     * Whether frames are clean: the HUD hidden as F1 does, and also the whole HUD pass, with the layers and overlays
     * other mods draw in it; the local player's night vision, blindness, darkness and nausea don't show, and the field
     * of view effects (sprinting, speed) stay still. Any thread.
     */
    public static boolean clean() {
        return LAYERS.clean();
    }

    /** {@code on}, {@code off} or {@code clean}, as {@code option hud} reads it. */
    public static String option() {
        return LAYERS.option();
    }

    /** One of {@link HudLayers#VALUES}. */
    public static void setOption(String value) {
        LAYERS.setOption(value);
    }

    /** Shows the HUD so until the capture ends. */
    public static HudLayers.Capture capture(HudLayers.Mode mode) {
        return LAYERS.capture(mode);
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
