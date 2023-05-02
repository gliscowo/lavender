package io.wispforest.lavender.parsing;

import com.google.common.collect.ImmutableMap;
import com.mojang.brigadier.StringReader;
import it.unimi.dsi.fastutil.chars.Char2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.chars.Char2ObjectMap;
import net.minecraft.text.Style;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.function.CharPredicate;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

public class Lexer {

    private static final Map<String, Formatting> FORMATTING_COLORS = Stream.of(Formatting.values())
            .filter(Formatting::isColor)
            .collect(ImmutableMap.toImmutableMap(formatting -> formatting.getName().toLowerCase(Locale.ROOT), Function.identity()));

    private static final Char2ObjectMap<List<BiFunction<StringReader, List<Token>, Boolean>>> LEX_FUNCTIONS = new Char2ObjectLinkedOpenHashMap<>();

    static {
        registerToken('\\', TextToken::lexEscape);
        registerToken('\n', NewlineToken::lex);
        registerToken('[', primitiveTokenLexer(OpenLinkToken::new));
        registerToken('!', ImageToken::lex);
        registerToken(']', CloseLinkToken::lex);
        registerToken('*', StarToken::lex);
        registerToken('{', OpenColorToken::lex);
        registerToken('~', primitiveTokenLexer(TildeToken::new));
        registerToken('_', primitiveTokenLexer(UnderscoreToken::new));
        registerToken('>', QuotationToken::lex);
        registerToken('-', HorizontalRuleToken::lex);
        registerToken('-', ListToken::lexUnordered);

        registerToken(ListToken::lexOrdered, '0', '1', '2', '3', '4', '5', '6', '7', '8', '9');
    }

    public static void registerToken(char trigger, BiFunction<StringReader, List<Token>, Boolean> lexer) {
        LEX_FUNCTIONS.computeIfAbsent(trigger, character -> new ArrayList<>()).add(lexer);
    }

    public static void registerToken(BiFunction<StringReader, List<Token>, Boolean> lexer, char... triggers) {
        for (var trigger : triggers) {
            registerToken(trigger, lexer);
        }
    }

    public static List<Token> lex(String input) {
        var tokens = new ArrayList<Token>();
        var reader = new StringReader(input.strip());

        while (reader.canRead()) {
            char current = reader.peek();
            if (LEX_FUNCTIONS.containsKey(current)) {
                int cursorBefore = reader.getCursor();

                boolean success = false;
                for (var lexer : LEX_FUNCTIONS.get(current)) {
                    if (lexer.apply(reader, tokens)) {
                        success = true;
                        break;
                    } else {
                        reader.setCursor(cursorBefore);
                    }
                }

                if (!success) {
                    if (reader.getCursor() == cursorBefore) reader.skip();
                    appendText(tokens, reader.getRead().substring(cursorBefore));
                }

            } else {
                appendText(tokens, readTextUntil(reader, LEX_FUNCTIONS.keySet()::contains));
            }
        }

        return tokens;
    }

    private static String readTextUntil(StringReader reader, CharPredicate until) {
        var text = new StringBuilder();
        while (reader.canRead() && !until.test(reader.peek())) {
            text.append(reader.read());
        }

        return text.toString();
    }

    private static int whitespaceSinceLineBreak(StringReader reader) {
        int offset = 1;
        int whitespace = 0;

        while (reader.getCursor() - offset >= 0) {
            char current = reader.peek(-offset);
            if (current == '\n') return whitespace;

            if (Character.isWhitespace(current)) {
                whitespace++;
            } else {
                return -1;
            }

            offset++;
        }

        return whitespace;
    }

    private static void appendText(List<Token> tokens, String text) {
        if (!tokens.isEmpty() && tokens.get(tokens.size() - 1) instanceof TextToken textToken) {
            textToken.append(text);
        } else {
            tokens.add(new TextToken(text));
        }
    }

    private static void appendText(List<Token> tokens, char text) {
        if (!tokens.isEmpty() && tokens.get(tokens.size() - 1) instanceof TextToken textToken) {
            textToken.append(text);
        } else {
            tokens.add(new TextToken(String.valueOf(text)));
        }
    }

