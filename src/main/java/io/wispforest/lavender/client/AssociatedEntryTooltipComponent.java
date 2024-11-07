package io.wispforest.lavender.client;

import io.wispforest.lavender.book.Entry;
import io.wispforest.owo.ui.component.Components;
import io.wispforest.owo.ui.container.Containers;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.core.*;
import io.wispforest.owo.ui.util.Delta;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.TooltipComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;

public class AssociatedEntryTooltipComponent implements TooltipComponent {

    public static float entryTriggerProgress = 0f;
    public static @Nullable WeakReference<ItemStack> tooltipStack = null;

    private final FlowLayout layout;

    public AssociatedEntryTooltipComponent(ItemStack book, Entry entry, float progress) {
        this.layout = Containers.horizontalFlow(Sizing.content(), Sizing.content()).gap(2);

        this.layout.child(Containers.verticalFlow(Sizing.content(), Sizing.content())
            .child(entry.iconFactory().apply(Sizing.fixed(16)).margins(Insets.of(2)))
            .child(Components.item(book).sizing(Sizing.fixed(8)).positioning(Positioning.absolute(11, 11)).zIndex(50)));

        this.layout.child(Containers.verticalFlow(Sizing.content(), Sizing.content())
            .child(Components.label(Text.literal(entry.title()).formatted(Formatting.GRAY)))
            .child(Components.label(progress >= .05f
                ? Text.translatable("text.lavender.entry_tooltip.progress", "|".repeat((int) (30 * progress)), "|".repeat((int) Math.ceil(30 * (1 - progress))))
                : Text.translatable("text.lavender.entry_tooltip"))));

        this.layout.verticalAlignment(VerticalAlignment.CENTER);

        this.layout.inflate(Size.of(1000, 1000));
        this.layout.mount(null, 0, 0);
    }

    @Override
    public void drawItems(TextRenderer textRenderer, int x, int y, int width, int height, DrawContext context) {
        context = OwoUIDrawContext.of(context);
        context.getMatrices().push();
        context.getMatrices().translate(0, 0, 1000);

        this.layout.moveTo(x, y);
        this.layout.draw((OwoUIDrawContext) context, 0, 0, 0, 0);

        context.getMatrices().pop();
    }

    @Override
    public int getHeight(TextRenderer textRenderer) {
        return this.layout.height();
    }

    @Override
    public int getWidth(TextRenderer textRenderer) {
        return this.layout.width();
    }

    static {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (Screen.hasAltDown()) return;
            entryTriggerProgress += Delta.compute(entryTriggerProgress, 0f, .125f);
        });
    }
}
