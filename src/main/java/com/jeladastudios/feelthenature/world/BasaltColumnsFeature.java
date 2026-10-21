package com.jeladastudios.feelthenature.world;

import com.jeladastudios.feelthenature.FeelTheNature;
import com.jeladastudios.feelthenature.GeologyBridge;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * The columns of a basalt sea coast, in place of Terralith's: its basalt cliff biome sets some three hundred spires
 * a chunk, made for its own cliffs, and on FT's Geology's ground they came up out of a low plain like a field of
 * spikes. Here a patch round a point on the shore, now and then: upright columns in a cliff's face and leaning on its
 * foot, a low platform of column ends along the water, stepping stones out into the sea, the outer ones drowned
 * ({@link BasaltCauseway}). Nothing away from the sea.
 *
 * <p>One placement every other chunk (the placed feature's rarity), in the basalt cliff biome only. It looks at the
 * ground eight blocks round the chunk, so a patch near the chunk's edge carries on into the next, and writes no
 * further than its patch reaches.</p>
 */
public class BasaltColumnsFeature extends Feature<NoneFeatureConfiguration> {

    /** The square looked at: the chunk and eight blocks round it. */
    private static final int MARGIN = 8, N = 16 + 2 * MARGIN;

    private static final BlockState BASALT = Blocks.BASALT.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y);

    public BasaltColumnsFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        int x0 = (context.origin().getX() & ~15) - MARGIN, z0 = (context.origin().getZ() & ~15) - MARGIN;
        int seaLevel = level.getSeaLevel();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();

        // A cheap look first: the sea and dry land both in the chunk, or no shore to stand on.
        boolean anySea = false, anyLand = false;
        for (int x = MARGIN; x < MARGIN + 16; x += 5) {
            for (int z = MARGIN; z < MARGIN + 16; z += 5) {
                if (sea(level, m.set(x0 + x, seaLevel - 1, z0 + z))) anySea = true;
                else anyLand = true;
            }
        }
        if (!anySea || !anyLand) return false;

        int[] ground = new int[N * N];
        boolean[] sea = new boolean[N * N];
        for (int z = 0; z < N; z++) {
            for (int x = 0; x < N; x++) {
                int i = z * N + x;
                sea[i] = sea(level, m.set(x0 + x, seaLevel - 1, z0 + z));
                ground[i] = ground(level, m, x0 + x, z0 + z, seaLevel);
            }
        }
        // The patch's middle: a column of dry land on the shore, two blocks inside the chunk, so the patch keeps to
        // the ground looked at.
        int shoreCount = 0, cx = -1, cz = -1;
        for (int z = MARGIN + 2; z < MARGIN + 14; z++) {
            for (int x = MARGIN + 2; x < MARGIN + 14; x++) {
                if (sea[z * N + x] || !touchesSea(sea, x, z)) continue;
                // A shore column picked evenly from all of them, in one pass.
                if (random.nextInt(++shoreCount) == 0) {
                    cx = x;
                    cz = z;
                }
            }
        }
        if (shoreCount == 0) return false;

        var plan = BasaltCauseway.plan(N, ground, sea, seaLevel, cx, cz, 4 + random.nextInt(5), random.nextLong());
        int columns = 0, faces = 0;
        for (int z = 0; z < N; z++) {
            for (int x = 0; x < N; x++) {
                int i = z * N + x, top = plan.top()[i], face = plan.faceFrom()[i];
                if (top != BasaltCauseway.NONE) {
                    // The column from the ground's top block up: its base turned, then built up through water or air.
                    for (int y = ground[i]; y <= top; y++) {
                        if (!replaceable(level.getBlockState(m.set(x0 + x, y, z0 + z)))) break;
                        level.setBlock(m, BASALT, 2);
                    }
                    columns++;
                }
                if (face != BasaltCauseway.NONE) {
                    // A cliff's face: its blocks under the top turned to columns, the soil over it left.
                    for (int y = face; y < ground[i]; y++) {
                        if (natural(level.getBlockState(m.set(x0 + x, y, z0 + z)))) level.setBlock(m, BASALT, 2);
                    }
                    if (!level.getBlockState(m.set(x0 + x, ground[i], z0 + z)).is(BlockTags.DIRT)) level.setBlock(m, BASALT, 2);
                    faces++;
                }
            }
        }
        if (com.jeladastudios.feelthenature.dev.TestCommands.ON && columns + faces > 0) {
            FeelTheNature.LOGGER.info("BASALT_COLUMNS at {} {} columns={} faces={}", x0 + cx, z0 + cz, columns, faces);
        }
        return columns + faces > 0;
    }

    /**
     * Whether the sea stands at its top water block: vanilla's water, not a river's or a lake's, with the open air
     * over it, not the water of an aquifer or a cave at the sea's level under the land.
     */
    private static boolean sea(WorldGenLevel level, BlockPos.MutableBlockPos at) {
        BlockState s = level.getBlockState(at);
        if (!s.getFluidState().is(FluidTags.WATER) || GeologyBridge.isRiverWater(s)) return false;
        BlockState over = level.getBlockState(at.move(0, 1, 0));
        at.move(0, -1, 0);
        return over.isAir() || over.canBeReplaced() && over.getFluidState().isEmpty();
    }

    /** The top of the solid ground at a column, from a little over the shore's highest down to under the sea. */
    private static int ground(WorldGenLevel level, BlockPos.MutableBlockPos m, int x, int z, int seaLevel) {
        for (int y = seaLevel + 40; y > seaLevel - 24; y--) {
            BlockState s = level.getBlockState(m.set(x, y, z));
            if (!s.isAir() && s.getFluidState().isEmpty() && !s.canBeReplaced() && !s.is(BlockTags.LEAVES)
                    && !s.is(BlockTags.LOGS)) {
                return y;
            }
        }
        return seaLevel - 24;
    }

    private static boolean touchesSea(boolean[] sea, int x, int z) {
        return sea[z * N + x + 1] || sea[z * N + x - 1] || sea[(z + 1) * N + x] || sea[(z - 1) * N + x];
    }

    /** What a column may stand in: the open air or water, plants, and the ground's own loose or solid rock. */
    private static boolean replaceable(BlockState s) {
        return s.isAir() || s.canBeReplaced() || (s.getFluidState().is(FluidTags.WATER) && !s.hasBlockEntity()) || natural(s);
    }

    /** The ground's own blocks, which a column may be cut from: not wood, an ore, or anything built. */
    private static boolean natural(BlockState s) {
        return s.is(BlockTags.DIRT) || s.is(BlockTags.SAND) || s.is(Blocks.GRAVEL) || s.is(Blocks.CLAY)
                || s.is(BlockTags.BASE_STONE_OVERWORLD) || s.is(Blocks.BASALT) || s.is(Blocks.SMOOTH_BASALT)
                || s.is(BlockTags.TERRACOTTA);
    }
}
