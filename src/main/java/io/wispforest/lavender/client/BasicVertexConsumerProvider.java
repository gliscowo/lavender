package io.wispforest.lavender.client;

import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.TexturedRenderLayers;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.util.Util;

public class BasicVertexConsumerProvider extends VertexConsumerProvider.Immediate {
    public BasicVertexConsumerProvider(int initialBufferSize) {
        super(new BufferAllocator(initialBufferSize), Util.make(new Object2ObjectLinkedOpenHashMap<>(), buffers -> {
            buffers.put(TexturedRenderLayers.getEntitySolid(), new BufferAllocator(TexturedRenderLayers.getEntitySolid().getExpectedBufferSize()));
            buffers.put(TexturedRenderLayers.getEntityCutout(), new BufferAllocator(TexturedRenderLayers.getEntityCutout().getExpectedBufferSize()));
            buffers.put(TexturedRenderLayers.getBannerPatterns(), new BufferAllocator(TexturedRenderLayers.getBannerPatterns().getExpectedBufferSize()));
            buffers.put(TexturedRenderLayers.getItemEntityTranslucentCull(), new BufferAllocator(TexturedRenderLayers.getItemEntityTranslucentCull().getExpectedBufferSize()));
            buffers.put(RenderLayer.getArmorEntityGlint(), new BufferAllocator(RenderLayer.getArmorEntityGlint().getExpectedBufferSize()));
            buffers.put(RenderLayer.getGlint(), new BufferAllocator(RenderLayer.getGlint().getExpectedBufferSize()));
            buffers.put(RenderLayer.getGlintTranslucent(), new BufferAllocator(RenderLayer.getGlintTranslucent().getExpectedBufferSize()));
            buffers.put(RenderLayer.getEntityGlint(), new BufferAllocator(RenderLayer.getEntityGlint().getExpectedBufferSize()));
            buffers.put(RenderLayer.getWaterMask(), new BufferAllocator(RenderLayer.getWaterMask().getExpectedBufferSize()));
        }));
    }
}