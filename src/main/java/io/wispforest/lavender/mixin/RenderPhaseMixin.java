package io.wispforest.lavender.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import io.wispforest.lavender.client.LavenderClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.render.RenderPhase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(RenderPhase.class)
public class RenderPhaseMixin {

    @ModifyExpressionValue(method = {"method_62272", "method_34555", "method_29377"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/MinecraftClient;getFramebuffer()Lnet/minecraft/client/gl/Framebuffer;"))
    private static Framebuffer injectProperRenderTarget(Framebuffer original) {
        if (LavenderClient.mainTargetOverride != null) {
            return LavenderClient.mainTargetOverride;
        }

        return original;
    }

}
