package io.wispforest.lavender.pond;

import net.minecraft.client.gl.ShaderProgramKey;

import java.util.function.Supplier;

public interface LavenderFramebufferExtension {
    void lavender$setBlitProgram(Supplier<ShaderProgramKey> blitProgram);

    void lavender$enableDepthTest();
}
