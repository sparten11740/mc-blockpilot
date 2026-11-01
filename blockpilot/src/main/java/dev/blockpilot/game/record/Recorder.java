package dev.blockpilot.game.record;

import dev.blockpilot.Blockpilot;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** The recording, at most one, which outlasts the connection that started it; client thread only. */
public final class Recorder {
    private static final long CLOSE_WAIT_SECONDS = 60;
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

    /** Ends a running recording and waits for its file, so closing the game leaves it saved. */
    public static void onClientStopping() {
        if (recording == null) {
            return;
        }
        Recording closing = recording;
        recording = null;
        closing.stop();
        try {
            Recording.Summary summary = closing.finished().get(CLOSE_WAIT_SECONDS, TimeUnit.SECONDS);
            Blockpilot.LOGGER.info("Saved recording {} as the game closed", summary.file());
        } catch (ExecutionException | TimeoutException e) {
            Blockpilot.LOGGER.warn("Recording {} could not be saved as the game closed: {}", closing.name(),
                message(e));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
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
        Throwable cause = (failed instanceof CompletionException || failed instanceof ExecutionException)
            && failed.getCause() != null ? failed.getCause() : failed;
        return cause.getMessage();
    }
}
