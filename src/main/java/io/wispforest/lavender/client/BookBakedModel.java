package io.wispforest.lavender.client;

import io.wispforest.lavender.book.LavenderBookItem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.item.ItemModelManager;
import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.client.render.item.model.ItemModel;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ModelTransformationMode;
import org.jetbrains.annotations.Nullable;

public class BookBakedModel implements ItemModel {

    private final ItemModel defaultModel;

    public BookBakedModel(ItemModel defaultModel) {
        this.defaultModel = defaultModel;
    }

    @Override
    public void update(ItemRenderState state, ItemStack stack, ItemModelManager resolver, ModelTransformationMode transformationMode, @Nullable ClientWorld world, @Nullable LivingEntity user, int seed) {
        var book = LavenderBookItem.bookOf(stack);
        if (book != null && book.dynamicBookModel() != null) {
            MinecraftClient.getInstance().getBakedModelManager().getItemModel(book.dynamicBookModel()).update(state, stack, resolver, transformationMode, world, user, seed);
        } else {
            this.defaultModel.update(state, stack, resolver, transformationMode, world, user, seed);
        }
    }

//    private final ModelOverrideList overrides = new ModelOverrideList() {
//        @Override
//        public @Nullable BakedModel getModel(ItemStack stack, @Nullable ClientWorld world, @Nullable LivingEntity entity, int seed) {
//            var book = LavenderBookItem.bookOf(stack);
//            if (book == null || book.dynamicBookModel() == null) return null;
//
//            var bookModel = MinecraftClient.getInstance().getBakedModelManager().getModel(new ModelIdentifier(book.dynamicBookModel(), "inventory"));
//            return bookModel != MinecraftClient.getInstance().getBakedModelManager().getMissingModel()
//                ? bookModel
//                : null;
//        }
//    };
//
//    private BookBakedModel(BakedModel parent) {
//        this.wrapped = parent;
//    }
//
//    @Override
//    public ModelOverrideList getOverrides() {
//        return this.overrides;
//    }
//
//    public static class Unbaked implements UnbakedModel {
//
//
//        @Override
//        public void resolve(Resolver resolver) {
//            resolver.resolve(BROWN_BOOK_ID);
//        }
//
//        @Nullable
//        @Override
//        public BakedModel bake(Baker baker, Function<SpriteIdentifier, Sprite> textureGetter, ModelBakeSettings rotationContainer) {
//            return new BookBakedModel(baker.bake(BROWN_BOOK_ID, rotationContainer));
//        }
//    }
}
