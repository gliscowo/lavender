package io.wispforest.lavender.structure;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import it.unimi.dsi.fastutil.chars.Char2ObjectOpenHashMap;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.command.argument.BlockArgumentParser;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntryList;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.state.property.Property;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Identifier;
import net.minecraft.util.JsonHelper;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.BlockRenderView;
import net.minecraft.world.World;
import net.minecraft.world.biome.ColorResolver;
import net.minecraft.world.chunk.light.LightingProvider;
import org.apache.commons.lang3.mutable.MutableInt;
import org.apache.commons.lang3.tuple.MutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.StreamSupport;

public class StructureTemplate implements Iterable<Pair<BlockPos, BlockStatePredicate>> {

    private static final char AIR_BLOCKSTATE_KEY = '_';
    private static final char NULL_BLOCKSTATE_KEY = ' ';
    private static final char ANCHOR_BLOCKSTATE_KEY = '#';

    private final BlockStatePredicate[][][] predicates;
    private final EnumMap<BlockStatePredicate.MatchCategory, MutableInt> predicateCountByType;
    public final int xSize, ySize, zSize;
    public final Vec3i anchor;
    public final Identifier id;

    public StructureTemplate(Identifier id, BlockStatePredicate[][][] predicates, int xSize, int ySize, int zSize, @Nullable Vec3i anchor) {
        this.id = id;
        this.predicates = predicates;
        this.xSize = xSize;
        this.ySize = ySize;
        this.zSize = zSize;

        this.anchor = anchor != null
            ? anchor
            : new Vec3i(this.xSize / 2, 0, this.ySize / 2);

        this.predicateCountByType = new EnumMap<>(BlockStatePredicate.MatchCategory.class);
        for (var type : BlockStatePredicate.MatchCategory.values()) {
            this.forEachPredicate((blockPos, predicate) -> {
                if (!predicate.isOf(type)) return;
                this.predicateCountByType.computeIfAbsent(type, $ -> new MutableInt()).increment();
            });
        }
    }

    /**
     * @return How many predicates of this structure template fall
     * into the given match category
     */
    public int predicatesOfType(BlockStatePredicate.MatchCategory type) {
        return this.predicateCountByType.get(type).intValue();
    }

    public Identifier id() {
        return this.id;
    }

    public BlockStatePredicate[][][] predicates() {
        return this.predicates;
    }

    public EnumMap<BlockStatePredicate.MatchCategory, MutableInt> predicateCountByType() {
        return this.predicateCountByType;
    }

    public int xSize() {
        return this.xSize;
    }

    public int ySize() {
        return this.ySize;
    }

    public int zSize() {
        return this.zSize;
    }

    /**
     * @return The anchor position of this template,
     * to be used when placing in the world
     */
    public Vec3i anchor() {
        return this.anchor;
    }

    // --- iteration ---

    public void forEachPredicate(BiConsumer<BlockPos, BlockStatePredicate> action) {
        this.forEachPredicate(action, BlockRotation.NONE);
    }

    /**
     * Execute {@code action} for every predicate in this structure template,
     * rotated on the y-axis by {@code rotation}
     */
    public void forEachPredicate(BiConsumer<BlockPos, BlockStatePredicate> action, BlockRotation rotation) {
        var mutable = new BlockPos.Mutable();

        for (int x = 0; x < this.predicates.length; x++) {
            for (int y = 0; y < this.predicates[x].length; y++) {
                for (int z = 0; z < this.predicates[x][y].length; z++) {

                    switch (rotation) {
                        case CLOCKWISE_90 -> mutable.set(this.zSize - z - 1, y, x);
                        case COUNTERCLOCKWISE_90 -> mutable.set(z, y, this.xSize - x - 1);
                        case CLOCKWISE_180 -> mutable.set(this.xSize - x - 1, y, this.zSize - z - 1);
                        default -> mutable.set(x, y, z);
                    }

                    action.accept(mutable, this.predicates[x][y][z]);
                }
            }
        }
    }

