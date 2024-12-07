package io.wispforest.lavender.book;

import com.google.common.collect.ImmutableSet;
import com.google.gson.*;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.JsonOps;
import io.wispforest.lavender.Lavender;
import io.wispforest.lavender.mixin.access.RegistryOpsAccessor;
import io.wispforest.owo.ui.component.Components;
import io.wispforest.owo.ui.container.Containers;
import io.wispforest.owo.ui.core.Component;
import io.wispforest.owo.ui.core.Sizing;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.conditions.v1.ResourceCondition;
import net.fabricmc.fabric.api.resource.conditions.v1.ResourceConditions;
import net.minecraft.client.MinecraftClient;
import net.minecraft.command.argument.ItemStringReader;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryOps;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.resource.*;
import net.minecraft.util.Identifier;
import net.minecraft.util.JsonHelper;
import org.apache.commons.io.IOUtils;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Function;

public class BookContentLoader implements SynchronousResourceReloader, IdentifiableResourceReloadListener {

    private static final ResourceFinder ENTRY_FINDER = new ResourceFinder("lavender/entries", ".md");
    private static final ResourceFinder CATEGORY_FINDER = new ResourceFinder("lavender/categories", ".md");
    private static final Gson GSON = new GsonBuilder().setLenient().disableHtmlEscaping().create();

    public static void initialize() {
        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(new BookContentLoader());
    }

    @Override
    public Identifier getFabricId() {
        return Lavender.id("book_content_loader");
    }

    @Override
    public void reload(ResourceManager manager) {
        if (MinecraftClient.getInstance().world == null) return;
        reloadContents(manager);
    }

    public static void reloadContents(ResourceManager manager) {
        var entries = findResources(manager, ENTRY_FINDER);
        var categories = findResources(manager, CATEGORY_FINDER);

        for (var book : BookLoader.allBooks()) {
            forResourceOfBook(categories, book, "category", (identifier, resource) -> {
                var markdown = parseMarkdown(book, identifier, resource);
                if (markdown == null) return;

                var parentCategory = JsonHelper.getString(markdown.meta, "parent", null);
                var parentCategoryId = parentCategory != null
                    ? parentCategory.indexOf(':') > 0 ? Identifier.tryParse(parentCategory) : Identifier.of(identifier.getNamespace(), parentCategory)
                    : null;

                book.addCategory(new Category(
                    identifier,
                    parentCategoryId,
                    JsonHelper.getString(markdown.meta, "title"),
                    getIcon(markdown.meta),
                    JsonHelper.getBoolean(markdown.meta, "secret", false),
                    JsonHelper.getInt(markdown.meta, "ordinal", Integer.MAX_VALUE),
                    markdown.content
                ));
            });
        }

        for (var book : BookLoader.allBooks()) {
            forResourceOfBook(entries, book, "entry", (identifier, resource) -> {
                var markdown = parseMarkdown(book, identifier, resource);
                if (markdown == null) return;

                var entryCategories = new ArrayList<Identifier>();
                for (var categoryElement : JsonHelper.getArray(markdown.meta, "categories", new JsonArray())) {
                    var categoryString = categoryElement.getAsString();
                    entryCategories.add(categoryString.indexOf(':') > 0 ? Identifier.tryParse(categoryString) : Identifier.of(identifier.getNamespace(), categoryString));
                }

                var legacyCategory = JsonHelper.getString(markdown.meta, "category", null);
                if (legacyCategory != null) {
                    entryCategories.add(legacyCategory.indexOf(':') > 0 ? Identifier.tryParse(legacyCategory) : Identifier.of(identifier.getNamespace(), legacyCategory));
                }

                var title = JsonHelper.getString(markdown.meta, "title");
                var icon = getIcon(markdown.meta);
                var secret = JsonHelper.getBoolean(markdown.meta, "secret", false);
                var ordinal = JsonHelper.getInt(markdown.meta, "ordinal", Integer.MAX_VALUE);

                var associatedItems = new ImmutableSet.Builder<ItemStack>();
                for (var itemElement : JsonHelper.getArray(markdown.meta, "associated_items", new JsonArray())) {
                    associatedItems.addAll(itemsFromString(itemElement.getAsString()));
                }

                var requiredAdvancements = new ImmutableSet.Builder<Identifier>();
                for (var advancementElement : JsonHelper.getArray(markdown.meta, "required_advancements", new JsonArray())) {
                    if (!advancementElement.isJsonPrimitive()) continue;

                    var advancementId = Identifier.tryParse(advancementElement.getAsString());
                    if (advancementId == null) {
                        Lavender.LOGGER.warn("Did not add advancement '{}' as requirement to entry '{}' as it is not a valid advancement identifier", advancementElement.getAsString(), identifier);
                        continue;
                    }

                    requiredAdvancements.add(advancementId);
                }

                var additionalSearchTerms = new ImmutableSet.Builder<String>();
                for (var termElement : JsonHelper.getArray(markdown.meta, "additional_search_terms", new JsonArray())) {
                    if (!termElement.isJsonPrimitive()) continue;

                    var term = termElement.getAsString();
                    // Lowercase the term in advance to save a little time when searching.
                    additionalSearchTerms.add(term.toLowerCase(Locale.ROOT));
                }

                var entry = new Entry(
                        identifier, entryCategories, title, icon, secret, ordinal, requiredAdvancements.build(),
                        associatedItems.build(), additionalSearchTerms.build(), markdown.content
                );
                if (entry.id().getPath().equals("landing_page")) {
                    book.setLandingPage(entry);
                } else {
                    book.addEntry(entry);
                }
            });
        }
    }

