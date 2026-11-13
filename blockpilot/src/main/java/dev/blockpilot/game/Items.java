package dev.blockpilot.game;

import dev.blockpilot.api.Fields;
import dev.blockpilot.game.common.Ids;
import java.util.ArrayList;
import java.util.List;
//? if >=1.21 {
import net.minecraft.core.component.DataComponents;
//?} else {
/*import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
*///?}
import net.minecraft.world.item.ItemStack;

/** Item stacks as agents read them: {@code <slot>:<item> x<count>}, durability only when damaged. */
final class Items {
    private Items() {
    }

    static Fields describe(String slot, ItemStack stack, boolean detail) {
        Fields fields = new Fields().hidden("slot", slot).put("item", Ids.of(stack), slot + ":" + Ids.of(stack));
        if (stack.getCount() > 1) {
            fields.put("count", stack.getCount(), "x" + stack.getCount());
        }
        if (stack.isDamaged()) {
            int remaining = stack.getMaxDamage() - stack.getDamageValue();
            fields.put("dur", remaining, "dur" + remaining + "/" + stack.getMaxDamage()).hidden("max_dur",
                stack.getMaxDamage());
        }
        if (detail) {
            List<String> enchantments = new ArrayList<>();
            //? if >=1.21 {
            stack.getEnchantments().entrySet().forEach(entry -> entry.getKey().unwrapKey().ifPresent(key ->
                enchantments.add(Ids.of(key.location()) + entry.getIntValue())));
            //?} else {
            /*EnchantmentHelper.deserializeEnchantments(stack.getEnchantmentTags()).forEach((enchantment, level) ->
                BuiltInRegistries.ENCHANTMENT.getResourceKey(enchantment).ifPresent(key ->
                    enchantments.add(Ids.of(key.location()) + level)));
            *///?}
            if (!enchantments.isEmpty()) {
                fields.put("ench", enchantments, "ench:" + String.join(",", enchantments));
            }
            //? if >=1.21 {
            if (stack.has(DataComponents.CUSTOM_NAME)) {
            //?} else {
            /*if (stack.hasCustomHoverName()) {
            *///?}
                String name = stack.getHoverName().getString();
                fields.put("name", name, "\"" + name + "\"");
            }
        }
        return fields;
    }
}
