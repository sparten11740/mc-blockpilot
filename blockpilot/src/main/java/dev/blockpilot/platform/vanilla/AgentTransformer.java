package dev.blockpilot.platform.vanilla;

import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;
import org.spongepowered.asm.mixin.transformer.IMixinTransformer;

/** Hands each class the game's class loader defines to Mixin; the JDK's and the agent's own libraries are skipped. */
final class AgentTransformer implements ClassFileTransformer {
    private static final String[] SKIPPED = {"java/", "javax/", "jdk/", "sun/", "com/sun/", "org/spongepowered/",
        "org/objectweb/", "com/llamalad7/", "net/fabricmc/accesswidener/"};

    private final IMixinTransformer mixins;

    AgentTransformer(IMixinTransformer mixins) {
        this.mixins = mixins;
    }

    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> redefined, ProtectionDomain domain,
        byte[] bytes) {
        if (loader == null || className == null || redefined != null) {
            return null;
        }
        for (String prefix : SKIPPED) {
            if (className.startsWith(prefix)) {
                return null;
            }
        }
        String name = className.replace('/', '.');
        try {
            byte[] transformed = mixins.transformClassBytes(name, name, bytes);
            return transformed == bytes ? null : transformed;
        } catch (Throwable failure) {
            // The JVM drops a transformer's exception silently and loads the class unchanged; say so instead.
            System.err.println("Blockpilot: could not apply mixins to " + name);
            failure.printStackTrace();
            return null;
        }
    }
}