    @Override
    public Iterator<Pair<BlockPos, BlockStatePredicate>> iterator() {
        return iterator(BlockRotation.NONE);
    }

    @Override
    public void forEach(Consumer<? super Pair<BlockPos, BlockStatePredicate>> action) {
        var mutablePair = new MutablePair<BlockPos, BlockStatePredicate>();
        forEachPredicate((pos, predicate) -> {
            mutablePair.setLeft(pos);
            mutablePair.setRight(predicate);
            action.accept(mutablePair);
        });
    }

    public Iterator<Pair<BlockPos, BlockStatePredicate>> iterator(BlockRotation rotation) {
        return new StructureTemplateIterator(this, rotation);
    }

    // --- validation ---

    /**
     * Shorthand of {@link #validate(World, BlockPos, BlockRotation)} which uses
     * {@link BlockRotation#NONE}
     */
    public boolean validate(World world, BlockPos anchor) {
        return this.validate(world, anchor, BlockRotation.NONE);
    }

    /**
     * @return {@code true} if this template matches the block states present
     * in the given world at the given position
     */
    public boolean validate(World world, BlockPos anchor, BlockRotation rotation) {
        return this.countValidStates(world, anchor, rotation) == this.predicatesOfType(BlockStatePredicate.MatchCategory.NON_NULL);
    }

    /**
     * Shorthand of {@link #countValidStates(World, BlockPos, BlockRotation)} which uses
     * {@link BlockRotation#NONE}
     */
    public int countValidStates(World world, BlockPos anchor) {
        return countValidStates(world, anchor, BlockRotation.NONE, BlockStatePredicate.MatchCategory.NON_NULL);
    }

    /**
     * Shorthand of {@link #countValidStates(World, BlockPos, BlockRotation, BlockStatePredicate.MatchCategory)}
     * which uses {@link BlockStatePredicate.MatchCategory#NON_NULL}
     */
    public int countValidStates(World world, BlockPos anchor, BlockRotation rotation) {
        return countValidStates(world, anchor, rotation, BlockStatePredicate.MatchCategory.NON_NULL);
    }

    /**
     * @return The amount of predicates in this template which match the block
     * states present in the given world at the given position
     */
    public int countValidStates(World world, BlockPos anchor, BlockRotation rotation, BlockStatePredicate.MatchCategory predicateFilter) {
        var validStates = new MutableInt();
        var mutable = new BlockPos.Mutable();

        this.forEachPredicate((pos, predicate) -> {
            if (!predicate.isOf(predicateFilter)) return;

            if (predicate.matches(world.getBlockState(mutable.set(pos).move(anchor)).rotate(inverse(rotation)))) {
                validStates.increment();
            }
        }, rotation);

        return validStates.intValue();
    }

    // --- utility ---

    public BlockRenderView asBlockRenderView() {
        return new StructureTemplateRenderView(Objects.requireNonNull(MinecraftClient.getInstance().world), this);
    }

    public static BlockRotation inverse(BlockRotation rotation) {
        return switch (rotation) {
            case NONE -> BlockRotation.NONE;
            case CLOCKWISE_90 -> BlockRotation.COUNTERCLOCKWISE_90;
            case COUNTERCLOCKWISE_90 -> BlockRotation.CLOCKWISE_90;
            case CLOCKWISE_180 -> BlockRotation.CLOCKWISE_180;
        };
    }

    // --- parsing ---