    private static Map<String, Map<String, Resource>> findResources(ResourceManager manager, ResourceFinder finder) {
        var resources = new HashMap<String, Map<String, Resource>>();
        finder.findResources(manager).forEach((identifier, resource) -> {
            var resourceId = finder.toResourceId(identifier);
            resources.computeIfAbsent(resourceId.getNamespace(), s -> new HashMap<>()).put(resourceId.getPath(), resource);
        });

        return resources;
    }

    private static void forResourceOfBook(Map<String, Map<String, Resource>> resources, Book book, String resourceType, BiConsumer<Identifier, Resource> action) {
        if (!resources.containsKey(book.id().getNamespace())) return;

        var targetBook = book.id().getPath();
        var activeLanguage = MinecraftClient.getInstance().getLanguageManager().getLanguage();

        var discoveredResources = new HashMap<Identifier, Resource>();

        resources.get(book.id().getNamespace()).forEach((path, resource) -> {
            var bookResourcePath = getBookResourcePath(path, targetBook, null);
            if (bookResourcePath == null) return;

            discoveredResources.put(Identifier.of(book.id().getNamespace(), bookResourcePath), resource);
        });

        resources.get(book.id().getNamespace()).forEach((path, resource) -> {
            var bookResourcePath = getBookResourcePath(path, targetBook, activeLanguage);
            if (bookResourcePath == null) return;

            discoveredResources.put(Identifier.of(book.id().getNamespace(), bookResourcePath), resource);
        });

        discoveredResources.forEach((resourceId, resource) -> {
            try {
                action.accept(resourceId, resource);
            } catch (RuntimeException e) {
                Lavender.LOGGER.warn("Could not load {} '{}'", resourceType, resourceId, e);
            }
        });
    }

    private static @Nullable String getBookResourcePath(String resourcePath, String bookName, @Nullable String activeLanguage) {
        String book = null;
        String language = null;

        if (resourcePath.indexOf('/') != -1) {
            book = resourcePath.substring(0, resourcePath.indexOf('/'));
            resourcePath = resourcePath.substring(resourcePath.indexOf('/') + 1);
        }

        if (resourcePath.indexOf('/') != -1) {
            language = resourcePath.substring(0, resourcePath.indexOf('/'));
            if (MinecraftClient.getInstance().getLanguageManager().getAllLanguages().keySet().contains(language)) {
                resourcePath = resourcePath.substring(resourcePath.indexOf('/') + 1);
            } else {
                language = null;
            }
        }

        if (!bookName.equals(book) || !Objects.equals(activeLanguage, language)) return null;
        return resourcePath;
    }

