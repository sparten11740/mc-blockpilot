package dev.blockpilot.game.input;

import org.lwjgl.glfw.GLFW;

/**
 * Real keyboard and mouse input from the player while an agent acts. Only input to the focused window counts, and
 * cursor movement only beyond a small threshold, so cursor warps by the game itself do not cancel the agent.
 */
public final class HumanInput {
    private static final double MOVE_THRESHOLD = 4;
    private static volatile Runnable listener = () -> { };
    private static double lastX = Double.NaN;
    private static double lastY = Double.NaN;

    private HumanInput() {
    }

    public static void setListener(Runnable value) {
        listener = value;
    }

    public static void key(long window, int key, int action) {
        if (action == GLFW.GLFW_PRESS && Focus.reallyFocused()) {
            listener.run();
        }
    }

    public static void mouseButton(long window) {
        if (Focus.reallyFocused()) {
            listener.run();
        }
    }

    public static void mouseMove(long window, double x, double y) {
        boolean moved = !Double.isNaN(lastX)
            && (Math.abs(x - lastX) > MOVE_THRESHOLD || Math.abs(y - lastY) > MOVE_THRESHOLD);
        lastX = x;
        lastY = y;
        if (moved && Focus.reallyFocused()) {
            listener.run();
        }
    }
}
