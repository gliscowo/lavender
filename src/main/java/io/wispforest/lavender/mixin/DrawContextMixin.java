package io.wispforest.lavender.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import io.wispforest.lavender.book.LavenderBookItem;
import io.wispforest.lavender.book.BookLoader;
import io.wispforest.lavender.client.AssociatedEntryTooltipComponent;
import io.wispforest.lavender.client.LavenderBookScreen;
import io.wispforest.owo.ui.util.Delta;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.TooltipComponent;
import net.minecraft.client.gui.tooltip.TooltipPositioner;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

import static io.wispforest.lavender.client.AssociatedEntryTooltipComponent.entryTriggerProgress;

@Mixin(DrawContext.class)
public class DrawContextMixin {

    @Inject(method = "drawTooltip(Lnet/minecraft/client/font/TextRenderer;Ljava/util/List;IILnet/minecraft/client/gui/tooltip/TooltipPositioner;Lnet/minecraft/util/Identifier;)V", at = @At("HEAD"))
    private void injectTooltipComponents(TextRenderer textRenderer, List<TooltipComponent> components, int x, int y, TooltipPositioner positioner, @Nullable Identifier texture, CallbackInfo ci, @Local(argsOnly = true) LocalRef<List<TooltipComponent>> componentsRef) {
        var client = MinecraftClient.getInstance();

        if (AssociatedEntryTooltipComponent.tooltipStack != null && AssociatedEntryTooltipComponent.tooltipStack.get() != null) {
            var stack = AssociatedEntryTooltipComponent.tooltipStack.get();
            AssociatedEntryTooltipComponent.tooltipStack = null;

            for (var book : BookLoader.loadedBooks()) {
                var associatedEntry = book.entryByAssociatedItem(stack);
                if (associatedEntry == null || !associatedEntry.canPlayerView(client.player)) continue;

                int bookIndex = -1;
                for (int i = 0; i < 9; i++) {
                    if (LavenderBookItem.bookOf(client.player.getInventory().getStack(i)) == book) {
                        bookIndex = i;
                        break;
                    }
                }

                if (LavenderBookItem.bookOf(client.player.getOffHandStack()) == book) {
                    bookIndex = -69;
                }

                if (bookIndex == -1) return;

                components = new ArrayList<>(components);
                components.add(new AssociatedEntryTooltipComponent(LavenderBookItem.itemOf(book), associatedEntry, entryTriggerProgress));
                componentsRef.set(components);

                entryTriggerProgress += Delta.compute(entryTriggerProgress, Screen.hasAltDown() ? 1.35f : 0f, client.getRenderTickCounter().getLastFrameDuration() * .125f);

                if (entryTriggerProgress >= .95) {
                    LavenderBookScreen.pushEntry(book, associatedEntry);
                    client.setScreen(new LavenderBookScreen(book));

                    if (bookIndex >= 0) {
                        client.player.getInventory().selectedSlot = bookIndex;
                    }

                    entryTriggerProgress = 0f;
                }

                return;
            }
        }
    }
}
