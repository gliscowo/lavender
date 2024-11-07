package io.wispforest.lavender.mixin;

import com.mojang.blaze3d.platform.GlStateManager;
import io.wispforest.lavender.pond.LavenderFramebufferExtension;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.ShaderProgramKey;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Supplier;

@Mixin(Framebuffer.class)
public class FramebufferMixin implements LavenderFramebufferExtension {

    @Unique
    private Supplier<ShaderProgramKey> blitProgram = null;

    @Unique
    private boolean enableDepthTest = false;

    @Override
    public void lavender$setBlitProgram(Supplier<ShaderProgramKey> blitProgram) {
        this.blitProgram = blitProgram;
    }

    @Override
    public void lavender$enableDepthTest() {
        this.enableDepthTest = true;
    }

    @Inject(method = "drawInternal", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/GlStateManager;_disableDepthTest()V"))
    private void weDeep(int width, int height, CallbackInfo ci) {
        if (!this.enableDepthTest) return;
        GlStateManager._enableDepthTest();
    }

    @ModifyArg(method = "drawInternal", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;setShader(Lnet/minecraft/client/gl/ShaderProgramKey;)Lnet/minecraft/client/gl/ShaderProgram;"))
    private ShaderProgramKey applyBlitProgram(ShaderProgramKey shaderProgramKey) {
        if (this.blitProgram == null) return shaderProgramKey;
        return this.blitProgram.get();
    }
}
