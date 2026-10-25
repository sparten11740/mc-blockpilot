package dev.blockpilot.game.sable;

import dev.blockpilot.Blockpilot;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Whether Blockpilot works with Sable's sub-levels, and why not when it doesn't. It starts off, {@link SableSupport}
 * turns it on once the compat package has loaded, and the first call into Sable that doesn't link turns it off until
 * restart.
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
        switchOff("off: " + why);
    }

    /** Sable's API is not what the compat package was compiled against, so retrying would fail the same way. */
    public void failed(String call, LinkageError error) {
        synchronized (this) {
            if (subLevels == SubLevels.NONE) {
                return;
            }
            switchOff("off until restart: " + call + " failed with " + error);
        }
        Blockpilot.LOGGER.error("Sable integration switched off: {} failed", call, error);
    }

    private void switchOff(String newStatus) {
        subLevels = SubLevels.NONE;
        status = newStatus;
        switchedOff.accept(status);
    }
}
