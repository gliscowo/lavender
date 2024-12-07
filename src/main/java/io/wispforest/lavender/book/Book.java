package io.wispforest.lavender.book;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.gson.JsonParseException;
import io.wispforest.lavender.Lavender;
import io.wispforest.owo.ui.core.Component;
import io.wispforest.owo.ui.core.Sizing;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class Book {

    private static final Pattern MACRO_NAME_PATTERN = Pattern.compile("[a-zA-Z0-9_-]+");
    private static final Pattern MACRO_ARG_PATTERN = Pattern.compile("\\$\\d+");

    private final Identifier id;
    private final @Nullable Identifier texture;
    private final @Nullable Identifier dynamicBookModel;
    private final @Nullable Text dynamicBookName;
    private final SoundEvent openSound, flippingSound;
    private final @Nullable Identifier introEntry;
    private final boolean displayUnreadEntryNotifications;
    private final boolean displayCompletion;
    private final @Nullable ToastSettings newEntriesToast;
    private final Map<String, String> zeroArgMacros = new HashMap<>();
    private final Map<Pattern, Macro> macros = new HashMap<>();

    private final @Nullable Identifier extend;
    private @Nullable Book resolvedExtend = null;

    private final Map<Identifier, Category> categories = new HashMap<>();
    private final Collection<Category> categoriesView = Collections.unmodifiableCollection(this.categories.values());

    private final Map<Identifier, Entry> entriesById = new HashMap<>();
    private final Collection<Entry> entriesView = Collections.unmodifiableCollection(this.entriesById.values());

    private final Map<Category, List<Entry>> entriesByCategory = new HashMap<>();
    private final Multimap<Item, Entry> entriesByAssociatedItem = HashMultimap.create();

    private final List<Entry> orphanedEntries = new ArrayList<>();
    private final Collection<Entry> orphanedEntriesView = Collections.unmodifiableCollection(this.orphanedEntries);

    private @Nullable Entry landingPage = null;

    public Book(
        Identifier id,
        @Nullable Identifier extend,
        @Nullable Identifier texture,
        @Nullable Identifier dynamicBookModel,
        @Nullable Text dynamicBookName,
        @Nullable SoundEvent openSound,
        @Nullable SoundEvent flippingSound,
        @Nullable Identifier introEntry,
        boolean displayUnreadEntryNotifications,
        boolean displayCompletion,
        @Nullable ToastSettings newEntriesToast,
        Map<String, String> macros
    ) {
        this.id = id;
        this.extend = extend;
        this.texture = texture;
        this.dynamicBookModel = dynamicBookModel;
        this.dynamicBookName = dynamicBookName;
        this.openSound = openSound != null ? openSound : Lavender.ITEM_BOOK_OPEN;
        this.flippingSound = flippingSound != null ? flippingSound : SoundEvents.ITEM_BOOK_PAGE_TURN;
        this.introEntry = introEntry;
        this.displayUnreadEntryNotifications = displayUnreadEntryNotifications;
        this.displayCompletion = displayCompletion;
        this.newEntriesToast = newEntriesToast;

        macros.forEach((macro, replacement) -> {
            int argCount = (int) MACRO_ARG_PATTERN.matcher(replacement).results().count();
            if (argCount > 0) {
                if (!MACRO_NAME_PATTERN.asPredicate().test(macro)) {
                    throw new JsonParseException("Parametrized macro '" + macro + "' contains invalid characters. Parametrized macro names must only contain '[a-zA-Z0-9_-]'");
                }

                var parts = new ArrayList<String>();
                var argIndices = new IntArrayList();

                var result = new StringBuilder(replacement);
                var argMatcher = MACRO_ARG_PATTERN.matcher(result);

                while (argMatcher.find()) {
                    parts.add(result.substring(0, argMatcher.start()));
                    argIndices.add(Integer.parseInt(argMatcher.group().substring(1)) - 1);

                    result.delete(0, argMatcher.end());
                    argMatcher.reset();
                }

                parts.add(result.toString());
                this.macros.put(
                    Pattern.compile(Pattern.quote(macro) + "\\(" + Stream.generate(() -> "(.*)").limit(argCount).collect(Collectors.joining(",")) + "\\)"),
                    new Macro(parts, argIndices)
                );
            } else {
                this.zeroArgMacros.put(macro, replacement);
            }
        });
    }

    public Identifier id() {
        return id;
    }

    public boolean displayCompletion() {
        return this.displayCompletion;
    }

    public boolean displayUnreadEntryNotifications() {
        return this.displayUnreadEntryNotifications;
    }

    public Collection<Entry> entries() {
        return this.entriesView;
    }

    public Collection<Entry> orphanedEntries() {
        return this.orphanedEntriesView;
    }

    public @Nullable Entry introEntry() {
        return this.introEntry != null
            ? this.entryById(this.introEntry)
            : null;
    }

    public @Nullable Entry entryById(Identifier entryId) {
        return this.entriesById.get(entryId);
    }

    public @Nullable Entry entryByAssociatedItem(ItemStack associatedStack) {
        var candidates = this.entriesByAssociatedItem.get(associatedStack.getItem());
        for (var candidateEntry : candidates) {
            for (var candidateAssociatedStack : candidateEntry.associatedItems()) {
                if (ItemStack.areItemsAndComponentsEqual(candidateAssociatedStack, associatedStack)) {
                    return candidateEntry;
                }
            }
        }

        return null;
    }

    public @Nullable Collection<Entry> entriesByCategory(Category category) {
        var entries = this.entriesByCategory.get(category);
        if (entries == null) return null;

        return Collections.unmodifiableCollection(entries);
    }

    public @Nullable Collection<Entry> descendantEntriesByCategory(Category category) {
        var entries = new ArrayList<Entry>();
        for (var candidate : this.categories.values()) {
            if (candidate == category || Objects.equals(candidate.parent(), category.id())) {
                var possibleEntries = this.entriesByCategory(candidate);
                if (possibleEntries != null) entries.addAll(possibleEntries);
            }
        }

        if (entries.isEmpty()) return null;
        return entries;
    }

    public Collection<Category> categories() {
        return this.categoriesView;
    }

    public @Nullable Category categoryById(Identifier categoryId) {
        return this.categories.get(categoryId);
    }

    public boolean shouldDisplayCategory(Category category, ClientPlayerEntity player) {
        var entries = this.descendantEntriesByCategory(category);
        if (entries == null) return false;

        boolean anyVisible = false;
        for (var entry : entries) {
            if (entry.canPlayerView(player)) anyVisible = true;
        }

        return anyVisible;
    }

    public boolean shouldDisplayUnreadNotification(Entry entry) {
        return this.displayUnreadEntryNotifications && !LavenderClientStorage.wasEntryViewed(this, entry);
    }

    public boolean shouldDisplayUnreadNotification(Category category, ClientPlayerEntity player) {
        if (!this.displayUnreadEntryNotifications) return false;

        var entries = this.descendantEntriesByCategory(category);
        if (entries == null) return false;

        for (var entry : entries) {
            if (!entry.canPlayerView(player)) continue;
            if (!LavenderClientStorage.wasEntryViewed(this, entry)) return true;
        }

        return false;
    }

    public @Nullable Entry landingPage() {
        return this.landingPage;
    }

    public @Nullable Identifier texture() {
        return this.texture;
    }

    public @Nullable Identifier dynamicBookModel() {
        return this.dynamicBookModel;
    }

    public @Nullable Text dynamicBookName() {
        return this.dynamicBookName;
    }

    public SoundEvent openSound() {
        return this.openSound;
    }

    public SoundEvent flippingSound() {
        return this.flippingSound;
    }

    public @Nullable ToastSettings newEntriesToast() {
        return this.newEntriesToast;
    }

    public int countVisibleEntries(ClientPlayerEntity player) {
        int visible = 0;
        for (var entry : this.entriesById.values()) {
            if (!entry.canPlayerView(player)) continue;
            visible++;
        }

        return visible;
    }

    // --- construction-related methods ---

    String expandMacros(Identifier entry, String input) {
        var builder = new StringBuilder(input);

        int scans = 0;
        boolean anyExpansions = true;
        while (scans < 1000 && anyExpansions) {
            anyExpansions = false;

            for (var pattern : this.zeroArgMacros.keySet()) {
                int replaceIndex = builder.indexOf(pattern);
                while (replaceIndex != -1) {
                    var replacement = this.zeroArgMacros.get(pattern);
                    anyExpansions = true;

                    builder.replace(replaceIndex, replaceIndex + pattern.length(), replacement);
                    replaceIndex = builder.indexOf(pattern, replaceIndex + replacement.length());
                }
            }

            for (var pattern : this.macros.keySet()) {
                var replacement = this.macros.get(pattern);
                var matcher = pattern.matcher(builder);

                while (scans < 1000 && matcher.find()) {
                    scans++;
                    anyExpansions = true;

                    var args = new ArrayList<String>();
                    for (int i = 1; i <= matcher.groupCount(); i++) {
                        args.add(matcher.group(i));
                    }

                    builder.replace(matcher.start(), matcher.end(), replacement.apply(args));
                    matcher.reset();
                }
            }
        }

        if (scans >= 1000) {
            Lavender.LOGGER.warn(
                "Preprocessing of entry {} in book {} failed: Macro expansion proceeded for over 1000 scans, a circular macro invocation is likely",
                entry,
                this.id
            );

            return """
                {red}**Entry processing failed:**{}
                
                
                Macro expansion proceeded for over 1000 scans without reaching
                a result - this is almost definitely due to a circular macro invocation
                """;
        } else {
            return builder.toString();
        }
    }

    void setLandingPage(@NotNull Entry landingPage) {
        this.landingPage = landingPage;
    }

    void addEntry(Entry entry) {
        if (this.resolvedExtend != null) {
            this.resolvedExtend.addEntry(entry);
        } else {
            this.entriesById.put(entry.id(), entry);
            entry.associatedItems().forEach(stack -> this.entriesByAssociatedItem.put(stack.getItem(), entry));

            if (!entry.categories().isEmpty()) {
                for (var category : entry.categories()) {
                    if (this.categories.containsKey(category)) {
                        this.entriesByCategory
                            .computeIfAbsent(this.categories.get(category), $ -> new ArrayList<>())
                            .add(entry);
                    } else {
                        throw new RuntimeException("Could not load entry '" + entry.id() + "' because category '" + category + "' was not found in book '" + this.effectiveId() + "'");
                    }
                }
            } else {
                this.orphanedEntries.add(entry);
            }
        }
    }

    void addCategory(Category category) {
        if (this.resolvedExtend != null) {
            this.resolvedExtend.addCategory(category);
        } else {
            this.categories.put(category.id(), category);
        }
    }

    boolean tryResolveExtension() {
        if (this.extend == null) return true;

        this.resolvedExtend = BookLoader.get(this.extend);
        return this.resolvedExtend != null;
    }

    Identifier effectiveId() {
        return this.resolvedExtend != null ? this.resolvedExtend.effectiveId() : this.id;
    }

    public interface BookmarkableElement {
        String title();
        Function<Sizing, Component> iconFactory();
    }

    public record Macro(List<String> parts, IntList argIndices) {
        String apply(List<String> args) {
            var result = new StringBuilder();
            for (int i = 0; i < this.argIndices.size(); i++) {
                result.append(this.parts.get(i));

                var argIndex = this.argIndices.getInt(i);
                result.append(argIndex - 1 < args.size() ? args.get(i) : "");
            }

            result.append(this.parts.get(this.parts.size() - 1));
            return result.toString();
        }
    }

    public record ToastSettings(ItemStack iconStack, Text bookName, @Nullable Identifier backgroundSprite) {}
}