    public static StructureTemplate parse(Identifier resourceId, JsonObject json) {
        Vec3i anchor = null;

        var keyObject = JsonHelper.getObject(json, "keys");
        var keys = StructureTemplate.buildStructureKeysMap(keyObject);

        var layersArray = JsonHelper.getArray(json, "layers");
        int xSize = 0, ySize = layersArray.size(), zSize = 0;

        for (var element : layersArray) {
            if (!(element instanceof JsonArray layer)) {
                throw new JsonParseException("Every element in the 'layers' array must itself be an array");
            }

            if (zSize == 0) {
                zSize = layer.size();
            } else if (zSize != layer.size()) {
                throw new JsonParseException("Every layer must have the same amount of rows");
            }

            for (var rowElement : layer) {
                if (!rowElement.isJsonPrimitive()) {
                    throw new JsonParseException("Every element in a row must be a primitive");
                }
                if (xSize == 0) {
                    xSize = rowElement.getAsString().length();
                } else if (xSize != rowElement.getAsString().length()) {
                    throw new JsonParseException("Every row must have the same length");
                }
            }
        }

        var result = new BlockStatePredicate[xSize][][];
        for (int x = 0; x < xSize; x++) {
            result[x] = new BlockStatePredicate[ySize][];
            for (int y = 0; y < ySize; y++) {
                result[x][y] = new BlockStatePredicate[zSize];
            }
        }

        for (int y = 0; y < layersArray.size(); y++) {
            var layer = (JsonArray) layersArray.get(y);
            for (int z = 0; z < layer.size(); z++) {
                var row = layer.get(z).getAsString();
                for (int x = 0; x < row.length(); x++) {
                    char key = row.charAt(x);

                    BlockStatePredicate predicate;
                    if (keys.containsKey(key)) {
                        predicate = keys.get(key);

                        if (key == ANCHOR_BLOCKSTATE_KEY) {
                            if (anchor != null) {
                                throw new JsonParseException("Anchor key '#' cannot be used twice within the same structure");
                            } else {
                                anchor = new Vec3i(x, y, z);
                            }
                        }
                    } else if (key == NULL_BLOCKSTATE_KEY) {
                        predicate = BlockStatePredicate.NULL_PREDICATE;
                    } else if (key == AIR_BLOCKSTATE_KEY) {
                        predicate = BlockStatePredicate.AIR_PREDICATE;
                    } else {
                        throw new JsonParseException("Unknown key '" + key + "'");
                    }

                    result[x][y][z] = predicate;
                }
            }
        }

        return new StructureTemplate(resourceId, result, xSize, ySize, zSize, anchor);
    }

    private static Char2ObjectOpenHashMap<BlockStatePredicate> buildStructureKeysMap(JsonObject keyObject) {
        var keys = new Char2ObjectOpenHashMap<BlockStatePredicate>();
        for (var entry : keyObject.entrySet()) {
            char key = blockstateKeyForEntry(entry);

            if (keys.containsKey(key)) {
                throw new JsonParseException("Keys can only appear once. Key '%s' appears twice.".formatted(key));
            }

            if (entry.getValue().isJsonArray()) {
                JsonArray blockStringsArray = entry.getValue().getAsJsonArray();
                var blockStatePredicates = StreamSupport.stream(blockStringsArray.spliterator(), false)
                        .map(blockString -> StructureTemplate.parseStringToBlockStatePredicate(blockString.getAsString()))
                        .toArray(BlockStatePredicate[]::new);
                keys.put(key, new OrBlockStatePredicate(blockStatePredicates));
            } else if (entry.getValue().isJsonPrimitive()) {
                keys.put(key, StructureTemplate.parseStringToBlockStatePredicate(entry.getValue().getAsString()));
            } else {
                throw new JsonParseException("The values for the map of key-to-blocks must either be a string or an array of strings.");
            }
        }

        return keys;
    }

