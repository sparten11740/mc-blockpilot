package dev.blockpilot.game.input;

import java.util.List;
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

    /** A modifier key's left and right key codes, and the bit GLFW sets in events while either is down. */
    private record Modifier(int left, int right, int bit) {
    }

    private static final List<Modifier> MODIFIERS = List.of(
        new Modifier(GLFW.GLFW_KEY_LEFT_SHIFT, GLFW.GLFW_KEY_RIGHT_SHIFT, GLFW.GLFW_MOD_SHIFT),
        new Modifier(GLFW.GLFW_KEY_LEFT_CONTROL, GLFW.GLFW_KEY_RIGHT_CONTROL, GLFW.GLFW_MOD_CONTROL),
        new Modifier(GLFW.GLFW_KEY_LEFT_ALT, GLFW.GLFW_KEY_RIGHT_ALT, GLFW.GLFW_MOD_ALT),
        new Modifier(GLFW.GLFW_KEY_LEFT_SUPER, GLFW.GLFW_KEY_RIGHT_SUPER, GLFW.GLFW_MOD_SUPER));

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

    /**
     * The modifier bits a real event carries while the agent holds modifier keys. Screens from 26.1 on read Shift and
     * Control from the event (quick-move clicks, select-all and copy in text fields) rather than polling the keys.
     */
    private static int modifiers() {
        int bits = 0;
        for (Modifier modifier : MODIFIERS) {
            if (InputState.isRawKeyHeld(modifier.left()) || InputState.isRawKeyHeld(modifier.right())) {
                bits |= modifier.bit();
            }
        }
        return bits;
    }

    private static Mouse mouse() {
        return (Mouse) minecraft().mouseHandler;
    }

    public static void mouseButton(int button, boolean down) {
        run(() -> {
            if (down && minecraft().screen == null) {
                mouse().blockpilot$markGrabbed();
            }
            mouse().blockpilot$press(window(), button, down ? GLFW.GLFW_PRESS : GLFW.GLFW_RELEASE, modifiers());
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
            down ? GLFW.GLFW_PRESS : GLFW.GLFW_RELEASE, modifiers()));
    }

    public static void typeCharacter(int codePoint) {
        run(() -> keyboard().blockpilot$charTyped(window(), codePoint, modifiers()));
    }
}
