package dev.blockpilot.platform.vanilla;

import org.spongepowered.asm.service.IMixinServiceBootstrap;

/** Names {@link AgentMixinService} to Mixin, which finds this through the service loader. */
public final class AgentMixinServiceBootstrap implements IMixinServiceBootstrap {
    @Override
    public String getName() {
        return "Blockpilot agent";
    }

    @Override
    public String getServiceClassName() {
        return "dev.blockpilot.platform.vanilla.AgentMixinService";
    }

    @Override
    public void bootstrap() {
    }
}
