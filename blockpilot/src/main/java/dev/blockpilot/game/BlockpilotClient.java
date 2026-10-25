package dev.blockpilot.game;

import com.google.gson.JsonObject;
import dev.blockpilot.Blockpilot;
import dev.blockpilot.api.BlockpilotApi;
import dev.blockpilot.api.CommandException;
import dev.blockpilot.api.CommandSpec;
import dev.blockpilot.api.Event;
import dev.blockpilot.api.Fields;
import dev.blockpilot.api.Position;
import dev.blockpilot.game.input.Focus;
import dev.blockpilot.game.input.HumanInput;
import dev.blockpilot.game.input.InputState;
import dev.blockpilot.game.log.LogCapture;
import dev.blockpilot.game.observe.CameraControl;
import dev.blockpilot.game.observe.ClientOptions;
import dev.blockpilot.game.observe.Observations;
import dev.blockpilot.game.perf.Perf;
import dev.blockpilot.game.sable.SableSupport;
import dev.blockpilot.platform.ClientPlatform;
import dev.blockpilot.protocol.CommandRegistry;
import dev.blockpilot.protocol.Config;
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
import java.util.List;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import org.lwjgl.glfw.GLFW;

/**
 * Brings Blockpilot up in the client: once mod loading completes it builds the command table (built-in commands
 * plus extensions), starts the endpoint and writes {@code blockpilot/endpoint.json}; every tick it drives the
 * engine, the held input and event detection.
 */
public final class BlockpilotClient implements ClientPlatform.Hooks, GameBridge {
    private static BlockpilotClient instance;

    /** The player's switch for agent control; agents can't press it (see InputCommands). */
    private final KeyMapping toggleKey = new KeyMapping("key.blockpilot.toggle", GLFW.GLFW_KEY_F8,
        "key.categories.blockpilot");
    // Set up on the loader's load-complete thread, then used on the client and server threads.
    private volatile Config config;
    private volatile Engine engine;
    private volatile Endpoint endpoint;
    private volatile Path endpointFile;
    private volatile Events events;
    private volatile boolean enabled = true;
    private long ticks;
    private boolean autoJoined;
    private boolean wasInWorld;
    private final AgentPresence presence = new AgentPresence();

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

    KeyMapping toggleKey() {
        return toggleKey;
    }

    @Override
    public void onLoadComplete() {
        Path directory = ClientPlatform.configDir().resolve(Blockpilot.MOD_ID);
        config = Config.load(directory.resolve("config.json"));
        CommandRegistry registry = new CommandRegistry();
        engine = new Engine(registry, this);
        events = new Events(engine);
        Observations.setEventSink(engine::broadcast);
        LogCapture.setSink(engine::broadcast);
        GameCommands.registerAll(registry, this);
        SableSupport.start(registry);
        registry.addExtensions(BlockpilotApi.freeze(engine::broadcast));
        HumanInput.setListener(this::onHumanInput);
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
        try {
            restoreOptions("the game is closing");
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
                restoreOptions("agent control turned off");
                CameraControl.reset();
            }
            engine.broadcast(Event.of(enabled ? "enabled" : "disabled"));
        }
        restoreOnWorldExit();
        Perf.clientTick();
        if (++ticks % 200 == 0) {
            // A loader or mod may reload the logging setup, which drops our appender.
            LogCapture.install();
        }
        long now = System.currentTimeMillis();
        presence.update(engine.sessionCount(), engine.activity(), now);
        boolean agentInControl = enabled && presence.present(now);
        Focus.setKeepActive(agentInControl);
        ActionCommands.holdFlight(agentInControl);
        InputState.reassert();
        events.tick();
        engine.tickClient();
    }

    static void restoreOptions(String reason) {
        List<String> restored = ClientOptions.restoreAll();
        if (!restored.isEmpty()) {
            Blockpilot.LOGGER.info("Restored the options agents changed, as {}: {}", reason, String.join(" ", restored));
        }
    }

    /** Agent option changes end when the player leaves the world (Blockpilot's own world switches keep them). */
    private void restoreOnWorldExit() {
        boolean inWorld = Minecraft.getInstance().level != null;
        if (wasInWorld && !inWorld && !SessionCommands.leftByBlockpilot()) {
            restoreOptions("the world was left");
        }
        wasInWorld = inWorld;
    }

    @Override
    public void onServerTickEnd(MinecraftServer server) {
        if (Perf.active) {
            // This tick's time is recorded after the event; the previous one is complete.
            long[] times = server.getTickTimesNanos();
            Perf.serverTick(times[Math.floorMod(server.getTickCount() - 1, times.length)]);
        }
        if (engine != null) {
            engine.tickServer();
        }
    }

    @Override
    public void onServerStopping(MinecraftServer server) {
        ServerCommands.onServerStopping(server);
    }

    @Override
    public void onChatReceived(Component message) {
        Observations.message("chat", message);
        if (events != null) {
            events.chat(message);
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
                    minecraft.createWorldOpenFlows().openWorld(world, () -> minecraft.setScreen(new TitleScreen()));
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
        if (engine == null || !presence.present(now) || Minecraft.getInstance().options.hideGui) {
            return;
        }
        String activity = presence.shownActivity();
        String text = !enabled ? "Blockpilot: agent control off (" + toggleKey.getTranslatedKeyMessage().getString()
            + ")" : activity != null ? "Blockpilot: " + activity : "Blockpilot: agent active";
        int color = !enabled ? 0xFFFF5555 : activity != null ? 0xFF55FF55 : 0xFFAAAAAA;
        graphics.drawString(Minecraft.getInstance().font, text, 4, 4, color);
    }

    private void onHumanInput() {
        if (engine != null && (engine.activity() != null || !InputState.isEmpty())) {
            engine.stopAll("interrupted");
            engine.broadcast(Event.of("interrupted", new Fields().bare("by", "player")));
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
    public boolean ready() {
        return Minecraft.getInstance().getOverlay() == null;
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
            if (!minecraft.player.hasPermissions(2)) {
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
