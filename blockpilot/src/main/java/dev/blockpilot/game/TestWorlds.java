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
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
//? if <26.1 {
import net.minecraft.world.level.GameRules;
//?}
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
    static final List<String> PRESETS = List.of("flat", "void", "normal");

    void register(CommandRegistry registry) {
        registry.register(CommandSpec.action("create", this::create).group("session")
            .form(form -> form.keyword("what", "world").arg("name", ArgType.WORD)
                .optionalKeyword("preset", PRESETS.toArray(String[]::new))
                .optionalKeyword("difficulty", "peaceful", "easy", "hard"))
            .option("difficulty", "the difficulty; written alone, normal is the world type",
                List.of("peaceful", "easy", "normal", "hard"))
            .option("mode", "the game mode (default creative)",
                List.of("creative", "survival", "adventure", "spectator")).option("seed", "the world seed")
            .option("rule:*", "a game rule, e.g. rule:doDaylightCycle=false")
            .help("a new world with cheats on, joined when ready (defaults flat, peaceful, creative)")
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
        String named = args.option("difficulty") != null ? args.option("difficulty") : args.word("difficulty");
        Difficulty difficulty = named == null ? Difficulty.PEACEFUL : Difficulty.byName(named);
        if (difficulty == null) {
            throw new CommandException("syntax", "difficulty= takes peaceful, easy, normal or hard, not " + named);
        }
        GameType mode = GameType.byName(args.option("mode") == null ? "creative" : args.option("mode"), null);
        if (mode == null) {
            throw new CommandException("syntax", "mode= takes creative, survival, adventure or spectator, not "
                + args.option("mode"));
        }
        return create(args.word("name"), args.has("preset") ? args.word("preset") : "flat", difficulty, mode,
            seed(args.option("seed")), args.family("rule"));
    }

    /** A seed that isn't a number becomes one, as in the vanilla create world screen; null means random. */
    private static Long seed(String text) {
        if (text == null) {
            return null;
        }
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException e) {
            return (long) text.hashCode();
        }
    }

    /** A flat, peaceful creative test world; used by auto-join on launch. */
    static Action create(String name, String preset) throws CommandException {
        if (!PRESETS.contains(preset)) {
            throw new CommandException("syntax", "world presets are " + String.join(", ", PRESETS) + ", not "
                + preset);
        }
        return create(name, preset, Difficulty.PEACEFUL, GameType.CREATIVE, null, Map.of());
    }

    /** Creates and joins a world; {@code seed} null is random, {@code rules} are game rules to set once in. */
    private static Action create(String name, String preset, Difficulty difficulty, GameType mode, Long seed,
        Map<String, String> rules) throws CommandException {
        Path folder = checkedFolder(name);
        if (Files.exists(folder)) {
            throw new CommandException("exists", name);
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null) {
            SessionCommands.disconnect();
        }
        //? if >=26.1 {
        /*LevelSettings settings = new LevelSettings(name, mode,
            new LevelSettings.DifficultySettings(difficulty, false, false), true, WorldDataConfiguration.DEFAULT);
        *///?} else {
        LevelSettings settings = new LevelSettings(name, mode, false, difficulty, true, new GameRules(),
            WorldDataConfiguration.DEFAULT);
        //?}
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
