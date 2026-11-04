package dev.blockpilot.game.input;

import net.minecraft.client.Minecraft;
//? if <26.3 {
import org.lwjgl.glfw.GLFW;
//?}

/** Whether the game should behave as focused although its window is not, because an agent drives it. */
public final class Focus {
    private static volatile boolean keepActive;

    private Focus() {
    }

    public static boolean keepActive() {
        return keepActive;
    }

    public static void setKeepActive(boolean value) {
        keepActive = value;
    }

    /** Whether the OS really has the game window focused (not the agent's pretend focus). */
    public static boolean reallyFocused() {
        //? if >=26.3 {
        /*return Minecraft.getInstance().getWindow().isFocused();
        *///?} else {
        long window = Minecraft.getInstance().getWindow().getWindow();
        return GLFW.glfwGetWindowAttrib(window, GLFW.GLFW_FOCUSED) == GLFW.GLFW_TRUE;
        //?}
    }
}
