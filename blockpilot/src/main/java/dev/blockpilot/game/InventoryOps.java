package dev.blockpilot.game;

import dev.blockpilot.api.CommandException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Inventory moves done the way a player does them: clicks in the player's inventory menu, sent through the game
 * mode, so servers see ordinary inventory interactions.
 */
final class InventoryOps {
    static final int OFFHAND_BUTTON = 40;

    private InventoryOps() {
    }

    private static LocalPlayer player() {
        return Minecraft.getInstance().player;
    }

    static Item item(String id) throws CommandException {
        var key = Ids.parse(id);
        if (!BuiltInRegistries.ITEM.containsKey(key)) {
            throw new CommandException("syntax", "unknown item " + id);
        }
        return BuiltInRegistries.ITEM.get(key);
    }

    /** The inventory index (0-35) holding the item, hotbar first, or -1. */
    static int find(Item item) {
        Inventory inventory = player().getInventory();
        for (int slot = 0; slot < inventory.items.size(); slot++) {
            if (inventory.items.get(slot).is(item)) {
                return slot;
            }
        }
        return -1;
    }

    /** How many of the item the player carries (main inventory and hotbar). */
    static int count(Item item) {
        Inventory inventory = player().getInventory();
        int total = 0;
        for (ItemStack stack : inventory.items) {
            if (stack.is(item)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    /** The inventory menu's slot number for an inventory index (hotbar 0-8 sits at 36-44). */
    static int menuSlot(int inventoryIndex) {
        return inventoryIndex < 9 ? InventoryMenu.USE_ROW_SLOT_START + inventoryIndex : inventoryIndex;
    }

    static void click(int menuSlot, int button, ClickType type) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.gameMode.handleInventoryMouseClick(player().inventoryMenu.containerId, menuSlot, button, type,
            player());
    }

    /** Puts the item in the selected hotbar slot: selects it if on the hotbar, swaps it in from the main inventory. */
    static void select(Item item) throws CommandException {
        int slot = find(item);
        if (slot < 0) {
            throw new CommandException("no_item", Ids.of(new ItemStack(item)));
        }
        selectSlot(slot);
    }

    static void selectSlot(int slot) {
        Inventory inventory = player().getInventory();
        if (slot < 9) {
            inventory.selected = slot;
        } else {
            click(menuSlot(slot), inventory.selected, ClickType.SWAP);
        }
    }

    /** Selects the inventory item that breaks the block fastest, if any beats the empty hand. */
    static void selectBestTool(BlockState state) {
        Inventory inventory = player().getInventory();
        int best = -1;
        float bestSpeed = 1.0f;
        for (int slot = 0; slot < inventory.items.size(); slot++) {
            ItemStack stack = inventory.items.get(slot);
            float speed = stack.isEmpty() ? 1.0f : stack.getDestroySpeed(state);
            if (speed > bestSpeed) {
                bestSpeed = speed;
                best = slot;
            }
        }
        if (best >= 0) {
            selectSlot(best);
        }
    }

    /** Parses a slot token: 0-35, head/chest/legs/feet or offhand, as the inventory menu's slot number. */
    static int menuSlotOf(String token) throws CommandException {
        return switch (token) {
            case "head" -> InventoryMenu.ARMOR_SLOT_START;
            case "chest" -> InventoryMenu.ARMOR_SLOT_START + 1;
            case "legs" -> InventoryMenu.ARMOR_SLOT_START + 2;
            case "feet" -> InventoryMenu.ARMOR_SLOT_START + 3;
            case "offhand" -> InventoryMenu.SHIELD_SLOT;
            default -> {
                try {
                    int index = Integer.parseInt(token.replace("*", ""));
                    if (index < 0 || index > 35) {
                        throw new CommandException("syntax", "slot 0-35, head, chest, legs, feet or offhand");
                    }
                    yield menuSlot(index);
                } catch (NumberFormatException e) {
                    throw new CommandException("syntax", "slot 0-35, head, chest, legs, feet or offhand");
                }
            }
        };
    }
}
