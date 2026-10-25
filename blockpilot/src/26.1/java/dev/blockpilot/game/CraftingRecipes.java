package dev.blockpilot.game;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.IntConsumer;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;
import net.minecraft.world.item.crafting.display.ShapedCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapelessCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;

/**
 * The crafting recipes for an item, as recipe-book entries. The client knows only the entries for unlocked recipes,
 * so in singleplayer every recipe comes from the integrated server's recipe manager (read-only after loading), and on
 * a remote server only the unlocked ones are found.
 */
final class CraftingRecipes {
    private CraftingRecipes() {
    }

    static List<CraftingCandidate> forItem(Item item) {
        Minecraft minecraft = Minecraft.getInstance();
        List<RecipeDisplayEntry> entries = new ArrayList<>();
        Set<RecipeDisplayId> unlocked = new HashSet<>();
        minecraft.player.getRecipeBook().getCollections().forEach(collection -> collection.getRecipes()
            .forEach(entry -> {
                entries.add(entry);
                unlocked.add(entry.id());
            }));
        MinecraftServer server = minecraft.getSingleplayerServer();
        if (server != null) {
            entries.clear();
            RecipeManager manager = server.getRecipeManager();
            manager.getRecipes().stream().filter(holder -> holder.value() instanceof CraftingRecipe)
                .forEach(holder -> manager.listDisplaysForRecipe(holder.id(), entries::add));
        }
        ContextMap context = SlotDisplayContext.fromLevel(minecraft.level);
        List<CraftingCandidate> candidates = new ArrayList<>();
        for (RecipeDisplayEntry entry : entries) {
            List<ItemStack> results = entry.resultItems(context);
            if (!results.isEmpty() && results.get(0).is(item)) {
                candidate(entry, results.get(0).getCount(), unlocked.contains(entry.id())).ifPresent(candidates::add);
            }
        }
        return candidates;
    }

    private static Optional<CraftingCandidate> candidate(RecipeDisplayEntry entry, int resultCount, boolean unlocked) {
        List<SlotDisplay> slots;
        int width;
        boolean fitsSmallGrid;
        if (entry.display() instanceof ShapedCraftingRecipeDisplay shaped) {
            slots = shaped.ingredients();
            width = shaped.width();
            fitsSmallGrid = shaped.width() <= 2 && shaped.height() <= 2;
        } else if (entry.display() instanceof ShapelessCraftingRecipeDisplay shapeless) {
            slots = shapeless.ingredients();
            width = 0;
            fitsSmallGrid = slots.size() <= 4;
        } else {
            return Optional.empty();
        }
        if (entry.craftingRequirements().isEmpty()) {
            return Optional.empty();
        }
        // The requirements are the pattern's ingredients without its gaps, in the same order.
        Iterator<Ingredient> requirements = entry.craftingRequirements().get().iterator();
        List<Optional<Predicate<ItemStack>>> ingredients = new ArrayList<>();
        for (SlotDisplay slot : slots) {
            ingredients.add(slot instanceof SlotDisplay.Empty || !requirements.hasNext() ? Optional.empty()
                : Optional.of(requirements.next()));
        }
        IntConsumer book = unlocked
            ? containerId -> Minecraft.getInstance().gameMode.handlePlaceRecipe(containerId, entry.id(), false) : null;
        return Optional.of(new CraftingCandidate(ingredients, width, fitsSmallGrid, Math.max(1, resultCount), book));
    }
}
