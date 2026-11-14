package dev.blockpilot.game.common;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Overlay;
import net.minecraft.client.gui.screens.Screen;

/** The open screen and overlay, which 26.2 moved from Minecraft to Gui. Client thread. */
public final class ClientScreens {
    private ClientScreens() {
    }

    public static Screen current() {
        //? if >=26.2 {
        /*return Minecraft.getInstance().gui.screen();
        *///?} else {
        return Minecraft.getInstance().screen;
        //?}
    }

    public static void open(Screen screen) {
        //? if >=26.2 {
        /*Minecraft.getInstance().gui.setScreen(screen);
        *///?} else {
        Minecraft.getInstance().setScreen(screen);
        //?}
    }

    public static Overlay overlay() {
        //? if >=26.2 {
        /*return Minecraft.getInstance().gui.overlay();
        *///?} else {
        return Minecraft.getInstance().getOverlay();
        //?}
    }

    /** Whether the open screen or overlay pauses singleplayer, as the game works it out at its next frame. */
    public static boolean pausing() {
        //? if >=26.2 {
        /*return Minecraft.getInstance().gui.isPausing();
        *///?} else {
        Screen screen = current();
        Overlay overlay = overlay();
        return screen != null && screen.isPauseScreen() || overlay != null && overlay.isPauseScreen();
        //?}
    }
}
