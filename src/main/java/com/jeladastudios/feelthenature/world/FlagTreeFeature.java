package com.jeladastudios.feelthenature.world;

import com.jeladastudios.feelthenature.GeologyBridge;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.phys.Vec3;

/**
 * A flag tree at the tree line: a lone spruce shaped by the wind that blows there year in, year out. The buds on the
 * windward side are killed by blown ice and drying, so its branches grow only to leeward, like a flag streaming from a
 * pole; its top is bent over the same way, and low down, in the shelter of the winter's snow, a skirt of branches
 * grows all round.
 *
 * <p>The wind is FT's Geology's prevailing wind, the same for the whole world, so every flag tree on a mountainside
 * points the same way. Where the core does not give it, the trees are not grown.</p>
 */
public class FlagTreeFeature extends Feature<NoneFeatureConfiguration> {

    private static volatile Vec3 wind;

    /** The prevailing wind, asked of FT's Geology once a world and kept; zero where it does not say. */
    private static Vec3 wind(WorldGenLevel level) {
        Vec3 w = wind;
        if (w == null) wind = w = GeologyBridge.prevailingWind(level.getLevel());
        return w;
    }

    /** Forgotten when the world closes: another world blows another way. */
    @net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid = com.jeladastudios.feelthenature.FeelTheNature.MODID)
    public static final class Forget {
        private Forget() {}

        @net.minecraftforge.eventbus.api.SubscribeEvent
        public static void onStopped(net.minecraftforge.event.server.ServerStoppedEvent event) {
            wind = null;
        }
    }

    public FlagTreeFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        Vec3 w = wind(level);
        if (w.lengthSqr() < 0.5) return false;
        BlockPos origin = context.origin();
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, origin.getX(), origin.getZ());
        BlockPos foot = new BlockPos(origin.getX(), y, origin.getZ());
        BlockState ground = level.getBlockState(foot.below());
        if (!ground.is(BlockTags.DIRT) || !level.getBlockState(foot).canBeReplaced()) return false;
        // Lone: open sky over it and no other tree within a few blocks.
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                int x = foot.getX() + dx, z = foot.getZ() + dz;
                if (level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) > level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) + 0) return false;
                if (level.getBlockState(new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1, z)).is(BlockTags.LOGS)) return false;
            }
        }
        grow(level, foot, w.x, w.z, random);
        return true;
    }

    /** The tree grown from its foot, its branches streaming to leeward along (wx, wz). */
    static void grow(WorldGenLevel level, BlockPos foot, double wx, double wz, RandomSource random) {
        BlockState log = Blocks.SPRUCE_LOG.defaultBlockState();
        BlockState leaves = Blocks.SPRUCE_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
        int height = 6 + random.nextInt(4);
        // The way the top bends: the leeward axis, to the nearest of the four.
        Direction lee = Math.abs(wx) >= Math.abs(wz) ? (wx > 0 ? Direction.EAST : Direction.WEST) : (wz > 0 ? Direction.SOUTH : Direction.NORTH);
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        int bendAt = height - 2;
        for (int i = 0; i < height; i++) {
            int shift = i >= bendAt ? 1 : 0;
            m.set(foot).move(0, i, 0).move(lee, shift);
            if (i == bendAt) {
                // The bend: a log lying across from the stem to the leaning top.
                level.setBlock(m.relative(lee.getOpposite()), log, 2);
                level.setBlock(m, log.setValue(RotatedPillarBlock.AXIS, lee.getAxis()), 2);
                continue;
            }
            level.setBlock(m, log, 2);
        }
        // The skirt: low branches all round, sheltered by the snow.
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (dx == 0 && dz == 0 || Math.abs(dx) + Math.abs(dz) > 3) continue;
                setLeaf(level, m.set(foot.getX() + dx, foot.getY(), foot.getZ() + dz), leaves);
                if (Math.abs(dx) + Math.abs(dz) <= 1) setLeaf(level, m.set(foot.getX() + dx, foot.getY() + 1, foot.getZ() + dz), leaves);
            }
        }
        // The flag: from a third of the way up, branches to leeward only, longest low down, a stub on either side.
        for (int i = 2; i <= height; i++) {
            int shift = i >= bendAt ? 1 : 0;
            double up = (double) (i - 2) / Math.max(1, height - 2);
            int reach = (int) Math.round(4.0 - 2.5 * up) + (random.nextFloat() < 0.3f ? 1 : 0);
            if (i % 2 == 1 && i < height - 1 && random.nextFloat() < 0.4f) continue;   // gaps between the tiers
            for (int k = 1; k <= reach; k++) {
                int x = foot.getX() + lee.getStepX() * shift + (int) Math.round(wx * k);
                int z = foot.getZ() + lee.getStepZ() * shift + (int) Math.round(wz * k);
                // The far end droops under its own weight and the winter's snow.
                int sag = k == reach && reach >= 3 ? 1 : 0;
                setLeaf(level, m.set(x, foot.getY() + i - sag, z), leaves);
                // Thick near the stem, a single strand out at the end.
                if (k <= reach / 2) {
                    Direction side = lee.getClockWise();
                    setLeaf(level, m.set(x + side.getStepX(), foot.getY() + i, z + side.getStepZ()), leaves);
                    setLeaf(level, m.set(x - side.getStepX(), foot.getY() + i, z - side.getStepZ()), leaves);
                }
            }
        }
        // On the lower tiers, now and then a stunted twig to windward, all the wind has left of it.
        for (int i = 2; i < height / 2 + 1; i++) {
            if (random.nextFloat() > 0.35f) continue;
            setLeaf(level, m.set(foot.getX() - (int) Math.round(wx), foot.getY() + i, foot.getZ() - (int) Math.round(wz)), leaves);
        }
        // A tuft at the top, over to leeward.
        BlockPos top = foot.above(height).relative(lee);
        setLeaf(level, m.set(top), leaves);
        setLeaf(level, m.set(top.relative(lee)), leaves);
    }

    private static void setLeaf(WorldGenLevel level, BlockPos p, BlockState leaves) {
        BlockState s = level.getBlockState(p);
        if (s.isAir() || s.canBeReplaced() && !s.is(BlockTags.LOGS)) level.setBlock(p, leaves, 2);
    }
}
