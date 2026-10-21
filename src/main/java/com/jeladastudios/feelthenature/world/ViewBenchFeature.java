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
 * <p>Looked for on a few points of the chunk: grassy ground, level a couple of blocks in front and behind, open to the
 * sky, with the ground some way lower a few blocks out in front: the brow of a pasture, not a ledge on a cliff.</p>
 */
public class ViewBenchFeature extends Feature<NoneFeatureConfiguration> {

    /** How far out the drop is looked for, and how deep it must be. */
    private static final int OUT = 8, DROP = 6;

    /** The country benches are put out in: the high pastures and the mountain woods. */
    static final net.minecraft.tags.TagKey<net.minecraft.world.level.biome.Biome> COUNTRY = net.minecraft.tags.TagKey.create(
            net.minecraft.core.registries.Registries.BIOME,
            new net.minecraft.resources.ResourceLocation(com.jeladastudios.feelthenature.FeelTheNature.MODID, "bench_country"));

    public ViewBenchFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        int x0 = context.origin().getX() & ~15, z0 = context.origin().getZ() & ~15;
        // One chunk of a cell twelve chunks square may have a bench.
        if (!Builder.chosen(level, new net.minecraft.world.level.ChunkPos(context.origin()), 12, 0xBE4C4L)) return false;
        for (int tries = 0; tries < 24; tries++) {
            int x = x0 + 2 + random.nextInt(12), z = z0 + 2 + random.nextInt(12);
            int y = height(level, x, z);
            if (!level.getBiome(new BlockPos(x, y, z)).is(COUNTRY)) continue;
            if (LookoutTowerFeature.nearTower(level, x, z, ShelterHutFeature.CLEAR)) continue;
            for (Direction view : Direction.Plane.HORIZONTAL) {
                Direction along = view.getClockWise();
                // Level for the two seats; in front of them the same or a step down, before the ground drops away.
                if (height(level, x + along.getStepX(), z + along.getStepZ()) != y) continue;
                int f1 = height(level, x + view.getStepX(), z + view.getStepZ());
                int f2 = height(level, x + along.getStepX() + view.getStepX(), z + along.getStepZ() + view.getStepZ());
                if (f1 > y || f1 < y - 1 || f2 > y || f2 < y - 1) continue;
                // A little more of the same ground two blocks out in front, and behind: a pasture's brow, not a ledge on a cliff.
                if (!within(level, x + view.getStepX() * 2, z + view.getStepZ() * 2, y - 1, y)) continue;
                if (!within(level, x - view.getStepX(), z - view.getStepZ(), y, y + 1)) continue;
                if (!within(level, x - view.getStepX() * 2, z - view.getStepZ() * 2, y, y + 2)) continue;
                if (!level.getBlockState(new BlockPos(x, y - 1, z)).is(BlockTags.DIRT)) continue;
                if (!level.getBlockState(new BlockPos(x + along.getStepX(), y - 1, z + along.getStepZ())).is(BlockTags.DIRT)) continue;
                int far = height(level, x + view.getStepX() * OUT, z + view.getStepZ() * OUT);
                if (far > y - DROP) continue;
                BlockPos a = new BlockPos(x, y, z), b = a.relative(along);
                if (!open(level, a) || !open(level, b)) continue;
                BlockState seat = Blocks.SPRUCE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, view.getOpposite());
                Builder.built("bench", a);
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

    private static boolean within(WorldGenLevel level, int x, int z, int lo, int hi) {
        int h = height(level, x, z);
        return h >= lo && h <= hi;
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
