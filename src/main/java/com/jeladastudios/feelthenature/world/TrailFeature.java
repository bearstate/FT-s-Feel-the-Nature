package com.jeladastudios.feelthenature.world;

import com.jeladastudios.feelthenature.GeologyBridge;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * The trail down from a fire lookout: a worn path from the foot of its stair down the mountain, winding to keep a
 * walker's slope where the ground is steep, to the river below, the valley's floor or a long way down; a board at its
 * foot pointing up to the lookout. Laid out once a tower on FT's Geology's own ground model, so every chunk it crosses
 * lays its own stretch of the same trail; where the core gives no ground model there are no trails.
 */
public class TrailFeature extends Feature<NoneFeatureConfiguration> {

    /** How far a trail runs at most, in blocks, and the step it is laid out at. */
    static final int LONGEST = 450, STEP = 4;
    /** The slope a trail keeps to where it can, blocks down a block along. */
    static final double SLOPE = 0.3;
    /** How far a trail comes down before a level stretch can end it: off the summit's own flat top. */
    static final double DESCENT = 25;

    /** A trail: its points from the tower down, and the box round them. */
    record Trail(int[] xs, int[] zs, int minX, int minZ, int maxX, int maxZ, int towerX, int towerZ) {
        boolean crosses(int x0, int z0, int x1, int z1) {
            return maxX >= x0 - 2 && minX <= x1 + 2 && maxZ >= z0 - 2 && minZ <= z1 + 2;
        }
    }

    private static final java.util.Map<Long, java.util.Optional<Trail>> TRAILS = new java.util.concurrent.ConcurrentHashMap<>();

