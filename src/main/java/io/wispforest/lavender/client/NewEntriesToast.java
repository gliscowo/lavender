package io.wispforest.lavender.client;

import io.wispforest.lavender.Lavender;
import io.wispforest.lavender.book.Book;
import io.wispforest.owo.ui.base.BaseOwoToast;
import io.wispforest.owo.ui.component.Components;
import io.wispforest.owo.ui.container.Containers;
import io.wispforest.owo.ui.container.StackLayout;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.VerticalAlignment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

@SuppressWarnings("UnstableApiUsage")
public class NewEntriesToast extends BaseOwoToast<StackLayout> {

    public static final Identifier TEXTURE = Lavender.id("new_entries_toast");

    public NewEntriesToast(Book.ToastSettings settings) {
        super(
            () -> Containers.stack(Sizing.content(), Sizing.content()).configure(component -> component
                .child(Components.sprite(MinecraftClient.getInstance().getGuiAtlasManager().getSprite(settings.backgroundSprite() != null ? settings.backgroundSprite() : TEXTURE)))
                .child(Containers.horizontalFlow(Sizing.content(), Sizing.content())
                    .child(Components.item(settings.iconStack()).margins(Insets.of(0, 0, 8, 6)))
                    .child(Components.label(Text.translatable("text.lavender.toast.new_entries", settings.bookName())))
                    .verticalAlignment(VerticalAlignment.CENTER))
                .verticalAlignment(VerticalAlignment.CENTER)),
            (baseOwoToast, time) -> time <= 5000 ? Visibility.SHOW : Visibility.HIDE
        );
    }
}
