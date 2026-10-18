package com.jeladastudios.feelthenature.world;

import com.jeladastudios.feelthenature.GeologyBridge;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * A bird hide on a lake's shore: a small timber hut a few blocks back from the water, facing it, a long slit at eye
 * height under a lip to watch through, a bench behind the slit, a door at the back, tall grass grown up round it.
 *
 * <p>Only by one of FT's Geology's lakes, on level ground a couple of blocks or more from its edge.</p>
 */
public class BirdHideFeature extends Feature<NoneFeatureConfiguration> {

    public BirdHideFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        if (!GeologyBridge.isGeologyWorld(level.getLevel())) return false;
        RandomSource random = context.random();
        int x0 = context.origin().getX() & ~15, z0 = context.origin().getZ() & ~15;
        for (int tries = 0; tries < 16; tries++) {
            BlockPos at = new BlockPos(x0 + 2 + random.nextInt(12), 0, z0 + 2 + random.nextInt(12));
            if (tryAt(level, at, random)) return true;
        }
        return false;
    }

    private static boolean tryAt(WorldGenLevel level, BlockPos origin, RandomSource random) {
        // Not in a lake itself, but with one a few blocks off: the network knows a lake by the columns in it.
        GeologyBridge.River here = GeologyBridge.river(origin.getX(), origin.getZ(), 1);
        if (here != null && here.lake()) return false;
        int y = height(level, origin.getX(), origin.getZ());
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                int h = height(level, origin.getX() + dx, origin.getZ() + dz);
                if (Math.abs(h - y) > 1) return false;
                if (level.getHeight(Heightmap.Types.MOTION_BLOCKING, origin.getX() + dx, origin.getZ() + dz) != h) return false;
                if (!level.getFluidState(new BlockPos(origin.getX() + dx, h - 1, origin.getZ() + dz)).isEmpty()) return false;
            }
        }
        // Facing the lake: the way its water lies nearest, three to seven blocks off.
        Direction facing = null;
        for (int r = 3; r <= 7 && facing == null; r++) {
            for (Direction d : Direction.Plane.HORIZONTAL) {
                int x = origin.getX() + d.getStepX() * r, z = origin.getZ() + d.getStepZ() * r;
                GeologyBridge.River there = GeologyBridge.river(x, z, 1);
                if (there != null && there.lake() && level.getFluidState(new BlockPos(x, height(level, x, z) - 1, z)).is(FluidTags.WATER)) {
                    facing = d;
                    break;
                }
            }
        }
        if (facing == null) return false;
        Rotation rotation = switch (facing) {
            case EAST -> Rotation.CLOCKWISE_90;
            case SOUTH -> Rotation.CLOCKWISE_180;
            case WEST -> Rotation.COUNTERCLOCKWISE_90;
            default -> Rotation.NONE;
        };
        Builder b = new Builder(level, new BlockPos(origin.getX(), y, origin.getZ()), rotation);
        build(b, random);
        b.join();
        return true;
    }

    /** The hide, drawn facing north: the water to the north. */
    private static void build(Builder b, RandomSource random) {
        BlockState plank = Blocks.SPRUCE_PLANKS.defaultBlockState();
        BlockState post = Blocks.SPRUCE_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y);
        b.clear(-3, 3, -2, 2, 4);
        for (int x = -2; x <= 2; x++) {
            for (int z = -1; z <= 1; z++) {
                b.set(x, -1, z, plank);
                b.fillDown(x, -2, z, Blocks.DIRT.defaultBlockState());
            }
        }
        for (int y = 0; y <= 2; y++) {
            for (int x = -2; x <= 2; x++) {
                for (int z = -1; z <= 1; z++) {
                    boolean corner = Math.abs(x) == 2 && Math.abs(z) == 1;
                    boolean wall = Math.abs(x) == 2 || Math.abs(z) == 1;
                    if (!wall) continue;
                    // The slit along the front, at the eyes of someone sitting.
                    if (z == -1 && y == 1 && Math.abs(x) <= 1) continue;
                    b.set(x, y, z, corner ? post : plank);
                }
            }
        }
        // The lip over the slit, the door at the back.
        for (int x = -1; x <= 1; x++) {
            b.set(x, 2, -2, Blocks.SPRUCE_TRAPDOOR.defaultBlockState().setValue(TrapDoorBlock.HALF, Half.TOP)
                    .setValue(TrapDoorBlock.FACING, Direction.NORTH));
        }
        BlockState door = Blocks.SPRUCE_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.SOUTH);
        b.set(0, 0, 1, door.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        b.set(0, 1, 1, door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        // A flat roof of boards, a little over the walls.
        for (int x = -3; x <= 3; x++) for (int z = -2; z <= 2; z++) b.set(x, 3, z, Blocks.SPRUCE_SLAB.defaultBlockState());
        // The bench behind the slit, a lantern over it.
        for (int x = -1; x <= 1; x++) {
            b.set(x, 0, 0, Blocks.SPRUCE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.SOUTH));
        }
        b.set(0, 2, 0, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
        // Tall grass grown up round its sides and back.
        for (int x = -3; x <= 3; x++) {
            for (int z = -2; z <= 3; z++) {
                if (Math.abs(x) <= 2 && z >= -1 && z <= 1) continue;
                if (z == -2 || random.nextFloat() > 0.5f) continue;
                if (!b.get(x, 0, z).isAir() || !b.get(x, 1, z).isAir()) continue;
                if (!b.get(x, -1, z).is(net.minecraft.tags.BlockTags.DIRT)) continue;
                DoublePlantBlock.placeAt(b.level, Blocks.TALL_GRASS.defaultBlockState(), b.at(x, 0, z).immutable(), 2);
            }
        }
    }

    private static int height(WorldGenLevel level, int x, int z) {
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
    }
}
