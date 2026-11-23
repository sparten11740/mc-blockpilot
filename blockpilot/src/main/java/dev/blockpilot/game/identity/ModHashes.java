package dev.blockpilot.game.identity;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The SHA-256 of each mod file as it was when the game loaded it, taken once in the background, with the file's size
 * and modification time then, so a file replaced since can be told apart.
 */
public final class ModHashes {
    private static final Logger LOGGER = LoggerFactory.getLogger(ModHashes.class);

    /** What a file was when it was hashed. */
    record Noted(long size, FileTime modified, String sha256) {
    }

    private final Map<Path, Noted> noted = new ConcurrentHashMap<>();
    private final AtomicInteger left = new AtomicInteger();

    /** Hashes the files on a daemon thread, in order; files already noted are skipped. */
    public void start(List<Path> files) {
        List<Path> fresh = files.stream().distinct().filter(file -> !noted.containsKey(file)).toList();
        left.addAndGet(fresh.size());
        Thread thread = new Thread(() -> fresh.forEach(this::hash), "Blockpilot mod hashes");
        thread.setDaemon(true);
        thread.start();
    }

    void hash(Path file) {
        try {
            long size = Files.size(file);
            FileTime modified = Files.getLastModifiedTime(file);
            noted.put(file, new Noted(size, modified, sha256(file)));
        } catch (IOException e) {
            LOGGER.warn("Could not hash {}", file, e);
        } finally {
            left.decrementAndGet();
        }
    }

    /** How many files are still to hash. */
    public int left() {
        return left.get();
    }

    /** The file's hash as loaded, or null while it isn't taken yet or couldn't be. */
    public String sha256Of(Path file) {
        Noted note = noted.get(file);
        return note == null ? null : note.sha256();
    }

    /** True when the file's size or modification time now differs from when it was hashed, or it is gone. */
    public boolean changed(Path file) {
        Noted note = noted.get(file);
        if (note == null) {
            return false;
        }
        try {
            return Files.size(file) != note.size() || !Objects.equals(Files.getLastModifiedTime(file), note.modified());
        } catch (IOException e) {
            return true;
        }
    }

    static String sha256(Path file) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("every Java runtime has SHA-256", e);
        }
        byte[] buffer = new byte[1 << 16];
        try (InputStream in = Files.newInputStream(file)) {
            for (int read = in.read(buffer); read >= 0; read = in.read(buffer)) {
                digest.update(buffer, 0, read);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }
}
