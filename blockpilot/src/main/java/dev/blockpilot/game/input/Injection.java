package dev.blockpilot.game.input;

import com.mojang.blaze3d.platform.InputConstants;
import dev.blockpilot.game.common.ClientScreens;
import dev.blockpilot.protocol.Refusal;
import java.lang.ref.WeakReference;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;

/**
 * Sends input to the game the way the window system would: through vanilla's mouse and keyboard handlers, so the game
 * and every mod hooking those handlers see it as real input. While an injection runs, {@link #active()} is true, which
 * tells the human-input detection to ignore it. Client thread only.
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
        void blockpilot$keyPress(long window, int key, int otherKeyCode, int action, int modifiers);

        void blockpilot$charTyped(long window, int codePoint, int modifiers);
    }


    private static int depth;
    /** Which screens Blockpilot's input may reach; escape, which only closes a screen, and releases always may. */
    private static Predicate<Screen> screenGate = screen -> true;
    /** The chat screen Blockpilot's input last reached, so a command sent from it counts as the agent's. */
    private static WeakReference<Screen> chatReached = new WeakReference<>(null);

    private Injection() {
    }

    public static boolean active() {
        return depth > 0;
    }

    public static void setScreenGate(Predicate<Screen> gate) {
        screenGate = gate;
    }

    /** Whether Blockpilot's own input reached this chat screen since it opened. */
    public static boolean reachedChat(Screen screen) {
        return screen != null && chatReached.get() == screen;
    }

    private static void run(Runnable injection) {
        run(injection, false);
    }

    /** Sends the input unless the screen gate refuses the screen under it, which fails the step that sent it. */
    private static void run(Runnable injection, boolean ungated) {
        if (!ungated && !screenGate.test(ClientScreens.current())) {
            Refusal.raise("not_allowed", "screen");
            return;
        }
        noteChat();
        depth++;
        try {
            injection.run();
        } finally {
            depth--;
            // Also a chat screen the injection itself opened.
            noteChat();
        }
    }

    private static void noteChat() {
        if (ClientScreens.current() instanceof ChatScreen chat) {
            chatReached = new WeakReference<>(chat);
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
        for (WindowSystem.Modifier modifier : WindowSystem.MODIFIERS) {
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
            if (down && ClientScreens.current() == null) {
                mouse().blockpilot$markGrabbed();
            }
            int action = down ? InputConstants.PRESS : InputConstants.RELEASE;
            mouse().blockpilot$press(window(), button, action, modifiers());
        }, !down);
    }

    /** Moves the cursor to an absolute window position in screen pixels; in-world this turns the camera. */
    public static void mouseMoveTo(double x, double y) {
        run(() -> {
            if (ClientScreens.current() == null) {
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
        int action = down ? InputConstants.PRESS : InputConstants.RELEASE;
        run(() -> keyboard().blockpilot$keyPress(window(), key, WindowSystem.otherKeyCode(key), action, modifiers()),
            !down || key == InputConstants.KEY_ESCAPE);
    }

    public static void typeCharacter(int codePoint) {
        run(() -> keyboard().blockpilot$charTyped(window(), codePoint, modifiers()));
    }
}
