package dev.blockpilot.game;

import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.InputConstants;
import dev.blockpilot.Blockpilot;
import dev.blockpilot.api.BlockpilotApi;
import dev.blockpilot.api.CommandException;
import dev.blockpilot.api.CommandSpec;
import dev.blockpilot.api.Event;
import dev.blockpilot.api.Fields;
import dev.blockpilot.api.Position;
import dev.blockpilot.api.Result;
import dev.blockpilot.cli.Launcher;
import dev.blockpilot.game.common.ClientScreens;
import dev.blockpilot.game.common.GameClock;
import dev.blockpilot.game.common.Sides;
import dev.blockpilot.game.input.Focus;
import dev.blockpilot.game.input.HumanInput;
import dev.blockpilot.game.input.InputState;
import dev.blockpilot.game.load.Load;
import dev.blockpilot.game.log.LogCapture;
import dev.blockpilot.game.observe.AwayCamera;
import dev.blockpilot.game.observe.CameraControl;
import dev.blockpilot.game.observe.ClientOptions;
import dev.blockpilot.game.observe.EntityUpdates;
import dev.blockpilot.game.observe.HudVisibility;
import dev.blockpilot.game.observe.Observations;
import dev.blockpilot.game.observe.Screenshots;
import dev.blockpilot.game.perf.Perf;
import dev.blockpilot.game.record.Recorder;
import dev.blockpilot.game.sable.SableSupport;
import dev.blockpilot.game.safety.TrustTracker;
import dev.blockpilot.platform.ClientPlatform;
import dev.blockpilot.protocol.CommandRegistry;
import dev.blockpilot.protocol.Config;
import dev.blockpilot.protocol.ConfigReload;
import dev.blockpilot.protocol.Endpoint;
import dev.blockpilot.protocol.Engine;
import dev.blockpilot.protocol.GameBridge;
import dev.blockpilot.protocol.McpServer;
import dev.blockpilot.protocol.Token;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
//? if >=26.1 {
/*import net.minecraft.resources.ResourceLocation;
*///?}
import net.minecraft.server.MinecraftServer;

/**
 * Brings Blockpilot up in the client: once mod loading completes it builds the command table (built-in commands
 * plus extensions), starts the endpoint and writes {@code blockpilot/endpoint.json}; every tick it drives the
 * engine, the held input and event detection.
 */
public final class BlockpilotClient implements ClientPlatform.Hooks, GameBridge {
    private static BlockpilotClient instance;

    /** The player's switch for agent control; agents can't press it (see InputCommands). */
    private final KeyMapping toggleKey = new KeyMapping("key.blockpilot.toggle", InputConstants.KEY_F8,
        //? if >=26.1 {
        /*new KeyMapping.Category(ResourceLocation.fromNamespaceAndPath(Blockpilot.MOD_ID, Blockpilot.MOD_ID)));
        *///?} else {
        "key.categories.blockpilot");
        //?}
    // Set up on the loader's load-complete thread, then used on the client and server threads.
    private volatile Config config;
    private volatile Engine engine;
    private volatile Endpoint endpoint;
    private volatile Path endpointFile;
    private volatile Events events;
    private volatile TrustTracker trust;
    private ConfigReload configReload;
    private volatile boolean enabled = true;
    private long ticks;
    private boolean autoJoined;
    private boolean wasInWorld;
    /** Ticks until the game closes after {@code quit}, or -1. */
    private int closeIn = -1;
    private final AgentPresence presence = new AgentPresence();
    /** The overworld's game time at the end of the last server tick, on the server thread. */
    private long lastServerGameTick;
    /** Chat lines since the end of the last client tick, which a stalled client reports. */
    private final AtomicLong chatSinceTick = new AtomicLong();

    private BlockpilotClient() {
    }

    /** Called from the loader's entry point while the mod is constructed. */
    public static void init() {
        instance = new BlockpilotClient();
        ClientPlatform.registerKeyMapping(instance.toggleKey);
        ClientPlatform.setHooks(instance);
    }

