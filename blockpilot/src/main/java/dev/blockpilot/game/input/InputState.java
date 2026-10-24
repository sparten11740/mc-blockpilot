package dev.blockpilot.game.input;

import dev.blockpilot.mixin.KeyMappingAccessor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.KeyMapping;

/**
 * Everything agents hold down, and which session holds it, so it can always be released: by {@code release},
 * {@code stop}, a failing script, human override, a closed session or leaving the world. Client thread only, except
 * the raw-key lookup polled by mixins.
 */
public final class InputState {
    private static final Map<KeyMapping, Integer> MAPPINGS = new HashMap<>();
    private static final Map<Integer, Integer> RAW_KEYS = new ConcurrentHashMap<>();
    private static final Map<Integer, Integer> BUTTONS = new HashMap<>();

    private InputState() {
    }

    /** Holds a key mapping (movement, sneak, use…) until released; re-asserted every tick. */
    public static void hold(int session, KeyMapping mapping) {
        MAPPINGS.put(mapping, session);
        setHeld(mapping, true);
    }

    public static void release(KeyMapping mapping) {
        if (MAPPINGS.remove(mapping) != null) {
            setHeld(mapping, false);
        }
    }

    private static void setHeld(KeyMapping mapping, boolean held) {
        ((KeyMappingAccessor) mapping).blockpilot$setHeld(held);
    }

    public static boolean isHeld(KeyMapping mapping) {
        return MAPPINGS.containsKey(mapping);
    }

    public static void holdKey(int session, int key) {
        RAW_KEYS.put(key, session);
        Injection.key(key, true);
    }

    public static void releaseKey(int key) {
        if (RAW_KEYS.remove(key) != null) {
            Injection.key(key, false);
        }
    }

    public static boolean isRawKeyHeld(int key) {
        return RAW_KEYS.containsKey(key);
    }

    public static void holdButton(int session, int button) {
        BUTTONS.put(button, session);
        Injection.mouseButton(button, true);
    }

    public static void releaseButton(int button) {
        if (BUTTONS.remove(button) != null) {
            Injection.mouseButton(button, false);
        }
    }

    /** Called every client tick: vanilla resets key mappings (screens, focus changes); held ones stay held. */
    public static void reassert() {
        MAPPINGS.keySet().forEach(mapping -> setHeld(mapping, true));
    }

    public static boolean isEmpty() {
        return MAPPINGS.isEmpty() && RAW_KEYS.isEmpty() && BUTTONS.isEmpty();
    }

    /** Releases what the session holds. */
    public static void release(int session) {
        for (KeyMapping mapping : owned(MAPPINGS, session)) {
            release(mapping);
        }
        for (Integer key : owned(RAW_KEYS, session)) {
            releaseKey(key);
        }
        for (Integer button : owned(BUTTONS, session)) {
            releaseButton(button);
        }
    }

    public static void releaseAll() {
        new ArrayList<>(MAPPINGS.keySet()).forEach(InputState::release);
        new ArrayList<>(RAW_KEYS.keySet()).forEach(InputState::releaseKey);
        new ArrayList<>(BUTTONS.keySet()).forEach(InputState::releaseButton);
    }

    private static <K> List<K> owned(Map<K, Integer> held, int session) {
        List<K> keys = new ArrayList<>();
        held.forEach((key, owner) -> {
            if (owner == session) {
                keys.add(key);
            }
        });
        return keys;
    }
}
