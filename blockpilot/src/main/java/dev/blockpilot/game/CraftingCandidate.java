package dev.blockpilot.game;

import java.util.List;
import java.util.Optional;
import java.util.function.IntConsumer;
import java.util.function.Predicate;
import net.minecraft.world.item.ItemStack;

/**
 * A crafting recipe as {@code craft} uses it, the same on every Minecraft version (see {@link CraftingRecipes}).
 *
 * @param ingredients in grid order, empty for a gap in a shaped pattern
 * @param width the pattern's width; 0 for a shapeless recipe, laid out row by row
 * @param resultCount the items one craft makes
 * @param placeWithBook places the recipe in the open menu (by container id) through the recipe book; null when the
 *     book can't, because the recipe isn't unlocked
 */
record CraftingCandidate(List<Optional<Predicate<ItemStack>>> ingredients, int width, boolean fitsSmallGrid,
    int resultCount, IntConsumer placeWithBook) {
}
