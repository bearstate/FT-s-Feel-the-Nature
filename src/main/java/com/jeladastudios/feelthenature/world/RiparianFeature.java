package com.jeladastudios.feelthenature.world;

import com.jeladastudios.feelthenature.GeologyBridge;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * The strip of richer growth along FT's Geology's rivers and lakes. A bank has water within reach of every root and
 * fresh silt every flood, so it grows taller and thicker than the land behind it: tall grass and ferns, reeds at the
 * water's edge where it is warm, lily pads on the still, shallow water of a lake's margin.
 *
 * <p>One placement a chunk, which walks the chunk's own columns and asks the river network how far each is from the
 * water. Only in FT's Geology's own world, where the rivers are its own.</p>
 */
public class RiparianFeature extends Feature<NoneFeatureConfiguration> {

    /** How far back from the water's edge the bank's growth reaches, in blocks. */
    private static final double BANK = 4.0;

    public RiparianFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    /** For the automated test: what the chunks cost, logged every 256 of them. */
    private static final java.util.concurrent.atomic.LongAdder COST_NANOS = new java.util.concurrent.atomic.LongAdder();
    private static final java.util.concurrent.atomic.AtomicLong COST_MAX = new java.util.concurrent.atomic.AtomicLong();
    private static final java.util.concurrent.atomic.AtomicInteger COST_CHUNKS = new java.util.concurrent.atomic.AtomicInteger();
    private static final java.util.concurrent.atomic.AtomicInteger CURTAINS = new java.util.concurrent.atomic.AtomicInteger();

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        if (!com.jeladastudios.feelthenature.dev.TestCommands.ON) return grow(context);
        long t0 = System.nanoTime();
        boolean placed = grow(context);
        long spent = System.nanoTime() - t0;
        COST_NANOS.add(spent);
        COST_MAX.accumulateAndGet(spent, Math::max);
        int n = COST_CHUNKS.incrementAndGet();
        if (n % 256 == 0) {
            com.jeladastudios.feelthenature.FeelTheNature.LOGGER.info("RIPARIAN_COST chunks={} avg_us={} max_us={} curtains={}",
                    n, COST_NANOS.sum() / n / 1000, COST_MAX.get() / 1000, CURTAINS.get());
        }
        return placed;
    }

    private boolean grow(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        if (!GeologyBridge.isGeologyWorld(level.getLevel())) return false;
        RandomSource random = context.random();
        int x0 = context.origin().getX() & ~15, z0 = context.origin().getZ() & ~15;
        boolean placed = false;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        java.util.Set<Integer> sprayed = new java.util.HashSet<>();
        for (int dx = 0; dx < 16; dx++) {
            for (int dz = 0; dz < 16; dz++) {
                int x = x0 + dx, z = z0 + dz;
                GeologyBridge.River river = GeologyBridge.river(x, z, BANK);
                if (river == null) continue;
                double bank = river.fromBank();
                if (bank > BANK) continue;
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                m.set(x, y, z);
                if (bank < 0) {
                    // In the water: lily pads on the still margin of a lake, where it is shallow.
                    if (river.lake() && bank > -3 && random.nextFloat() < 0.06f) placed |= lilyPad(level, m);
                    // A waterfall's curtain: the rock round its foot wet with spray, once for each of its cells.
                    boolean curtain = falling(level, m.below());
                    if (curtain && com.jeladastudios.feelthenature.dev.TestCommands.ON) CURTAINS.incrementAndGet();
                    if (curtain && sprayed.add(((dx >> 2) << 2) | (dz >> 2))) {
                        placed |= spray(level, m.below(), random);
                    } else if (bank > -1.5 && random.nextFloat() < (river.lake() ? 0.015f : 0.035f)) {
                        // Stones at a river's edge, of the rock it runs over; fewer round a lake, whose still water buries them.
                        placed |= riverStone(level, m, random);
                    }
                    continue;
                }
                placed |= bankPlant(level, m, random, bank);
            }
        }
        return placed;
    }

    /** Whether the water at a block is falling: a waterfall's curtain. */
    private static boolean falling(WorldGenLevel level, BlockPos at) {
        var f = level.getFluidState(at);
        return f.is(FluidTags.WATER) && f.hasProperty(net.minecraft.world.level.material.FlowingFluid.FALLING)
                && f.getValue(net.minecraft.world.level.material.FlowingFluid.FALLING);
    }

    /**
     * The foot of a waterfall: the spray keeps the rock round it wet, and moss and ferns grow on it. From the curtain
     * down to where it lands, then the rock within three blocks of there, from a block under the pool to three over:
     * its exposed faces turned to mossy rock, the soil over it to moss, ferns on the wettest ledges.
     */
    public static boolean spray(WorldGenLevel level, BlockPos curtain, RandomSource random) {
        BlockPos.MutableBlockPos m = curtain.mutable();
        int drop = 0;
        while (drop < 40 && falling(level, m.move(0, -1, 0))) drop++;
        if (drop < 3) return false;
        BlockPos foot = m.immutable();
        boolean placed = false;
        for (BlockPos p : BlockPos.betweenClosed(foot.offset(-3, -1, -3), foot.offset(3, 3, 3))) {
            if (p.distSqr(foot) > 12 || random.nextFloat() > 0.5f) continue;
            BlockState s = level.getBlockState(p);
            if (!exposed(level, p)) continue;
            if (s.is(BlockTags.BASE_STONE_OVERWORLD) || s.is(Blocks.COBBLESTONE) || s.is(Blocks.GRAVEL)) {
                level.setBlock(p, Blocks.MOSSY_COBBLESTONE.defaultBlockState(), 2);
                placed = true;
            } else if (s.is(BlockTags.DIRT) && level.getBlockState(p.above()).isAir()) {
                level.setBlock(p, Blocks.MOSS_BLOCK.defaultBlockState(), 2);
                if (random.nextFloat() < 0.35f) level.setBlock(p.above(), Blocks.FERN.defaultBlockState(), 2);
                placed = true;
            }
        }
        if (placed && com.jeladastudios.feelthenature.dev.TestCommands.ON) {
            com.jeladastudios.feelthenature.FeelTheNature.LOGGER.info("RIPARIAN_SPRAY {} {} {} drop={}",
                    foot.getX(), foot.getY(), foot.getZ(), drop);
        }
        return placed;
    }

    /** Whether a block shows a face to the open: air or water beside it or over it. */
    private static boolean exposed(WorldGenLevel level, BlockPos p) {
        for (Direction d : Direction.values()) {
            if (d == Direction.DOWN) continue;
            BlockState n = level.getBlockState(p.relative(d));
            if (n.isAir() || n.getFluidState().is(FluidTags.WATER)) return true;
        }
        return false;
    }

    /**
     * A stone or two at a running river's edge, in the shallows, of the rock the river runs over (now and then mossy):
     * the water's top block turned to rock where the bottom is within two blocks.
     */
    private static boolean riverStone(WorldGenLevel level, BlockPos.MutableBlockPos m, RandomSource random) {
        BlockPos top = m.below();
        if (!level.getFluidState(top).is(FluidTags.WATER) || !level.getBlockState(m).isAir()) return false;
        if (level.getFluidState(top.below(2)).is(FluidTags.WATER) || !nearShore(level, top)) return false;
        // The rock under the bed's gravel and silt (not FT's Geology's model of it: asked from a feature, that waits on
        // the server thread, and world generation hangs).
        BlockPos bottom = top;
        while (level.getFluidState(bottom.below()).is(FluidTags.WATER)) bottom = bottom.below();
        BlockState rock = BoulderFeature.rockUnder(level, bottom);
        if (rock == null || !rock.isSolidRender(level, top)) rock = Blocks.STONE.defaultBlockState();
        BlockState stone = random.nextFloat() < 0.35f ? Blocks.MOSSY_COBBLESTONE.defaultBlockState() : rock;
        level.setBlock(top, stone, 2);
        // Now and then a bigger one, its back standing out of the water.
        if (random.nextFloat() < 0.3f) level.setBlock(m, rock, 2);
        if (random.nextFloat() < 0.4f) {
            BlockPos next = top.relative(Direction.Plane.HORIZONTAL.getRandomDirection(random));
            if (level.getFluidState(next).is(FluidTags.WATER) && level.getBlockState(next.above()).isAir()
                    && !level.getFluidState(next.below(2)).is(FluidTags.WATER)) {
                level.setBlock(next, stone, 2);
            }
        }
        return true;
    }

    /**
     * Whether the dry bank is within two blocks of a block of the water's top: the network's distance from the bank is
     * the channel it carved, and the water a world lays in it can spread wider, so the stones go by the water seen.
     */
    private static boolean nearShore(WorldGenLevel level, BlockPos top) {
        for (Direction d : Direction.Plane.HORIZONTAL) {
            for (int i = 1; i <= 2; i++) {
                BlockPos p = top.relative(d, i);
                if (!level.getFluidState(p).isEmpty()) continue;
                // Dry: the bank itself, or the plants and air over a bank a block lower than the water's top.
                BlockState s = level.getBlockState(p);
                if (s.isSolidRender(level, p) || level.getBlockState(p.below()).isSolidRender(level, p.below())) return true;
                break;
            }
        }
        return false;
    }

    private static boolean lilyPad(WorldGenLevel level, BlockPos.MutableBlockPos m) {
        // The heightmap stands on the water's surface block here; the pad goes on the air over it.
        BlockPos surface = m.below();
        if (!level.getFluidState(surface).is(FluidTags.WATER) || !level.getFluidState(surface).isSource()) return false;
        if (!level.getBlockState(m).isAir()) return false;
        // Shallow: the bottom within three blocks.
        boolean shallow = false;
        for (int d = 1; d <= 3; d++) {
            if (!level.getFluidState(surface.below(d)).is(FluidTags.WATER)) {
                shallow = true;
                break;
            }
        }
        if (!shallow) return false;
        level.setBlock(m, Blocks.LILY_PAD.defaultBlockState(), 2);
        return true;
    }

    private static boolean bankPlant(WorldGenLevel level, BlockPos.MutableBlockPos m, RandomSource random, double bank) {
        BlockState ground = level.getBlockState(m.below());
        if (!ground.is(BlockTags.DIRT) || !level.getBlockState(m).isAir()) return false;
        float warmth = level.getBiome(m).value().getBaseTemperature();
        // Thickest at the water and thinning back from it.
        float density = (float) (0.65 * (1.0 - bank / BANK));
        if (random.nextFloat() > density) return false;
        if (bank < 1.5 && warmth >= 0.5f && touchesWater(level, m.below()) && random.nextFloat() < 0.45f) {
            int height = 1 + random.nextInt(3);
            for (int i = 0; i < height; i++) {
                if (!level.getBlockState(m.above(i)).isAir()) break;
                level.setBlock(m.above(i), Blocks.SUGAR_CANE.defaultBlockState(), 2);
            }
            return true;
        }
        boolean cool = warmth < 0.5f;
        float r = random.nextFloat();
        if (r < (cool ? 0.30f : 0.45f)) return doublePlant(level, m, Blocks.TALL_GRASS.defaultBlockState());
        if (r < (cool ? 0.60f : 0.55f)) return doublePlant(level, m, Blocks.LARGE_FERN.defaultBlockState());
        if (r < 0.80f) level.setBlock(m, (cool ? Blocks.FERN : Blocks.GRASS).defaultBlockState(), 2);
        return true;
    }

    private static boolean doublePlant(WorldGenLevel level, BlockPos m, BlockState plant) {
        if (!level.getBlockState(m.above()).isAir()) return false;
        DoublePlantBlock.placeAt(level, plant, m, 2);
        return true;
    }

    private static boolean touchesWater(WorldGenLevel level, BlockPos ground) {
        for (Direction d : Direction.Plane.HORIZONTAL) {
            if (level.getFluidState(ground.relative(d)).is(FluidTags.WATER)) return true;
        }
        return false;
    }
}