    /** Forgotten when the world closes. */
    @net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid = com.jeladastudios.feelthenature.FeelTheNature.MODID)
    public static final class Forget {
        private Forget() {}

        @net.minecraftforge.eventbus.api.SubscribeEvent
        public static void onStopped(net.minecraftforge.event.server.ServerStoppedEvent event) {
            TRAILS.clear();
        }
    }

    public TrailFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        ChunkPos chunk = new ChunkPos(context.origin());
        int x0 = chunk.getMinBlockX(), z0 = chunk.getMinBlockZ(), x1 = x0 + 15, z1 = z0 + 15;
        boolean placed = false;
        for (Trail t : near(level, x0 + 8, z0 + 8)) {
            if (!t.crosses(x0, z0, x1, z1)) continue;
            placed |= lay(level, t, x0, z0, x1, z1, random);
        }
        return placed;
    }

    /** The trails of the lookouts round a point: of its cell and the cells beside it. */
    static java.util.List<Trail> near(WorldGenLevel level, int x, int z) {
        int cell = LookoutTowerFeature.CELL, cx = Math.floorDiv(x >> 4, cell), cz = Math.floorDiv(z >> 4, cell);
        java.util.List<Trail> out = new java.util.ArrayList<>(2);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                long key = level.getSeed() * 31 + (((long) (cx + dx)) << 32 ^ ((cz + dz) & 0xFFFFFFFFL));
                int ccx = cx + dx, ccz = cz + dz;
                TRAILS.computeIfAbsent(key, k -> java.util.Optional.ofNullable(trace(level, ccx, ccz))).ifPresent(out::add);
            }
        }
        return out;
    }

    /** For the test command: the nearby trails as {points, startX, startZ, footX, midX, midZ, footZ}. */
    public static java.util.List<int[]> nearPublic(WorldGenLevel level, int x, int z) {
        java.util.List<int[]> out = new java.util.ArrayList<>();
        for (Trail t : near(level, x, z)) {
            int n = t.xs().length, m = n / 2;
            out.add(new int[]{n, t.xs()[0], t.zs()[0], t.xs()[n - 1], t.xs()[m], t.zs()[m], t.zs()[n - 1]});
        }
        out.sort(java.util.Comparator.comparingDouble(a -> Math.hypot(a[1] - x, a[2] - z)));
        return out;
    }

    /** The trail of a cell's lookout, laid out down its mountain; null where it has none or no ground model. */
    static Trail trace(WorldGenLevel level, int cx, int cz) {
        ServerLevel server = level.getLevel();
        int cell = LookoutTowerFeature.CELL;
        BlockPos site = LookoutTowerFeature.site(level, new ChunkPos(cx * cell, cz * cell));
        if (site == null || !LookoutTowerFeature.model(server, site)) return null;
        int[] foot = LookoutTowerFeature.foot(server, site, LookoutTowerFeature.ground(server));
        if (foot == null) return null;
        BlockPos at = new BlockPos(foot[0], foot[2], foot[1]);
        Rotation rotation = LookoutTowerFeature.rotation(level, at, LookoutTowerFeature.nearestLandmark(server, foot[0], foot[1]));
        // From the way in to the stair, on the tower's west side as it is drawn, out and down.
        int[] start = turn(rotation, -7, -1);
        double x = foot[0] + start[0], z = foot[1] + start[1];
        double angle = Math.atan2(turn(rotation, -1, 0)[1], turn(rotation, -1, 0)[0]);
        java.util.List<int[]> points = new java.util.ArrayList<>();
        points.add(new int[]{(int) Math.round(x), (int) Math.round(z)});
        double here = GeologyBridge.groundHeight(server, (int) x, (int) z), top = here;
        int flat = 0;
        for (int run = 0; run < LONGEST; run += STEP) {
            double bestCost = Double.MAX_VALUE, bestA = angle, bestH = here;
            for (int k = -3; k <= 3; k++) {
                double a = angle + k * Math.PI / 8;
                double nx = x + Math.cos(a) * STEP, nz = z + Math.sin(a) * STEP;
                double h = GeologyBridge.groundHeight(server, (int) Math.round(nx), (int) Math.round(nz));
                if (Double.isNaN(h)) continue;
                double slope = (here - h) / STEP;
                double cost = Math.abs(slope - SLOPE) + 0.12 * Math.abs(k);
                if (slope < -0.15) cost += 2;           // never back up a slope of any size
                if (Math.abs(slope) > 1.1) cost += 4;   // nor straight down a cliff
                // Away from the tower, not round it.
                if (Math.hypot(nx - foot[0], nz - foot[1]) < Math.hypot(x - foot[0], z - foot[1])) cost += 1;
                if (cost < bestCost) {
                    bestCost = cost;
                    bestA = a;
                    bestH = h;
                }
            }
            if (bestCost == Double.MAX_VALUE) break;
            double drop = here - bestH;
            angle = bestA;
            x += Math.cos(angle) * STEP;
            z += Math.sin(angle) * STEP;
            here = bestH;
            points.add(new int[]{(int) Math.round(x), (int) Math.round(z)});
            // Down at the river, or on the valley's floor for a while.
            GeologyBridge.River river = GeologyBridge.river((int) x, (int) z, 6);
            if (river != null && river.fromBank() < 3) break;
            flat = drop < 0.15 * STEP && top - here >= DESCENT ? flat + 1 : 0;
            if (flat >= 6) break;
        }
        if (points.size() < 6) return null;
        int n = points.size();
        int[] xs = new int[n], zs = new int[n];
        int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (int i = 0; i < n; i++) {
            xs[i] = points.get(i)[0];
            zs[i] = points.get(i)[1];
            minX = Math.min(minX, xs[i]);
            minZ = Math.min(minZ, zs[i]);
            maxX = Math.max(maxX, xs[i]);
            maxZ = Math.max(maxZ, zs[i]);
        }
        return new Trail(xs, zs, minX, minZ, maxX, maxZ, foot[0], foot[1]);
    }

    /** A point drawn relative to a tower turned as the tower's blocks are turned (see Builder.at). */
    static int[] turn(Rotation rotation, int x, int z) {
        return switch (rotation) {
            case CLOCKWISE_90 -> new int[]{-z, x};
            case CLOCKWISE_180 -> new int[]{-x, -z};
            case COUNTERCLOCKWISE_90 -> new int[]{z, -x};
            default -> new int[]{x, z};
        };
    }

    /** This chunk's stretch of a trail laid: the worn ground along it, and the board where it ends. */
    private static boolean lay(WorldGenLevel level, Trail t, int x0, int z0, int x1, int z1, RandomSource random) {
        boolean placed = false;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int i = 0; i + 1 < t.xs().length; i++) {
            int ax = t.xs()[i], az = t.zs()[i], bx = t.xs()[i + 1], bz = t.zs()[i + 1];
            int steps = Math.max(Math.abs(bx - ax), Math.abs(bz - az));
            for (int s = 0; s <= steps; s++) {
                int x = ax + Math.round((float) (bx - ax) * s / Math.max(1, steps));
                int z = az + Math.round((float) (bz - az) * s / Math.max(1, steps));
                // Now and then a second block wide, where feet have strayed.
                placed |= wear(level, m, x, z, x0, z0, x1, z1, random);
                if (random.nextFloat() < 0.3f) {
                    boolean acrossX = Math.abs(bz - az) > Math.abs(bx - ax);
                    placed |= wear(level, m, x + (acrossX ? 1 : 0), z + (acrossX ? 0 : 1), x0, z0, x1, z1, random);
                }
            }
        }
        // The board at the trail's foot, beside it, turned to face a walker coming to it.
        int n = t.xs().length, ex = t.xs()[n - 1], ez = t.zs()[n - 1];
        if (ex >= x0 && ex <= x1 && ez >= z0 && ez <= z1) placed |= trailhead(level, t, ex, ez);
        return placed;
    }

    /** A block of trail worn into the ground at a column, if the column is in the chunk and the ground can take it. */
    private static boolean wear(WorldGenLevel level, BlockPos.MutableBlockPos m, int x, int z, int x0, int z0, int x1, int z1,
                                RandomSource random) {
        if (x < x0 || x > x1 || z < z0 || z > z1) return false;
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
        m.set(x, y, z);
        BlockState top = level.getBlockState(m);
        if (!(top.is(Blocks.GRASS_BLOCK) || top.is(Blocks.DIRT) || top.is(Blocks.PODZOL) || top.is(Blocks.COARSE_DIRT)
                || top.is(Blocks.MYCELIUM) || top.is(Blocks.ROOTED_DIRT))) return false;
        // What grows on it trodden flat; a tree on the way is walked round, not through.
        BlockState over = level.getBlockState(m.above());
        if (!over.isAir()) {
            if (!Builder.growth(over) || over.is(BlockTags.LOGS)) return false;
            level.setBlock(m.above(), Blocks.AIR.defaultBlockState(), 2);
            if (level.getBlockState(m.above(2)).is(Blocks.TALL_GRASS) || level.getBlockState(m.above(2)).is(Blocks.LARGE_FERN)) {
                level.setBlock(m.above(2), Blocks.AIR.defaultBlockState(), 2);
            }
        }
        float r = random.nextFloat();
        BlockState worn = r < 0.65f ? Blocks.DIRT_PATH.defaultBlockState() : r < 0.88f ? Blocks.COARSE_DIRT.defaultBlockState() : top;
        level.setBlock(m, worn, 2);
        return true;
    }

    /** A standing board beside the trail's foot: the lookout up the trail, and how far. */
    private static boolean trailhead(WorldGenLevel level, Trail t, int ex, int ez) {
        int n = t.xs().length;
        int px = t.xs()[n - 2], pz = t.zs()[n - 2];
        // Beside the trail, to its right going down; facing back down it, the way a walker comes up.
        double ux = ex - px, uz = ez - pz, len = Math.max(1, Math.hypot(ux, uz));
        int sx = ex + (int) Math.round(-uz / len * 2), sz = ez + (int) Math.round(ux / len * 2);
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, sx, sz);
        BlockPos at = new BlockPos(sx, y, sz);
        if (!level.getBlockState(at).isAir() && !level.getBlockState(at).canBeReplaced()) return false;
        if (!level.getBlockState(at.below()).isFaceSturdy(level, at.below(), Direction.UP)) return false;
        // A standing sign's rotation, in sixteenths of a turn from facing south: towards the trail's foot, below it.
        double face = Math.atan2(ux, -uz);
        int rot = Math.floorMod((int) Math.round(face / (Math.PI * 2) * 16) + 8, 16);
        level.setBlock(at, Blocks.SPRUCE_SIGN.defaultBlockState().setValue(StandingSignBlock.ROTATION, rot), 2);
        if (level.getBlockEntity(at) instanceof SignBlockEntity sign) {
            int dx = t.towerX() - ex, dz = t.towerZ() - ez;
            int distance = (int) Math.round(Math.hypot(dx, dz) / 10.0) * 10;
            SignText text = new SignText()
                    .setMessage(0, Component.translatable("sign.fts_feelthenature.rescue.lookout"))
                    .setMessage(1, Component.translatable("sign.fts_feelthenature.rescue.way",
                            Component.translatable("sign.fts_feelthenature.point." + RescueSignFeature.point(dx, dz)), distance))
                    .setMessage(2, Component.translatable("sign.fts_feelthenature.rescue.title"))
                    .setMessage(3, Component.literal("145.500 MHz"));
            var tag = sign.saveWithoutMetadata();
            SignText.DIRECT_CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, text).result().ifPresent(tt -> tag.put("front_text", tt));
            tag.putBoolean("is_waxed", true);
            sign.load(tag);
        }
        Builder.built("trailhead", at);
        return true;
    }
}
