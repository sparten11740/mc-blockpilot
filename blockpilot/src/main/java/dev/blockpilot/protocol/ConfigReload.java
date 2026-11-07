package dev.blockpilot.protocol;

import com.google.gson.JsonParseException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Rereads the config file when its text changes, without writing it, and hands the settings on; while it can't be
 * read, the last good ones stay. It compares the text, as a modification time can miss two saves within the
 * filesystem's time granularity.
 */
public final class ConfigReload {
    private static final Logger LOGGER = LoggerFactory.getLogger("Blockpilot");

    private final Path file;
    private final Consumer<Config> apply;
    private String seen;
    private String failed;

    public ConfigReload(Path file, Consumer<Config> apply) {
        this.file = file;
        this.apply = apply;
        this.seen = text();
    }

    /** Looks at the file once; called about once a second. */
    public void check() {
        String text = text();
        if (text == null || text.equals(seen) || text.equals(failed)) {
            return;
        }
        try {
            Config read = Config.parse(text);
            seen = text;
            failed = null;
            apply.accept(read);
        } catch (JsonParseException e) {
            failed = text;
            LOGGER.error("Could not read {}; the last settings stay until it is fixed: {}", file, e.getMessage());
        }
    }

    private String text() {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (NoSuchFileException e) {
            return null;
        } catch (IOException e) {
            LOGGER.debug("Could not read {}", file, e);
            return null;
        }
    }
}
