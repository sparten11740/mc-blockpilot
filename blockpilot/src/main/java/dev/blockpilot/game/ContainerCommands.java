package dev.blockpilot.game;

import dev.blockpilot.api.Action;
import dev.blockpilot.api.ArgType;
import dev.blockpilot.api.Args;
import dev.blockpilot.api.CommandContext;
import dev.blockpilot.api.CommandException;
import dev.blockpilot.api.CommandSpec;
import dev.blockpilot.api.Fields;
import dev.blockpilot.api.Result;
import dev.blockpilot.game.common.ClientScreens;
import dev.blockpilot.game.common.Ids;
import dev.blockpilot.game.path.Navigator;
import dev.blockpilot.game.sable.Rel;
import dev.blockpilot.protocol.CommandRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Containers and crafting, done with the clicks and recipe-book placement a player uses. */
final class ContainerCommands {
    private static final int TABLE_RANGE = 4;

    void register(CommandRegistry registry) {
        registry.register(CommandSpec.action("open", this::open).needsWorld().group("inventory")
            .arg("pos", ArgType.POSITION)
            .help("walks into reach of a container (chest, furnace, table…), opens it and waits for its screen")
            .example("open ~3,0,1"));
        registry.register(CommandSpec.action("take", this::take).needsWorld().group("inventory")
            .arg("what", ArgType.MENU_SLOT, ArgType.ID).optionalArg("count", ArgType.INT)
            .help("moves an item (or slot s<i>) from the open container into the inventory; all by default")
            .field("moved", "items moved").example("take iron_ingot 10"));
        registry.register(CommandSpec.action("put", this::put).needsWorld().group("inventory")
            .arg("what", ArgType.MENU_SLOT, ArgType.ID).optionalArg("count", ArgType.INT)
            .help("moves an item from the inventory into the open container; all by default")
            .field("moved", "items moved").example("put cobblestone 64"));
        registry.register(CommandSpec.action("craft", this::craft).needsWorld().group("inventory")
            .arg("item", ArgType.ID).optionalArg("count", ArgType.COUNT)
            .help("crafts with the recipe book: in the 2x2 grid when the recipe fits, else at an open or nearby "
                //? if >=1.21.11 {
                /*+ "crafting table; x<n> items (default one craft). On a remote server only unlocked recipes are "
                + "known")
                *///?} else {
                + "crafting table; x<n> items (default one craft)")
                //?}
            .field("crafted", "items made")
            .example("craft oak_planks x8"));
    }

    private static Minecraft minecraft() {
        return Minecraft.getInstance();
    }

    private static LocalPlayer player() {
        return minecraft().player;
    }

    /** Walks into reach if needed, aims at the block, uses it, and waits for a container screen. */
    private static Action openAt(int session, BlockPos pos) {
        return new Action() {
            private Navigator walking;
            private int step;

            @Override
            public Result tick() throws CommandException {
                Vec3 center = Vec3.atCenterOf(pos);
                if (step == 0 && center.distanceTo(player().getEyePosition()) > PlayerActions.blockReach() - 0.5) {
                    if (walking == null) {
                        walking = new Navigator(session, () -> Vec3.atBottomCenterOf(pos), 3);
                    }
                    if (walking.tick() != null) {
                        walking = null;
                    }
                    return null;
                }
                if (step == 0) {
                    PlayerActions.lookAt(center);
                    step = 1;
                    return null;
                }
                if (step == 1) {
                    HitResult hit = minecraft().hitResult;
                    if (!(hit instanceof BlockHitResult blockHit) || !blockHit.getBlockPos().equals(pos)) {
                        throw new CommandException("out_of_reach", Rel.of(pos) + " is not visible");
                    }
                    PlayerActions.click(minecraft().options.keyUse);
                    step = 2;
                    return null;
                }
                if (ClientScreens.current() instanceof AbstractContainerScreen<?> screen && screen.getMenu() != player()
                    .inventoryMenu) {
                    return Result.ok(new Fields().bare("screen", Screens.type(screen)));
                }
                if (++step > 40) {
                    throw new CommandException("no_screen", "nothing opened at " + Rel.of(pos));
                }
                return null;
            }

            @Override
            public void cancel() {
                if (walking != null) {
                    walking.cancel();
                }
            }

            @Override
            public String label() {
                return "open";
            }
        };
    }

    private Action open(CommandContext context, Args args) throws CommandException {
        return openAt(context.sessionId(), Rel.block(context.resolve(args.position("pos"))));
    }

    private static AbstractContainerMenu openMenu() throws CommandException {
        LocalPlayer player = player();
        if (!(ClientScreens.current() instanceof AbstractContainerScreen<?>) || player.containerMenu == player.inventoryMenu) {
            throw new CommandException("no_screen", "open a container first");
        }
        return player.containerMenu;
    }

    private static boolean inPlayerInventory(Slot slot) {
        return slot.container == player().getInventory();
    }

    private Action take(CommandContext context, Args args) throws CommandException {
        return transfer(args, false);
    }

    private Action put(CommandContext context, Args args) throws CommandException {
        return transfer(args, true);
    }

