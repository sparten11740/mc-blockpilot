package dev.blockpilot.mixin;

import io.netty.channel.Channel;
import net.minecraft.network.Connection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The connection's channel, whose pipeline the network load adds its handler to. */
@Mixin(Connection.class)
public interface ConnectionAccessor {
    @Accessor("channel")
    Channel blockpilot$channel();
}
