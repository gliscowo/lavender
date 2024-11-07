package io.wispforest.lavender;

import io.wispforest.endec.StructEndec;
import io.wispforest.endec.impl.StructEndecBuilder;
import io.wispforest.lavender.client.LavenderBookScreen;
import io.wispforest.owo.serialization.CodecUtils;
import io.wispforest.owo.serialization.endec.MinecraftEndecs;
import it.unimi.dsi.fastutil.objects.Reference2LongMap;
import it.unimi.dsi.fastutil.objects.Reference2LongOpenHashMap;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.recipe.Recipe;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class LavenderClientRecipeCache {

    private static final Map<Identifier, RecipeEntry<?>> RECIPE_CACHE = new HashMap<>();
    private static final Reference2LongMap<Identifier> LAST_FETCHED_TIMESTAMP = new Reference2LongOpenHashMap<>();

    public static Optional<RecipeEntry<?>> getOrFetchRecipe(Identifier recipeId) {
        if (RECIPE_CACHE.containsKey(recipeId)) return Optional.of(RECIPE_CACHE.get(recipeId));

        if (System.currentTimeMillis() - LAST_FETCHED_TIMESTAMP.getOrDefault(recipeId, 0) < 5_000) {
            return Optional.empty();
        }

        LAST_FETCHED_TIMESTAMP.put(recipeId, System.currentTimeMillis());
        Lavender.CHANNEL.clientHandle().send(new RequestRecipePacket(recipeId));

        return Optional.empty();
    }

    // ---

    public static void initialize() {
        Lavender.CHANNEL.registerServerbound(RequestRecipePacket.class, (packet, serverAccess) -> {
            var recipeEntry = serverAccess.runtime().getRecipeManager().get(RegistryKey.of(RegistryKeys.RECIPE, packet.recipeId));
            if (recipeEntry.isEmpty()) return;

            var recipe = recipeEntry.get().value();
            Lavender.CHANNEL.serverHandle(serverAccess.player()).send(new RecipePayloadPacket(packet.recipeId, recipe));
        });

        ServerLifecycleEvents.SYNC_DATA_PACK_CONTENTS.register((player, b) -> {
            Lavender.CHANNEL.serverHandle(player).send(new ClearRecipeCachePacket());
        });

        Lavender.CHANNEL.registerClientboundDeferred(RecipePayloadPacket.class, RecipePayloadPacket.ENDEC);
        Lavender.CHANNEL.registerClientboundDeferred(ClearRecipeCachePacket.class);
    }

    @Environment(EnvType.CLIENT)
    public static void initializeClient() {
        Lavender.CHANNEL.registerClientbound(RecipePayloadPacket.class, RecipePayloadPacket.ENDEC, (payload, clientAccess) -> handleRecipePayload(payload));
        Lavender.CHANNEL.registerClientbound(ClearRecipeCachePacket.class, (packet, clientAccess) -> handleClearCache());
    }

    @Environment(EnvType.CLIENT)
    private static void handleRecipePayload(RecipePayloadPacket payload) {
        RECIPE_CACHE.put(payload.recipeId, new RecipeEntry<>(RegistryKey.of(RegistryKeys.RECIPE, payload.recipeId), payload.recipe));

        if (MinecraftClient.getInstance().currentScreen instanceof LavenderBookScreen bookScreen) {
            bookScreen.rebuildContent(null);
        }
    }

    @Environment(EnvType.CLIENT)
    private static void handleClearCache() {
        RECIPE_CACHE.clear();
        LAST_FETCHED_TIMESTAMP.clear();
    }

    public record ClearRecipeCachePacket() {}

    public record RequestRecipePacket(Identifier recipeId) {}

    public record RecipePayloadPacket(Identifier recipeId, Recipe<?> recipe) {
        public static final StructEndec<RecipePayloadPacket> ENDEC = StructEndecBuilder.of(
            MinecraftEndecs.IDENTIFIER.fieldOf("recipe_id", RecipePayloadPacket::recipeId),
            CodecUtils.toEndec(Recipe.CODEC).fieldOf("recipe", RecipePayloadPacket::recipe),
            RecipePayloadPacket::new
        );
    }
}
