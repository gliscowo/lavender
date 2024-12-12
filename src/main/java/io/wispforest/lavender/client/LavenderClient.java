package io.wispforest.lavender.client;

import io.wispforest.lavender.Lavender;
import io.wispforest.lavender.LavenderCommands;
import io.wispforest.lavender.book.*;
import io.wispforest.lavender.md.ItemListComponent;
import io.wispforest.lavender.structure.LavenderStructures;
import io.wispforest.owo.ui.component.Components;
import io.wispforest.owo.ui.container.Containers;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.Positioning;
import io.wispforest.owo.ui.core.Size;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.hud.Hud;
import io.wispforest.owo.ui.parsing.UIParsing;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

@Environment(EnvType.CLIENT)
public class LavenderClient implements ClientModInitializer {

    public static final BlitCutoutProgram BLIT_CUTOUT_PROGRAM = new BlitCutoutProgram();
    public static final BlitAlphaProgram BLIT_ALPHA_PROGRAM = new BlitAlphaProgram();

    private static final Int2ObjectMap<Size> TEXTURE_SIZES = new Int2ObjectOpenHashMap<>();
    private static final Identifier ENTRY_HUD_ID = Lavender.id("entry_hud");

    private static UUID currentWorldId = null;

    @Override
    public void onInitializeClient() {
        ClientCommandRegistrationCallback.EVENT.register(LavenderCommands.Client::register);

        ModelLoadingPlugin.register(pluginContext -> {
            pluginContext.resolveModel().register(context -> {
                if (!context.id().equals(Lavender.id("item/dynamic_book"))) return null;
                return new BookBakedModel.Unbaked();
            });
        });

        StructureOverlayRenderer.initialize();
        OffhandBookRenderer.initialize();

        LavenderStructures.initialize();
        BookLoader.initialize();
        BookContentLoader.initialize();

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            BookLoader.reload(MinecraftClient.getInstance().getResourceManager());
            BookContentLoader.reloadContents(MinecraftClient.getInstance().getResourceManager());
        });

        Hud.add(ENTRY_HUD_ID, () -> Containers.horizontalFlow(Sizing.content(), Sizing.content()).gap(5).positioning(Positioning.across(50, 52)));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.world == null || !(Hud.getComponent(ENTRY_HUD_ID) instanceof FlowLayout hudComponent)) return;

            hudComponent.<FlowLayout>configure(container -> {
                container.clearChildren();

                Book book = LavenderBookItem.bookOf(client.player.getMainHandStack());
                if (book == null) book = LavenderBookItem.bookOf(client.player.getOffHandStack());
                if (book == null) return;

                if (!(client.crosshairTarget instanceof BlockHitResult hitResult)) return;
                var item = client.world.getBlockState(hitResult.getBlockPos()).getBlock().asItem();
                if (item == Items.AIR) return;

                var associatedEntry = book.entryByAssociatedItem(item.getDefaultStack());
                if (associatedEntry == null || !associatedEntry.canPlayerView(client.player)) return;

                container.child(Containers.verticalFlow(Sizing.content(), Sizing.content())
                    .child(associatedEntry.iconFactory().apply(Sizing.fixed(16)).margins(Insets.of(0, 1, 0, 1)))
                    .child(Components.item(LavenderBookItem.itemOf(book)).sizing(Sizing.fixed(8)).positioning(Positioning.absolute(9, 9)).zIndex(50)));
                container.child(Containers.verticalFlow(Sizing.content(), Sizing.content())
                    .child(Components.label(Text.literal(associatedEntry.title())).shadow(true))
                    .child(Components.label(Text.translatable(client.player.isSneaking() ? "text.lavender.entry_hud.click_to_view" : "text.lavender.entry_hud.sneak_to_view"))));
            });
        });

        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            var stack = player.getStackInHand(hand);
            if (!player.isSneaking()) return ActionResult.PASS;

            var book = LavenderBookItem.bookOf(stack);
            if (book == null) return ActionResult.PASS;

            var item = world.getBlockState(hitResult.getBlockPos()).getBlock().asItem();
            if (item == Items.AIR) return ActionResult.PASS;

            var associatedEntry = book.entryByAssociatedItem(item.getDefaultStack());
            if (associatedEntry == null || !associatedEntry.canPlayerView((ClientPlayerEntity) player)) {
                return ActionResult.PASS;
            }

            LavenderBookScreen.pushEntry(book, associatedEntry);
            MinecraftClient.getInstance().setScreen(new LavenderBookScreen(book));

            player.swingHand(hand);
            return ActionResult.FAIL;
        });

        ClientNewEntriesUnlockedCallback.EVENT.register((client, book, newEntryCount) -> {
            if (book.newEntriesToast() != null) {
                client.getToastManager().add(new NewEntriesToast(book.newEntriesToast()));
            }
        });

        ClientPlayNetworking.registerGlobalReceiver(Lavender.WorldUUIDPayload.ID, (payload, context) -> {
            currentWorldId = payload.worldUuid();
        });

        UIParsing.registerFactory(Lavender.id("ingredient"), element -> {
            Lavender.LOGGER.warn("Deprecated <ingredient> element used, migrate to <item-list> instead");
            return new ItemListComponent();
        });

        UIParsing.registerFactory(Lavender.id("item-list"), element -> new ItemListComponent());
    }

    public static UUID currentWorldId() {
        return currentWorldId;
    }

    public static void registerTextureSize(int textureId, int width, int height) {
        TEXTURE_SIZES.put(textureId, Size.of(width, height));
    }

    public static @Nullable Size getTextureSize(Identifier texture) {
        return TEXTURE_SIZES.get(MinecraftClient.getInstance().getTextureManager().getTexture(texture).getGlId());
    }
}
