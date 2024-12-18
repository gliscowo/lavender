package io.wispforest.lavender.book;

import com.mojang.blaze3d.systems.RenderSystem;
import io.wispforest.lavender.client.StructureOverlayRenderer;
import io.wispforest.lavender.structure.LavenderStructures;
import io.wispforest.lavender.structure.StructureTemplate;
import io.wispforest.owo.ui.base.BaseComponent;
import io.wispforest.owo.ui.core.CursorStyle;
import io.wispforest.owo.ui.core.Easing;
import io.wispforest.owo.ui.core.OwoUIDrawContext;
import io.wispforest.owo.ui.parsing.UIModelParsingException;
import io.wispforest.owo.ui.parsing.UIParsing;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.TooltipComponent;
import net.minecraft.client.render.DiffuseLighting;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.math.RotationAxis;
import org.lwjgl.glfw.GLFW;
import org.w3c.dom.Element;

import java.util.List;

public class StructureComponent extends BaseComponent {

    private final StructureTemplate structure;
    private final int displayAngle;

    private float rotation = -45;
    private long lastInteractionTime = 0L;

    private boolean placeable = true;
    private int visibleLayer = -1;

    public StructureComponent(StructureTemplate structure, int displayAngle) {
        this.structure = structure;
        this.displayAngle = displayAngle;
        this.cursorStyle(CursorStyle.HAND);
    }

    @Override
    public void update(float delta, int mouseX, int mouseY) {
        super.update(delta, mouseX, mouseY);

        var diff = Util.getMeasuringTimeMs() - this.lastInteractionTime;
        if (diff < 5000L) return;

        this.rotation += delta * Easing.SINE.apply(Math.min(1f, (diff - 5000) / 1500f));
    }

    @Override
    public void draw(OwoUIDrawContext context, int mouseX, int mouseY, float partialTicks, float delta) {
        var client = MinecraftClient.getInstance();
        var entityBuffers = client.getBufferBuilders().getEntityVertexConsumers();

        float scale = Math.min(this.width, this.height);
        scale /= Math.max(this.structure.xSize(), Math.max(this.structure.ySize(), this.structure.zSize()));
        scale /= 1.625f;

        var matrices = context.getMatrices();

        matrices.push();
        matrices.translate(this.x + this.width / 2f, this.y + this.height / 2f, 100);
        matrices.scale(scale, -scale, scale);

        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(this.displayAngle));
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(this.rotation));
        matrices.translate(this.structure.xSize() / -2.0f, this.structure.ySize() / -2.0f, this.structure.zSize() / -2.0f);

        RenderSystem.runAsFancy(() -> {
            structure.forEachPredicate((blockPos, predicate) -> {
                if (this.visibleLayer != -1 && this.visibleLayer != blockPos.getY()) return;

                matrices.push();
                matrices.translate(blockPos.getX(), blockPos.getY(), blockPos.getZ());

                client.getBlockRenderManager().renderBlockAsEntity(
                        predicate.preview(), matrices, entityBuffers,
                        LightmapTextureManager.MAX_BLOCK_LIGHT_COORDINATE,
                        OverlayTexture.DEFAULT_UV
                );

                matrices.pop();
            });

            matrices.pop();

            DiffuseLighting.disableGuiDepthLighting();
            entityBuffers.draw();
            DiffuseLighting.enableGuiDepthLighting();
        });

        if (this.placeable) {
            if (StructureOverlayRenderer.isShowingOverlay(this.structure.id())) {
                context.drawText(client.textRenderer, Text.translatable("text.lavender.structure_component.active_overlay_hint"), this.x + this.width - 5 - client.textRenderer.getWidth("⚓"), this.y + this.height - 9 - 5, 0, false);
                this.tooltip(Text.translatable("text.lavender.structure_component.hide_hint"));
            } else {
                this.tooltip(Text.translatable("text.lavender.structure_component.place_hint"));
            }
        }
    }

    @Override
    public boolean onMouseDown(double mouseX, double mouseY, int button) {
        var result = super.onMouseDown(mouseX, mouseY, button);
        if (!this.placeable || button != GLFW.GLFW_MOUSE_BUTTON_LEFT || !Screen.hasShiftDown()) return result;

        if (StructureOverlayRenderer.isShowingOverlay(this.structure.id())) {
            StructureOverlayRenderer.removeAllOverlays(this.structure.id());
        } else {
            StructureOverlayRenderer.addPendingOverlay(this.structure.id());
            StructureOverlayRenderer.restrictVisibleLayer(this.structure.id(), this.visibleLayer);

            MinecraftClient.getInstance().setScreen(null);
        }

        return true;
    }

    @Override
    public boolean onMouseDrag(double mouseX, double mouseY, double deltaX, double deltaY, int button) {
        var result = super.onMouseDrag(mouseX, mouseY, deltaX, deltaY, button);
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return result;

        this.rotation += (float) deltaX;
        this.lastInteractionTime = Util.getMeasuringTimeMs();

        return true;
    }

    @Override
    public boolean canFocus(FocusSource source) {
        return source == FocusSource.MOUSE_CLICK;
    }

    public StructureComponent visibleLayer(int visibleLayer) {
        StructureOverlayRenderer.restrictVisibleLayer(this.structure.id(), visibleLayer);

        this.visibleLayer = visibleLayer;
        return this;
    }

    public StructureComponent placeable(boolean placeable) {
        if (!placeable) {
            this.tooltip((List<TooltipComponent>) null);
        }

        this.cursorStyle(placeable ? CursorStyle.HAND : CursorStyle.POINTER);

        this.placeable = placeable;
        return this;
    }

    public boolean placeable() {
        return this.placeable;
    }

    public static StructureComponent parse(Element element) {
        UIParsing.expectAttributes(element, "structure-id");

        var structureId = Identifier.tryParse(element.getAttribute("structure-id"));
        if (structureId == null) {
            throw new UIModelParsingException("Invalid structure id '" + element.getAttribute("structure-id") + "'");
        }

        var structure = LavenderStructures.get(structureId);
        if (structure == null) throw new UIModelParsingException("Unknown structure '" + structureId + "'");

        int displayAngle = 35;
        if (element.hasAttribute("display-angle")) {
            displayAngle = UIParsing.parseSignedInt(element.getAttributeNode("display-angle"));
        }

        return new StructureComponent(structure, displayAngle);
    }
}
