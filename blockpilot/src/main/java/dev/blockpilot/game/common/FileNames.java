package dev.blockpilot.game.common;

import dev.blockpilot.api.CommandException;
import java.util.regex.Pattern;

/** Names agents give the files Blockpilot writes for them: captures, recordings and snapshots. */
public final class FileNames {
    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_.+-]{1,64}");

    private FileNames() {
    }

    /** The name, or {@code err syntax} naming it; {@code kind} is what it names, such as {@code capture}. */
    public static String checked(String kind, String name) throws CommandException {
        if (!NAME.matcher(name).matches() || name.startsWith(".")) {
            throw new CommandException("syntax", kind + " names use letters, digits, _ . + - (max 64, not starting "
                + "with .), not " + name);
        }
        return name;
    }
}
