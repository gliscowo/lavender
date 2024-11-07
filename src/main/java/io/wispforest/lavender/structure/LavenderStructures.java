package io.wispforest.lavender.structure;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import io.wispforest.lavender.Lavender;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.CommonLifecycleEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resource.ResourceFinder;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class LavenderStructures {

    private static final Map<Identifier, JsonObject> PENDING_STRUCTURES = new HashMap<>();
    private static final Map<Identifier, StructureTemplate> LOADED_STRUCTURES = new HashMap<>();

    private static boolean tagsAvailable = false;

    @ApiStatus.Internal
    public static void initialize() {
        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(new ReloadListener());

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> tagsAvailable = false);
        CommonLifecycleEvents.TAGS_LOADED.register((registries, client) -> {
            tagsAvailable = true;
            tryParseStructures();
        });
    }

    /**
     * @return A view over the identifiers of all currently loaded structures
     */
    public static Set<Identifier> loadedStructures() {
        return Collections.unmodifiableSet(LOADED_STRUCTURES.keySet());
    }

    /**
     * @return The structure currently associated with the given id,
     * or {@code null} if no such structure is loaded
     */
    public static @Nullable StructureTemplate get(Identifier structureId) {
        return LOADED_STRUCTURES.get(structureId);
    }

    private static void tryParseStructures() {
        LOADED_STRUCTURES.clear();
        PENDING_STRUCTURES.forEach((identifier, pending) -> {
            try {
                LOADED_STRUCTURES.put(identifier, StructureTemplate.parse(identifier, pending));
            } catch (JsonParseException e) {
                Lavender.LOGGER.warn("Failed to load structure info {}", identifier, e);
            }
        });
    }

    private static class ReloadListener implements SimpleSynchronousResourceReloadListener {

        @Override
        public void reload(ResourceManager manager) {
            PENDING_STRUCTURES.clear();

            var resourceFinder = ResourceFinder.json("lavender/structures");
            for (var entry : resourceFinder.findResources(manager).entrySet()) {
                var resourceId = entry.getKey();
                var structureId = resourceFinder.toResourceId(resourceId);

                try (var reader = entry.getValue().getReader()) {
                    var json = JsonParser.parseReader(reader);

                    if (!json.isJsonObject()) return;
                    PENDING_STRUCTURES.put(structureId, json.getAsJsonObject());
                } catch (IllegalArgumentException | IOException | JsonParseException error) {
                    Lavender.LOGGER.error("Couldn't parse data file '{}' from '{}'", structureId, resourceId, error);
                }
            }

            if (tagsAvailable) tryParseStructures();
        }

        @Override
        public Identifier getFabricId() {
            return Lavender.id("structure_info_loader");
        }
    }
}