    private static BiFunction<StringReader, List<Token>, Boolean> primitiveTokenLexer(Supplier<Token> factory) {
        return (reader, tokens) -> {
            reader.skip();
            tokens.add(factory.get());

            return true;
        };
    }

    // --- Tokens with lexing implementations ---

    public abstract static class Token {
        protected final String content;

        protected Token(String content) {
            this.content = content;
        }

        public String content() {
            return this.content;
        }

        public boolean isBoundary() {
            return false;
        }
    }

    public static final class TextToken extends Token {

        private final StringBuilder contentBuilder;

        private String contentCache = "";
        private boolean dirty = true;

        public TextToken(String content) {
            super("");
            this.contentBuilder = new StringBuilder(content);
        }

        private static boolean lexEscape(StringReader reader, List<Token> tokens) {
            reader.skip();
            if (!reader.canRead() || !LEX_FUNCTIONS.keySet().contains(reader.peek())) return false;

            var escaped = reader.read();
            if (escaped == '\n') {
                tokens.add(new NewlineToken("\n", false));
            } else {
                appendText(tokens, escaped);
            }
            return true;
        }

        public void append(String content) {
            this.contentBuilder.append(content);
            this.dirty = true;
        }

        public void append(char content) {
            this.contentBuilder.append(content);
            this.dirty = true;
        }

        @Override
        public String content() {
            if (this.dirty) {
                this.contentCache = this.contentBuilder.toString();
                this.dirty = false;
            }

            return this.contentCache;
        }
    }

    public static final class StarToken extends Token {

        public final boolean leftAdjacent, rightAdjacent;

        public StarToken(String content, boolean leftAdjacent, boolean rightAdjacent) {
            super(content);
            this.leftAdjacent = leftAdjacent;
            this.rightAdjacent = rightAdjacent;
        }

        private static boolean lex(StringReader reader, List<Token> tokens) {
            int starCount = readTextUntil(reader, c -> c != '*').length();

            boolean leftAdjacent = reader.getCursor() - starCount - 1 >= 0 && reader.peek(-starCount - 1) != ' ';
            boolean rightAdjacent = (reader.canRead() && reader.peek() != ' ');

            if (starCount > 3 || !(rightAdjacent || leftAdjacent)) {
                return false;
            }

            for (int i = 0; i < starCount; i++) {
                tokens.add(new StarToken("*", leftAdjacent, rightAdjacent));
            }
            return true;
        }
    }

    public static final class NewlineToken extends Token {

        private final boolean isBoundary;

        public NewlineToken(String content, boolean isBoundary) {
            super(content);
            this.isBoundary = isBoundary;
        }

        public static boolean lex(StringReader reader, List<Token> tokens) {
            var newlines = readTextUntil(reader, c -> c != '\n');
            if (newlines.length() > 1) {
                tokens.add(new NewlineToken("\n".repeat(newlines.length() - 1), true));
            } else {
                tokens.add(new NewlineToken(" ", false));
            }

            return true;
        }

        @Override
        public boolean isBoundary() {
            return this.isBoundary;
        }
    }

    public static final class TildeToken extends Token {
        public TildeToken() {
            super("~");
        }
    }

    public static final class UnderscoreToken extends Token {
        public UnderscoreToken() {
            super("_");
        }
    }

    public static final class ListToken extends Token {

        public final int depth;
        public final OptionalInt ordinal;

        public ListToken(int depth, OptionalInt ordinal) {
            super("- ");
            this.depth = depth;
            this.ordinal = ordinal;
        }

        public static boolean lexUnordered(StringReader reader, List<Token> tokens) {
            int whitespace = whitespaceSinceLineBreak(reader);
            if (whitespace < 0) return false;

            reader.skip();
            if (!reader.canRead() || reader.read() != ' ') return false;

            tokens.add(new ListToken(whitespace, OptionalInt.empty()));
            return true;
        }

        public static boolean lexOrdered(StringReader reader, List<Token> tokens) {
            int whitespace = whitespaceSinceLineBreak(reader);
            if (whitespace < 0) return false;

            var ordinal = readTextUntil(reader, c -> c < '0' || c > '9');
            if (!ordinal.matches("[0-9]+") || !reader.canRead(2) || reader.read() != '.' || reader.read() != ' ') return false;

            tokens.add(new ListToken(whitespace, OptionalInt.of(Integer.parseInt(ordinal))));
            return true;
        }

