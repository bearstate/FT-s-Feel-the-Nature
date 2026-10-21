package com.jeladastudios.feelthenature.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;

/**
 * A trunk lying on the forest floor where a tree came down: a few logs end to end along the ground, moss and now and
 * then a mushroom growing on it. Old forest is full of dead wood, and a forest without any reads as a plantation.
 *
 * <p>Laid only where the whole length rests on ground (soil, moss or rock) and nothing but plants stands in its way,
 * so a log never hangs over a slope or cuts through a tree.</p>
 */
public class FallenLogFeature extends Feature<FallenLogFeature.Config> {

    public record Config(BlockStateProvider log, IntProvider length, float mossChance) implements FeatureConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(i -> i.group(
                BlockStateProvider.CODEC.fieldOf("log").forGetter(Config::log),
                IntProvider.codec(2, 12).fieldOf("length").forGetter(Config::length),
                Codec.floatRange(0f, 1f).fieldOf("moss_chance").forGetter(Config::mossChance)
        ).apply(i, Config::new));
    }

    public FallenLogFeature(Codec<Config> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<Config> context) {
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        Config config = context.config();
        // The ground a block or two either way of where it was asked for: a placement off the heightmap stands on it, but
        // one by hand or on a bumpy floor may be a block out.
        BlockPos origin = null;
        for (int dy : new int[]{0, 1, -1, 2, -2}) {
            BlockPos p = context.origin().above(dy);
            if (clear(level.getBlockState(p)) && resting(level, p)) {
                origin = p;
                break;
            }
        }
        if (origin == null) return false;
        Direction along = Direction.Plane.HORIZONTAL.getRandomDirection(random);
        int length = config.length().sample(random);

        // The whole length clear, both ends resting on ground, and at most one block between over a dip: a trunk is
        // stiff enough to lie across a small hollow, but not to hang off a slope.
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        int unsupported = 0;
        for (int i = 0; i < length; i++) {
            m.set(origin).move(along, i);
            if (!clear(level.getBlockState(m))) return false;
            if (resting(level, m)) continue;
            BlockState under = level.getBlockState(m.below());
            if (i == 0 || i == length - 1 || !under.getFluidState().isEmpty() || !clear(under)) return false;
            if (++unsupported > 1) return false;
        }
        BlockState log = config.log().getState(random, origin);
        if (log.hasProperty(RotatedPillarBlock.AXIS)) log = log.setValue(RotatedPillarBlock.AXIS, along.getAxis());
        for (int i = 0; i < length; i++) {
            m.set(origin).move(along, i);
            level.setBlock(m, log, 2);
            BlockPos top = m.above();
            if (!level.isEmptyBlock(top)) continue;
            float r = random.nextFloat();
            if (r < config.mossChance()) level.setBlock(top, Blocks.MOSS_CARPET.defaultBlockState(), 2);
            else if (r < config.mossChance() + 0.06f) {
                level.setBlock(top, (random.nextBoolean() ? Blocks.BROWN_MUSHROOM : Blocks.RED_MUSHROOM).defaultBlockState(), 2);
            }
        }
        // Along a wet forest's log, the wood rotting into the ground beside it: dark soil, and mushrooms in it. Podzol
        // keeps a mushroom alive in any light, as the rotting wood would.
        if (config.mossChance() > 0.2f) {
            for (int i = 0; i < length; i++) {
                for (Direction side : new Direction[]{along.getClockWise(), along.getCounterClockWise()}) {
                    if (random.nextFloat() > 0.35f) continue;
                    m.set(origin).move(along, i).move(side).move(Direction.DOWN);
                    BlockState soil = level.getBlockState(m);
                    if (!(soil.is(Blocks.GRASS_BLOCK) || soil.is(Blocks.DIRT) || soil.is(Blocks.COARSE_DIRT))) continue;
                    if (!clear(level.getBlockState(m.above()))) continue;
                    level.setBlock(m, Blocks.PODZOL.defaultBlockState(), 2);
                    if (random.nextFloat() < 0.45f) {
                        level.setBlock(m.above(), (random.nextFloat() < 0.7f ? Blocks.BROWN_MUSHROOM : Blocks.RED_MUSHROOM)
                                .defaultBlockState(), 2);
                    }
                }
            }
        }
        return true;
    }

    /** Whether a block of the trunk at this place would rest on firm ground. */
    private static boolean resting(WorldGenLevel level, BlockPos at) {
        BlockState under = level.getBlockState(at.below());
        return ground(under) && under.isFaceSturdy(level, at.below(), Direction.UP);
    }

    /** Air, or a plant the log would have flattened. */
    private static boolean clear(BlockState s) {
        return s.isAir() || s.is(BlockTags.REPLACEABLE_BY_TREES) || s.is(BlockTags.FLOWERS) || s.is(Blocks.GRASS)
                || s.is(Blocks.FERN) || s.is(Blocks.SNOW);
    }

    /** What a fallen trunk can lie on: the forest floor or rock, never water, leaves or another log. */
    private static boolean ground(BlockState s) {
        return s.is(BlockTags.DIRT) || s.is(BlockTags.BASE_STONE_OVERWORLD) || s.is(Blocks.MOSS_BLOCK)
                || s.is(Blocks.GRAVEL) || s.is(Blocks.COARSE_DIRT) || s.is(Blocks.SNOW_BLOCK);
    }
}
