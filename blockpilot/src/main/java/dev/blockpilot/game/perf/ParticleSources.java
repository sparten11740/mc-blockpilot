package dev.blockpilot.game.perf;

import dev.blockpilot.platform.ClientPlatform;
import net.minecraft.client.multiplayer.ClientPacketListener;

/**
 * Which code spawned a particle, during a capture: a sample of each type's spawns walks the stack to the first frame
 * outside the JDK, Minecraft, the loaders, Mixin and Blockpilot, and names its class and mod.
 */
public final class ParticleSources {
    private static final StackWalker WALKER = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);
    /** Its runtime name, which on Fabric is an intermediary one. */
    private static final String PACKET_LISTENER = ClientPacketListener.class.getName();

    private ParticleSources() {
    }

    /** On the client thread, as the particle engine creates a particle of the type. */
    public static void created(String type) {
        if (!Perf.traceSpawn(type, false)) {
            return;
        }
        Class<?> source = source();
        if (source == ClientPacketListener.class) {
            Perf.spawnedBy(type, null, null, "server");
        } else {
            record(type, source, "client");
        }
    }

    /** On the server thread, as server code sends particles of the type to players. */
    public static void sent(String type) {
        if (Perf.traceSpawn(type, true)) {
            record(type, source(), "server_side");
        }
    }

    /** The class of the first frame that names where the particle came from, or null when none does. */
    private static Class<?> source() {
        return WALKER.walk(frames -> frames.map(StackWalker.StackFrame::getDeclaringClass)
            .filter(frame -> SourceFrames.namesSource(frame.getName(), PACKET_LISTENER)).findFirst().orElse(null));
    }

    private static void record(String type, Class<?> source, String via) {
        if (source == null) {
            Perf.spawnedBy(type, "minecraft", null, via);
        } else {
            Perf.spawnedBy(type, ClientPlatform.modOf(source), source.getName(), via);
        }
    }
}