        @Override
        public boolean isBoundary() {
            return true;
        }
    }

    public static final class QuotationToken extends Token {

        public final int depth;

        public QuotationToken(int depth) {
            super(">".repeat(depth) + " ");
            this.depth = depth;
        }

        public static boolean lex(StringReader reader, List<Token> tokens) {
            var quotes = readTextUntil(reader, c -> c != '>');
            if (!reader.canRead() || reader.read() != ' ') return false;

            tokens.add(new QuotationToken(quotes.length()));
            return true;
        }

        @Override
        public boolean isBoundary() {
            return true;
        }
    }

    public static final class HorizontalRuleToken extends Token {
        public HorizontalRuleToken() {
            super("---");
        }

        public static boolean lex(StringReader reader, List<Token> tokens) {
            if (reader.getCursor() - 2 < 0 || reader.peek(-1) != '\n' || reader.peek(-2) != '\n') return false;

            var dashes = readTextUntil(reader, c -> c != '-');
            if (dashes.length() != 3 || !reader.canRead(2) || reader.peek() != '\n' || reader.peek(1) != '\n') {
                return false;
            }

            tokens.add(new HorizontalRuleToken());
            return true;
        }
    }

    public static final class OpenLinkToken extends Token {
        public OpenLinkToken() {
            super("[");
        }
    }

    public static final class ImageToken extends Token {

        public final String description, identifier;

        public ImageToken(String description, String identifier) {
            super("![" + description + "](" + identifier + ")");
            this.description = description;
            this.identifier = identifier;
        }

        public static boolean lex(StringReader reader, List<Token> tokens) {
            reader.skip();
            if (!reader.canRead() || reader.read() != '[') return false;

            var description = readTextUntil(reader, c -> c == ']');
            if (!reader.canRead(2) || reader.peek(1) != '(') return false;
            reader.setCursor(reader.getCursor() + 2);

            var identifier = readTextUntil(reader, c -> c == ')');
            if (!reader.canRead() || Identifier.tryParse(identifier) == null) return false;
            reader.skip();

            tokens.add(new ImageToken(description, identifier));
            return true;
        }
    }

    public static final class CloseLinkToken extends Token {

        public final @NotNull String link;

        public CloseLinkToken(@NotNull String link) {
            super("](" + link + ")");
            this.link = link;
        }

        private static boolean lex(StringReader reader, List<Token> tokens) {
            reader.skip();

            if (!reader.canRead() || reader.peek() != '(') {
                return false;
            }

            reader.skip();
            var link = readTextUntil(reader, c -> c == ')');

            if (!reader.canRead()) return false;
            reader.skip();

            tokens.add(new CloseLinkToken(link));
            return true;
        }
    }

    public static final class OpenColorToken extends Token {

        public final @NotNull UnaryOperator<Style> style;

        public OpenColorToken(String content, @NotNull UnaryOperator<Style> style) {
            super(content);
            this.style = style;
        }

        private static boolean lex(StringReader reader, List<Token> tokens) {
            reader.skip();
            if (!reader.canRead()) return false;

            if (reader.peek() == '}') {
                reader.skip();
                tokens.add(new CloseColorToken());
            } else {
                if (reader.peek() == '#') {
                    reader.skip();

                    var color = readTextUntil(reader, c -> c == '}');
                    if (!reader.canRead()) return false;
                    reader.skip();

                    if (!color.matches("[0-9a-fA-F]{6}")) return false;
                    tokens.add(new OpenColorToken("{#" + color + "}", style -> style.withColor(Integer.parseInt(color, 16))));
                } else {
                    var color = readTextUntil(reader, c -> c == '}');
                    if (!reader.canRead()) return false;
                    reader.skip();

                    if (!FORMATTING_COLORS.containsKey(color)) return false;
                    tokens.add(new OpenColorToken("{" + color + "}", style -> style.withFormatting(FORMATTING_COLORS.get(color))));
                }
            }

            return true;
        }
    }

    public static final class CloseColorToken extends Token {
        private CloseColorToken() {
            super("{}");
        }
    }
}