    private static @Nullable MarkdownResource parseMarkdown(Book book, Identifier resourceId, Resource resource) {
        try {
            var content = IOUtils.toString(resource.getInputStream(), StandardCharsets.UTF_8).strip();
            JsonObject meta;

            if (content.startsWith("```json")) {
                content = content.substring("```json".length());
                int frontmatterEnd = content.indexOf("```");
                if (frontmatterEnd == -1) {
                    throw new RuntimeException("Unterminated markdown meta");
                }

                meta = GSON.fromJson(content.substring(0, frontmatterEnd), JsonObject.class);
                content = content.substring(frontmatterEnd + 3).stripLeading();

                if (meta.has(ResourceConditions.CONDITIONS_KEY)) {
                    var conditions = ResourceCondition.CONDITION_CODEC.parse(JsonOps.INSTANCE, meta.get(ResourceConditions.CONDITIONS_KEY));
                    var infoGetter = ((RegistryOpsAccessor) RegistryOps.of(JsonOps.INSTANCE, MinecraftClient.getInstance().world.getRegistryManager())).lavender$getInfoGetter();

                    if (conditions.isSuccess() && !conditions.getOrThrow().test(infoGetter)) {
                        return null;
                    }
                }

                return new MarkdownResource(meta, book.expandMacros(resourceId, content.replaceAll("\\r\\n?", "\n")));
            } else {
                throw new RuntimeException("Missing markdown meta");
            }
        } catch (Exception e) {
            Lavender.LOGGER.warn("Could not load markdown file {}", resourceId, e);
            return null;
        }
    }

    private record MarkdownResource(JsonObject meta, String content) {}

    private static Function<Sizing, Component> getIcon(JsonObject meta) {
        if (meta.has("icon")) {
            var stack = itemStackFromString(JsonHelper.getString(meta, "icon"));
            return sizing -> Components.item(stack).sizing(sizing);
        } else if (meta.has("icon_sprite")) {
            var id = Identifier.tryParse(JsonHelper.getString(meta, "icon_sprite"));
            if (id == null) return null;

            return sizing -> Components.sprite(MinecraftClient.getInstance().getGuiAtlasManager().getSprite(id)).sizing(sizing);
        } else {
            return sizing -> Containers.stack(sizing, sizing);
        }
    }

    private static Collection<ItemStack> itemsFromString(String itemsString) {
        if (!itemsString.startsWith("#")) return List.of(itemStackFromString(itemsString));

        var tagId = Identifier.tryParse(itemsString.substring(1));
        if (tagId == null) {
            Lavender.LOGGER.warn("Could not parse tag ID '{}'", itemsString);
            return List.of();
        }

        var entries = Registries.ITEM.getOptional(TagKey.of(RegistryKeys.ITEM, tagId));
        if (entries.isEmpty()) {
            Lavender.LOGGER.warn("Unknown item tag: '{}'", itemsString);
            return List.of();
        }

        return entries.get().stream().map(RegistryEntry::value).map(Item::getDefaultStack).toList();
    }

    public static ItemStack itemStackFromString(String stackString) {
        try {
            var parsed = new ItemStringReader(MinecraftClient.getInstance().world.getRegistryManager()).consume(new StringReader(stackString));

            var stack = parsed.item().value().getDefaultStack();
            if (parsed.components() != null) stack.applyUnvalidatedChanges(parsed.components());

            return stack;
        } catch (CommandSyntaxException e) {
            throw new JsonSyntaxException("Invalid item stack: '" + stackString + "'", e);
        }
    }
}
