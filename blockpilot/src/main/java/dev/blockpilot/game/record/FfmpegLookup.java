package dev.blockpilot.game.record;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Where ffmpeg is: at the configured path when one is set, else the first on the PATH or in the platform's usual
 * install folders. A game started from a launcher gets a bare PATH (on macOS without Homebrew's folder), so the PATH
 * alone often misses it.
 */
final class FfmpegLookup {
    enum Platform {
        MAC, LINUX, WINDOWS;

        static Platform current() {
            String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
            return os.startsWith("windows") ? WINDOWS : os.startsWith("mac") ? MAC : LINUX;
        }

        private String separator() {
            return this == WINDOWS ? "\\" : "/";
        }

        private String executable() {
            return this == WINDOWS ? "ffmpeg.exe" : "ffmpeg";
        }
    }

    /** {@code ffmpeg} is null when none was found; {@code searched} names each place looked in, in order. */
    record Found(String ffmpeg, List<String> searched) {
    }

    private FfmpegLookup() {
    }

    static Found find(String configured, Platform platform, Map<String, String> environment,
        Predicate<String> isFile) {
        if (configured != null && !configured.isBlank()) {
            return new Found(isFile.test(configured) ? configured : null, List.of(configured));
        }
        Set<String> folders = new LinkedHashSet<>();
        String path = variable(environment, "PATH");
        if (path != null) {
            for (String folder : path.split(platform == Platform.WINDOWS ? ";" : ":")) {
                if (!folder.isBlank()) {
                    folders.add(folder);
                }
            }
        }
        folders.addAll(usualFolders(platform, environment));
        List<String> searched = new ArrayList<>();
        for (String folder : folders) {
            searched.add(folder);
            String candidate = folder.endsWith(platform.separator()) ? folder + platform.executable()
                : folder + platform.separator() + platform.executable();
            if (isFile.test(candidate)) {
                return new Found(candidate, searched);
            }
        }
        return new Found(null, searched);
    }

    private static List<String> usualFolders(Platform platform, Map<String, String> environment) {
        return switch (platform) {
            case MAC -> List.of("/opt/homebrew/bin", "/usr/local/bin", "/opt/local/bin");
            case LINUX -> List.of("/usr/bin", "/usr/local/bin", "/snap/bin");
            case WINDOWS -> {
                List<String> folders = new ArrayList<>();
                addUnder(folders, variable(environment, "LOCALAPPDATA"), "Microsoft\\WinGet\\Links");
                addUnder(folders, variable(environment, "USERPROFILE"), "scoop\\shims");
                addUnder(folders, variable(environment, "ProgramData"), "chocolatey\\bin");
                folders.add("C:\\ffmpeg\\bin");
                yield folders;
            }
        };
    }

    private static void addUnder(List<String> folders, String root, String folder) {
        if (root != null && !root.isBlank()) {
            folders.add(root + "\\" + folder);
        }
    }

    /** Windows names its variables in any case ({@code Path}, {@code PATH}). */
    private static String variable(Map<String, String> environment, String name) {
        for (Map.Entry<String, String> entry : environment.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(name)) {
                return entry.getValue();
            }
        }
        return null;
    }
}
