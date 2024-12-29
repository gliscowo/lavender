package io.wispforest.lavender.client;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.client.render.item.model.ItemModel;
import net.minecraft.client.render.item.model.ItemModelTypes;

public class UnbakedBookModel implements ItemModel.Unbaked {

    public static final MapCodec<UnbakedBookModel> CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(
            ItemModelTypes.CODEC.fieldOf("default").forGetter(unbakedBookModel -> unbakedBookModel.defaultModel)
        ).apply(instance, UnbakedBookModel::new)
    );

    private final ItemModel.Unbaked defaultModel;

    public UnbakedBookModel(ItemModel.Unbaked defaultModel) {
        this.defaultModel = defaultModel;
    }

    @Override
    public MapCodec<? extends ItemModel.Unbaked> getCodec() {
        return CODEC;
    }

    @Override
    public void resolve(Resolver resolver) {
        this.defaultModel.resolve(resolver);
    }

    @Override
    public ItemModel bake(ItemModel.BakeContext context) {
        return new BookBakedModel(this.defaultModel.bake(context));
    }
}
