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

    /**
     * Whether a chunk is the one chunk of its cell -- {@code cell} chunks square -- that a kind of building may stand in:
     * one picked from the world's seed and the cell, the same whichever chunk asks. So a kind is never thicker than one
     * to a cell, however much of the country suits it.
     */
    static boolean chosen(WorldGenLevel level, net.minecraft.world.level.ChunkPos chunk, int cell, long salt) {
        int cx = Math.floorDiv(chunk.x, cell), cz = Math.floorDiv(chunk.z, cell);
        long h = level.getSeed() ^ salt ^ (cx * 341873128712L) ^ (cz * 132897987541L);
        h ^= h >>> 33;
        h *= 0xff51afd7ed558ccdL;
        h ^= h >>> 33;
        int px = (int) Math.floorMod(h, (long) cell), pz = (int) Math.floorMod(h >>> 20, (long) cell);
        return chunk.x == cx * cell + px && chunk.z == cz * cell + pz;
    }

    /** For the automated test and the frequency counts: a building put up, logged where the test switch is on. */
    static void built(String what, BlockPos at) {
        if (com.jeladastudios.feelthenature.dev.TestCommands.ON) {
            com.jeladastudios.feelthenature.FeelTheNature.LOGGER.info("BUILT {} at {} {} {}", what, at.getX(), at.getY(), at.getZ());
        }
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

    /** Where fences, panes and walls were set, to be joined to what stands beside them once the building is done. */
    private final java.util.List<BlockPos> joints = new java.util.ArrayList<>();

    void set(int x, int y, int z, BlockState s) {
        BlockState turned = s.rotate(level, at(x, y, z), rotation);
        level.setBlock(m, turned, Block.UPDATE_CLIENTS);
        if (turned.getBlock() instanceof net.minecraft.world.level.block.CrossCollisionBlock
                || turned.getBlock() instanceof net.minecraft.world.level.block.WallBlock) joints.add(m.immutable());
    }

    /**
     * The fences, panes and walls set joined to their neighbours, as the game joins them when a player places one: in a
     * chunk being generated nothing is told of what was set beside it, and each stood alone as a bare post.
     */
    void join() {
        for (BlockPos p : joints) {
            BlockState s = level.getBlockState(p);
            if (!(s.getBlock() instanceof net.minecraft.world.level.block.CrossCollisionBlock
                    || s.getBlock() instanceof net.minecraft.world.level.block.WallBlock)) continue;
            for (Direction d : Direction.Plane.HORIZONTAL) {
                BlockPos n = p.relative(d);
                s = s.updateShape(d, level.getBlockState(n), level, p, n);
            }
            level.setBlock(p, s, Block.UPDATE_CLIENTS);
        }
        joints.clear();
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
                    if (growth(s)) level.setBlock(m, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
            }
        }
    }

    /**
     * What grows, and nothing built: trees and their leaves, plants, loose snow. A neighbour's stair, door, fence or roof
     * is not growth, and is left standing.
     */
    static boolean growth(BlockState s) {
        if (!s.getFluidState().isEmpty()) return false;
        return s.is(BlockTags.LOGS) || s.is(BlockTags.LEAVES) || s.is(BlockTags.REPLACEABLE_BY_TREES) || s.is(BlockTags.FLOWERS)
                || s.is(BlockTags.SAPLINGS) || s.is(Blocks.SNOW) || s.is(Blocks.BROWN_MUSHROOM) || s.is(Blocks.RED_MUSHROOM)
                || s.is(Blocks.SWEET_BERRY_BUSH) || s.is(Blocks.MOSS_CARPET) || s.is(Blocks.VINE) || s.canBeReplaced();
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

    /** A barrel standing up, to be filled from a loot table when first opened. */
    void barrel(int x, int y, int z, ResourceLocation loot, net.minecraft.util.RandomSource random) {
        set(x, y, z, Blocks.BARREL.defaultBlockState().setValue(net.minecraft.world.level.block.BarrelBlock.FACING, Direction.UP));
        net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity.setLootTable(level, random, at(x, y, z), loot);
    }

    /** A lectern facing a way with a book open on it. */
    void lectern(int x, int y, int z, Direction facing, net.minecraft.world.item.ItemStack book) {
        set(x, y, z, Blocks.LECTERN.defaultBlockState().setValue(net.minecraft.world.level.block.LecternBlock.FACING, facing)
                .setValue(net.minecraft.world.level.block.LecternBlock.HAS_BOOK, true));
        // Its book set straight on its block entity, which in a chunk being generated has no level to tell.
        if (level.getBlockEntity(at(x, y, z)) instanceof net.minecraft.world.level.block.entity.LecternBlockEntity lectern) {
            lectern.setBook(book);
        }
    }

    /** One of FT's Geology's blocks by its name, facing a way; nothing where the mod has no such block. */
    void geology(int x, int y, int z, String name, Direction facing) {
        Block block = ForgeRegistries.BLOCKS.getValue(new ResourceLocation("fts_geology", name));
        if (block == null || block == Blocks.AIR) return;
        BlockState s = block.defaultBlockState();
        // Turned here: the instruments do not turn themselves, and one hung on a wall must face out from it.
        if (s.hasProperty(HorizontalDirectionalBlock.FACING)) s = s.setValue(HorizontalDirectionalBlock.FACING, rotation.rotate(facing));
        level.setBlock(at(x, y, z), s, Block.UPDATE_CLIENTS);
    }

    /**
     * Whether the square {@code r} blocks round a world position is all within what may be built in: in world
     * generation the chunk being made and those beside it, since a block set further out is lost (and its ground read
     * there is not the ground).
     */
    boolean writable(BlockPos c, int r) {
        if (!(level instanceof net.minecraft.server.level.WorldGenRegion region)) return true;
        net.minecraft.world.level.ChunkPos center = region.getCenter();
        return Math.abs(((c.getX() - r) >> 4) - center.x) <= 1 && Math.abs(((c.getX() + r) >> 4) - center.x) <= 1
                && Math.abs(((c.getZ() - r) >> 4) - center.z) <= 1 && Math.abs(((c.getZ() + r) >> 4) - center.z) <= 1;
    }

    /** Whether FT's Geology installed has a block of this name. */
    static boolean has(String name) {
        Block block = ForgeRegistries.BLOCKS.getValue(new ResourceLocation("fts_geology", name));
        return block != null && block != Blocks.AIR;
    }

    /** Which way, as drawn, a building turned as this one is faces a way in the world. */
    Direction drawn(Direction world) {
        for (Direction d : Direction.Plane.HORIZONTAL) if (rotation.rotate(d) == world) return d;
        return world;
    }
}
