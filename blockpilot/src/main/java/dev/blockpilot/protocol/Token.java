package dev.blockpilot.protocol;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/** The per-install secret every client must present; created on first start, readable only by the owner. */
public final class Token {
    private final String value;
    private final Path file;

    private Token(String value, Path file) {
        this.value = value;
        this.file = file;
    }

    public static Token loadOrCreate(Path file) throws IOException {
        if (Files.exists(file)) {
            String existing = Files.readString(file, StandardCharsets.UTF_8).strip();
            if (!existing.isEmpty()) {
                return new Token(existing, file);
            }
        }
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        String value = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Files.createDirectories(file.getParent());
        Files.deleteIfExists(file);
        try {
            // Created owner-only from the start, so the secret is never readable by others, even briefly.
            Files.createFile(file, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
        } catch (UnsupportedOperationException e) {
            // Not a POSIX file system (Windows); the user's profile directory protects it there.
            Files.createFile(file);
        }
        Files.writeString(file, value + "\n", StandardCharsets.UTF_8);
        return new Token(value, file);
    }

    public String value() {
        return value;
    }

    public Path file() {
        return file;
    }

    /** Constant-time comparison, so timing does not reveal how much of a guess was right. */
    public boolean matches(String candidate) {
        if (candidate == null) {
            return false;
        }
        return MessageDigest.isEqual(value.getBytes(StandardCharsets.UTF_8),
            candidate.strip().getBytes(StandardCharsets.UTF_8));
    }
}
