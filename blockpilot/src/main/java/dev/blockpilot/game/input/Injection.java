package dev.blockpilot.game.input;

import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/**
 * Sends input to the game the way GLFW would: through vanilla's mouse and keyboard handlers, so the game and every
 * mod hooking those handlers see it as real input. While an injection runs, {@link #active()} is true, which tells
 * the human-input detection to ignore it. Client thread only.
 */
public final class Injection {
    /** Implemented by the mouse handler mixin. */
    public interface Mouse {
        void blockpilot$press(long window, int button, int action, int modifiers);

        void blockpilot$move(long window, double x, double y);

        void blockpilot$scroll(long window, double xOffset, double yOffset);

        /** Marks the mouse as captured for the game without capturing the OS cursor. */
        void blockpilot$markGrabbed();
    }

    /** Implemented by the keyboard handler mixin. */
    public interface Keyboard {
        void blockpilot$keyPress(long window, int key, int scanCode, int action, int modifiers);

        void blockpilot$charTyped(long window, int codePoint, int modifiers);
    }

    private static int depth;

    private Injection() {
    }

    public static boolean active() {
        return depth > 0;
    }

    private static void run(Runnable injection) {
        depth++;
        try {
            injection.run();
        } finally {
            depth--;
        }
    }

    private static Minecraft minecraft() {
        return Minecraft.getInstance();
    }

    private static long window() {
        return minecraft().getWindow().getWindow();
    }

    private static Mouse mouse() {
        return (Mouse) minecraft().mouseHandler;
    }

    public static void mouseButton(int button, boolean down) {
        run(() -> {
            if (down && minecraft().screen == null) {
                mouse().blockpilot$markGrabbed();
            }
            mouse().blockpilot$press(window(), button, down ? GLFW.GLFW_PRESS : GLFW.GLFW_RELEASE, 0);
        });
    }

    /** Moves the cursor to an absolute window position in screen pixels; in-world this turns the camera. */
    public static void mouseMoveTo(double x, double y) {
        run(() -> {
            if (minecraft().screen == null) {
                mouse().blockpilot$markGrabbed();
            }
            mouse().blockpilot$move(window(), x, y);
        });
    }

    public static void scroll(double amount) {
        run(() -> mouse().blockpilot$scroll(window(), 0, amount));
    }

    private static Keyboard keyboard() {
        return (Keyboard) minecraft().keyboardHandler;
    }

    public static void key(int key, boolean down) {
        run(() -> keyboard().blockpilot$keyPress(window(), key, GLFW.glfwGetKeyScancode(key),
            down ? GLFW.GLFW_PRESS : GLFW.GLFW_RELEASE, 0));
    }

    public static void typeCharacter(int codePoint) {
        run(() -> keyboard().blockpilot$charTyped(window(), codePoint, 0));
    }
}