    public static BlockpilotClient get() {
        return instance;
    }

    public Engine engine() {
        return engine;
    }

    public Config config() {
        return config;
    }

    public TrustTracker trust() {
        return trust;
    }

    KeyMapping toggleKey() {
        return toggleKey;
    }

    @Override
    public void onLoadComplete() {
        Path directory = ClientPlatform.configDir().resolve(Blockpilot.MOD_ID);
        config = Config.load(directory.resolve("config.json"));
        trust = new TrustTracker(config);
        configReload = new ConfigReload(directory.resolve("config.json"), this::applySafetySettings);
        CommandRegistry registry = new CommandRegistry();
        engine = new Engine(registry, this);
        engine.setPlayOnly(config.playOnly());
        engine.startWatchdog();
        events = new Events(engine);
        Observations.setEventSink(engine::broadcast);
        LogCapture.setSink(engine::broadcast);
        GameCommands.registerAll(registry, this);
        SableSupport.start(registry);
        registry.addExtensions(BlockpilotApi.freeze(engine::broadcast, trust.trust()::trusts));
        HumanInput.setListener(this::onHumanInput);
        writeLauncher();
        try {
            Token token = Token.loadOrCreate(directory.resolve("token"));
            McpServer mcp = new McpServer(engine, token, Screenshots::capture, ClientPlatform.modVersion());
            endpoint = new Endpoint(engine, token, mcp, new Endpoint.Identity(ClientPlatform.modVersion(),
                ClientPlatform.minecraftVersion(), ClientPlatform.loaderName()));
            endpoint.start(config.bind, config.port, config.portTries);
            writeEndpointFile(token);
            Blockpilot.LOGGER.info("Blockpilot endpoint details in {}; token in {}", endpointFile, token.file());
        } catch (Exception e) {
            Blockpilot.LOGGER.error("Blockpilot could not open its endpoint on {}:{}; agents cannot connect",
                config.bind, config.port, e);
            endpoint = null;
        }
    }

