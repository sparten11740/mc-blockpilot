package dev.blockpilot.game;

import dev.blockpilot.api.Action;
import dev.blockpilot.api.ArgType;
import dev.blockpilot.api.Args;
import dev.blockpilot.api.CommandContext;
import dev.blockpilot.api.CommandException;
import dev.blockpilot.api.CommandSpec;
import dev.blockpilot.api.Fields;
import dev.blockpilot.api.Numbers;
import dev.blockpilot.api.Result;
import dev.blockpilot.api.Row;
import dev.blockpilot.platform.ClientPlatform;
import dev.blockpilot.protocol.CommandRegistry;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

/**
 * Whole-world snapshots for repeatable tests: the world is saved and left, its folder copied (or restored), and the
 * world joined again. Copying the whole save also captures other mods' data that lives outside the region files.
 */
final class Snapshots {
    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_.-]{1,64}");

    void register(CommandRegistry registry) {
        registry.register(CommandSpec.action("snapshot", this::snapshot).needsServer().group("session")
            .form(form -> form.keyword("op", "save", "restore", "delete").arg("name", ArgType.WORD))
            .form(form -> form.keyword("op", "list"))
            .help("copies of this world's whole save folder: save, restore and delete one by name, or list them; "
                + "save and restore leave the world, copy, and join it again")
            .example("snapshot save base"));
        registry.register(CommandSpec.action("delete", this::delete).group("session")
            .keyword("what", "world").arg("folder", ArgType.TEXT)
            .help("delete world <folder>: removes a test world made by create world (and its snapshots); other "
                + "saves are refused").example("delete world mymod-test"));
    }

    private static Path snapshotsOf(String folder) {
        return ClientPlatform.gameDir().resolve("blockpilot").resolve("snapshots").resolve(folder);
    }

    /** The save folder name of the world the integrated server runs. */
    static String folderOf(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize().getFileName().toString();
    }

    private static String currentFolder() throws CommandException {
        MinecraftServer server = Minecraft.getInstance().getSingleplayerServer();
        if (server == null) {
            throw new CommandException("no_server");
        }
        return folderOf(server);
    }

    private static String checkedName(Args args) throws CommandException {
        String name = args.word("name");
        if (!NAME.matcher(name).matches() || name.startsWith(".")) {
            throw new CommandException("syntax", "snapshot names use letters, digits, _ . - (max 64)");
        }
        return name;
    }

    private Action snapshot(CommandContext context, Args args) throws CommandException {
        String folder = currentFolder();
        Path snapshots = snapshotsOf(folder);
        switch (args.word("op")) {
            case "list" -> {
                List<Row> rows = new ArrayList<>();
                if (Files.isDirectory(snapshots)) {
                    try (Stream<Path> entries = Files.list(snapshots)) {
                        for (Path entry : entries.sorted().toList()) {
                            String created = Files.getLastModifiedTime(entry).toInstant().toString();
                            double megabytes = megabytes(size(entry));
                            rows.add(Row.of(entry.getFileName().toString(), new Fields()
                                .bare("name", entry.getFileName().toString()).bare("created", created)
                                .put("mb", megabytes, Numbers.format(megabytes) + "MB")));
                        }
                    } catch (IOException e) {
                        throw new CommandException("internal", "could not list snapshots: " + e.getMessage());
                    }
                }
                return () -> Result.ok().withRows(rows);
            }
            case "delete" -> {
                Path snapshot = snapshots.resolve(checkedName(args));
                if (!Files.isDirectory(snapshot)) {
                    throw new CommandException("no_snapshot", args.word("name"));
                }
                deleteTree(snapshot);
                return () -> Result.ok();
            }
            case "save" -> {
                return copyAndRejoin(folder, checkedName(args), false);
            }
            default -> {
                String name = checkedName(args);
                if (!Files.isDirectory(snapshots.resolve(name))) {
                    throw new CommandException("no_snapshot", name);
                }
                return copyAndRejoin(folder, name, true);
            }
        }
    }

    /** Leaves the world (saving it), copies one way or the other, and joins it again. */
    private static Action copyAndRejoin(String folder, String name, boolean restore) throws CommandException {
        long started = System.nanoTime();
        // The folder comes from the running server, not from the agent, so any name the game allows is fine.
        Path save = TestWorlds.savesDir().resolve(folder);
        Path snapshot = snapshotsOf(folder).resolve(name);
        SessionCommands.disconnect();
        try {
            if (restore) {
                Path aside = save.resolveSibling(folder + ".blockpilot-restore");
                deleteTree(aside);
                Files.move(save, aside);
                try {
                    copyTree(snapshot, save);
                } catch (IOException e) {
                    deleteTree(save);
                    Files.move(aside, save);
                    throw e;
                }
                deleteTree(aside);
            } else {
                deleteTree(snapshot);
                copyTree(save, snapshot);
            }
        } catch (IOException e) {
            throw new CommandException("internal", (restore ? "restore" : "snapshot") + " failed: " + e.getMessage());
        }
        long bytes = size(restore ? save : snapshot);
        Minecraft.getInstance().createWorldOpenFlows().openWorld(folder, () -> { });
        return () -> {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player == null || minecraft.level == null
                || minecraft.screen != null && Screens.type(minecraft.screen).equals("loading")) {
                return null;
            }
            double seconds = (System.nanoTime() - started) / 1e9;
            return Result.ok(new Fields().put(restore ? "restored" : "saved", name, (restore ? "restored " : "saved ")
                    + name).put("mb", megabytes(bytes), Numbers.format(megabytes(bytes)) + "MB")
                .put("seconds", Math.round(seconds * 10) / 10.0, Numbers.format(Math.round(seconds * 10) / 10.0) + "s"));
        };
    }

    private Action delete(CommandContext context, Args args) throws CommandException {
        String folder = args.text("folder").strip();
        Path save = TestWorlds.checkedFolder(folder);
        if (!Files.isDirectory(save)) {
            throw new CommandException("syntax", "no save folder " + folder);
        }
        if (!TestWorlds.isTestWorld(folder)) {
            throw new CommandException("not_test_world", folder);
        }
        MinecraftServer server = Minecraft.getInstance().getSingleplayerServer();
        if (server != null && folderOf(server).equals(folder)) {
            throw new CommandException("syntax", "leave the world first");
        }
        deleteTree(save);
        deleteTree(snapshotsOf(folder));
        return () -> Result.ok(new Fields().put("deleted", folder, "deleted " + folder));
    }

    private static void copyTree(Path from, Path to) throws IOException {
        Files.walkFileTree(from, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) throws IOException {
                Files.createDirectories(to.resolve(from.relativize(directory).toString()));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                if (!file.getFileName().toString().equals("session.lock")) {
                    Files.copy(file, to.resolve(from.relativize(file).toString()), StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.COPY_ATTRIBUTES);
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

    static void deleteTree(Path root) throws CommandException {
        if (!Files.exists(root)) {
            return;
        }
        try {
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path directory, IOException failure) throws IOException {
                    Files.delete(directory);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            throw new CommandException("internal", "could not delete " + root.getFileName() + ": " + e.getMessage());
        }
    }

    private static long size(Path root) {
        try (Stream<Path> files = Files.walk(root)) {
            return files.filter(Files::isRegularFile).mapToLong(file -> file.toFile().length()).sum();
        } catch (IOException e) {
            return 0;
        }
    }

    private static double megabytes(long bytes) {
        return Math.round(bytes / 1048576.0 * 10) / 10.0;
    }
}
