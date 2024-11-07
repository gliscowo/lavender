package io.wispforest.lavender.md.features;

import io.wispforest.lavender.md.ItemListComponent;
import io.wispforest.lavendermd.Lexer;
import io.wispforest.lavendermd.MarkdownFeature;
import io.wispforest.lavendermd.Parser;
import io.wispforest.lavendermd.compiler.MarkdownCompiler;
import io.wispforest.lavendermd.compiler.OwoUICompiler;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;

public class ItemTagFeature implements MarkdownFeature {

    @Override
    public String name() {
        return "item_tags";
    }

    @Override
    public boolean supportsCompiler(MarkdownCompiler<?> compiler) {
        return compiler instanceof OwoUICompiler;
    }

    @Override
    public void registerTokens(TokenRegistrar registrar) {
        registrar.registerToken((nibbler, tokens) -> {
            if (!nibbler.tryConsume("<item-tag;")) return false;

            var tagString = nibbler.consumeUntil('>');
            if (tagString == null) return false;

            var tagId = Identifier.tryParse(tagString);
            if (tagId == null) return false;

            var tagKey = TagKey.of(RegistryKeys.ITEM, tagId);
            if (Registries.ITEM.getOptional(tagKey).isEmpty()) return false;

            tokens.add(new ItemTagToken(tagString, tagKey));
            return true;
        }, '<');
    }

    @Override
    public void registerNodes(NodeRegistrar registrar) {
        registrar.registerNode(
                (parser, tagToken, tokens) -> new ItemStackNode(tagToken.tag),
                (token, tokens) -> token instanceof ItemTagToken tag ? tag : null
        );
    }

    private static class ItemTagToken extends Lexer.Token {

        public final TagKey<Item> tag;

        public ItemTagToken(String content, TagKey<Item> tag) {
            super(content);
            this.tag = tag;
        }
    }

    private static class ItemStackNode extends Parser.Node {

        private final TagKey<Item> tag;

        public ItemStackNode(TagKey<Item> tag) {
            this.tag = tag;
        }

        @Override
        protected void visitStart(MarkdownCompiler<?> compiler) {
            ((OwoUICompiler) compiler).visitComponent(new ItemListComponent().tag(this.tag));
        }

        @Override
        protected void visitEnd(MarkdownCompiler<?> compiler) {}
    }

}
