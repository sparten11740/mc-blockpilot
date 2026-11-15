package dev.blockpilot.game.observe;

import dev.blockpilot.api.Args;
import dev.blockpilot.api.CommandContext;
import dev.blockpilot.api.CommandException;
import dev.blockpilot.api.CommandSpec;
import java.util.List;

/**
 * {@code camera play}: shots played one after another on the camera's clock while scripts go on. Each shot runs its
 * form as the camera command would when it starts, so it sets off from where the camera is drawn then; a move or
 * orbit runs in the background and ends as camera at would, and a hold keeps the camera as it is for its ticks.
 * Another camera command, stop, real input or a reset ends it. Client thread.
 */
final class CameraTimeline {
    /** One shot: a camera form and its arguments, or a hold of {@code hold} ticks when the form is null. */
    record Shot(CommandSpec.ActionHandler form, Args args, int hold) {
        int ticks(int duration) {
            return form == null ? hold : duration;
        }
    }

    private static CameraTimeline playing;
    private static CommandException failed;

    private final List<Shot> shots;
    private final CommandContext context;
    private int index = -1;
    private long holdUntil;

    private CameraTimeline(List<Shot> shots, CommandContext context) {
        this.shots = shots;
        this.context = context;
    }

    /** Starts the shots, ending a play under way; the first starts at once. */
    static void play(List<Shot> shots, CommandContext context) throws CommandException {
        playing = new CameraTimeline(shots, context);
        failed = null;
        playing.next();
    }

    /** Whether shots are still to play or under way. */
    static boolean playing() {
        return playing != null;
    }

    /** Why the last play ended early, once; null when it played to its end or was stopped. */
    static CommandException takeFailure() {
        CommandException failure = failed;
        failed = null;
        return failure;
    }

    /** Ends a play, leaving the camera where it is. */
    static void stop() {
        playing = null;
    }

    /** Each client tick, after timed motions have ended: starts the next shot once the one under way is done. */
    static void tick() {
        CameraTimeline timeline = playing;
        if (timeline == null) {
            return;
        }
        boolean done = timeline.current() == null ? CameraControl.moving() == null
            : CameraControl.ticks() >= timeline.holdUntil;
        if (done) {
            try {
                timeline.next();
            } catch (CommandException e) {
                failed = e;
                playing = null;
            }
        }
    }

    /** The shot under way when it is a hold, else null. */
    private Shot current() {
        Shot shot = index >= 0 && index < shots.size() ? shots.get(index) : null;
        return shot != null && shot.form() == null ? shot : null;
    }

    private void next() throws CommandException {
        index++;
        if (index >= shots.size()) {
            if (playing == this) {
                playing = null;
            }
            return;
        }
        Shot shot = shots.get(index);
        if (shot.form() == null) {
            holdUntil = CameraControl.ticks() + shot.hold();
            return;
        }
        // The form's answer is for a script; the play answered when it started.
        shot.form().start(context, shot.args());
    }
}
