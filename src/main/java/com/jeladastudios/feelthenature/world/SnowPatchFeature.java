package com.jeladastudios.feelthenature.world;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.SnowyDirtBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * A patch of old snow lying on into the summer below the snow line: in the alpine belt, on a slope facing north, where
 * the sun comes in lowest and the winter's drifts are deepest. Long along the slope, as a drift lies under a break in it:
 * firm old snow in the middle, a thin skin of it round the edge.
 *
 * <p>The slope is read off the world's own heightmap a few blocks either side, so nothing outside the chunks being
 * decorated is asked.</p>
 */
public class SnowPatchFeature extends Feature<NoneFeatureConfiguration> {

    /** How far either side the slope is measured, and how much the ground must rise to the south over that. */
    private static final int REACH = 4, RISE = 3;

    public SnowPatchFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        BlockPos origin = context.origin();
        int x0 = origin.getX(), z0 = origin.getZ();
        // Facing north: the ground higher to the south than to the north, so the slope turns its face from the sun.
        int south = height(level, x0, z0 + REACH), north = height(level, x0, z0 - REACH);
        if (south - north < RISE) return false;

        int rz = 3 + random.nextInt(3), rx = rz + 2 + random.nextInt(4);
        boolean placed = false;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int dx = -rx; dx <= rx; dx++) {
            for (int dz = -rz; dz <= rz; dz++) {
                double d = (double) (dx * dx) / (rx * rx) + (double) (dz * dz) / (rz * rz) + random.nextDouble() * 0.35;
                if (d > 1.0) continue;
                int x = x0 + dx, z = z0 + dz;
                m.set(x, height(level, x, z) - 1, z);
                BlockState ground = level.getBlockState(m);
                if (!holds(ground)) continue;
                BlockState over = level.getBlockState(m.above());
                if (!(over.isAir() || small(over))) continue;
                if (d < 0.45) {
                    // The firm old snow of the middle, the ground's own top gone under it.
                    level.setBlock(m, Blocks.SNOW_BLOCK.defaultBlockState(), 2);
                    // Deepest under a step up the slope, where the drift built against it.
                    int step = height(level, x, z + 1) - 1 - m.getY();
                    if (step > 0) {
                        level.setBlock(m.above(), Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, Math.min(7, 3 + step * 2 + random.nextInt(2))), 2);
                    } else if (!over.isAir()) {
                        level.setBlock(m.above(), Blocks.AIR.defaultBlockState(), 2);
                    }
                } else {
                    if (ground.hasProperty(SnowyDirtBlock.SNOWY)) level.setBlock(m, ground.setValue(SnowyDirtBlock.SNOWY, true), 2);
                    int layers = d < 0.75 ? 2 : 1;
                    level.setBlock(m.above(), Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, layers), 2);
                }
                placed = true;
            }
        }
        return placed;
    }

    private static int height(WorldGenLevel level, int x, int z) {
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
    }

    /** Ground snow lies on: soil, rock and scree, not water, wood or leaves. */
    private static boolean holds(BlockState s) {
        return s.is(BlockTags.DIRT) || s.is(BlockTags.BASE_STONE_OVERWORLD) || s.is(Blocks.GRAVEL)
                || s.is(Blocks.COBBLESTONE) || s.is(Blocks.MOSSY_COBBLESTONE);
    }

    /** A plant of one block the snow lies over. */
    private static boolean small(BlockState s) {
        return s.is(Blocks.GRASS) || s.is(Blocks.FERN) || s.is(BlockTags.SMALL_FLOWERS) || s.is(Blocks.MOSS_CARPET);
    }
}
