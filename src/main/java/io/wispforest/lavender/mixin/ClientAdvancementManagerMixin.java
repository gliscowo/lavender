package io.wispforest.lavender.mixin;

import com.google.common.collect.Iterables;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import io.wispforest.lavender.book.Book;
import io.wispforest.lavender.book.BookLoader;
import io.wispforest.lavender.book.ClientNewEntriesUnlockedCallback;
import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientAdvancementManager;
import net.minecraft.network.packet.s2c.play.AdvancementUpdateS2CPacket;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientAdvancementManager.class)
public class ClientAdvancementManagerMixin {

    @Shadow
    @Final
    private MinecraftClient client;

    @Unique
    private boolean receivedInitialPacket = false;

    @Inject(method = "onAdvancements", at = @At("HEAD"))
    private void captureAdvancementsPreUpdate(AdvancementUpdateS2CPacket packet, CallbackInfo ci, @Share("entriesPreUpdate") LocalRef<Reference2IntMap<Book>> entriesPreUpdate) {
        if (!this.receivedInitialPacket) {
            return;
        }

        var entryCountByBook = new Reference2IntOpenHashMap<Book>();

        for (var book : Iterables.filter(BookLoader.loadedBooks(), book -> book.newEntriesToast() != null)) {
            entryCountByBook.put(book, book.countVisibleEntries(this.client.player));
        }

        entriesPreUpdate.set(entryCountByBook);
    }

    @Inject(method = "onAdvancements", at = @At("TAIL"))
    private void checkForNewAdvancements(AdvancementUpdateS2CPacket packet, CallbackInfo ci, @Share("entriesPreUpdate") LocalRef<Reference2IntMap<Book>> entriesPreUpdate) {
        if (!this.receivedInitialPacket) {
            this.receivedInitialPacket = true;
            return;
        }

        var entryCountByBook = entriesPreUpdate.get();

        for (var book : Iterables.filter(BookLoader.loadedBooks(), book -> book.newEntriesToast() != null)) {
            var newEntryCount = book.countVisibleEntries(this.client.player) - entryCountByBook.getInt(book);
            if (newEntryCount > 0) {
                ClientNewEntriesUnlockedCallback.EVENT.invoker().newEntriesUnlocked(this.client, book, newEntryCount);
            }
        }
    }
}
