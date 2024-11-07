package io.wispforest.lavender.client;

import io.wispforest.owo.ui.base.BaseComponent;
import io.wispforest.owo.ui.core.OwoUIDrawContext;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.parsing.UIParsing;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.w3c.dom.Element;

public class UnreadNotificationComponent extends BaseComponent {

    private final Identifier bookTexture;

    public UnreadNotificationComponent(Identifier bookTexture, boolean plural) {
        this.bookTexture = bookTexture;
        this.tooltip(Text.translatable(plural ? "text.lavender.entry.multiple_unread" : "text.lavender.entry.unread"));
    }

    @Override
    protected int determineHorizontalContentSize(Sizing sizing) {
        return 8;
    }

    @Override
    protected int determineVerticalContentSize(Sizing sizing) {
        return 8;
    }

    @Override
    public void draw(OwoUIDrawContext context, int mouseX, int mouseY, float partialTicks, float delta) {
        context.push().translate(0, 0, 200);
        context.drawTexture(
            RenderLayer::getGuiTextured,
            this.bookTexture,
            this.x,
            this.y,
            (long) (System.currentTimeMillis() / 1500d) % 2 == 0 ? 188 : 204,
            180,
            this.width,
            this.height,
            16,
            16,
            512,
            256
        );
        context.pop();
    }

    public static UnreadNotificationComponent parse(Element element) {
        UIParsing.expectAttributes(element, "book-texture", "plural");
        return new UnreadNotificationComponent(
            UIParsing.parseIdentifier(element.getAttributeNode("book-texture")),
            UIParsing.parseBool(element.getAttributeNode("plural"))
        );
    }
}
