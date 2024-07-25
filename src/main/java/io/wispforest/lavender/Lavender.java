package io.wispforest.lavender;

import com.mojang.logging.LogUtils;
import io.wispforest.endec.Endec;
import io.wispforest.endec.impl.BuiltInEndecs;
import io.wispforest.endec.impl.StructEndecBuilder;
import io.wispforest.lavender.book.LavenderBookItem;
import io.wispforest.owo.serialization.CodecUtils;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.minecraft.world.PersistentState;
import org.slf4j.Logger;
import io.wispforest.lavender.LavenderConfig;

import java.util.UUID;

public class Lavender implements ModInitializer {

    public static final Logger LOGGER = LogUtils.getLogger();
    public static final String MOD_ID = "lavender";
    public static final SoundEvent ITEM_BOOK_OPEN = SoundEvent.of(id("item.book.open"));

    public static final Identifier WORLD_ID_CHANNEL = Lavender.id("world_id_channel");

    public static final LavenderConfig CONFIG = LavenderConfig.createAndLoad();

    @Override
    public void onInitialize() {
        Registry.register(Registries.ITEM, id("dynamic_book"), LavenderBookItem.DYNAMIC_BOOK);
        Registry.register(Registries.SOUND_EVENT, ITEM_BOOK_OPEN.getId(), ITEM_BOOK_OPEN);

        CommandRegistrationCallback.EVENT.register(LavenderCommands::register);

        PayloadTypeRegistry.playS2C().register(WorldUUIDPayload.ID, CodecUtils.toPacketCodec(WorldUUIDPayload.ENDEC));

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            sender.sendPacket(new WorldUUIDPayload(server.getOverworld().getPersistentStateManager().getOrCreate(WorldUUIDState.TYPE, "lavender_world_id").id));
        });
    }

    public static Identifier id(String path) {
        return Identifier.of(MOD_ID, path);
    }

    public static class WorldUUIDState extends PersistentState {

        public static final PersistentState.Type<WorldUUIDState> TYPE = new Type<>(() -> {
            var state = new WorldUUIDState(UUID.randomUUID());
            state.markDirty();
            return state;
        }, WorldUUIDState::read, DataFixTypes.LEVEL);

        public final UUID id;

        private WorldUUIDState(UUID id) {
            this.id = id;
        }

        @Override
        public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
            nbt.putUuid("UUID", id);
            return nbt;
        }

        public static WorldUUIDState read(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
            return new WorldUUIDState(nbt.contains("UUID", NbtElement.INT_ARRAY_TYPE) ? nbt.getUuid("UUID") : null);
        }
    }

    public record WorldUUIDPayload(UUID worldUuid) implements CustomPayload {
        public static final CustomPayload.Id<WorldUUIDPayload> ID = new CustomPayload.Id<>(Lavender.id("world_uuid"));
        public static final Endec<WorldUUIDPayload> ENDEC = StructEndecBuilder.of(
                BuiltInEndecs.UUID.fieldOf("world_uuid", WorldUUIDPayload::worldUuid),
                WorldUUIDPayload::new
        );

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }
}
