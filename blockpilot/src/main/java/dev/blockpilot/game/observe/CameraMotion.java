package dev.blockpilot.game.observe;

import dev.blockpilot.api.Action;
import dev.blockpilot.api.CommandException;
import dev.blockpilot.api.Result;
import dev.blockpilot.protocol.Engine;
import java.util.function.Function;
import net.minecraft.client.Minecraft;

/**
 * A timed camera motion, whatever its shape: it counts {@link CameraControl}'s client ticks, so one in the background,
 * which no action ticks, advances the same way and CameraControl places its end. In the foreground it answers as
 * {@code camera at} once its time is up; {@code stop} and real input leave the camera where the last frame drew it.
 * Along the way it glides the lens from the one in use to the field of view and roll the command gives.
 */
abstract class CameraMotion implements Action, Engine.SelfTimed {
    private final int duration;
    private final CameraPath.Ease ease;
    private final boolean background;
    /** The reply once the camera rests at the end, as {@code camera at} gives it. */
    private final Function<CameraControl.Placement, Action> answer;
    /** CameraControl's client tick count when the motion began. */
    private final long start;
    private final CameraControl.Lens fromLens;
    private final CameraControl.Lens toLens;
    private Action ending;

    /** {@code fov}, {@code roll}: the lens to glide to, null keeping the value in use. */
    CameraMotion(int duration, CameraPath.Ease ease, boolean background, Double fov, Double roll,
        Function<CameraControl.Placement, Action> answer) {
        this.duration = duration;
        this.ease = ease;
        this.background = background;
        this.answer = answer;
        start = CameraControl.ticks();
        fromLens = startLens(CameraControl.lens(), fov, Minecraft.getInstance().options.fov().get());
        toLens = fromLens.with(fov, roll);
    }

    /** The lens a glide starts from: the one in use, its field of view the player's value now when it has none. */
    static CameraControl.Lens startLens(CameraControl.Lens now, Double fov, double playerFov) {
        return now.fov() == null && fov != null ? new CameraControl.Lens(playerFov, now.roll()) : now;
    }

    /** Where the camera rests after the motion, as {@code camera at} would place it, before its lens. */
    abstract CameraControl.Placement endShape();

    /** The placement that moves frame by frame, before its lens. */
    abstract CameraControl.Placement movingShape();

    /** A still placement where the motion has got to, before its lens. */
    abstract CameraControl.Placement stillShape();

    /** Where the camera rests after the motion, with the lens glided to. */
    final CameraControl.Placement end() {
        return endShape().withLens(() -> toLens);
    }

    /** The placement that moves frame by frame, gliding the lens. */
    final CameraControl.Placement placement() {
        return movingShape().withLens(() -> lensAt(eased(progress())));
    }

    /** A still placement where the motion has got to, for a stop before any frame was drawn from it. */
    final CameraControl.Placement stillNow() {
        CameraControl.Lens now = lensAt(eased(progress()));
        return stillShape().withLens(() -> now);
    }

    private CameraControl.Lens lensAt(double share) {
        Double fov = toLens.fov() == null ? null : CameraPath.between(fromLens.fov(), toLens.fov(), share);
        return new CameraControl.Lens(fov, CameraPath.angleBetween(fromLens.roll(), toLens.roll(), share));
    }

    boolean background() {
        return background;
    }

    /** Whether its time is up: on the client tick its duration has passed. */
    boolean finished() {
        return CameraControl.ticks() - start >= duration;
    }

    /** How far through its time the motion is for the frame being drawn, 0 to 1, before easing. */
    final double progress() {
        return Math.min(1, (CameraControl.ticks() - start + CameraControl.realPartialTick()) / duration);
    }

    /** The progress shaped by the motion's easing. */
    final double eased(double progress) {
        return ease.apply(progress);
    }

    @Override
    public final Result tick() throws CommandException {
        if (ending != null) {
            return ending.tick();
        }
        if (!finished()) {
            return null;
        }
        CameraControl.Placement end = end();
        CameraControl.place(end);
        ending = answer.apply(end);
        return ending.tick();
    }

    /** Ended early by stop or real input: the camera stays where the last frame drew it. */
    @Override
    public final void cancel() {
        CameraControl.Placement fixed = CameraControl.fixedAtLastFrame();
        CameraControl.hold(fixed != null ? fixed : stillNow());
    }

    @Override
    public String label() {
        return "camera";
    }
}
