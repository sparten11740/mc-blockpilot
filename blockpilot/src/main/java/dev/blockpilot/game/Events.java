package dev.blockpilot.game;

import dev.blockpilot.api.EntityRef;
import dev.blockpilot.api.Event;
import dev.blockpilot.api.Fields;
import dev.blockpilot.game.common.ClientScreens;
import dev.blockpilot.game.observe.CameraControl;
import dev.blockpilot.protocol.Engine;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * Turns what happens to the player into events: hurt, healed, died, items gained or lost, screens opened and
 * closed, dimension changes and chat. Mostly by comparing the player's state from tick to tick, which works the
 * same on every loader.
 */
final class Events {
    private final Engine engine;
    private float lastHealth = -1;
    private boolean wasDead;
    private String lastScreen;
    private String lastDimension;
    private Map<String, Integer> lastItems;

    Events(Engine engine) {
        this.engine = engine;
    }

    void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        String screen = ClientScreens.current() == null ? null : Screens.type(ClientScreens.current());
        if (lastScreen != null && screen == null) {
            engine.broadcast(Event.of("screen_closed", new Fields().bare("screen", lastScreen)));
        }
        lastScreen = screen;
        if (player == null || minecraft.level == null) {
            CameraControl.reset();
            lastHealth = -1;
            lastItems = null;
            lastDimension = null;
            wasDead = false;
            return;
        }
        String dimension = Ids.of(minecraft.level.dimension().location());
        if (lastDimension != null && !lastDimension.equals(dimension)) {
            engine.broadcast(Event.of("dimension", new Fields().bare("dim", dimension)));
        }
        lastDimension = dimension;
        health(player);
        items(player.getInventory());
    }

    private void health(LocalPlayer player) {
        float health = player.getHealth();
        if (lastHealth >= 0 && health < lastHealth - 0.01f) {
            Fields fields = new Fields().bare("amount", Math.round((lastHealth - health) * 10) / 10.0);
            DamageSource source = player.getLastDamageSource();
            if (source != null) {
                fields.bare("cause", source.getMsgId());
                if (source.getEntity() != null) {
                    fields.bare("by", Ids.of(source.getEntity())).bare("by_ref", new EntityRef(source.getEntity().getId()));
                }
            }
            fields.tag("hp", Math.round(health * 10) / 10.0);
            engine.broadcast(Event.of("hurt", fields));
        }
        lastHealth = health;
        boolean dead = player.isDeadOrDying();
        if (dead && !wasDead) {
            DamageSource source = player.getLastDamageSource();
            engine.broadcast(Event.of("died", new Fields().bare("cause", source == null ? "unknown" : source.getMsgId())));
        }
        wasDead = dead;
    }

    private void items(Inventory inventory) {
        Map<String, Integer> counts = new HashMap<>();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty()) {
                counts.merge(Ids.of(stack), stack.getCount(), Integer::sum);
            }
        }
        if (lastItems != null) {
            Map<String, Integer> before = lastItems;
            counts.forEach((item, count) -> {
                int change = count - before.getOrDefault(item, 0);
                if (change > 0) {
                    engine.broadcast(Event.of("got", new Fields().bare("item", item).put("count", change, "x" + change)));
                }
            });
            before.forEach((item, count) -> {
                int change = count - counts.getOrDefault(item, 0);
                if (change > 0) {
                    engine.broadcast(Event.of("lost", new Fields().bare("item", item).put("count", change, "x" + change)));
                }
            });
        }
        lastItems = counts;
    }

    void chat(Component message) {
        engine.broadcast(Event.of("chat", new Fields().bare("text", message.getString())));
    }

    void screenOpened(Screen screen) {
        String type = Screens.type(screen);
        if (!type.equals(lastScreen)) {
            engine.broadcast(Event.of("screen", new Fields().bare("screen", type)));
        }
    }
}
