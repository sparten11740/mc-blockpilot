package dev.blockpilot.platform.vanilla;

import java.lang.instrument.Instrumentation;
import java.lang.reflect.Method;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.Mixins;

/**
 * The vanilla client's entry point, run by {@code -javaagent} before the game's main class: boots Mixin with this
 * agent's service and hands the classes the game loads to Mixin's transformer.
 */
public final class BlockpilotAgent {
    private BlockpilotAgent() {
    }

    public static void premain(String arguments, Instrumentation instrumentation) throws ReflectiveOperationException {
        MixinBootstrap.init();
        Mixins.addConfiguration("blockpilot-vanilla.mixins.json");
        MixinEnvironment.getDefaultEnvironment().setSide(MixinEnvironment.Side.CLIENT);
        // Mixin moves to its default phase only when told, as Fabric's loader tells it.
        Method gotoPhase = MixinEnvironment.class.getDeclaredMethod("gotoPhase", MixinEnvironment.Phase.class);
        gotoPhase.setAccessible(true);
        gotoPhase.invoke(null, MixinEnvironment.Phase.INIT);
        gotoPhase.invoke(null, MixinEnvironment.Phase.DEFAULT);
        instrumentation.addTransformer(new AgentTransformer(AgentMixinService.transformer()));
    }
}
