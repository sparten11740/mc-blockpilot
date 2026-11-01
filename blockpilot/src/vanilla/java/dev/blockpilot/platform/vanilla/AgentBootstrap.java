package dev.blockpilot.platform.vanilla;

import com.llamalad7.mixinextras.MixinExtrasBootstrap;
import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Method;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.Mixins;

/**
 * Boots Mixin with this agent's service and hands the classes the game loads to the access widener and Mixin's
 * transformer. Started by {@code BlockpilotAgent} once it has checked the game's version.
 */
public final class AgentBootstrap {
    private AgentBootstrap() {
    }

    public static void start(Instrumentation instrumentation) throws ReflectiveOperationException, IOException {
        MixinBootstrap.init();
        MixinExtrasBootstrap.init();
        Mixins.addConfiguration("blockpilot-vanilla.mixins.json");
        MixinEnvironment.getDefaultEnvironment().setSide(MixinEnvironment.Side.CLIENT);
        // Mixin moves to its default phase only when told, as Fabric's loader tells it.
        Method gotoPhase = MixinEnvironment.class.getDeclaredMethod("gotoPhase", MixinEnvironment.Phase.class);
        gotoPhase.setAccessible(true);
        gotoPhase.invoke(null, MixinEnvironment.Phase.INIT);
        gotoPhase.invoke(null, MixinEnvironment.Phase.DEFAULT);
        instrumentation.addTransformer(new AgentTransformer(new AccessWidening("blockpilot.accesswidener"),
            AgentMixinService.transformer()));
    }
}
