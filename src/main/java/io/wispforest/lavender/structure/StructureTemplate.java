package io.wispforest.lavender.structure;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import it.unimi.dsi.fastutil.chars.Char2ObjectOpenHashMap;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.command.argument.BlockArgumentParser;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.registry.Registries;
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
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Optional;
import java.util.function.BiConsumer;

public class StructureTemplate {

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
     * which uses {@link io.wispforest.lavender.structure.BlockStatePredicate.MatchCategory#NON_NULL}
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
        var world = MinecraftClient.getInstance().world;
        return new BlockRenderView() {
            @Override
            public float getBrightness(Direction direction, boolean shaded) {
                return 1f;
            }

            @Override
            public LightingProvider getLightingProvider() {
                return world.getLightingProvider();
            }

            @Override
            public int getColor(BlockPos pos, ColorResolver colorResolver) {
                return colorResolver.getColor(world.getBiome(pos).value(), pos.getX(), pos.getZ());
            }

            @Nullable
            @Override
            public BlockEntity getBlockEntity(BlockPos pos) {
                return null;
            }

            @Override
            public BlockState getBlockState(BlockPos pos) {
                if (pos.getX() < 0 || pos.getX() >= StructureTemplate.this.xSize || pos.getY() < 0 || pos.getY() >= StructureTemplate.this.ySize || pos.getZ() < 0 || pos.getZ() >= StructureTemplate.this.zSize)
                    return Blocks.AIR.getDefaultState();
                return StructureTemplate.this.predicates[pos.getX()][pos.getY()][pos.getZ()].preview();
            }

            @Override
            public FluidState getFluidState(BlockPos pos) {
                return Fluids.EMPTY.getDefaultState();
            }

            @Override
            public int getHeight() {
                return world.getHeight();
            }

            @Override
            public int getBottomY() {
                return world.getBottomY();
            }
        };
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

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static StructureTemplate parse(Identifier resourceId, JsonObject json) {
        var keyObject = JsonHelper.getObject(json, "keys");
        var keys = new Char2ObjectOpenHashMap<BlockStatePredicate>();
        Vec3i anchor = null;

        for (var entry : keyObject.entrySet()) {
            char key;
            if (entry.getKey().length() == 1) {
                key = entry.getKey().charAt(0);
                if (key == '#') {
                    throw new JsonParseException("Key '#' is reserved for 'anchor' declarations");
                }

            } else if (entry.getKey().equals("anchor")) {
                key = '#';
            } else {
                continue;
            }

            try {
                var result = BlockArgumentParser.blockOrTag(Registries.BLOCK, entry.getValue().getAsString(), false);
                if (result.left().isPresent()) {
                    var predicate = result.left().get();

                    keys.put(key, new BlockStatePredicate() {
                        @Override
                        public BlockState preview() {
                            return predicate.blockState();
                        }

                        @Override
                        public Result test(BlockState state) {
                            if (state.getBlock() != predicate.blockState().getBlock()) return Result.NO_MATCH;

                            for (var propAndValue : predicate.properties().entrySet()) {
                                if (!state.get(propAndValue.getKey()).equals(propAndValue.getValue())) {
                                    return Result.BLOCK_MATCH;
                                }
                            }

                            return Result.STATE_MATCH;
                        }
                    });
                } else {
                    var predicate = result.right().get();

                    var previewStates = new ArrayList<BlockState>();
                    predicate.tag().forEach(registryEntry -> {
                        var block = registryEntry.value();
                        var state = block.getDefaultState();

                        for (var propAndValue : predicate.vagueProperties().entrySet()) {
                            Property prop = block.getStateManager().getProperty(propAndValue.getKey());
                            if (prop == null) return;

                            Optional<Comparable> value = prop.parse(propAndValue.getValue());
                            if (value.isEmpty()) return;

                            state = state.with(prop, value.get());
                        }

                        previewStates.add(state);
                    });

                    keys.put(key, new BlockStatePredicate() {
                        @Override
                        public BlockState preview() {
                            if (previewStates.isEmpty()) return Blocks.AIR.getDefaultState();
                            return previewStates.get((int) (System.currentTimeMillis() / 1000 % previewStates.size()));
                        }

                        @Override
                        public Result test(BlockState state) {
                            if (!state.isIn(predicate.tag())) return Result.NO_MATCH;

                            for (var propAndValue : predicate.vagueProperties().entrySet()) {
                                var prop = state.getBlock().getStateManager().getProperty(propAndValue.getKey());
                                if (prop == null) return Result.BLOCK_MATCH;

                                var expected = prop.parse(propAndValue.getValue());
                                if (expected.isEmpty()) return Result.BLOCK_MATCH;

                                if (!state.get(prop).equals(expected.get())) return Result.BLOCK_MATCH;
                            }

                            return Result.STATE_MATCH;
                        }
                    });
                }
            } catch (CommandSyntaxException e) {
                throw new JsonParseException("Failed to parse block state predicate", e);
            }
        }

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

                        if (key == '#') {
                            if (anchor != null) {
                                throw new JsonParseException("Anchor key '#' cannot be used twice within the same structure");
                            }

                            anchor = new Vec3i(x, y, z);
                        }
                    } else if (key == ' ') {
                        predicate = BlockStatePredicate.NULL_PREDICATE;
                    } else if (key == '_') {
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
}
