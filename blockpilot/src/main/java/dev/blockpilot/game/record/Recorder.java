package dev.blockpilot.game.record;

import java.util.concurrent.CompletionException;

/** The recording, at most one, which outlasts the connection that started it; client thread only. */
public final class Recorder {
    private static Recording recording;
    /** An encoder failure that neither record status nor record stop has reported yet. */
    private static String failure;

    private Recorder() {
    }

    /** From record start until the recording stops taking frames. */
    public static boolean active() {
        return recording != null && !recording.stopping();
    }

    /** At the start of each frame, when the one before has been rendered. */
    public static void onFrame() {
        if (recording == null) {
            return;
        }
        if (recording.stopping()) {
            // Finished while no record stop waited for it, e.g. one that timed out.
            if (recording.finished().isDone()) {
                failure = recording.finished().isCompletedExceptionally() ? recording.failure() : null;
                recording = null;
            }
            return;
        }
        recording.frame();
        if (recording.failure() != null) {
            failure = recording.failure();
            recording.abandon();
            recording = null;
        }
    }

    static Recording recording() {
        return recording;
    }

    static void started(Recording started) {
        recording = started;
        failure = null;
    }

    /** The recording record stop ended has been reported; client thread. */
    static void reported(Recording reported) {
        if (recording == reported) {
            recording = null;
        }
    }

    static String failure() {
        return failure;
    }

    static String takeFailure() {
        String taken = failure;
        failure = null;
        return taken;
    }

    static String message(Throwable failed) {
        Throwable cause = failed instanceof CompletionException && failed.getCause() != null ? failed.getCause()
            : failed;
        return cause.getMessage();
    }
}
