package dev.blockpilot.protocol;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** {@code config/blockpilot/config.json}; missing or unreadable values fall back to the defaults below. */
public final class Config {
    private static final Logger LOGGER = LoggerFactory.getLogger("Blockpilot");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public int port = 25599;
    /** How many ports from {@code port} on to try when it is taken (several game instances on one machine). */
    public int portTries = 10;
    /** Loopback by default; anything else exposes the endpoint to the network. */
    public String bind = "127.0.0.1";
    /** Extra block ids or id patterns ({@code *_ore}) that {@code look} lists besides blocks with block entities. */
    public List<String> notableBlocks = new ArrayList<>(List.of("*_ore", "ancient_debris", "water", "lava",
        "crafting_table", "*_bed", "*_door", "ladder", "*_log", "spawner", "nether_portal", "end_portal_frame"));
    /** Upper bound on rows in list results such as {@code look}. */
    public int maxRows = 24;
    /** The ffmpeg {@code record} encodes with; empty: the first on the PATH or in the platform's usual folders. */
    public String ffmpeg = "";

    public static Config load(Path file) {
        Config config = new Config();
        try {
            if (Files.exists(file)) {
                Config read = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), Config.class);
                if (read != null) {
                    config = read;
                }
            }
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(config), StandardCharsets.UTF_8);
        } catch (IOException | JsonParseException e) {
            LOGGER.warn("Could not read {}; using defaults", file, e);
        }
        return config;
    }
}
