package com.jeladastudios.feelthenature.world;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * A bench where the ground falls away in front of it: two seats of spruce side by side on level ground at the edge of
 * a drop, facing out over it, a board at each end for an arm. Someone put it there for the view.
 *
 * <p>Looked for on a few points of the chunk: level ground, open to the sky, with the ground some way lower a few blocks
 * out one way.</p>
 */
public class ViewBenchFeature extends Feature<NoneFeatureConfiguration> {

    /** How far out the drop is looked for, and how deep it must be. */
    private static final int OUT = 8, DROP = 6;

    public ViewBenchFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        int x0 = context.origin().getX() & ~15, z0 = context.origin().getZ() & ~15;
        for (int tries = 0; tries < 24; tries++) {
            int x = x0 + 2 + random.nextInt(12), z = z0 + 2 + random.nextInt(12);
            int y = height(level, x, z);
            for (Direction view : Direction.Plane.HORIZONTAL) {
                Direction along = view.getClockWise();
                // Level for the two seats; in front of them the same or a step down, before the ground drops away.
                if (height(level, x + along.getStepX(), z + along.getStepZ()) != y) continue;
                int f1 = height(level, x + view.getStepX(), z + view.getStepZ());
                int f2 = height(level, x + along.getStepX() + view.getStepX(), z + along.getStepZ() + view.getStepZ());
                if (f1 > y || f1 < y - 1 || f2 > y || f2 < y - 1) continue;
                int far = height(level, x + view.getStepX() * OUT, z + view.getStepZ() * OUT);
                if (far > y - DROP) continue;
                BlockPos a = new BlockPos(x, y, z), b = a.relative(along);
                if (!open(level, a) || !open(level, b)) continue;
                BlockState seat = Blocks.SPRUCE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, view.getOpposite());
                level.setBlock(a, seat, 2);
                level.setBlock(b, seat, 2);
                BlockState arm = Blocks.SPRUCE_WALL_SIGN.defaultBlockState();
                setArm(level, a.relative(along.getOpposite()), arm.setValue(WallSignBlock.FACING, along.getOpposite()));
                setArm(level, b.relative(along), arm.setValue(WallSignBlock.FACING, along));
                return true;
            }
        }
        return false;
    }

    private static int height(WorldGenLevel level, int x, int z) {
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
    }

    /** Air or a low plant over firm ground, open to the sky with no tree over it. */
    private static boolean open(WorldGenLevel level, BlockPos p) {
        BlockState s = level.getBlockState(p);
        if (!(s.isAir() || s.canBeReplaced()) || s.is(BlockTags.LEAVES)) return false;
        if (!level.getBlockState(p.below()).isFaceSturdy(level, p.below(), Direction.UP)) return false;
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING, p.getX(), p.getZ()) == p.getY();
    }

    private static void setArm(WorldGenLevel level, BlockPos p, BlockState sign) {
        BlockState s = level.getBlockState(p);
        if (s.isAir() || s.canBeReplaced()) level.setBlock(p, sign, 2);
    }
}
