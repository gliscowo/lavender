package io.wispforest.lavender.book;

import com.google.common.collect.ImmutableSet;
import io.wispforest.lavender.mixin.access.ClientAdvancementManagerAccessor;
import io.wispforest.owo.ui.core.Component;
import io.wispforest.owo.ui.core.Sizing;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;

import java.util.List;
import java.util.function.Function;

public record Entry(
        Identifier id,
        List<Identifier> categories,
        String title,
        Function<Sizing, Component> iconFactory,
        boolean secret,
        int ordinal,
        ImmutableSet<Identifier> requiredAdvancements,
        ImmutableSet<ItemStack> associatedItems,
        ImmutableSet<String> additionalSearchTerms,
        String content
) implements Book.BookmarkableElement {

    public boolean canPlayerView(ClientPlayerEntity player) {
        var advancementHandler = player.networkHandler.getAdvancementHandler();

        for (var advancementId : this.requiredAdvancements) {
            var advancement = advancementHandler.getManager().get(advancementId);
            if (advancement == null) return false;

            var progress = ((ClientAdvancementManagerAccessor) advancementHandler).lavender$getAdvancementProgresses().get(advancement.getAdvancementEntry());
            if (progress == null || !progress.isDone()) return false;
        }

        return true;
    }

}
