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
import java.util.stream.Collectors;
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
    /** Players whose chat reaches agents with its text, by name ignoring case; nobody else's does. */
    public List<String> trustedPlayers = new ArrayList<>();
    /** {@code all}, or {@code play} to hold agents to playing; any other value counts as {@code play}. */
    public String agentCommands = "all";
    /** The namespaces of other mods' commands agents may use; Blockpilot's own need none. */
    public List<String> allowedExtensions = new ArrayList<>();
    /** Dangerous: true turns every agent safety check off (trusted players, allowedExtensions, agentCommands). */
    public boolean dangerouslyAllowAll = false;

    /**
     * Reads the file and writes it back with the defaults of missing keys. A file that is there but can't be read is
     * left as it is, and the safety settings fail closed until it is fixed: agents held to play, nobody trusted.
     */
    public static Config load(Path file) {
        Config config = new Config();
        if (Files.exists(file)) {
            try {
                config = parse(Files.readString(file, StandardCharsets.UTF_8));
            } catch (IOException | JsonParseException e) {
                LOGGER.error("Could not read {}; agents are held to play and nobody is trusted until it is fixed",
                    file, e);
                Config closed = new Config();
                closed.agentCommands = "play";
                return closed;
            }
        }
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(config), StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.warn("Could not write {}", file, e);
        }
        return config;
    }

    /** The settings in a config file's text, without writing anything. */
    static Config parse(String text) {
        Config config = GSON.fromJson(text, Config.class);
        if (config == null) {
            config = new Config();
        }
        config.trustedPlayers = names(config.trustedPlayers);
        config.allowedExtensions = names(config.allowedExtensions);
        if (config.agentCommands == null) {
            config.agentCommands = "all";
        }
        return config;
    }

    /** A list from the file without the null a trailing comma reads as, or blanks. */
    private static List<String> names(List<String> written) {
        return written == null ? new ArrayList<>() : written.stream().filter(name -> name != null && !name.isBlank())
            .collect(Collectors.toCollection(ArrayList::new));
    }

}