    private static char blockstateKeyForEntry(final Map.Entry<String, JsonElement> entry) {
        char key;
        if (entry.getKey().length() == 1) {
            key = entry.getKey().charAt(0);
            if (key == ANCHOR_BLOCKSTATE_KEY) {
                throw new JsonParseException("Key '#' is reserved for 'anchor' declarations. Rename the key to 'anchor' and use '#' in the structure definition.");
            } else if (key == AIR_BLOCKSTATE_KEY) {
                throw new JsonParseException("Key '_' is a reserved key for marking a block that must be AIR.");
            } else if (key == NULL_BLOCKSTATE_KEY) {
                throw new JsonParseException("Key ' ' is a reserved key for marking a block that can be anything.");
            }
        } else if ("anchor".equals(entry.getKey())) {
            key = ANCHOR_BLOCKSTATE_KEY;
        } else {
            throw new JsonParseException("Keys should only be a single character or should be 'anchor'.");
        }
        return key;
    }

    private static BlockStatePredicate parseStringToBlockStatePredicate(String blockOrTag) {
        try {
            var result = BlockArgumentParser.blockOrTag(Registries.BLOCK.getReadOnlyWrapper(), blockOrTag, false);
            return result.map(
                    blockResult -> new SingleBlockStatePredicate(blockResult.blockState(), blockResult.properties()),
                    tagResult -> new TagBlockStatePredicate((RegistryEntryList.Named<Block>) tagResult.tag(), tagResult.vagueProperties())
            );
        } catch (CommandSyntaxException e) {
            throw new JsonParseException("Failed to parse block state predicate", e);
        }
    }

    public static class OrBlockStatePredicate implements BlockStatePredicate {
        private final BlockStatePredicate[] predicates;
        private final BlockState[] previewStates;

        public OrBlockStatePredicate(BlockStatePredicate[] predicates) {
            this.predicates = predicates;
            this.previewStates = Arrays.stream(predicates)
                    .flatMap((predicate) -> Arrays.stream(predicate.previewBlockstates()))
                    .toArray(BlockState[]::new);
        }

        @Override
        public BlockState[] previewBlockstates() {
            return this.previewStates;
        }

        @Override
        public Result test(BlockState state) {
            boolean hasBlockMatch = false;
            for (var predicate : this.predicates) {
                var result = predicate.test(state);
                if (result == Result.STATE_MATCH)
                    return Result.STATE_MATCH;
                else if (result == Result.BLOCK_MATCH)
                    hasBlockMatch = true;
            }

            return hasBlockMatch ? Result.BLOCK_MATCH : Result.NO_MATCH;
        }
    }

    public static class SingleBlockStatePredicate implements BlockStatePredicate {
        private final BlockState state;
        private final BlockState[] states;
        private final Map<Property<?>, Comparable<?>> properties;

        public SingleBlockStatePredicate(BlockState state, Map<Property<?>, Comparable<?>> properties) {
            this.state = state;
            this.states = new BlockState[]{state};
            this.properties = properties;
        }

        @Override
        public BlockState[] previewBlockstates() {
            return this.states;
        }

