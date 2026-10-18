package com.jeladastudios.feelthenature.world;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * A board nailed to a tree in the country round a fire lookout: the rescue frequency to call on, and which way the
 * lookout lies. Now and then one, a few round each tower, at eye height on the trunk, facing out the way a walker comes.
 * Written in translatable text, so each reader reads it in their own language.
 */
public class RescueSignFeature extends Feature<NoneFeatureConfiguration> {

    /** How far from a lookout its boards are put up, in blocks. */
    static final int REACH = 400;
    /** One chance in how many a chunk has a board: one a trail crosses, and one elsewhere near a lookout. */
    static final int ALONG = 4, ELSEWHERE = 500;

    public RescueSignFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        int x0 = context.origin().getX() & ~15, z0 = context.origin().getZ() & ~15;
        BlockPos tower = nearestTower(level, x0 + 8, z0 + 8);
        if (tower == null) return false;
        // Mostly on the trees along a lookout's trail; elsewhere seldom.
        java.util.List<int[]> beside = new java.util.ArrayList<>();
        for (TrailFeature.Trail t : TrailFeature.near(level, x0 + 8, z0 + 8)) {
            if (!t.crosses(x0, z0, x0 + 15, z0 + 15)) continue;
            for (int i = 0; i < t.xs().length; i++) {
                for (int dx = -3; dx <= 3; dx++) {
                    for (int dz = -3; dz <= 3; dz++) {
                        int x = t.xs()[i] + dx, z = t.zs()[i] + dz;
                        if (Math.abs(dx) + Math.abs(dz) >= 2 && x >= x0 && x < x0 + 16 && z >= z0 && z < z0 + 16) beside.add(new int[]{x, z});
                    }
                }
            }
        }
        if (beside.isEmpty() ? random.nextInt(ELSEWHERE) != 0 : random.nextInt(ALONG) != 0) return false;
        // A trunk standing on the ground in this chunk, with room beside it at eye height.
        for (int tries = 0; tries < 24; tries++) {
            int[] spot = beside.isEmpty() ? new int[]{x0 + random.nextInt(16), z0 + random.nextInt(16)} : beside.get(random.nextInt(beside.size()));
            int x = spot[0], z = spot[1];
            // Down the trunk from its top to the ground it stands on; the board at a walker's eye, two blocks up it.
            BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1, z);
            if (!level.getBlockState(m).is(BlockTags.LOGS)) continue;
            int height = 0;
            while (height < 30 && level.getBlockState(m.move(0, -1, 0)).is(BlockTags.LOGS)) height++;
            if (height < 3 || !level.getBlockState(m).is(BlockTags.DIRT)) continue;
            BlockPos trunk = m.above(2).immutable();
            // Facing out the way a walker comes from: away from the tower, so it is read on the way to it.
            Direction out = facing(tower.getX() - x, tower.getZ() - z).getOpposite();
            BlockPos at = trunk.relative(out);
            if (!level.getBlockState(at).isAir() || !level.getBlockState(at.below()).isAir()) continue;
            level.setBlock(at, Blocks.SPRUCE_WALL_SIGN.defaultBlockState().setValue(WallSignBlock.FACING, out), 2);
            if (level.getBlockEntity(at) instanceof SignBlockEntity sign) write(sign, tower.getX() - x, tower.getZ() - z);
            Builder.built("rescue_sign", at);
            return true;
        }
        return false;
    }

    /** The nearest lookout within reach of a point, or null. */
    static BlockPos nearestTower(WorldGenLevel level, int x, int z) {
        int cell = LookoutTowerFeature.CELL, cx = Math.floorDiv(x >> 4, cell), cz = Math.floorDiv(z >> 4, cell);
        BlockPos best = null;
        double nearest = REACH;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos site = LookoutTowerFeature.site(level, new net.minecraft.world.level.ChunkPos((cx + dx) * cell, (cz + dz) * cell));
                if (site == null) continue;
                double d = Math.hypot(site.getX() - x, site.getZ() - z);
                if (d < nearest) {
                    nearest = d;
                    best = site;
                }
            }
        }
        return best;
    }

    /** The way across the ground, to the nearest of the four, from a point towards another. */
    static Direction facing(int dx, int dz) {
        if (Math.abs(dx) >= Math.abs(dz)) return dx >= 0 ? Direction.EAST : Direction.WEST;
        return dz >= 0 ? Direction.SOUTH : Direction.NORTH;
    }

    /** The compass point, of eight, from a point towards another; north is towards -z. */
    static String point(int dx, int dz) {
        String[] points = {"e", "se", "s", "sw", "w", "nw", "n", "ne"};
        double a = Math.atan2(dz, dx);
        int i = (int) Math.round(a / (Math.PI / 4));
        return points[Math.floorMod(i, 8)];
    }

    /** The board's text: the rescue frequency, the lookout's way and distance. Through its saved form (see the tower's board). */
    static void write(SignBlockEntity sign, int dx, int dz) {
        int distance = (int) Math.round(Math.hypot(dx, dz) / 10.0) * 10;
        SignText text = new SignText()
                .setMessage(0, Component.translatable("sign.fts_feelthenature.rescue.title"))
                .setMessage(1, Component.literal("145.500 MHz"))
                .setMessage(2, Component.translatable("sign.fts_feelthenature.rescue.lookout"))
                .setMessage(3, Component.translatable("sign.fts_feelthenature.rescue.way",
                        Component.translatable("sign.fts_feelthenature.point." + point(dx, dz)), distance));
        var tag = sign.saveWithoutMetadata();
        SignText.DIRECT_CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, text).result().ifPresent(t -> tag.put("front_text", t));
        tag.putBoolean("is_waxed", true);
        sign.load(tag);
    }
}
