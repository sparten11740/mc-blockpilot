package dev.blockpilot.game.sable;

import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Whether Blockpilot works with Sable's sub-levels, and why not when it doesn't. It starts off, and {@link SableSupport}
 * turns it on once the compat package has loaded.
 */
public final class SableIntegration {
    /** The companion API the compat package compiles against; releases within a major only add to it. */
    static final int MAJOR = 1;
    static final int MINOR = 6;
    private static final Pattern VERSION = Pattern.compile("(\\d{1,9})\\.(\\d{1,9})");

    private final Consumer<String> switchedOff;
    private volatile SubLevels subLevels = SubLevels.NONE;
    private volatile String status = "off: not started";

    /** {@code switchedOff} gets the status each time the integration turns off. */
    public SableIntegration(Consumer<String> switchedOff) {
        this.switchedOff = switchedOff;
    }

    /** Null when the compat package supports this companion version, else why not. */
    static String versionProblem(String version) {
        if (version == null) {
            return "no Sable companion found";
        }
        Matcher matcher = VERSION.matcher(version);
        if (matcher.lookingAt() && Integer.parseInt(matcher.group(1)) == MAJOR
            && Integer.parseInt(matcher.group(2)) >= MINOR) {
            return null;
        }
        return "Sable companion " + version + " found, Blockpilot supports " + MAJOR + ".x from " + MAJOR + "." + MINOR;
    }

    public SubLevels subLevels() {
        return subLevels;
    }

    public String status() {
        return status;
    }

    synchronized void on(SubLevels implementation, String version) {
        subLevels = implementation;
        status = "on: Sable companion " + version;
    }

    synchronized void off(String why) {
        subLevels = SubLevels.NONE;
        status = "off: " + why;
        switchedOff.accept(status);
    }
}
