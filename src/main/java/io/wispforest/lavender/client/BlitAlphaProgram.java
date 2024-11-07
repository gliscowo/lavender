package io.wispforest.lavender.client;

import io.wispforest.lavender.Lavender;
import io.wispforest.owo.shader.GlProgram;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gl.ShaderProgramKey;
import net.minecraft.client.gl.Uniform;
import net.minecraft.client.render.VertexFormats;

public class BlitAlphaProgram extends GlProgram {

    private Uniform alpha;

    public BlitAlphaProgram() {
        super(Lavender.id("blit_alpha"), VertexFormats.BLIT_SCREEN);
    }

    @Override
    protected void setup() {
        super.setup();
        this.alpha = this.findUniform("Alpha");
    }

    public void setAlpha(float alpha) {
        this.alpha.set(alpha);
    }

    public ShaderProgramKey key() {
        return this.programKey;
    }
}
