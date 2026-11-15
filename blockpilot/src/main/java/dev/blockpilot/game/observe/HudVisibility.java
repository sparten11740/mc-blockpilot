package dev.blockpilot.game.observe;

import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;

/** Whether the game hides its HUD, as F1 does. From 26.2 the HUD only offers to toggle it. Client thread. */
public final class HudVisibility {
    /** Whether frames are clean: see {@link #setClean}. */
    private static volatile boolean clean;
    /** The field of view effect scale before a clean frame zeroed it. */
    private static Double fovEffectsBefore;

    private HudVisibility() {
    }

    /** How a capture shows the HUD: as it is, hidden as F1 does, or clean; from its hud= option. */
    public enum Capture {
        AS_IS, HIDDEN, CLEAN;

        public static Capture of(String option) {
            return option == null ? AS_IS : switch (option) {
                case "off" -> HIDDEN;
                case "clean" -> CLEAN;
                default -> AS_IS;
            };
        }

        /** Shows the HUD so; again on every tick, as the player may press F1 meanwhile. */
        public void apply() {
            switch (this) {
                case HIDDEN -> setHidden(true);
                case CLEAN -> setClean(true);
                default -> {
                }
            }
        }
    }

    /** The HUD as it was, to put back after a capture. */
    public record State(boolean hidden, boolean clean) {
        public void restore() {
            setClean(clean);
            setHidden(hidden);
        }
    }

    public static State state() {
        return new State(hidden(), clean());
    }

    public static boolean clean() {
        return clean;
    }

    /**
     * Clean frames: the HUD hidden as F1 does, and also the whole HUD pass, with the layers and overlays other mods
     * draw in it; the local player's night vision, blindness, darkness and nausea don't show, and the field of view
     * effects (sprinting, speed) stay still. Turning it off leaves the HUD hidden; callers put that back themselves.
     */
    public static void setClean(boolean value) {
        if (value == clean) {
            return;
        }
        clean = value;
        OptionInstance<Double> fovEffects = Minecraft.getInstance().options.fovEffectScale();
        if (value) {
            setHidden(true);
            fovEffectsBefore = fovEffects.get();
            fovEffects.set(0.0);
        } else if (fovEffectsBefore != null) {
            fovEffects.set(fovEffectsBefore);
            fovEffectsBefore = null;
        }
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
