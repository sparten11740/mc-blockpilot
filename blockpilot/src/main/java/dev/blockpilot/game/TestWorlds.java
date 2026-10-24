package dev.blockpilot.game;

import dev.blockpilot.api.Action;
import dev.blockpilot.api.ArgType;
import dev.blockpilot.api.Args;
import dev.blockpilot.api.CommandContext;
import dev.blockpilot.api.CommandException;
import dev.blockpilot.api.CommandSpec;
import dev.blockpilot.api.Fields;
import dev.blockpilot.api.Result;
import dev.blockpilot.platform.ClientPlatform;
import dev.blockpilot.protocol.CommandRegistry;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.levelgen.WorldDimensions;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorPresets;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

/** Disposable singleplayer worlds for tests: created with cheats on, marked so they can be told apart and deleted. */
final class TestWorlds {
    static final String MARKER = "blockpilot-test.json";
    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9 _.-]{1,64}");

    void register(CommandRegistry registry) {
        registry.register(CommandSpec.action("create", this::create).group("session")
            .choice("what", false, "world").arg("spec", ArgType.TEXT)
            .help("create world <name> [flat|void|normal] [peaceful|easy|hard|difficulty=normal] "
                + "[mode=creative|survival] [seed=<n>] [rule:<gamerule>=<value>…]: a new world with cheats on, joined "
                + "when ready (defaults flat, peaceful, creative; 'normal' alone means the normal world type)")
            .example("create world aerolarge-test flat peaceful rule:doDaylightCycle=false"));
    }

    static Path savesDir() {
        return ClientPlatform.gameDir().resolve("saves");
    }

    static boolean exists(String folder) {
        return Files.isDirectory(savesDir().resolve(folder).resolve("region"))
            || Files.exists(savesDir().resolve(folder).resolve("level.dat"));
    }

    static boolean isTestWorld(String folder) {
        return Files.exists(savesDir().resolve(folder).resolve(MARKER));
    }

    /** Checks an agent-supplied world name: allowed characters, no {@code ..}, stays inside the saves folder. */
    static Path checkedFolder(String name) throws CommandException {
        if (!NAME.matcher(name).matches() || name.equals(".") || name.equals("..") || name.startsWith(".")) {
            throw new CommandException("syntax", "world names use letters, digits, space, _ . - (max 64)");
        }
        Path saves = savesDir().toAbsolutePath().normalize();
        Path folder = saves.resolve(name).normalize();
        if (!folder.getParent().equals(saves)) {
            throw new CommandException("syntax", "invalid world name " + name);
        }
        return folder;
    }

    private Action create(CommandContext context, Args args) throws CommandException {
        return create(args.text("spec"));
    }

    /** Creates and joins a world from a {@code create world} spec; also used by auto-join on launch. */
    static Action create(String spec) throws CommandException {
        List<String> tokens = new ArrayList<>(List.of(spec.strip().split("\\s+")));
        String name = tokens.remove(0);
        Path folder = checkedFolder(name);
        if (Files.exists(folder)) {
            throw new CommandException("exists", name);
        }
        String preset = "flat";
        Difficulty difficulty = Difficulty.PEACEFUL;
        GameType mode = GameType.CREATIVE;
        Long seed = null;
        Map<String, String> rules = new LinkedHashMap<>();
        for (String token : tokens) {
            switch (token) {
                case "flat", "void", "normal" -> preset = token;
                case "peaceful", "easy", "hard" -> difficulty = Difficulty.byName(token);
                default -> {
                    if (token.startsWith("difficulty=")) {
                        difficulty = Difficulty.byName(token.substring("difficulty=".length()));
                        if (difficulty == null) {
                            throw new CommandException("syntax", "difficulty=peaceful|easy|normal|hard");
                        }
                    } else if (token.startsWith("mode=")) {
                        mode = GameType.byName(token.substring(5), null);
                        if (mode == null) {
                            throw new CommandException("syntax", "mode=creative|survival|adventure|spectator");
                        }
                    } else if (token.startsWith("seed=")) {
                        try {
                            seed = Long.parseLong(token.substring(5));
                        } catch (NumberFormatException e) {
                            seed = (long) token.substring(5).hashCode();
                        }
                    } else if (token.startsWith("rule:") && token.contains("=")) {
                        rules.put(token.substring(5, token.indexOf('=')), token.substring(token.indexOf('=') + 1));
                    } else {
                        throw new CommandException("syntax", "unexpected " + token);
                    }
                }
            }
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null) {
            SessionCommands.disconnect();
        }
        LevelSettings settings = new LevelSettings(name, mode, false, difficulty, true, new GameRules(),
            WorldDataConfiguration.DEFAULT);
        WorldOptions options = new WorldOptions(seed == null ? WorldOptions.randomSeed() : seed, false, false);
        String chosen = preset;
        minecraft.createWorldOpenFlows().createFreshLevel(name, settings, options, registries -> {
            if (chosen.equals("normal")) {
                return registries.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.NORMAL)
                    .value().createWorldDimensions();
            }
            WorldDimensions flat = registries.registryOrThrow(Registries.WORLD_PRESET)
                .getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions();
            if (chosen.equals("void")) {
                var voidSettings = registries.registryOrThrow(Registries.FLAT_LEVEL_GENERATOR_PRESET)
                    .getHolderOrThrow(FlatLevelGeneratorPresets.THE_VOID).value().settings();
                return flat.replaceOverworldGenerator(registries, new FlatLevelSource(voidSettings));
            }
            return flat;
        }, new TitleScreen());
        try {
            Files.createDirectories(folder);
            Files.writeString(folder.resolve(MARKER), "{\"created\":\"" + Instant.now() + "\",\"by\":\""
                + ClientPlatform.modVersion() + "\"}");
        } catch (IOException e) {
            throw new CommandException("internal", "could not mark the test world: " + e.getMessage());
        }
        return new Action() {
            private boolean rulesSent;

            @Override
            public Result tick() throws CommandException {
                var player = minecraft.player;
                var server = minecraft.getSingleplayerServer();
                if (player == null || server == null || minecraft.screen != null
                    && Screens.type(minecraft.screen).equals("loading")) {
                    return null;
                }
                if (!rulesSent) {
                    rulesSent = true;
                    for (Map.Entry<String, String> rule : rules.entrySet()) {
                        String command = "gamerule " + rule.getKey() + " " + rule.getValue();
                        server.execute(() -> server.getCommands().performPrefixedCommand(
                            server.createCommandSourceStack().withSuppressedOutput(), command));
                    }
                }
                return Result.ok(new Fields().put("joined", name, "joined " + name));
            }

            @Override
            public String label() {
                return "create";
            }
        };
    }
}
