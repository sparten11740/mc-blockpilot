package dev.blockpilot.game.observe;

import dev.blockpilot.api.CommandException;
import dev.blockpilot.api.Duration;
import dev.blockpilot.api.Lexicon;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/** The shots of {@code camera play} as written: separated by {@code |}, each a hold or a step of a camera form. */
final class ShotList {
    /** Reads a shot that names a camera form, its first word, into what plays it. */
    @FunctionalInterface
    interface Reader {
        CameraTimeline.Shot read(String form, String shot) throws CommandException;
    }

    private ShotList() {
    }

    /**
     * The shots in order: {@code hold <duration>}, or a form {@code isForm} accepts, read by the reader. A syntax error
     * names the shot; {@code kinds} says what a shot may be.
     */
    static List<CameraTimeline.Shot> parse(String text, Predicate<String> isForm, String kinds, Reader reader)
        throws CommandException {
        List<CameraTimeline.Shot> shots = new ArrayList<>();
        String[] written = text.strip().split("\\s*\\|\\s*", -1);
        for (int index = 0; index < written.length; index++) {
            String shot = written[index].strip();
            if (shot.isEmpty()) {
                throw new CommandException("syntax", "shot " + (index + 1) + " is empty");
            }
            try {
                shots.add(read(shot, isForm, kinds, reader));
            } catch (CommandException e) {
                throw e.code().equals("syntax")
                    ? new CommandException("syntax", "shot " + (index + 1) + ", " + shot + ": " + e.argument()) : e;
            }
        }
        return shots;
    }

    private static CameraTimeline.Shot read(String shot, Predicate<String> isForm, String kinds, Reader reader)
        throws CommandException {
        String[] tokens = shot.split("\\s+");
        if (tokens[0].equals("hold")) {
            if (tokens.length != 2 || !Lexicon.isDuration(tokens[1])) {
                throw new CommandException("syntax", "hold takes a duration, as hold 40t");
            }
            return new CameraTimeline.Hold(Duration.parse(tokens[1]).ticks());
        }
        if (!isForm.test(tokens[0])) {
            throw new CommandException("syntax", "a shot is " + kinds);
        }
        return reader.read(tokens[0], shot);
    }
}
