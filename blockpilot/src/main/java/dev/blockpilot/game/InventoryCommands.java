package dev.blockpilot.game;

import static dev.blockpilot.game.PlayerActions.closeScreen;
import static dev.blockpilot.game.PlayerActions.player;

import dev.blockpilot.api.Action;
import dev.blockpilot.api.ArgType;
import dev.blockpilot.api.Args;
import dev.blockpilot.api.CommandContext;
import dev.blockpilot.api.CommandException;
import dev.blockpilot.api.CommandSpec;
import dev.blockpilot.api.Fields;
import dev.blockpilot.api.Result;
import dev.blockpilot.protocol.CommandRegistry;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Commands that rearrange what the player carries. */
final class InventoryCommands {
    void register(CommandRegistry registry) {
        registry.register(CommandSpec.action("select", this::select).needsWorld().group("inventory")
            .arg("what", ArgType.INT, ArgType.ID)
            .help("selects hotbar slot 0-8, or puts the item into the selected slot")
            .example("select iron_pickaxe"));
        registry.register(CommandSpec.action("swap", this::swap).needsWorld().group("inventory")
            .form(form -> form.arg("a", InventoryOps.SLOT_NAMES, ArgType.INT)
                .arg("b", InventoryOps.SLOT_NAMES, ArgType.INT))
            .help("swaps two inventory slots (0-35, head, chest, legs, feet, offhand)").example("swap 0 9"));
        registry.register(CommandSpec.action("drop", this::drop).needsWorld().group("inventory")
            .form(form -> form.arg("what", InventoryOps.SLOT_NAMES, ArgType.INT, ArgType.ID)
                .optionalArg("count", ArgType.INT))
            .help("drops an item or a slot's stack (all by default)").example("drop dirt 16"));
        registry.register(CommandSpec.action("offhand", this::offhand).needsWorld().group("inventory")
            .arg("item", ArgType.ID).help("moves the item to the offhand").example("offhand shield"));
        registry.register(CommandSpec.action("equip", this::equip).needsWorld().group("inventory")
            .arg("item", ArgType.ID).help("puts on an armor item").example("equip iron_helmet"));
    }

    private Action select(CommandContext context, Args args) throws CommandException {
        closeScreen();
        if (args.type("what") == ArgType.INT) {
            int slot = args.integer("what", 0);
            if (slot < 0 || slot > 8) {
                throw new CommandException("syntax", "hotbar slots are 0-8, not " + slot);
            }
            InventoryOps.selectSlot(slot);
        } else {
            InventoryOps.select(InventoryOps.item(args.word("what")));
        }
        return () -> Result.ok(new Fields().put("hand", Ids.of(player().getMainHandItem()),
            "hand:" + Ids.of(player().getMainHandItem())));
    }

    private Action swap(CommandContext context, Args args) throws CommandException {
        closeScreen();
        int a = InventoryOps.menuSlotOf(args.word("a"));
        int b = InventoryOps.menuSlotOf(args.word("b"));
        InventoryOps.click(a, 0, ClickType.PICKUP);
        InventoryOps.click(b, 0, ClickType.PICKUP);
        InventoryOps.click(a, 0, ClickType.PICKUP);
        return () -> Result.ok();
    }

    private Action drop(CommandContext context, Args args) throws CommandException {
        closeScreen();
        String what = args.word("what");
        int menuSlot;
        if (args.type("what") != ArgType.ID) {
            menuSlot = InventoryOps.menuSlotOf(what);
        } else {
            int index = InventoryOps.find(InventoryOps.item(what));
            if (index < 0) {
                throw new CommandException("no_item", what);
            }
            menuSlot = InventoryOps.menuSlot(index);
        }
        ItemStack stack = player().inventoryMenu.getSlot(menuSlot).getItem();
        if (stack.isEmpty()) {
            throw new CommandException("no_item", "slot " + what + " is empty");
        }
        // Read before throwing: a throw empties this same stack.
        String item = Ids.of(stack);
        int count = Math.min(args.integer("count", stack.getCount()), stack.getCount());
        if (count >= stack.getCount()) {
            InventoryOps.click(menuSlot, 1, ClickType.THROW);
        } else {
            for (int index = 0; index < count; index++) {
                InventoryOps.click(menuSlot, 0, ClickType.THROW);
            }
        }
        return () -> Result.ok(new Fields().bare("item", item).put("count", count, "x" + count));
    }

    private Action offhand(CommandContext context, Args args) throws CommandException {
        closeScreen();
        Item item = InventoryOps.item(args.word("item"));
        int index = InventoryOps.find(item);
        if (index < 0) {
            throw new CommandException("no_item", args.word("item"));
        }
        InventoryOps.click(InventoryOps.menuSlot(index), InventoryOps.OFFHAND_BUTTON, ClickType.SWAP);
        return () -> Result.ok();
    }

    private Action equip(CommandContext context, Args args) throws CommandException {
        closeScreen();
        Item item = InventoryOps.item(args.word("item"));
        int index = InventoryOps.find(item);
        if (index < 0) {
            throw new CommandException("no_item", args.word("item"));
        }
        InventoryOps.click(InventoryOps.menuSlot(index), 0, ClickType.QUICK_MOVE);
        return () -> {
            for (int slot = InventoryMenu.ARMOR_SLOT_START; slot < InventoryMenu.ARMOR_SLOT_END; slot++) {
                if (player().inventoryMenu.getSlot(slot).getItem().is(item)) {
                    return Result.ok();
                }
            }
            throw new CommandException("no_item", args.word("item") + " is not armor or the slot is taken");
        };
    }
}
