package dev.blockpilot.game.identity;

//? if >=1.21.11 {
/*import com.mojang.blaze3d.systems.RenderSystem;
*///?} else {
import com.mojang.blaze3d.platform.GlUtil;
//?}
import dev.blockpilot.api.ArgType;
import dev.blockpilot.api.BlockpilotApi;
import dev.blockpilot.api.CommandSpec;
import dev.blockpilot.api.Fields;
import dev.blockpilot.api.Result;
import dev.blockpilot.api.Row;
import dev.blockpilot.game.observe.TextPattern;
import dev.blockpilot.platform.ClientPlatform;
import dev.blockpilot.protocol.CommandRegistry;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.regex.Pattern;
//? if >=26.2 {
/*import net.minecraft.client.Minecraft;
*///?}

/** What this client is: its versions, its renderer, and the mod files it loaded with their hashes from load time. */
public final class ClientIdentity {
    private static final ModHashes HASHES = new ModHashes();
    private static final Map<String, String> LOADER_MODS = Map.of("neoforge", "neoforge", "forge", "forge", "fabric",
        "fabricloader");

    /** The backend and the GPU as the game reports them. */
    private record Renderer(String backend, String gpu, String vendor, String driver) {
    }

    public void register(CommandRegistry registry) {
        registry.register(CommandSpec.query("client", (context, args) -> client()).group("session")
            .help("this game: the Minecraft, loader, Blockpilot, API, Java and OS versions, the GPU backend "
                + "(opengl|vulkan), from 26.2 the graphics API the options ask for as asked=, the GPU, its vendor and "
                + "driver, and how many mods are loaded")
            .example("client"));
        registry.register(CommandSpec.query("mods", (context, args) -> mods(args.option("match"))).group("session")
            .option("match", "mod id or pattern, e.g. mymod or mymod*", ArgType.PATTERN, ArgType.ID)
            .help("the loaded mods by id: version, file (relative to the game directory when inside it) and its "
                + "SHA-256 as the game loaded it, changed when the file on disk differs since; nested with in=<mod> "
                + "for a mod inside another's jar where the loader tells (Fabric, NeoForge), folder= for one a "
                + "development run loads from folders, only id and version for one with no file on disk. hashing=<n> "
                + "counts the files still being hashed after the start: wait mods until hashing==0")
            .field("hashing", "files still being hashed, 0 once every hash is taken")
            .example("mods match=mymod*"));
    }

    /** Hashes every mod file in the background; called as the game finishes loading. */
    public static void startHashing() {
        HASHES.start(ClientPlatform.mods().stream().map(ModRows::file).filter(Objects::nonNull).toList());
    }

    private static Result client() {
        Renderer renderer = renderer();
        String loader = ClientPlatform.loaderName();
        String loaderMod = LOADER_MODS.get(loader);
        Fields fields = new Fields().put("minecraft", ClientPlatform.minecraftVersion()).put("loader", loader)
            .put("loader_version", loaderMod == null ? null : ClientPlatform.versionOf(loaderMod))
            .put("blockpilot", ClientPlatform.modVersion()).put("api", BlockpilotApi.VERSION)
            .put("java", System.getProperty("java.version"));
        quoted(fields, "os", System.getProperty("os.name") + " " + System.getProperty("os.version") + " "
            + System.getProperty("os.arch"));
        fields.put("backend", renderer.backend().toLowerCase(Locale.ROOT));
        //? if >=26.2 {
        /*fields.put("asked", Minecraft.getInstance().options.preferredGraphicsBackend().get().getSerializedName());
        *///?}
        quoted(fields, "gpu", renderer.gpu());
        quoted(fields, "vendor", renderer.vendor());
        quoted(fields, "driver", renderer.driver());
        return Result.ok(fields.put("mods", ClientPlatform.mods().size()));
    }

    private static Result mods(String match) {
        Pattern pattern = match == null ? null : TextPattern.compile(match);
        Predicate<String> keep = id -> pattern == null || pattern.matcher(id).matches();
        List<Row> rows = ModRows.rows(ClientPlatform.mods(), keep, ClientPlatform.gameDir(), HASHES);
        return Result.ok(new Fields().put("hashing", HASHES.left())).withRows(rows);
    }

    private static Renderer renderer() {
        //? if >=26.2 {
        /*var info = RenderSystem.getDevice().getDeviceInfo();
        return new Renderer(info.backendName(), info.name(), info.vendorName(), info.driverInfo());
        *///?} else if >=1.21.11 {
        /*var device = RenderSystem.getDevice();
        return new Renderer(device.getBackendName(), device.getRenderer(), device.getVendor(), device.getVersion());
        *///?} else {
        return new Renderer("OpenGL", GlUtil.getRenderer(), GlUtil.getVendor(), GlUtil.getOpenGLVersion());
        //?}
    }

    /** Text that may hold spaces, in quotes so the line still splits into fields. */
    private static void quoted(Fields fields, String name, String value) {
        fields.put(name, value, value == null ? null : name + "=\"" + value + "\"");
    }
}
