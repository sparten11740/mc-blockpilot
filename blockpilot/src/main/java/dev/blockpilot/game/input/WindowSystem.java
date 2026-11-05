package dev.blockpilot.game.input;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.List;
import net.minecraft.client.Minecraft;
//? if >=26.3 {
/*import org.lwjgl.sdl.SDLKeyboard;
import org.lwjgl.sdl.SDLVideo;
*///?} else {
import org.lwjgl.glfw.GLFW;
//?}

/**
 * What depends on the game's window system: GLFW, and SDL from 26.3. The numbers are compile-time constants and the
 * native calls stay in method bodies, so tests can load this without the game's libraries.
 */
final class WindowSystem {
    /** A modifier's left and right keys, as the game numbers them, and the bit real events carry while one is down. */
    record Modifier(int left, int right, int bit) {
    }

    //? if >=26.3 {
    /*static final List<Modifier> MODIFIERS = List.of(
        new Modifier(InputConstants.KEY_LSHIFT, InputConstants.KEY_RSHIFT, InputConstants.MOD_SHIFT),
        new Modifier(InputConstants.KEY_LCONTROL, InputConstants.KEY_RCONTROL, InputConstants.MOD_CONTROL),
        new Modifier(InputConstants.KEY_LALT, InputConstants.KEY_RALT, InputConstants.MOD_ALT),
        new Modifier(InputConstants.KEY_LGUI, InputConstants.KEY_RGUI, InputConstants.MOD_SUPER));
    *///?} else {
    static final List<Modifier> MODIFIERS = List.of(
        new Modifier(GLFW.GLFW_KEY_LEFT_SHIFT, GLFW.GLFW_KEY_RIGHT_SHIFT, GLFW.GLFW_MOD_SHIFT),
        new Modifier(GLFW.GLFW_KEY_LEFT_CONTROL, GLFW.GLFW_KEY_RIGHT_CONTROL, GLFW.GLFW_MOD_CONTROL),
        new Modifier(GLFW.GLFW_KEY_LEFT_ALT, GLFW.GLFW_KEY_RIGHT_ALT, GLFW.GLFW_MOD_ALT),
        new Modifier(GLFW.GLFW_KEY_LEFT_SUPER, GLFW.GLFW_KEY_RIGHT_SUPER, GLFW.GLFW_MOD_SUPER));
    //?}

    // The numbers mouse events give the left, right and middle buttons, and what takes a further button's number to
    // the one in its name, as in key.mouse.4.
    //? if >=26.3 {
    /*static final List<Integer> BUTTON_NUMBERS = List.of(1, 3, 2);
    static final int BUTTON_NAME_OFFSET = 0;
    *///?} else {
    static final List<Integer> BUTTON_NUMBERS = List.of(0, 1, 2);
    static final int BUTTON_NAME_OFFSET = 1;
    //?}

    private WindowSystem() {
    }

    /** Whether the OS has the game window focused. */
    static boolean focused() {
        long window = Minecraft.getInstance().getWindow().getWindow();
        //? if >=26.3 {
        /*return (SDLVideo.SDL_GetWindowFlags(window) & SDLVideo.SDL_WINDOW_INPUT_FOCUS) != 0;
        *///?} else {
        return GLFW.glfwGetWindowAttrib(window, GLFW.GLFW_FOCUSED) == GLFW.GLFW_TRUE;
        //?}
    }

    /** The game's name for a key its keyboard events number {@code key}. */
    static String keyName(int key) {
        //? if >=26.3 {
        /*return InputConstants.Type.KEYBOARD.getOrCreate(key).getName();
        *///?} else {
        return InputConstants.Type.KEYSYM.getOrCreate(key).getName();
        //?}
    }

    /**
     * What a real event carries beside the key: up to 26.2 the key is a key code and this its scan code; from 26.3 the
     * key is a scan code and this its key code, which the game's text shortcuts read. An agent's {@code a} is a place
     * on the keyboard on every version, so it stays 97 whatever the layout, or select-all would fail on AZERTY, whose
     * key there gives {@code q}. Other keys get what SDL makes for an event, with {@code key_event}.
     */
    static int otherKeyCode(int key) {
        //? if >=26.3 {
        /*if (key >= InputConstants.KEY_A && key <= InputConstants.KEY_Z) {
            return 'a' + key - InputConstants.KEY_A;
        }
        return SDLKeyboard.SDL_GetKeyFromScancode(key, (short) 0, true);
        *///?} else {
        return GLFW.glfwGetKeyScancode(key);
        //?}
    }
}
