package dev.blockpilot.game;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.IntConsumer;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;

/** The crafting recipes for an item, from the client's recipe manager, which holds every recipe up to 1.21.1. */
final class CraftingRecipes {
    private CraftingRecipes() {
    }

    static List<CraftingCandidate> forItem(Item item) {
        Minecraft minecraft = Minecraft.getInstance();
        var level = minecraft.level;
        List<CraftingCandidate> candidates = new ArrayList<>();
        for (RecipeHolder<CraftingRecipe> holder : level.getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING)) {
            CraftingRecipe recipe = holder.value();
            ItemStack result = recipe.getResultItem(level.registryAccess());
            if (!result.is(item)) {
                continue;
            }
            List<Optional<Predicate<ItemStack>>> ingredients = recipe.getIngredients().stream()
                .map(ingredient -> ingredient.isEmpty() ? Optional.<Predicate<ItemStack>>empty()
                    : Optional.<Predicate<ItemStack>>of(ingredient))
                .toList();
            int width = recipe instanceof ShapedRecipe shaped ? shaped.getWidth() : 0;
            IntConsumer book = minecraft.player.getRecipeBook().contains(holder)
                ? containerId -> minecraft.gameMode.handlePlaceRecipe(containerId, holder, false) : null;
            candidates.add(new CraftingCandidate(ingredients, width, recipe.canCraftInDimensions(2, 2),
                Math.max(1, result.getCount()), book));
        }
        return candidates;
    }
}
