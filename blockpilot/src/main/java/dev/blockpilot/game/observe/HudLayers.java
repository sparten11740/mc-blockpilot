package dev.blockpilot.game.observe;

import java.util.ArrayList;
import java.util.List;

/**
 * The HUD as {@code option hud} sets it, under the captures that show it their own way while they run: those hold the
 * F1 switch on, and when the last ends it goes back to the option. While captures run, the option reads and sets what
 * they will leave, so a capture is never taken for an agent's change. Client thread; {@link #clean()} from any.
 */
public final class HudLayers {
    /** The values {@code option hud} and {@code hud=} take. */
    public static final List<String> VALUES = List.of("on", "off", "clean");

    /** The game's F1 switch. */
    interface Switch {
        boolean hidden();

        void setHidden(boolean hidden);
    }

    /** How a capture shows the HUD: as it is, hidden as F1 does, or clean. */
    public enum Mode {
        AS_IS, HIDDEN, CLEAN;

        /** From a hud= option: {@code off} hides, {@code clean} cleans, anything else leaves it. */
        public static Mode of(String option) {
            return "off".equals(option) ? HIDDEN : "clean".equals(option) ? CLEAN : AS_IS;
        }
    }

    private final Switch gui;
    private final List<Capture> captures = new ArrayList<>();
    private volatile boolean optionClean;
    private volatile int cleanCaptures;
    /** The F1 switch as the first capture found it, which the last puts back. */
    private boolean hiddenBefore;

    HudLayers(Switch gui) {
        this.gui = gui;
    }

    /** Whether frames are clean: by the option, or while a clean capture runs. */
    public boolean clean() {
        return optionClean || cleanCaptures > 0;
    }

    /** {@code on}, {@code off} or {@code clean}, as captures will leave it. */
    public String option() {
        boolean hidden = captures.isEmpty() ? gui.hidden() : hiddenBefore;
        return optionClean ? "clean" : hidden ? "off" : "on";
    }

    /** Sets the option, one of {@link #VALUES}; while captures run, for when they end. */
    public void setOption(String value) {
        optionClean = value.equals("clean");
        boolean hidden = !value.equals("on");
        if (captures.isEmpty()) {
            gui.setHidden(hidden);
        } else {
            hiddenBefore = hidden;
        }
    }

    /** Starts showing the HUD so until the capture ends. */
    public Capture capture(Mode mode) {
        Capture capture = new Capture(mode);
        if (mode != Mode.AS_IS) {
            if (captures.isEmpty()) {
                hiddenBefore = gui.hidden();
            }
            captures.add(capture);
            if (mode == Mode.CLEAN) {
                cleanCaptures++;
            }
            capture.tick();
        }
        return capture;
    }

    /** One capture's hold on the HUD. */
    public final class Capture {
        private final Mode mode;
        private boolean ended;

        private Capture(Mode mode) {
            this.mode = mode;
        }

        /** Holds the F1 switch on again, as the player may have pressed F1 since; each tick or frame. */
        public void tick() {
            if (mode != Mode.AS_IS && !ended) {
                gui.setHidden(true);
            }
        }

        /** Lets go of the HUD; the last capture to end puts the F1 switch back as the option has it. */
        public void end() {
            if (mode == Mode.AS_IS || ended) {
                return;
            }
            ended = true;
            captures.remove(this);
            if (mode == Mode.CLEAN) {
                cleanCaptures--;
            }
            if (captures.isEmpty()) {
                gui.setHidden(hiddenBefore);
            }
        }
    }
}
