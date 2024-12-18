package io.wispforest.lavender.structure;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;

/**
 * A predicate used for matching the elements of a structure
 * template against some concrete block state in the world
 * <p>
 * Importantly, it also provides a mechanism for getting a representative
 * sample block state that can be used for the structure preview
 */
public interface BlockStatePredicate {

    /**
     * The built-in null predicate that always returns
     * a full state match
     */
    BlockStatePredicate NULL_PREDICATE = new BlockStatePredicate() {
        private static final BlockState[] PREVIEW_STATES = {Blocks.AIR.getDefaultState()};

        @Override
        public BlockState[] previewBlockstates() {
            return PREVIEW_STATES;
        }

        @Override
        public Result test(BlockState blockState) {
            return Result.STATE_MATCH;
        }

        @Override
        public boolean isOf(MatchCategory type) {
            return type == MatchCategory.ANY || type == MatchCategory.NULL;
        }
    };

    /**
     * The built-in air predicate which returns a full state
     * match on any air block
     */
    BlockStatePredicate AIR_PREDICATE = new BlockStatePredicate() {
        private static final BlockState[] PREVIEW_STATES = {Blocks.AIR.getDefaultState()};

        @Override
        public BlockState[] previewBlockstates() {
            return PREVIEW_STATES;
        }

        @Override
        public Result test(BlockState blockState) {
            return blockState.isAir() ? Result.STATE_MATCH : Result.NO_MATCH;
        }

        @Override
        public boolean isOf(MatchCategory type) {
            return type == MatchCategory.ANY || type == MatchCategory.NON_NULL || type == MatchCategory.AIR;
        }
    };

    Result test(BlockState state);

    /**
     * @return {@code true} if this predicate finds a {@linkplain Result#STATE_MATCH state match}
     * on the given state
     */
    default boolean matches(BlockState state) {
        return this.test(state) == Result.STATE_MATCH;
    }

    /**
     * @return A representative sample state for this predicate. As this function
     * is called every frame the preview is rendered, returning a different sample
     * depending on system time (e.g. to cycle to a block tag) is valid behavior
     */
    default BlockState preview() {
        BlockState[] states = this.previewBlockstates();
        return states[(int) (System.currentTimeMillis() / 1000 % states.length)];
    }

    /**
     * @return An array of all possible preview block states.
     */
    BlockState[] previewBlockstates();

    /**
     * @return Whether this predicate falls into the given matching category, generally
     * useful for communicating information about predicates to the user
     */
    default boolean isOf(MatchCategory type) {
        return type != MatchCategory.AIR && type != MatchCategory.NULL;
    }

    enum Result {
        /**
         * The predicate fully rejected
         * the tested state
         */
        NO_MATCH,
        /**
         * The predicate rejected the tested
         * state's properties but accepted the
         * base block
         */
        BLOCK_MATCH,
        /**
         * The predicate accepted the entire
         * block state including properties
         */
        STATE_MATCH
    }

    enum MatchCategory {
        /**
         * No requirements on the matched states,
         * every predicate falls into this category
         */
        ANY,
        /**
         * All predicates which are more specific than
         * the null predicate, that is, they have at least
         * <i>some</i> requirements for states to match
         */
        NON_NULL,
        /**
         * All predicates which are more specific than the
         * null predicate <i>and</i> which don't match air
         */
        NON_AIR,
        /**
         * The air predicate
         */
        AIR,
        /**
         * The null predicate
         */
        NULL
    }
}
