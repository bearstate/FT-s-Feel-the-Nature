package com.jeladastudios.feelthenature.world;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * A cairn on a bare top: stones of the rock under it piled by the people who passed, to mark the top or the way over
 * it. A ring of four round a fifth on the ground, one or two on that, a narrow one crowning it; a lichened stone or two
 * in it.
 *
 * <p>On the highest point of the chunk it is placed in, and only if that point stands over the ground a few blocks
 * round it, so it marks a top or a ridge, not a slope.</p>
 */
public class CairnFeature extends Feature<NoneFeatureConfiguration> {

    /** How far round the top the ground is looked at, near and further off, and how much lower it must lie. */
    private static final int ROUND = 6, UNDER = 2, FAR = 16, FALL = 5;

    public CairnFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        int x0 = context.origin().getX() & ~15, z0 = context.origin().getZ() & ~15;
        // The chunk's highest point.
        int bx = 0, bz = 0, by = Integer.MIN_VALUE;
        for (int dx = 2; dx < 14; dx++) {
            for (int dz = 2; dz < 14; dz++) {
                int h = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x0 + dx, z0 + dz);
                if (h > by) {
                    by = h;
                    bx = x0 + dx;
                    bz = z0 + dz;
                }
            }
        }
        // A bare top: open to the sky, no crown of leaves over it or round it, and the ground lower on every side close
        // by and on most sides further off: a summit or a ridge, not a knoll in a valley.
        int falls = 0;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            int nx = bx + d.getStepX() * ROUND, nz = bz + d.getStepZ() * ROUND;
            if (level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, nx, nz) > by - UNDER) return false;
            if (level.getHeight(Heightmap.Types.MOTION_BLOCKING, nx, nz) != level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, nx, nz)) return false;
            int far = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx + d.getStepX() * FAR, bz + d.getStepZ() * FAR);
            if (far <= by - FALL) falls++;
        }
        if (falls < 3 || level.getHeight(Heightmap.Types.MOTION_BLOCKING, bx, bz) != by) return false;
        BlockPos top = new BlockPos(bx, by, bz);
        BlockState rock = BoulderFeature.rockUnder(level, top);
        if (rock == null) rock = Blocks.COBBLESTONE.defaultBlockState();
        // The ring and the stone in it, set on the ground; then the stones piled on.
        put(level, top, rock, random);
        for (Direction d : Direction.Plane.HORIZONTAL) {
            BlockPos p = top.relative(d);
            int h = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, p.getX(), p.getZ());
            if (Math.abs(h - by) <= 1 && random.nextFloat() < 0.85f) put(level, new BlockPos(p.getX(), h, p.getZ()), rock, random);
        }
        put(level, top.above(), rock, random);
        BlockPos side = top.above().relative(Direction.Plane.HORIZONTAL.getRandomDirection(random));
        if (!level.getBlockState(side.below()).isAir()) put(level, side, rock, random);
        level.setBlock(top.above(2), crown(rock), 2);
        return true;
    }

    /** The narrow stone crowning the cairn: the rock's own wall or its slab, or where it has neither, a stone of it. */
    static BlockState crown(BlockState rock) {
        var key = net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(rock.getBlock());
        if (key != null) {
            String path = key.getPath();
            for (String name : new String[]{path + "_wall", path.replace("deepslate", "cobbled_deepslate") + "_wall",
                    path.replace("polished_", "") + "_wall", path + "_slab"}) {
                var block = net.minecraftforge.registries.ForgeRegistries.BLOCKS.getValue(
                        new net.minecraft.resources.ResourceLocation(key.getNamespace(), name));
                if (block != null && block != Blocks.AIR) return block.defaultBlockState();
            }
        }
        return rock;
    }

    /** A stone of the cairn: the rock, now and then lichened. */
    private static void put(WorldGenLevel level, BlockPos p, BlockState rock, RandomSource random) {
        BlockState over = level.getBlockState(p);
        if (!(over.isAir() || over.canBeReplaced())) return;
        level.setBlock(p, random.nextFloat() < 0.2f ? Blocks.MOSSY_COBBLESTONE.defaultBlockState() : rock, 2);
    }
}
