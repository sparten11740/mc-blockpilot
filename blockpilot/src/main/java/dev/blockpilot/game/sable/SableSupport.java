package dev.blockpilot.game.sable;

import dev.blockpilot.Blockpilot;
import dev.blockpilot.platform.ClientPlatform;
import dev.blockpilot.protocol.CommandRegistry;
import java.lang.reflect.InvocationTargetException;

/**
 * Turns the Sable integration on when a compatible Sable is installed. The compat package is loaded by name only after
 * these checks pass, so without Sable none of Sable's classes is ever loaded.
 */
public final class SableSupport {
    public static final String NAMESPACE = "sable";
    private static final String COMPAT = "dev.blockpilot.compat.sable.SableCompat";
    /** Overrides the companion version found, to check the unsupported path without another Sable build. */
    private static final String VERSION_PROPERTY = "blockpilot.sable.version";
    private static volatile SableIntegration integration = new SableIntegration(status -> {
    });

    private SableSupport() {
    }

    /** What the rest of Blockpilot asks about sub-levels; it knows none while the integration is off. */
    public static SubLevels subLevels() {
        return integration.subLevels();
    }

    /** Must run before the extension registrations freeze, so the compat package can register its commands. */
    public static void start(CommandRegistry registry) {
        SableIntegration started = new SableIntegration(status -> registry.disable(NAMESPACE, status));
        integration = started;
        if (!ClientPlatform.isModLoaded("sable")) {
            started.off("Sable is not installed");
            return;
        }
        String version = System.getProperty(VERSION_PROPERTY, ClientPlatform.versionOf("sablecompanion"));
        String problem = SableIntegration.versionProblem(version);
        if (problem != null) {
            started.off(problem);
            Blockpilot.LOGGER.warn("Sable integration {}", started.status());
            return;
        }
        try {
            SubLevels compat = Class.forName(COMPAT).asSubclass(SubLevels.class)
                .getConstructor(SableIntegration.class).newInstance(started);
            started.on(compat, version);
            SableCommands.register();
            Blockpilot.LOGGER.info("Sable integration {}", started.status());
        } catch (LinkageError | ReflectiveOperationException | RuntimeException e) {
            Throwable cause = e instanceof InvocationTargetException thrown ? thrown.getCause() : e;
            started.off("Sable companion " + version + " did not load: " + cause);
            Blockpilot.LOGGER.warn("Sable integration {}", started.status(), cause);
        }
    }
}
