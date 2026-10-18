package com.jeladastudios.feelthenature.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * A small building's blocks set relative to its foot and turned with it, one of four ways: what is drawn facing north
 * stands facing whichever way the building was turned to.
 */
final class Builder {

    final WorldGenLevel level;
    final BlockPos origin;
    final Rotation rotation;
    final BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();

    Builder(WorldGenLevel level, BlockPos origin, Rotation rotation) {
        this.level = level;
        this.origin = origin;
        this.rotation = rotation;
    }

    /** The world position of a point drawn relative to the foot. */
    BlockPos at(int x, int y, int z) {
        int rx, rz;
        switch (rotation) {
            case CLOCKWISE_90 -> { rx = -z; rz = x; }
            case CLOCKWISE_180 -> { rx = -x; rz = -z; }
            case COUNTERCLOCKWISE_90 -> { rx = z; rz = -x; }
            default -> { rx = x; rz = z; }
        }
        return m.set(origin.getX() + rx, origin.getY() + y, origin.getZ() + rz);
    }

    BlockState get(int x, int y, int z) {
        return level.getBlockState(at(x, y, z));
    }

    void set(int x, int y, int z, BlockState s) {
        level.setBlock(at(x, y, z), s.rotate(level, m, rotation), Block.UPDATE_CLIENTS);
    }

    void setIfAir(int x, int y, int z, BlockState s) {
        if (level.getBlockState(at(x, y, z)).isAir()) set(x, y, z, s);
    }

    /** A box cleared of ground, trees and plants: x and z from -r to r, y from 0 to the height. */
    void clear(int r, int height) {
        clear(-r, r, -r, r, height);
    }

    void clear(int x0, int x1, int z0, int z1, int height) {
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                for (int y = 0; y <= height; y++) {
                    if (!level.getBlockState(at(x, y, z)).isAir()) level.setBlock(m, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
            }
        }
    }

    /**
     * What grows in a box cleared, the ground left as it lies: trees, leaves, plants and loose snow, not soil or rock.
     * x and z from -r to r, y from 0 to the height.
     */
    void clearGrowth(int r, int height) {
        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                for (int y = 0; y <= height; y++) {
                    BlockState s = level.getBlockState(at(x, y, z));
                    if (s.isAir()) continue;
                    boolean ground = s.isSolidRender(level, m) && !s.is(BlockTags.LOGS) && !s.is(BlockTags.LEAVES);
                    if (!ground && s.getFluidState().isEmpty()) level.setBlock(m, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
            }
        }
    }

    /** The ground cut away in a box, from y0 to y1, where a way through it is wanted. */
    void carve(int x0, int x1, int z0, int z1, int y0, int y1) {
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                for (int y = y0; y <= y1; y++) {
                    if (!level.getBlockState(at(x, y, z)).isAir()) level.setBlock(m, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
            }
        }
    }

    /** A pier of a block under a point, down to firm ground. */
    void pier(int x, int z, BlockState block) {
        for (int y = -1; y >= -12; y--) {
            BlockState s = level.getBlockState(at(x, y, z));
            if (s.isSolidRender(level, m) && !s.is(BlockTags.LEAVES)) break;
            level.setBlock(m, block, Block.UPDATE_CLIENTS);
        }
    }

    /** Ground filled in under a point down to firm ground. */
    void fillDown(int x, int y0, int z, BlockState block) {
        for (int y = y0; y >= y0 - 12; y--) {
            BlockState s = level.getBlockState(at(x, y, z));
            if (s.isSolidRender(level, m)) break;
            level.setBlock(m, block, Block.UPDATE_CLIENTS);
        }
    }

    /** One of FT's Geology's blocks by its name, facing a way; nothing where the mod has no such block. */
    void geology(int x, int y, int z, String name, Direction facing) {
        Block block = ForgeRegistries.BLOCKS.getValue(new ResourceLocation("fts_geology", name));
        if (block == null || block == Blocks.AIR) return;
        BlockState s = block.defaultBlockState();
        if (s.hasProperty(HorizontalDirectionalBlock.FACING)) s = s.setValue(HorizontalDirectionalBlock.FACING, facing);
        set(x, y, z, s);
    }
}
