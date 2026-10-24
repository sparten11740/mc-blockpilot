package dev.blockpilot.game;

import dev.blockpilot.api.CommandException;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Registry ids as agents read and write them: without the {@code minecraft:} namespace, others kept. */
public final class Ids {
    private Ids() {
    }

    public static String of(ResourceLocation id) {
        return id.getNamespace().equals(ResourceLocation.DEFAULT_NAMESPACE) ? id.getPath() : id.toString();
    }

    public static String of(Block block) {
        return of(BuiltInRegistries.BLOCK.getKey(block));
    }

    public static String of(BlockState state) {
        return of(state.getBlock());
    }

    public static String of(ItemStack stack) {
        return of(BuiltInRegistries.ITEM.getKey(stack.getItem()));
    }

    public static String of(Entity entity) {
        return of(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()));
    }

    /** Parses an id with or without its namespace; fails with {@code err syntax} when malformed. */
    public static ResourceLocation parse(String id) throws CommandException {
        ResourceLocation parsed = ResourceLocation.tryParse(id.contains(":") ? id : "minecraft:" + id);
        if (parsed == null) {
            throw new CommandException("syntax", "not an id: " + id);
        }
        return parsed;
    }
}
