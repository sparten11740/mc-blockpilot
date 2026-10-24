package dev.blockpilot.game;

import dev.blockpilot.api.Fields;
import dev.blockpilot.api.Result;
import dev.blockpilot.api.Row;
import dev.blockpilot.mixin.AbstractContainerScreenAccessor;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.layouts.LayoutElement;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.Slot;

/** Describes the open GUI so agents can read it and click, type and drag on it by GUI-scaled coordinates. */
final class Screens {
    /** Enough for a double chest; the creative inventory's item list is capped. */
    private static final int MAX_SLOT_ROWS = 64;

    private Screens() {
    }

    /** A stable short name for the screen; container screens use their menu type. */
    static String type(Screen screen) {
        if (screen instanceof TitleScreen) {
            return "title";
        }
        if (screen instanceof PauseScreen) {
            return "pause";
        }
        if (screen instanceof DeathScreen) {
            return "death";
        }
        if (screen instanceof ChatScreen) {
            return "chat";
        }
        if (screen instanceof InventoryScreen) {
            return "inventory";
        }
        if (screen instanceof CreativeModeInventoryScreen) {
            return "creative_inventory";
        }
        if (screen instanceof SelectWorldScreen) {
            return "select_world";
        }
        if (screen instanceof JoinMultiplayerScreen) {
            return "multiplayer";
        }
        if (screen instanceof ConnectScreen || screen instanceof LevelLoadingScreen
            || screen instanceof ReceivingLevelScreen || screen instanceof GenericMessageScreen) {
            return "loading";
        }
        if (screen instanceof DisconnectedScreen) {
            return "disconnected";
        }
        if (screen instanceof AbstractContainerScreen<?> container) {
            try {
                return Ids.of(BuiltInRegistries.MENU.getKey(container.getMenu().getType()));
            } catch (UnsupportedOperationException e) {
                return "container";
            }
        }
        String name = screen.getClass().getSimpleName().replaceAll("Screen$", "");
        return name.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase();
    }

    static Result describe() {
        Minecraft minecraft = Minecraft.getInstance();
        Screen screen = minecraft.screen;
        if (screen == null) {
            return Result.ok(new Fields().put("screen", "none", "none")).withRows(List.of());
        }
        var window = minecraft.getWindow();
        int cursorX = (int) (minecraft.mouseHandler.xpos() * window.getGuiScaledWidth() / window.getScreenWidth());
        int cursorY = (int) (minecraft.mouseHandler.ypos() * window.getGuiScaledHeight() / window.getScreenHeight());
        Fields fields = new Fields().bare("screen", type(screen));
        String title = screen.getTitle().getString();
        if (!title.isEmpty()) {
            fields.put("title", title, "\"" + title + "\"");
        }
        fields.put("size", List.of(window.getGuiScaledWidth(), window.getGuiScaledHeight()),
                "size " + window.getGuiScaledWidth() + "x" + window.getGuiScaledHeight())
            .put("scale", window.getGuiScale(), "scale" + window.getGuiScale())
            .put("cursor", List.of(cursorX, cursorY), "cursor " + cursorX + "," + cursorY);
        List<Row> rows = new ArrayList<>();
        int more = 0;
        if (screen instanceof AbstractContainerScreen<?> container) {
            AbstractContainerScreenAccessor offsets = (AbstractContainerScreenAccessor) container;
            int index = 0;
            int listed = 0;
            for (Slot slot : container.getMenu().slots) {
                if (slot.hasItem() && listed++ >= MAX_SLOT_ROWS) {
                    more++;
                } else if (slot.hasItem()) {
                    var stack = slot.getItem();
                    Fields slotFields = new Fields().put("item", Ids.of(stack), "s" + index + ":" + Ids.of(stack));
                    if (stack.getCount() > 1) {
                        slotFields.put("count", stack.getCount(), "x" + stack.getCount());
                    }
                    slotFields.hidden("at", List.of(offsets.blockpilot$leftPos() + slot.x + 8,
                        offsets.blockpilot$topPos() + slot.y + 8));
                    rows.add(Row.of("s" + index, slotFields));
                }
                index++;
            }
            fields.put("slots", container.getMenu().slots.size(), "slots=" + container.getMenu().slots.size());
        }
        List<GuiEventListener> widgets = new ArrayList<>();
        collect(screen, widgets);
        for (int index = 0; index < widgets.size(); index++) {
            rows.add(Row.of("w" + index, widget(index, widgets.get(index))));
        }
        return Result.ok(fields).withRows(rows, more);
    }

    private static void collect(ContainerEventHandler parent, List<GuiEventListener> out) {
        for (GuiEventListener child : parent.children()) {
            if (child instanceof AbstractWidget || !(child instanceof ContainerEventHandler)) {
                out.add(child);
            } else {
                collect((ContainerEventHandler) child, out);
            }
        }
    }

    private static Fields widget(int index, GuiEventListener listener) {
        Fields fields = new Fields();
        String kind = kind(listener);
        fields.put("kind", kind, "w" + index + ":" + kind);
        if (listener instanceof AbstractWidget widget) {
            String label = widget.getMessage().getString();
            if (!label.isEmpty()) {
                fields.put("label", label, "\"" + label + "\"");
            }
            if (widget instanceof EditBox edit) {
                fields.put("value", edit.getValue(), "value=\"" + edit.getValue() + "\"");
            }
            if (widget instanceof Checkbox checkbox) {
                fields.put("checked", checkbox.selected(), checkbox.selected() ? "checked" : "unchecked");
            }
            if (!widget.active) {
                fields.put("active", false, "inactive");
            }
            if (widget.getTooltip() != null && label.isEmpty()) {
                fields.put("tooltip", true, "has_tooltip");
            }
        }
        if (listener instanceof LayoutElement element) {
            List<Integer> bounds = List.of(element.getX(), element.getY(), element.getWidth(), element.getHeight());
            fields.put("bounds", bounds, "@" + bounds.get(0) + "," + bounds.get(1) + "," + bounds.get(2) + ","
                + bounds.get(3));
        }
        return fields;
    }

    private static String kind(GuiEventListener listener) {
        if (listener instanceof EditBox) {
            return "text";
        }
        if (listener instanceof AbstractSliderButton) {
            return "slider";
        }
        if (listener instanceof Checkbox) {
            return "checkbox";
        }
        if (listener instanceof CycleButton<?>) {
            return "cycle";
        }
        if (listener instanceof Button) {
            return "button";
        }
        return listener.getClass().getSimpleName();
    }
}
