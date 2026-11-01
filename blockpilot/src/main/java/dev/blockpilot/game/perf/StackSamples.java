package dev.blockpilot.game.perf;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** The server-thread stack samples of a profiled capture, counted by the mod and the top frame of each. */
final class StackSamples {
    /** Code that runs everyone's work: a sample counts toward the innermost frame outside it that a mod owns. */
    private static final List<String> PLATFORM = List.of("java.", "javax.", "jdk.", "sun.", "com.sun.",
        "net.minecraft.", "com.mojang.", "net.neoforged.", "net.fabricmc.", "cpw.mods.", "org.spongepowered.",
        "com.llamalad7.mixinextras.");
    private static final List<String> MINECRAFT = List.of("net.minecraft.", "com.mojang.");
    private static final List<String> JDK = List.of("java.", "javax.", "jdk.", "sun.", "com.sun.");
    static final String MINECRAFT_MOD = "minecraft";
    static final String JAVA = "java";

    record Frame(String className, String method, boolean nativeMethod) {
        String name() {
            return className + "." + method;
        }
    }

    private record Top(String name, boolean nativeMethod, String mod) {
    }

    private final Function<String, String> modOf;
    private final Map<String, Integer> mods = new HashMap<>();
    private final Map<Top, Integer> tops = new HashMap<>();
    private int kept;
    private int outsideTicks;

    /** {@code modOf} gives the mod owning a class by its name, or null. */
    StackSamples(Function<String, String> modOf) {
        this.modOf = modOf;
    }

    /** Counts a sample taken inside a tick, its frames top first. */
    synchronized void add(List<Frame> stack) {
        if (stack.isEmpty()) {
            return;
        }
        kept++;
        mods.merge(modOf(stack), 1, Integer::sum);
        Frame top = stack.get(0);
        tops.merge(new Top(top.name(), top.nativeMethod(), owner(top.className())), 1, Integer::sum);
    }

    /** Counts a sample taken while the server waited between ticks. */
    synchronized void skip() {
        outsideTicks++;
    }

    /** The mod a sample counts toward. */
    String modOf(List<Frame> stack) {
        boolean minecraft = false;
        for (Frame frame : stack) {
            String className = frame.className();
            minecraft |= MINECRAFT.stream().anyMatch(className::startsWith);
            if (PLATFORM.stream().noneMatch(className::startsWith)) {
                String mod = modOf.apply(className);
                if (mod != null) {
                    return mod;
                }
            }
        }
        return minecraft ? MINECRAFT_MOD : JAVA;
    }

    /** The mod owning a class, {@code java} for the JDK's, or null. */
    private String owner(String className) {
        return JDK.stream().anyMatch(className::startsWith) ? JAVA : modOf.apply(className);
    }

    /** The counts, each mod's share and the {@code limit} most frequent top frames' shares, in percent. */
    synchronized JsonObject summary(int limit) {
        JsonObject summary = new JsonObject();
        summary.addProperty("samples", kept);
        summary.addProperty("outside_ticks", outsideTicks);
        JsonArray modRows = new JsonArray();
        mods.entrySet().stream().sorted(Map.Entry.<String, Integer>comparingByValue().reversed()
            .thenComparing(Map.Entry.comparingByKey())).forEach(entry -> {
                JsonObject row = new JsonObject();
                row.addProperty("mod", entry.getKey());
                row.addProperty("samples", entry.getValue());
                row.addProperty("share", share(entry.getValue()));
                modRows.add(row);
            });
        summary.add("mods", modRows);
        JsonArray frameRows = new JsonArray();
        tops.entrySet().stream().sorted(Map.Entry.<Top, Integer>comparingByValue().reversed()
            .thenComparing(entry -> entry.getKey().name())).limit(limit).forEach(entry -> {
                JsonObject row = new JsonObject();
                row.addProperty("frame", entry.getKey().name());
                if (entry.getKey().nativeMethod()) {
                    row.addProperty("native", true);
                }
                if (entry.getKey().mod() != null) {
                    row.addProperty("mod", entry.getKey().mod());
                }
                row.addProperty("samples", entry.getValue());
                row.addProperty("share", share(entry.getValue()));
                frameRows.add(row);
            });
        summary.add("frames", frameRows);
        return summary;
    }

    private double share(int samples) {
        return Math.round(samples * 1000.0 / kept) / 10.0;
    }
}
