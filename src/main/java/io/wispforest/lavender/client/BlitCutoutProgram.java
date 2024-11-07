package io.wispforest.lavender.client;

import io.wispforest.lavender.Lavender;
import io.wispforest.owo.shader.GlProgram;
import net.minecraft.client.gl.ShaderProgramKey;
import net.minecraft.client.render.VertexFormats;

public class BlitCutoutProgram extends GlProgram {

    public BlitCutoutProgram() {
        super(Lavender.id("blit_cutout"), VertexFormats.BLIT_SCREEN);
    }

    public ShaderProgramKey key() {
        return this.programKey;
    }
}