    /**
     * {@code <gameDir>/blockpilot/blockpilot} ({@code .cmd} on Windows): runs the command-line client on this game's
     * Java and jar. Rewritten at every start and kept after exit, so it works while the game is down too.
     */
    private void writeLauncher() {
        boolean windows = Launcher.onWindows();
        Path gameDir = ClientPlatform.gameDir().toAbsolutePath().normalize();
        Path launcher = gameDir.resolve(Blockpilot.MOD_ID).resolve(Launcher.fileName(windows));
        // Whatever goes wrong here must not keep the endpoint, which starts next, from coming up.
        try {
            List<Path> classpath = ClientPlatform.modPaths().stream().map(path -> path.toAbsolutePath().normalize())
                .filter(Files::exists).toList();
            if (classpath.isEmpty()) {
                Blockpilot.LOGGER.warn("Blockpilot wrote no launcher: the loader names no file it loaded it from");
                return;
            }
            Files.createDirectories(launcher.getParent());
            Path temporary = launcher.resolveSibling(launcher.getFileName() + ".tmp");
            Files.writeString(temporary, Launcher.text(windows, Launcher.java(windows), classpath, gameDir,
                ClientPlatform.modVersion()), StandardCharsets.UTF_8);
            if (!windows) {
                Files.setPosixFilePermissions(temporary, PosixFilePermissions.fromString("rwxr-xr-x"));
            }
            Files.move(temporary, launcher, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            Blockpilot.LOGGER.info("Blockpilot's command-line client: {}", launcher);
        } catch (IOException | RuntimeException e) {
            Blockpilot.LOGGER.warn("Blockpilot could not write its launcher {}", launcher, e);
        }
    }

    /** {@code <gameDir>/blockpilot/endpoint.json}: tells agents and the CLI that the endpoint is up and where. */
    private void writeEndpointFile(Token token) throws IOException {
        JsonObject json = new JsonObject();
        json.addProperty("port", endpoint.port());
        json.addProperty("pid", ProcessHandle.current().pid());
        json.addProperty("mod", ClientPlatform.modVersion());
        json.addProperty("api", BlockpilotApi.VERSION);
        json.addProperty("minecraft", ClientPlatform.minecraftVersion());
        json.addProperty("loader", ClientPlatform.loaderName());
        json.addProperty("token_file", token.file().toAbsolutePath().toString());
        endpointFile = ClientPlatform.gameDir().resolve(Blockpilot.MOD_ID).resolve("endpoint.json");
        Files.createDirectories(endpointFile.getParent());
        Path temporary = endpointFile.resolveSibling("endpoint.json.tmp");
        Files.writeString(temporary, json.toString(), StandardCharsets.UTF_8);
        Files.move(temporary, endpointFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    @Override
    public void onClientStopping() {
        Recorder.onClientStopping();
        try {
            restoreAgentChanges("the game is closing");
        } catch (RuntimeException e) {
            // An option's update hook failing must not break shutdown; nothing writes the changed values anyway.
            Blockpilot.LOGGER.warn("Could not restore the options agents changed", e);
        }
        if (endpoint != null) {
            endpoint.stop();
        }
        if (endpointFile != null) {
            try {
                Files.deleteIfExists(endpointFile);
            } catch (IOException e) {
                Blockpilot.LOGGER.warn("Could not remove {}", endpointFile, e);
            }
        }
    }

    @Override
    public void onClientTickEnd() {
        if (engine == null) {
            return;
        }
        while (toggleKey.consumeClick()) {
            enabled = !enabled;
            if (!enabled) {
                engine.stopAll("disabled");
                restoreAgentChanges("agent control turned off");
                CameraControl.reset();
            }
            engine.broadcast(Event.of(enabled ? "enabled" : "disabled"));
        }
        restoreOnWorldExit();
        if (ticks % 20 == 0) {
            configReload.check();
        }
        trust.tick();
        Perf.clientTick();
        if (++ticks % 200 == 0) {
            // A loader or mod may reload the logging setup, which drops our appender.
            LogCapture.install();
        }
        long now = System.currentTimeMillis();
        presence.update(engine.sessionCount(), engine.activity(), now);
        boolean agentInControl = enabled && presence.present(now);
        Focus.setKeepActive(agentInControl || Recorder.active());
        MovementCommands.holdFlight(agentInControl);
        InputState.reassert();
        events.tick();
        SableSupport.tick(Minecraft.getInstance());
        AwayCamera.tick();
        CameraControl.tick();
        chatSinceTick.set(0);
        engine.tickClient();
        EntityUpdates.CLIENT.clear();
        if (closeIn > 0 && --closeIn == 0) {
            Minecraft.getInstance().stop();
        }
    }

    /** Closes the game after that many ticks, as its Quit Game button does. */
    void closeAfter(int ticks) {
        closeIn = ticks;
    }

    private static void endLoad(String reason) {
        String ended = Load.off();
        if (!ended.isEmpty()) {
            Blockpilot.LOGGER.info("Ended the load agents added, as {}: {}", reason, ended);
        }
    }

    /** Config values first: they catch their own failures, which an option's update hook doesn't. */
    static void restoreAgentChanges(String reason) {
        endLoad(reason);
        List<String> values = ConfigCommands.restoreAll();
        if (!values.isEmpty()) {
            Blockpilot.LOGGER.info("Put back the config values agents changed, as {}: {}", reason,
                String.join(" ", values));
        }
        List<String> restored = ClientOptions.restoreAll();
        if (!restored.isEmpty()) {
            Blockpilot.LOGGER.info("Restored the options agents changed, as {}: {}", reason,
                String.join(" ", restored));
        }
    }

    /**
     * Agent option and config changes end when the player leaves the world (Blockpilot's own world switches keep
     * them), load ends then too, and entering a world notes its tick for since=.
     */
    private void restoreOnWorldExit() {
        boolean inWorld = Minecraft.getInstance().level != null;
        if (wasInWorld && !inWorld) {
            // Unlike options, load ends on Blockpilot's own world switches too.
            endLoad("the world was left");
        }
        if (wasInWorld && !inWorld && !SessionCommands.leftByBlockpilot()) {
            restoreAgentChanges("the world was left");
        }
        if (!wasInWorld && inWorld) {
            Observations.joined(GameClock.now());
        }
        wasInWorld = inWorld;
    }

    @Override
    public void onServerStarted(MinecraftServer server) {
        ServerCommands.onServerStarted(server);
    }

    @Override
    public void onServerTickEnd(MinecraftServer server) {
        if (Perf.active) {
            // This tick's time is recorded after the event on some loaders; the previous one is complete, and it
            // ended on the game tick this hook saw last time.
            long[] times = server.getTickTimesNanos();
            Perf.serverTick(times[Math.floorMod(server.getTickCount() - 1, times.length)], lastServerGameTick);
        }
        lastServerGameTick = server.overworld().getGameTime();
        AwayCamera.serverTick(server);
        if (engine != null) {
            engine.tickServer();
        }
    }

    @Override
    public void onServerChunkLoaded() {
        Perf.chunkLoaded();
    }

    @Override
    public void onServerChunkGenerated() {
        Perf.chunkGenerated();
    }

    @Override
    public void onRenderStageStart() {
        Perf.stageStart();
    }

    @Override
    public void onRenderStageEnd(String stage) {
        Perf.stageEnd(stage);
    }

    @Override
    public void onServerStopping(MinecraftServer server) {
        endLoad("the server stopped");
        SessionCommands.onServerStopping(server);
        ServerCommands.onServerStopping(server);
        ConfigCommands.forgetServerValues();
    }

    /** The trusted players and the agent command profile as the config file has them now. */
    private void applySafetySettings(Config read) {
        config.trustedPlayers = read.trustedPlayers;
        config.agentCommands = read.agentCommands;
        trust.trust().set(read.trustedPlayers);
        engine.setPlayOnly(read.playOnly());
    }

    @Override
    public void onChatReceived(Component message, UUID sender) {
        chatSinceTick.incrementAndGet();
        Component judged = trust.judge(message, sender);
        Observations.message("chat", judged);
        if (events != null) {
            events.chat(judged);
        }
    }

    @Override
    public void onScreenInit(Screen screen) {
        if (events != null) {
            events.screenOpened(screen);
        }
        autoJoin(screen);
    }

    /**
     * {@code -Dblockpilot.autojoin=<world>} joins that world once the title screen (or the first-launch onboarding
     * screen) shows; with {@code -Dblockpilot.autocreate=flat|void|normal} a missing world is created first.
     */
    private void autoJoin(Screen screen) {
        String world = System.getProperty("blockpilot.autojoin");
        if (autoJoined || world == null || world.isBlank()
            || !(screen instanceof TitleScreen || screen instanceof AccessibilityOnboardingScreen)) {
            return;
        }
        autoJoined = true;
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.options.onboardAccessibility = false;
        minecraft.options.save();
        String preset = System.getProperty("blockpilot.autocreate");
        minecraft.execute(() -> {
            try {
                if (TestWorlds.exists(world)) {
                    minecraft.createWorldOpenFlows().openWorld(world, () -> ClientScreens.open(new TitleScreen()));
                } else if (preset != null) {
                    TestWorlds.create(world, preset);
                } else {
                    Blockpilot.LOGGER.warn("blockpilot.autojoin: no world {} and no blockpilot.autocreate; staying on "
                        + "the title screen", world);
                }
            } catch (CommandException e) {
                Blockpilot.LOGGER.warn("blockpilot.autojoin {} failed: {}", world, e.getMessage());
            }
        });
    }

    @Override
    public void onHudRender(GuiGraphics graphics) {
        CameraControl.recordRendered();
        long now = System.currentTimeMillis();
        if (engine == null || !presence.present(now) || HudVisibility.hidden()
            || Recorder.active()) {
            return;
        }
        String activity = presence.shownActivity();
        String text = !enabled ? "Blockpilot: agent control off (" + toggleKey.getTranslatedKeyMessage().getString()
            + ")" : activity != null ? "Blockpilot: " + activity : "Blockpilot: agent active";
        int color = !enabled ? 0xFFFF5555 : activity != null ? 0xFF55FF55 : 0xFFAAAAAA;
        graphics.drawString(Minecraft.getInstance().font, text, 4, 4, color);
    }

    private void onHumanInput(Fields input) {
        if (engine != null && (engine.activity() != null || !InputState.isEmpty() || CameraControl.moving() != null)) {
            engine.stopAll(Result.error("interrupted").with(input));
            Fields event = new Fields().bare("by", "player");
            input.list().forEach(field -> event.put(field.name(), field.value(), field.text()));
            engine.broadcast(Event.of("interrupted", event));
        }
    }

    @Override
    public void runOnClient(Runnable task) {
        Minecraft.getInstance().execute(task);
    }

    @Override
    public void runOnServer(Runnable task) {
        MinecraftServer server = Minecraft.getInstance().getSingleplayerServer();
        if (server == null || server.isSameThread()) {
            task.run();
        } else {
            server.execute(task);
        }
    }

    @Override
    public <T> T callOnServer(Supplier<T> task) throws CommandException {
        MinecraftServer server = Minecraft.getInstance().getSingleplayerServer();
        if (server == null) {
            throw new CommandException("no_server");
        }
        return server.isSameThread() ? task.get() : Sides.await(server.submit(task));
    }

    @Override
    public boolean ready() {
        return ClientScreens.overlay() == null;
    }

    @Override
    public String actionGate() {
        return enabled ? null : "disabled";
    }

    @Override
    public String gate(CommandSpec spec) {
        Minecraft minecraft = Minecraft.getInstance();
        if (spec.kind() == CommandSpec.Kind.ACTION && actionGate() != null) {
            return actionGate();
        }
        if (spec.needsWorldLoaded() && (minecraft.player == null || minecraft.level == null)) {
            return "not_in_world";
        }
        if (spec.needsIntegratedServer()) {
            if (minecraft.getSingleplayerServer() == null) {
                return "no_server";
            }
            if (!Sides.cheats(minecraft.player)) {
                return "no_cheats";
            }
        }
        return null;
    }

    @Override
    public boolean serverPaused() {
        var server = Minecraft.getInstance().getSingleplayerServer();
        return server != null && server.isPaused();
    }

    @Override
    public Position resolve(Position position) throws CommandException {
        if (!position.relative()) {
            return position;
        }
        var player = Minecraft.getInstance().player;
        if (player == null) {
            throw new CommandException("not_in_world");
        }
        BlockPos base = player.blockPosition();
        return position.resolve(base.getX(), base.getY(), base.getZ());
    }

    @Override
    public OptionalInt resolveEntity(String name) throws CommandException {
        return NamedEntities.find(name);
    }

    @Override
    public void stopBackground() {
        CameraControl.stopMove();
    }

    @Override
    public long clientTicks() {
        return ticks;
    }

    @Override
    public int serverLoad() {
        return Load.server().ms();
    }

    @Override
    public String stallHint() {
        long chat = chatSinceTick.get();
        return chat == 0 ? null : chat + " chat lines arrived since";
    }

    @Override
    public long gameTime() {
        return GameClock.now();
    }

    @Override
    public void releaseInputs() {
        runOnClient(InputState::releaseAll);
    }

    @Override
    public void releaseInputs(int sessionId) {
        runOnClient(() -> InputState.release(sessionId));
    }
}