        @Override
        public Result test(BlockState state) {
            if (state.getBlock() != this.state.getBlock()) return Result.NO_MATCH;

            for (var propAndValue : this.properties.entrySet()) {
                if (!state.get(propAndValue.getKey()).equals(propAndValue.getValue())) {
                    return Result.BLOCK_MATCH;
                }
            }

            return Result.STATE_MATCH;
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static class TagBlockStatePredicate implements BlockStatePredicate {
        private final TagKey<Block> tag;
        private final Map<String, String> vagueProperties;
        private final BlockState[] previewStates;

        public TagBlockStatePredicate(RegistryEntryList.Named<Block> tagEntries, Map<String, String> properties) {
            this.vagueProperties = properties;
            this.tag = tagEntries.getTag();
            this.previewStates = tagEntries.stream().map(entry -> {
                var block = entry.value();
                var state = block.getDefaultState();

                for (var propAndValue : this.vagueProperties.entrySet()) {
                    Property prop = block.getStateManager().getProperty(propAndValue.getKey());
                    if (prop == null) continue;

                    Optional<Comparable> value = prop.parse(propAndValue.getValue());
                    if (value.isEmpty()) continue;

                    state = state.with(prop, value.get());
                }

                return state;
            }).toArray(BlockState[]::new);
        }

        @Override
        public BlockState[] previewBlockstates() {
            return this.previewStates;
        }

        @Override
        public Result test(BlockState state) {
            if (!state.isIn(this.tag))
                return Result.NO_MATCH;

            for (var propAndValue : this.vagueProperties.entrySet()) {
                var prop = state.getBlock().getStateManager().getProperty(propAndValue.getKey());
                if (prop == null)
                    return Result.BLOCK_MATCH;

                var expected = prop.parse(propAndValue.getValue());
                if (expected.isEmpty())
                    return Result.BLOCK_MATCH;

                if (!state.get(prop).equals(expected.get()))
                    return Result.BLOCK_MATCH;
            }

            return Result.STATE_MATCH;
        }
    }

    private record StructureTemplateRenderView(World world, StructureTemplate template) implements BlockRenderView {
        @Override
        public float getBrightness(Direction direction, boolean shaded) {
            return 1.0f;
        }

        @Override
        public LightingProvider getLightingProvider() {
            return this.world.getLightingProvider();
        }

        @Override
        public int getColor(BlockPos pos, ColorResolver colorResolver) {
            return colorResolver.getColor(this.world.getBiome(pos).value(), pos.getX(), pos.getZ());
        }

        @Override
        public BlockEntity getBlockEntity(BlockPos pos) {
            return null;
        }

        @Override
        public BlockState getBlockState(BlockPos pos) {
            if (pos.getX() < 0 || pos.getX() >= this.template.xSize ||
                pos.getY() < 0 || pos.getY() >= this.template.ySize ||
                pos.getZ() < 0 || pos.getZ() >= this.template.zSize)
                return Blocks.AIR.getDefaultState();
            return this.template.predicates()[pos.getX()][pos.getY()][pos.getZ()].preview();
        }

        @Override
        public FluidState getFluidState(BlockPos pos) {
            return Fluids.EMPTY.getDefaultState();
        }

        @Override
        public int getHeight() {
            return this.world.getHeight();
        }

        @Override
        public int getBottomY() {
            return this.world.getBottomY();
        }
    }

    private static final class StructureTemplateIterator implements Iterator<Pair<BlockPos, BlockStatePredicate>> {

        private final StructureTemplate template;

        private final BlockPos.Mutable currentPos = new BlockPos.Mutable();

        private final MutablePair<BlockPos, BlockStatePredicate> currentElement = new MutablePair<>();

        private final BlockRotation rotation;

        private int posX = 0, posY = 0, posZ = 0;

        private StructureTemplateIterator(StructureTemplate template, BlockRotation rotation) {
            this.template = template;
            this.rotation = rotation;
        }

        @Override
        public boolean hasNext() {
            return this.posX < this.template.xSize() - 1 && this.posY < this.template.ySize() - 1 && this.posZ < this.template.zSize() - 1;
        }

        @Override
        public Pair<BlockPos, BlockStatePredicate> next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }

            switch (this.rotation) {
                case CLOCKWISE_90 -> this.currentPos.set(this.template.zSize() - this.posZ - 1, this.posY, this.posX);
                case COUNTERCLOCKWISE_90 -> this.currentPos.set(this.posZ, this.posY, this.template.xSize() - this.posX - 1);
                case CLOCKWISE_180 ->
                        this.currentPos.set(this.template.xSize() - this.posX - 1, this.posY, this.template.zSize() - this.posZ - 1);
                default -> this.currentPos.set(this.posX, this.posY, this.posZ);
            }

            this.currentElement.setRight(this.template.predicates()[this.posX][this.posY][this.posZ]);
            this.currentElement.setLeft(this.currentPos);

            // Advance to next position
            if (++this.posZ >= this.template.zSize()) {
                this.posZ = 0;
                if (++this.posY >= this.template.ySize()) {
                    this.posY = 0;
                    ++this.posX;
                }
            }

            return this.currentElement;
        }
    }
}
