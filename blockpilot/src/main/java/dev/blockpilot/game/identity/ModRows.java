package dev.blockpilot.game.identity;

import dev.blockpilot.api.Fields;
import dev.blockpilot.api.Row;
import dev.blockpilot.platform.ClientPlatform.LoadedMod;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

/** The rows of {@code mods} and the entries of {@code endpoint.json}: each mod with its file, folder or carrier. */
final class ModRows {
    private ModRows() {
    }

    /** The mods whose id the filter keeps, ordered by id, each with its file's hash as loaded. */
    static List<Row> rows(List<LoadedMod> mods, Predicate<String> keep, Path gameDir, ModHashes hashes) {
        return mods.stream().filter(mod -> keep.test(mod.id())).sorted(Comparator.comparing(LoadedMod::id))
            .map(mod -> Row.of(mod.id(), fields(mod, gameDir, hashes))).toList();
    }

    static Fields fields(LoadedMod mod, Path gameDir, ModHashes hashes) {
        Fields fields = new Fields().bare("id", mod.id()).bare("version", mod.version());
        Path file = file(mod);
        if (mod.nested()) {
            fields.put("nested", true, "nested").put("in", mod.parent());
        } else if (file != null) {
            fields.put("file", shown(file, gameDir));
            if (hashes != null) {
                fields.put("sha256", hashes.sha256Of(file));
                if (hashes.changed(file)) {
                    fields.put("changed", true, "changed");
                }
            }
        } else if (!mod.paths().isEmpty()) {
            fields.put("folder", shown(mod.paths().get(0), gameDir));
        }
        return fields;
    }

    /** The mod's own jar, or null when it has none: nested, loaded from folders, or built into the game. */
    static Path file(LoadedMod mod) {
        return mod.nested() || mod.paths().isEmpty() || !Files.isRegularFile(mod.paths().get(0)) ? null
            : mod.paths().get(0);
    }

    /** Relative to the game directory when inside it, else absolute. */
    static String shown(Path path, Path gameDir) {
        Path absolute = path.toAbsolutePath().normalize();
        Path base = gameDir.toAbsolutePath().normalize();
        return (absolute.startsWith(base) ? base.relativize(absolute) : absolute).toString().replace('\\', '/');
    }
}
