package io.wispforest.lavender.book;

import com.google.common.base.Preconditions;
import io.wispforest.lavender.Lavender;
import io.wispforest.lavender.client.LavenderBookScreen;
import io.wispforest.owo.ops.TextOps;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.component.ComponentType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LavenderBookItem extends Item {

    public static final ComponentType<Identifier> BOOK_ID = Registry.register(
            Registries.DATA_COMPONENT_TYPE,
            Lavender.id("book_id"),
            ComponentType.<Identifier>builder()
                    .codec(Identifier.CODEC)
                    .packetCodec(Identifier.PACKET_CODEC)
                    .build()
    );

    public static final LavenderBookItem DYNAMIC_BOOK = new LavenderBookItem(null, new Settings().maxCount(1).registryKey(RegistryKey.of(RegistryKeys.ITEM, Lavender.id("dynamic_book"))));

    private static final Map<Identifier, LavenderBookItem> BOOK_ITEMS = new HashMap<>();

    private final @Nullable Identifier bookId;

    private LavenderBookItem(@Nullable Identifier bookId, Settings settings) {
        super(settings);
        this.bookId = bookId;
    }

    protected LavenderBookItem(Settings settings, @NotNull Identifier bookId) {
        super(settings);
        this.bookId = Preconditions.checkNotNull(bookId, "Book-specific book items must have a non-null book ID");
    }

    @SuppressWarnings("DataFlowIssue")
    protected @NotNull Identifier bookId() {
        return this.bookId;
    }

    /**
     * Shorthand of {@link #registerForBook(Identifier, Identifier, net.minecraft.item.Item.Settings)} which
     * uses {@code bookId} as the item id
     */
    public static LavenderBookItem registerForBook(@NotNull Identifier bookId, Settings settings) {
        return registerForBook(bookId, bookId, settings);
    }

    /**
     * Create, register and return a book item under {@code itemId} as the canonical
     * item for the book referred to by the given {@code bookId}
     */
    public static LavenderBookItem registerForBook(@NotNull Identifier bookId, @NotNull Identifier itemId, Settings settings) {
        return registerForBook(Registry.register(Registries.ITEM, itemId, new LavenderBookItem(bookId, settings.registryKey(RegistryKey.of(RegistryKeys.ITEM, itemId)))));
    }

    /**
     * Register and return the given book item as the canonical item
     * for the book referred to by the item's bookId field
     */
    public static LavenderBookItem registerForBook(LavenderBookItem item) {
        BOOK_ITEMS.put(item.bookId(), item);
        return item;
    }

    /**
     * @return The id of the book referred to by the given item stack
     * (either through static associating of NBT in the case the dynamic book),
     * or {@code null} if neither a static association nor NBT exist
     */
    public static @Nullable Identifier bookIdOf(ItemStack bookStack) {
        if (!(bookStack.getItem() instanceof LavenderBookItem book)) return null;
        return book.bookId != null ? book.bookId : bookStack.get(BOOK_ID);
    }

    /**
     * Convenience variant of {@link #bookIdOf(ItemStack)} which attempts
     * looking up the book referred to by the id said method returns
     */
    public static @Nullable Book bookOf(ItemStack bookStack) {
        var bookId = bookIdOf(bookStack);
        if (bookId == null) return null;

        return BookLoader.get(bookId);
    }

    /**
     * @return An item stack representing the given book. If a canonical item
     * was registered, it is used - otherwise a dynamic book with NBT is created
     */
    public static ItemStack itemOf(Book book) {
        var bookItem = BOOK_ITEMS.get(book.id());
        if (bookItem != null) {
            return bookItem.getDefaultStack();
        } else {
            return createDynamic(book);
        }
    }

    @Override
    public Text getName(ItemStack stack) {
        if (this.bookId != null) return super.getName(stack);

        var book = bookOf(stack);
        if (book == null || book.dynamicBookName() == null) return super.getName(stack);

        return book.dynamicBookName();
    }

    /**
     * @return A dynamic book with the correct NBT to represent the given book
     */
    public static ItemStack createDynamic(Book book) {
        var stack = DYNAMIC_BOOK.getDefaultStack();
        stack.set(BOOK_ID, book.id());
        return stack;
    }

    @Override
    public ActionResult use(World world, PlayerEntity user, Hand hand) {
        var playerStack = user.getStackInHand(hand);

        var bookId = bookIdOf(playerStack);
        if (bookId == null) return ActionResult.SUCCESS;
        if (!world.isClient) return ActionResult.SUCCESS;

        var book = BookLoader.get(bookId);
        if (book == null) {
            user.sendMessage(Text.translatable("text.lavender.unknown_book", bookId).formatted(Formatting.RED), false);
            return ActionResult.PASS;
        }

        openBookScreen(book);
        return ActionResult.SUCCESS;
    }

    @Environment(EnvType.CLIENT)
    private static void openBookScreen(Book book) {
        MinecraftClient.getInstance().setScreen(new LavenderBookScreen(book));
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        var bookId = bookIdOf(stack);
        if (bookId == null) {
            tooltip.add(TextOps.withFormatting("⚠ §No associated book", Formatting.RED, Formatting.DARK_GRAY));
        } else {
            var book = BookLoader.get(bookId);
            if (book != null) return;

            tooltip.add(TextOps.withFormatting("⚠ §Unknown book \"" + bookId + "\"", Formatting.RED, Formatting.DARK_GRAY));
        }
    }
}
