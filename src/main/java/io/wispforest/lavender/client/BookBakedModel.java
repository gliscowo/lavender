package io.wispforest.lavender.client;

import io.wispforest.lavender.Lavender;
import io.wispforest.lavender.book.LavenderBookItem;
import net.fabricmc.fabric.api.renderer.v1.model.ForwardingBakedModel;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.Baker;
import net.minecraft.client.render.model.ModelBakeSettings;
import net.minecraft.client.render.model.UnbakedModel;
import net.minecraft.client.render.model.json.ModelOverrideList;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.ModelIdentifier;
import net.minecraft.client.util.SpriteIdentifier;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;

public class BookBakedModel extends ForwardingBakedModel {

    private final ModelOverrideList overrides = new ModelOverrideList() {
        @Override
        public @Nullable BakedModel getModel(ItemStack stack, @Nullable ClientWorld world, @Nullable LivingEntity entity, int seed) {
            var book = LavenderBookItem.bookOf(stack);
            if (book == null || book.dynamicBookModel() == null) return null;

            var bookModel = MinecraftClient.getInstance().getBakedModelManager().getModel(new ModelIdentifier(book.dynamicBookModel(), "inventory"));
            return bookModel != MinecraftClient.getInstance().getBakedModelManager().getMissingModel()
                ? bookModel
                : null;
        }
    };

    private BookBakedModel(BakedModel parent) {
        this.wrapped = parent;
    }

    @Override
    public ModelOverrideList getOverrides() {
        return this.overrides;
    }

    public static class Unbaked implements UnbakedModel {

        public static final Identifier BROWN_BOOK_ID = Lavender.id("item/brown_book");

        @Override
        public void resolve(Resolver resolver) {
            resolver.resolve(BROWN_BOOK_ID);
        }

        @Nullable
        @Override
        public BakedModel bake(Baker baker, Function<SpriteIdentifier, Sprite> textureGetter, ModelBakeSettings rotationContainer) {
            return new BookBakedModel(baker.bake(BROWN_BOOK_ID, rotationContainer));
        }
    }
}
