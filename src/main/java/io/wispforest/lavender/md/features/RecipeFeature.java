package io.wispforest.lavender.md.features;

import io.wispforest.lavender.LavenderClientRecipeCache;
import io.wispforest.lavender.md.ItemListComponent;
import io.wispforest.lavender.md.compiler.BookCompiler;
import io.wispforest.lavendermd.Lexer;
import io.wispforest.lavendermd.MarkdownFeature;
import io.wispforest.lavendermd.Parser;
import io.wispforest.lavendermd.compiler.MarkdownCompiler;
import io.wispforest.lavendermd.compiler.OwoUICompiler;
import io.wispforest.owo.ui.component.Components;
import io.wispforest.owo.ui.component.ItemComponent;
import io.wispforest.owo.ui.container.Containers;
import io.wispforest.owo.ui.core.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.recipe.*;
import net.minecraft.recipe.display.SlotDisplayContexts;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.context.ContextParameterMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RecipeFeature implements MarkdownFeature {

    private final BookCompiler.ComponentSource bookComponentSource;
    private final Map<RecipeType<?>, RecipePreviewBuilder<?>> previewBuilders;

    public static final RecipePreviewBuilder<CraftingRecipe> CRAFTING_PREVIEW_BUILDER = new RecipePreviewBuilder<>() {
        @Override
        public @NotNull Component buildRecipePreview(BookCompiler.ComponentSource componentSource, ContextParameterMap slotContext, RecipeEntry<CraftingRecipe> recipeEntry) {
            var recipeComponent = componentSource.builtinTemplate(ParentComponent.class, "crafting-recipe");
            var value = recipeEntry.value();

            this.populateIngredientsGrid(recipeEntry, recipeComponent.childById(ParentComponent.class, "input-grid"), 3, 3);
            recipeComponent.childById(ItemComponent.class, "output").stack(value.getDisplays().getFirst().result().getFirst(slotContext));

            return recipeComponent;
        }
    };

    public static final RecipePreviewBuilder<AbstractCookingRecipe> SMELTING_PREVIEW_BUILDER = (componentSource, slotContext, recipeEntry) -> {
        var recipe = recipeEntry.value();
        var recipeComponent = componentSource.builtinTemplate(ParentComponent.class, "smelting-recipe");

        recipeComponent.childById(ItemListComponent.class, "input").ingredient(recipe.ingredient());
        recipeComponent.childById(ItemComponent.class, "output").stack(recipe.getDisplays().getFirst().result().getFirst(slotContext));

        var workstation = ItemStack.EMPTY;
        if (recipe instanceof SmeltingRecipe) workstation = Items.FURNACE.getDefaultStack();
        if (recipe instanceof BlastingRecipe) workstation = Items.BLAST_FURNACE.getDefaultStack();
        if (recipe instanceof SmokingRecipe) workstation = Items.SMOKER.getDefaultStack();
        if (recipe instanceof CampfireCookingRecipe) workstation = Items.CAMPFIRE.getDefaultStack();
        recipeComponent.childById(ItemComponent.class, "workstation").stack(workstation);

        return recipeComponent;
    };

    public static final RecipePreviewBuilder<SmithingRecipe> SMITHING_PREVIEW_BUILDER = (componentSource, slotContext, recipeEntry) -> {
        var recipe = recipeEntry.value();
        var recipeComponent = componentSource.builtinTemplate(ParentComponent.class, "smithing-recipe");

        recipe.template().ifPresent(ingredient -> recipeComponent.childById(ItemListComponent.class, "input-1").ingredient(ingredient));
        recipe.base().ifPresent(ingredient -> recipeComponent.childById(ItemListComponent.class, "input-2").ingredient(ingredient));
        recipe.addition().ifPresent(ingredient -> recipeComponent.childById(ItemListComponent.class, "input-3").ingredient(ingredient));

        recipeComponent.childById(ItemComponent.class, "output").stack(recipe.getDisplays().getFirst().result().getFirst(slotContext));

        return recipeComponent;
    };

    public static final RecipePreviewBuilder<StonecuttingRecipe> STONECUTTING_PREVIEW_BUILDER = (componentSource, slotContext, recipeEntry) -> {
        var recipe = recipeEntry.value();
        var recipeComponent = componentSource.builtinTemplate(ParentComponent.class, "stonecutting-recipe");

        recipeComponent.childById(ItemListComponent.class, "input").ingredient(recipe.ingredient());
        recipeComponent.childById(ItemComponent.class, "output").stack(recipe.getDisplays().getFirst().result().getFirst(slotContext));

        return recipeComponent;
    };

    public RecipeFeature(BookCompiler.ComponentSource bookComponentSource, @Nullable Map<RecipeType<?>, RecipePreviewBuilder<?>> previewBuilders) {
        this.bookComponentSource = bookComponentSource;

        this.previewBuilders = new HashMap<>(previewBuilders != null ? previewBuilders : Map.of());
        this.previewBuilders.putIfAbsent(RecipeType.CRAFTING, CRAFTING_PREVIEW_BUILDER);
        this.previewBuilders.putIfAbsent(RecipeType.SMELTING, SMELTING_PREVIEW_BUILDER);
        this.previewBuilders.putIfAbsent(RecipeType.BLASTING, SMELTING_PREVIEW_BUILDER);
        this.previewBuilders.putIfAbsent(RecipeType.SMOKING, SMELTING_PREVIEW_BUILDER);
        this.previewBuilders.putIfAbsent(RecipeType.CAMPFIRE_COOKING, SMELTING_PREVIEW_BUILDER);
        this.previewBuilders.putIfAbsent(RecipeType.SMITHING, SMITHING_PREVIEW_BUILDER);
        this.previewBuilders.putIfAbsent(RecipeType.STONECUTTING, STONECUTTING_PREVIEW_BUILDER);
    }

    @Override
    public String name() {
        return "recipes";
    }

    @Override
    public boolean supportsCompiler(MarkdownCompiler<?> compiler) {
        return compiler instanceof OwoUICompiler;
    }

    @Override
    public void registerTokens(TokenRegistrar registrar) {
        registrar.registerToken((nibbler, tokens) -> {
            if (!nibbler.tryConsume("<recipe;")) return false;

            var recipeIdString = nibbler.consumeUntil('>');
            if (recipeIdString == null) return false;

            var recipeId = Identifier.tryParse(recipeIdString);
            if (recipeId == null) return false;

            var recipe = LavenderClientRecipeCache.getOrFetchRecipe(recipeId);
            if (recipe.isEmpty()) return false;

            //noinspection unchecked
            tokens.add(new RecipeToken(recipeIdString, (RecipeEntry<Recipe<?>>) recipe.get()));
            return true;
        }, '<');
    }

    @Override
    public void registerNodes(NodeRegistrar registrar) {
        registrar.registerNode(
            (parser, recipeToken, tokens) -> new RecipeNode(recipeToken.recipe),
            (token, tokens) -> token instanceof RecipeToken recipe ? recipe : null
        );
    }

    private static class RecipeToken extends Lexer.Token {

        public final RecipeEntry<Recipe<?>> recipe;

        public RecipeToken(String content, RecipeEntry<Recipe<?>> recipe) {
            super(content);
            this.recipe = recipe;
        }
    }

    private class RecipeNode extends Parser.Node {

        private final RecipeEntry<Recipe<?>> recipe;

        public RecipeNode(RecipeEntry<Recipe<?>> recipe) {
            this.recipe = recipe;
        }

        @Override
        @SuppressWarnings({"rawtypes", "unchecked"})
        protected void visitStart(MarkdownCompiler<?> compiler) {
            var previewBuilder = (RecipePreviewBuilder) RecipeFeature.this.previewBuilders.get(this.recipe.value().getType());
            if (previewBuilder != null) {
                ((OwoUICompiler) compiler).visitComponent(previewBuilder.buildRecipePreview(RecipeFeature.this.bookComponentSource, SlotDisplayContexts.createParameters(MinecraftClient.getInstance().world), this.recipe));
            } else {
                ((OwoUICompiler) compiler).visitComponent(
                    Containers.verticalFlow(Sizing.fill(100), Sizing.content())
                        .child(Components.label(Text.literal("No preview builder registered for recipe type '" + Registries.RECIPE_TYPE.getId(this.recipe.value().getType()) + "'")).horizontalSizing(Sizing.fill(100)))
                        .padding(Insets.of(10))
                        .surface(Surface.flat(0x77A00000).and(Surface.outline(0x77FF0000)))
                );
            }
        }

        @Override
        protected void visitEnd(MarkdownCompiler<?> compiler) {}
    }

    @FunctionalInterface
    public interface RecipePreviewBuilder<R extends Recipe<?>> {
        @NotNull
        Component buildRecipePreview(BookCompiler.ComponentSource componentSource, ContextParameterMap slotContext, RecipeEntry<R> recipeEntry);

        default void populateIngredients(RecipeEntry<R> recipe, List<Ingredient> ingredients, ParentComponent componentContainer) {
            for (int i = 0; i < ingredients.size(); i++) {
                if (!(componentContainer.children().get(i) instanceof ItemListComponent ingredient)) continue;
                ingredient.ingredient(ingredients.get(i));
            }
        }

        default void populateIngredientsGrid(RecipeEntry<R> recipe, ParentComponent componentContainer, int gridWidth, int gridHeight) {
            var ingredients = recipe.value().getIngredientPlacement().getIngredients();
            RecipeGridAligner.alignRecipeToGrid(gridWidth, gridHeight, recipe.value(), recipe.value().getIngredientPlacement().getPlacementSlots(), (input, index, x, y) -> {
                if (input.isEmpty() || !(componentContainer.children().get(index) instanceof ItemListComponent ingredient)) return;
                ingredient.ingredient(ingredients.get(input.get().placerOutputPosition()));
            });
        }
    }
}
