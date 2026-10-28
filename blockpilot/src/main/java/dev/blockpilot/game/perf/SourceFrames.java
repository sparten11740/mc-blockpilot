package dev.blockpilot.game.perf;

import java.util.List;

/** Which frame of a stack walked from a particle's spawn names where the particle came from. */
final class SourceFrames {
    private static final List<String> SKIPPED = List.of("java.", "jdk.", "sun.", "net.minecraft.", "com.mojang.",
        "net.neoforged.", "net.fabricmc.", "cpw.mods.", "org.spongepowered.", "dev.blockpilot.");

    private SourceFrames() {
    }

    /**
     * Whether the frame's class names the source: the packet listener the server's particles arrive through, or code
     * outside the JDK, Minecraft, the loaders, Mixin and Blockpilot.
     */
    static boolean namesSource(String className, String packetListener) {
        return className.equals(packetListener) || SKIPPED.stream().noneMatch(className::startsWith);
    }
}
