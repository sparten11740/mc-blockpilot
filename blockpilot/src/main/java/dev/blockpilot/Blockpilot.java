package dev.blockpilot;

import dev.blockpilot.platform.ClientPlatform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** The mod's identity and its loader-independent start-up. */
public final class Blockpilot {
    public static final String MOD_ID = "blockpilot";
    public static final Logger LOGGER = LoggerFactory.getLogger("Blockpilot");

    private Blockpilot() {
    }

    /** Called by the loader's client entry point once the platform is installed. */
    public static void init() {
        LOGGER.info("Blockpilot {} on {}", ClientPlatform.modVersion(), ClientPlatform.loaderName());
    }
}
