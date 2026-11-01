package dev.blockpilot.platform.vanilla;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.instrument.Instrumentation;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The agent's entry point, run by {@code -javaagent} before the game's main class. It is compiled for Java 8 so it
 * loads on any version's runtime: on a game other than the one the agent was built for, it says so and leaves the game
 * as it is; on that one, it starts {@code AgentBootstrap}.
 */
public final class BlockpilotAgent {
    private static final String SUPPORTED = "26.1.2";
    private static final Pattern ID = Pattern.compile("\"id\"\\s*:\\s*\"([^\"]+)\"");

    private BlockpilotAgent() {
    }

    public static void premain(String arguments, Instrumentation instrumentation) throws Exception {
        String version = gameVersion();
        if (!SUPPORTED.equals(version)) {
            System.out.println("[Blockpilot] This agent runs on Minecraft " + SUPPORTED + " only, not "
                + (version == null ? "a game without version.json" : version) + "; the game starts without it");
            return;
        }
        Class.forName("dev.blockpilot.platform.vanilla.AgentBootstrap")
            .getMethod("start", Instrumentation.class).invoke(null, instrumentation);
    }

    /** The version the client jar's version.json names, or null. */
    private static String gameVersion() throws IOException {
        InputStream file = ClassLoader.getSystemResourceAsStream("version.json");
        if (file == null) {
            return null;
        }
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            for (int read = file.read(buffer); read != -1; read = file.read(buffer)) {
                bytes.write(buffer, 0, read);
            }
            Matcher id = ID.matcher(new String(bytes.toByteArray(), StandardCharsets.UTF_8));
            return id.find() ? id.group(1) : null;
        } finally {
            file.close();
        }
    }
}
