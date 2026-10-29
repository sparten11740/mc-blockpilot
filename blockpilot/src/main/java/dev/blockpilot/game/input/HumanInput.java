package dev.blockpilot.game.input;

import com.mojang.blaze3d.platform.InputConstants;
import dev.blockpilot.api.Fields;
import java.util.function.Consumer;
import org.lwjgl.glfw.GLFW;

/**
 * Real keyboard and mouse input from the player while an agent acts, described as {@code input=} fields for the
 * interrupt it causes. Only input to the focused window counts, and cursor movement only beyond a small threshold, so
 * cursor warps by the game itself do not cancel the agent.
 */
public final class HumanInput {
    private static final double MOVE_THRESHOLD = 4;
    private static volatile Consumer<Fields> listener = input -> { };
    private static double lastX = Double.NaN;
    private static double lastY = Double.NaN;

    private HumanInput() {
    }

    public static void setListener(Consumer<Fields> value) {
        listener = value;
    }

    public static void key(long window, int key, int action) {
        if (action == GLFW.GLFW_PRESS && Focus.reallyFocused()) {
            listener.accept(keyInput(KeyNames.agentName(InputConstants.Type.KEYSYM.getOrCreate(key).getName())));
        }
    }

    /** A press of the mouse button GLFW numbers {@code button}. */
    public static void mouseButton(long window, int button) {
        if (Focus.reallyFocused()) {
            listener.accept(buttonInput(button));
        }
    }

    public static void scroll(long window) {
        if (Focus.reallyFocused()) {
            listener.accept(new Fields().put("input", "scroll"));
        }
    }

    public static void mouseMove(long window, double x, double y) {
        double dx = x - lastX;
        double dy = y - lastY;
        boolean moved = !Double.isNaN(lastX) && (Math.abs(dx) > MOVE_THRESHOLD || Math.abs(dy) > MOVE_THRESHOLD);
        lastX = x;
        lastY = y;
        if (moved && Focus.reallyFocused()) {
            listener.accept(moveInput(dx, dy));
        }
    }

    static Fields keyInput(String name) {
        return new Fields().put("input", "key").put("key", name);
    }

    static Fields buttonInput(int button) {
        return new Fields().put("input", "button").put("button", KeyNames.buttonName(button));
    }

    /** A cursor move in window pixels, x to the right and y down. */
    static Fields moveInput(double dx, double dy) {
        return new Fields().put("input", "move").put("dx", Math.round(dx)).put("dy", Math.round(dy));
    }
}