    /**
     * Moves items between the container and the inventory: whole stacks with shift-clicks, and an exact remainder by
     * picking the stack up, placing single items with right-clicks and putting the rest back.
     */
    private static Action transfer(Args args, boolean intoContainer) throws CommandException {
        AbstractContainerMenu menu = openMenu();
        String what = args.word("what");
        int wanted = args.has("count") ? args.integer("count", 0) : Integer.MAX_VALUE;
        Item item = null;
        Integer onlySlot = null;
        if (args.type("what") == ArgType.MENU_SLOT) {
            onlySlot = args.index("what", 0);
            if (onlySlot >= menu.slots.size()) {
                throw new CommandException("syntax", "the menu has slots s0-s" + (menu.slots.size() - 1));
            }
        } else {
            item = InventoryOps.item(what);
        }
        int moved = 0;
        for (int index = 0; index < menu.slots.size() && moved < wanted; index++) {
            Slot slot = menu.slots.get(index);
            boolean source = inPlayerInventory(slot) == intoContainer;
            if (!source || !slot.hasItem() || onlySlot != null && onlySlot != index
                || item != null && !slot.getItem().is(item)) {
                continue;
            }
            int count = slot.getItem().getCount();
            if (count <= wanted - moved) {
                click(menu, index, 0, ClickType.QUICK_MOVE);
                moved += count - slot.getItem().getCount();
            } else {
                moved += partial(menu, index, wanted - moved, intoContainer);
            }
        }
        if (moved == 0) {
            throw new CommandException("no_item", what + (intoContainer ? " in the inventory" : " in the container")
                + " (or no room)");
        }
        int total = moved;
        return () -> Result.ok(new Fields().put("moved", total, "moved " + total));
    }

    /** Moves exactly {@code amount} items of a larger stack one by one; returns how many went. */
    private static int partial(AbstractContainerMenu menu, int from, int amount, boolean intoContainer) {
        click(menu, from, 0, ClickType.PICKUP);
        int placed = 0;
        for (int index = 0; index < menu.slots.size() && placed < amount; index++) {
            Slot target = menu.slots.get(index);
            if (inPlayerInventory(target) == intoContainer || index == from) {
                continue;
            }
            boolean fits = !target.hasItem() || ItemStack.isSameItemSameComponents(target.getItem(), menu.getCarried());
            while (fits && placed < amount && !menu.getCarried().isEmpty()
                && target.getItem().getCount() < target.getMaxStackSize(menu.getCarried())) {
                int before = target.getItem().getCount();
                click(menu, index, 1, ClickType.PICKUP);
                if (target.getItem().getCount() == before) {
                    break;
                }
                placed++;
            }
        }
        click(menu, from, 0, ClickType.PICKUP);
        return placed;
    }

    private static void click(AbstractContainerMenu menu, int slot, int button, ClickType type) {
        minecraft().gameMode.handleInventoryMouseClick(menu.containerId, slot, button, type, player());
    }

    private Action craft(CommandContext context, Args args) throws CommandException {
        Item item = InventoryOps.item(args.word("item"));
        List<CraftingCandidate> recipes = CraftingRecipes.forItem(item);
        if (recipes.isEmpty()) {
            throw new CommandException("no_recipe", args.word("item"));
        }
        int wanted = args.count("count", recipes.get(0).resultCount());
        boolean tableOpen = ClientScreens.current() instanceof CraftingScreen;
        // Several recipes can make one item (sticks from planks or bamboo): prefer one the inventory can make, and
        // one that fits the 2x2 grid unless a table is open.
        List<CraftingCandidate> craftable = recipes.stream().filter(ContainerCommands::haveIngredients).toList();
        List<CraftingCandidate> candidates = craftable.isEmpty() ? recipes : craftable;
        Optional<CraftingCandidate> small = candidates.stream().filter(CraftingCandidate::fitsSmallGrid).findFirst();
        CraftingCandidate recipe = tableOpen || small.isEmpty() ? candidates.get(0) : small.get();
        boolean needsTable = !recipe.fitsSmallGrid();
        Action opening = null;
        if (needsTable && !tableOpen) {
            BlockPos table = nearestTable();
            if (table == null) {
                throw new CommandException("need_table", "no crafting table within " + TABLE_RANGE + " blocks");
            }
            opening = openAt(context.sessionId(), table);
        } else if (!tableOpen && ClientScreens.current() != null) {
            minecraft().player.closeContainer();
        }
        return new Crafting(opening, recipe, item, wanted);
    }

