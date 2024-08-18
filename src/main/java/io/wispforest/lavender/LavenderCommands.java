package io.wispforest.lavender;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import io.wispforest.lavender.book.Book;
import io.wispforest.lavender.book.BookLoader;
import io.wispforest.lavender.book.LavenderBookItem;
import io.wispforest.lavender.client.StructureOverlayRenderer;
import io.wispforest.lavender.structure.LavenderStructures;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.IdentifierArgumentType;
import net.minecraft.component.Component;
import net.minecraft.nbt.NbtOps;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.stream.Collectors;
import java.util.stream.Stream;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public class LavenderCommands {

    private static final SimpleCommandExceptionType NO_SUCH_BOOK = new SimpleCommandExceptionType(Text.literal("No such book is loaded"));

    private static final SuggestionProvider<FabricClientCommandSource> LOADED_BOOKS = (context, builder) -> {
        return CommandSource.suggestIdentifiers(BookLoader.loadedBooks().stream().map(Book::id), builder);
    };

    @Environment(EnvType.CLIENT)
    public static class Client {

        private static final SimpleCommandExceptionType NO_SUCH_STRUCTURE = new SimpleCommandExceptionType(Text.literal("No such structure is loaded"));
        private static final SuggestionProvider<FabricClientCommandSource> STRUCTURE_INFO = (context, builder) ->
                CommandSource.suggestMatching(LavenderStructures.loadedStructures().stream().map(Identifier::toString), builder);

        private static int executeGetLavenderBook(CommandContext<FabricClientCommandSource> context, boolean forceDynamicBook) throws CommandSyntaxException {
            var book = BookLoader.get(context.getArgument("book_id", Identifier.class));
            if (book == null) {
                throw NO_SUCH_BOOK.create();
            }

            var stack = forceDynamicBook
                    ? LavenderBookItem.createDynamic(book)
                    : LavenderBookItem.itemOf(book);

            var command = "/give @s " + Registries.ITEM.getId(stack.getItem());

            var ops = context.getSource().getWorld().getRegistryManager().getOps(NbtOps.INSTANCE);
            var components = stack.getComponentChanges().entrySet().stream().flatMap(entry -> {
                var componentType = entry.getKey();
                var typeId = Registries.DATA_COMPONENT_TYPE.getId(componentType);
                if (typeId == null) return Stream.empty();

                var componentOptional = entry.getValue();
                if (componentOptional.isPresent()) {
                    Component<?> component = Component.of(componentType, componentOptional.get());
                    return component.encode(ops).result().stream().map(value -> typeId + "=" + value);
                } else {
                    return Stream.of("!" + typeId);
                }
            }).collect(Collectors.joining(String.valueOf(',')));

            if (!components.isEmpty()) {
                command += "[" + components + "]";
            }

            if (stack.getCount() > 1) {
                command += " " + stack.getCount();
            }

            var jAvAsE = command;
            context.getSource().getClient().send(() -> {
                context.getSource().getClient().setScreen(new ChatScreen(jAvAsE));
            });

            return 0;
        }

        public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher, CommandRegistryAccess access) {
            dispatcher.register(literal("get-lavender-book").requires(source -> source.hasPermissionLevel(2))
                    .then(argument("book_id", IdentifierArgumentType.identifier()).suggests(LOADED_BOOKS)
                            .executes(context -> executeGetLavenderBook(context, false))
                            .then(argument("force_dynamic_book", BoolArgumentType.bool())
                                    .executes(context -> executeGetLavenderBook(context, BoolArgumentType.getBool(context, "force_dynamic_book"))))));


            dispatcher.register(literal("structure-overlay")
                    .then(literal("clear-all").executes(context -> {
                        StructureOverlayRenderer.clearOverlays();
                        return 0;
                    }))

                    .then(literal("add")
                            .then(argument("structure", IdentifierArgumentType.identifier()).suggests(STRUCTURE_INFO).executes(context -> {
                                var structureId = context.getArgument("structure", Identifier.class);
                                if (LavenderStructures.get(structureId) == null) throw NO_SUCH_STRUCTURE.create();

                                StructureOverlayRenderer.addPendingOverlay(structureId);
                                return 0;
                            }))));
        }
    }

}
