package dev.blockpilot.platform.vanilla;

import java.io.IOException;
import java.io.InputStream;
import net.fabricmc.accesswidener.AccessWidener;
import net.fabricmc.accesswidener.AccessWidenerClassVisitor;
import net.fabricmc.accesswidener.AccessWidenerReader;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

/** The mod's access widener, the file Fabric applies, applied to the classes it names as they load. */
final class AccessWidening {
    private final AccessWidener widener = new AccessWidener();

    AccessWidening(String resource) throws IOException {
        try (InputStream file = ClassLoader.getSystemResourceAsStream(resource)) {
            if (file == null) {
                throw new IOException(resource + " is missing from the agent");
            }
            // 26.1 runs with the names the widener is written in.
            new AccessWidenerReader(widener).read(file.readAllBytes(), "official");
        }
    }

    /** The class with the widened access, or the same bytes when the widener doesn't name it. */
    byte[] apply(String name, byte[] bytes) {
        if (!widener.getTargets().contains(name)) {
            return bytes;
        }
        ClassWriter writer = new ClassWriter(0);
        new ClassReader(bytes).accept(AccessWidenerClassVisitor.createClassVisitor(Opcodes.ASM9, writer, widener), 0);
        return writer.toByteArray();
    }
}