    /** Whether the inventory holds one craft's worth of the recipe's ingredients. */
    private static boolean haveIngredients(CraftingCandidate recipe) {
        List<ItemStack> stock = new ArrayList<>();
        for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
            stock.add(player().getInventory().getItem(slot).copy());
        }
        for (Optional<Predicate<ItemStack>> slot : recipe.ingredients()) {
            if (slot.isEmpty()) {
                continue;
            }
            Predicate<ItemStack> ingredient = slot.get();
            ItemStack match = stock.stream().filter(stack -> !stack.isEmpty() && ingredient.test(stack)).findFirst()
                .orElse(null);
            if (match == null) {
                return false;
            }
            match.shrink(1);
        }
        return true;
    }

    private static BlockPos nearestTable() {
        BlockPos center = player().blockPosition();
        BlockPos best = null;
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-TABLE_RANGE, -TABLE_RANGE, -TABLE_RANGE),
            center.offset(TABLE_RANGE, TABLE_RANGE, TABLE_RANGE))) {
            if (minecraft().level.getBlockState(pos).is(Blocks.CRAFTING_TABLE)
                && (best == null || pos.distSqr(center) < best.distSqr(center))) {
                best = pos.immutable();
            }
        }
        return best;
    }

    /** Places the recipe with the recipe book, takes the result with a shift-click, until enough are made. */
    private static final class Crafting implements Action {
        private final Action opening;
        private final CraftingCandidate recipe;
        private final Item item;
        private final int wanted;
        private boolean opened;
        private int made;
        private int waiting;
        private boolean placed;

        Crafting(Action opening, CraftingCandidate recipe, Item item, int wanted) {
            this.opening = opening;
            this.recipe = recipe;
            this.item = item;
            this.wanted = wanted;
            this.opened = opening == null;
        }

        @Override
        public Result tick() throws CommandException {
            if (!opened) {
                if (opening.tick() == null) {
                    return null;
                }
                opened = true;
            }
            LocalPlayer player = player();
            AbstractContainerMenu menu = player.containerMenu;
            if (!placed) {
                // The recipe book only places unlocked recipes; others are laid out click by click.
                if (recipe.placeWithBook() != null) {
                    recipe.placeWithBook().accept(menu.containerId);
                } else if (!placeByHand(menu)) {
                    clearGrid(menu);
                    if (made == 0) {
                        closeOpened();
                        throw new CommandException("missing", "ingredients for " + Ids.of(new ItemStack(item)));
                    }
                    return done();
                }
                placed = true;
                waiting = 0;
                return null;
            }
            Slot result = menu.getSlot(0);
            if (!result.hasItem() || !result.getItem().is(item)) {
                if (++waiting < 10) {
                    return null;
                }
                if (made == 0) {
                    clearGrid(menu);
                    closeOpened();
                    throw new CommandException("missing", "ingredients for " + Ids.of(new ItemStack(item)));
                }
                clearGrid(menu);
                return done();
            }
            int count = InventoryOps.count(item);
            click(menu, 0, 0, ClickType.QUICK_MOVE);
            made += InventoryOps.count(item) - count;
            placed = false;
            if (made < wanted) {
                return null;
            }
            clearGrid(menu);
            return done();
        }

        private Result done() {
            closeOpened();
            return Result.ok(new Fields().put("crafted", made, "craft " + made + " " + Ids.of(new ItemStack(item))));
        }

        /** Closes the crafting table this craft opened, so later world actions aren't stuck behind its screen. */
        private void closeOpened() {
            if (opening != null && opened) {
                PlayerActions.closeScreen();
            }
        }

        /**
         * Lays one craft's ingredients into the grid by hand: for each ingredient, pick up a matching stack, drop one
         * item into its grid slot with a right-click, and put the stack back. Shaped recipes keep their pattern.
         * Returns false when an ingredient is missing.
         */
        private boolean placeByHand(AbstractContainerMenu menu) {
            int gridWidth = menu == player().inventoryMenu ? 2 : 3;
            List<Optional<Predicate<ItemStack>>> ingredients = recipe.ingredients();
            int recipeWidth = recipe.width() > 0 ? recipe.width() : gridWidth;
            for (int index = 0; index < ingredients.size(); index++) {
                if (ingredients.get(index).isEmpty()) {
                    continue;
                }
                int gridSlot = 1 + (index / recipeWidth) * gridWidth + index % recipeWidth;
                int source = findIngredient(menu, ingredients.get(index).get());
                if (source < 0 || gridSlot >= 1 + gridWidth * gridWidth) {
                    return false;
                }
                click(menu, source, 0, ClickType.PICKUP);
                click(menu, gridSlot, 1, ClickType.PICKUP);
                click(menu, source, 0, ClickType.PICKUP);
            }
            return true;
        }

        private static int findIngredient(AbstractContainerMenu menu, Predicate<ItemStack> ingredient) {
            for (int index = 0; index < menu.slots.size(); index++) {
                Slot slot = menu.slots.get(index);
                if (inPlayerInventory(slot) && slot.hasItem() && ingredient.test(slot.getItem())) {
                    return index;
                }
            }
            return -1;
        }

        /** Puts ingredients left in the grid back into the inventory (slots 1-4 or 1-9 after the result slot). */
        private static void clearGrid(AbstractContainerMenu menu) {
            int gridSlots = menu == player().inventoryMenu ? 4 : 9;
            for (int slot = 1; slot <= gridSlots; slot++) {
                if (menu.getSlot(slot).hasItem()) {
                    click(menu, slot, 0, ClickType.QUICK_MOVE);
                }
            }
        }

        @Override
        public void cancel() {
            if (opening != null && !opened) {
                opening.cancel();
            }
            closeOpened();
        }

        @Override
        public String label() {
            return "craft";
        }
    }
}
