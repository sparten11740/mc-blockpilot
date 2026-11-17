package dev.blockpilot.game.observe;

import dev.blockpilot.api.Action;
import dev.blockpilot.api.CommandException;
import dev.blockpilot.api.Result;
import dev.blockpilot.protocol.CommandFailure;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@code camera play}: shots played one after another on the camera's clock while scripts go on. Each shot starts its
 * form as the camera command would then, so it sets off from where the camera is drawn; a timed one runs beside the
 * play and ends as camera at would, and a hold keeps the camera as it is for its ticks. Another camera command, stop,
 * real input or a reset ends it. Shots start on the client thread; a stop may come from another.
 */
final class CameraTimeline {
    private static final Logger LOGGER = LoggerFactory.getLogger("Blockpilot");
    /** What {@link Shot#start} answers for a shot that ends once no motion runs. */
    private static final long WHEN_STILL = -1;

    /** The timeline of the camera frames are rendered from. */
    static final CameraTimeline CAMERA = new CameraTimeline(new Clock() {
        @Override
        public long ticks() {
            return CameraControl.ticks();
        }

        @Override
        public boolean moving() {
            return CameraControl.moving() != null;
        }
    });

    /** One shot of a play. */
    interface Shot {
        /** Starts the shot; the clock tick it ends at, or {@link #WHEN_STILL}. */
        long start(Clock clock) throws CommandException;
    }

    /** Keeps the camera as it is for that many ticks. */
    record Hold(int ticks) implements Shot {
        @Override
        public long start(Clock clock) {
            return clock.ticks() + ticks;
        }
    }

    /** Starts a camera form; its answer is for a script, and the play answered when it started. */
    record Cue(Starter starter) implements Shot {
        @Override
        public long start(Clock clock) throws CommandException {
            starter.start();
            return WHEN_STILL;
        }
    }

    /** Starts a camera form as its command would at that moment, its checks done. */
    @FunctionalInterface
    interface Starter {
        Action start() throws CommandException;
    }

    /** What a play reads of the camera: its clock in ticks, and whether a timed motion is under way. */
    interface Clock {
        long ticks();

        boolean moving();
    }

    private final Clock clock;
    private volatile Play playing;
    private volatile Result failed;

    CameraTimeline(Clock clock) {
        this.clock = clock;
    }

    /** Starts the shots in place of a play under way, the first at once; when that fails, nothing is left playing. */
    void play(List<Shot> shots) throws CommandException {
        Play play = new Play(shots);
        playing = play;
        failed = null;
        try {
            play.next();
        } catch (CommandException | RuntimeException e) {
            end(play);
            throw e;
        }
    }

    /** Whether shots are still to play or under way. */
    boolean playing() {
        return playing != null;
    }

    /** Why the last play ended early, once; null when it played to its end or was stopped. */
    Result takeFailure() {
        Result failure = failed;
        failed = null;
        return failure;
    }

    /** Ends a play, leaving the camera where it is, and forgets why the last one failed. */
    void stop() {
        playing = null;
        failed = null;
    }

    /** Each client tick, after timed motions have ended: starts the next shot once the one under way is done. */
    void tick() {
        Play play = playing;
        if (play == null || !play.shotDone()) {
            return;
        }
        try {
            play.next();
        } catch (CommandException e) {
            fail(play, e.toResult());
        } catch (CommandFailure e) {
            fail(play, e.toResult());
        } catch (RuntimeException e) {
            LOGGER.error("A shot of camera play failed", e);
            fail(play, Result.error("internal", "camera play " + (e.getMessage() != null ? e.getMessage()
                : e.getClass().getSimpleName())));
        }
    }

    private void fail(Play play, Result failure) {
        if (playing == play) {
            failed = failure;
            playing = null;
        }
    }

    private void end(Play play) {
        if (playing == play) {
            playing = null;
        }
    }

    /** The shots of one play and how far it has got. */
    private final class Play {
        private final List<Shot> shots;
        private int index = -1;
        private long until;

        Play(List<Shot> shots) {
            this.shots = shots;
        }

        boolean shotDone() {
            return until == WHEN_STILL ? !clock.moving() : clock.ticks() >= until;
        }

        /** Starts the next shot, unless the play was stopped meanwhile. */
        void next() throws CommandException {
            if (playing != this) {
                return;
            }
            index++;
            if (index >= shots.size()) {
                end(this);
                return;
            }
            until = shots.get(index).start(clock);
        }
    }
}
