package com.jeladastudios.feelthenature.world;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.block.state.properties.StairsShape;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * A fire lookout on a summit: a glazed cab on four splayed timber legs, girts and knee braces at each step, a
 * switchback stair up the middle, a walkway round the cab under the eaves carried on brackets, a hipped roof. Kept as a
 * weather station as much as a lookout: FT's Geology's instruments -- the terminal and the barometer and hygrometer
 * inside, the rain gauge on the walkway, the thermometer hung outside in the shade, the anemometer on the roof's peak --
 * and lived in: a bed, a stove and its chimney, lanterns, the fire finder on its stand in the middle of the floor.
 *
 * <p>Built in place from blocks, turned to one of four ways at random, the ground under it left as it lies: the legs
 * stand down into it where it is higher and on stone footings taken down to it where it is lower. Where FT's Geology is
 * not installed its instruments are left out.</p>
 */
public class LookoutTowerFeature extends Feature<NoneFeatureConfiguration> {

    /** The walkway's floor, in blocks over the ground; the cab stands on it. A landing every three blocks reaches it. */
    static final int FLOOR = 20;
    /** How far out the legs stand at the foot, the middle and the top, and from which height each holds. */
    private static final int[][] LEG = {{0, 5}, {7, 4}, {14, 3}};
    /** The heights of the girts round the frame: at each step in of the legs, and under the walkway. */
    private static final int[] GIRTS = {6, 13, FLOOR - 1};

    public LookoutTowerFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    /** The country is cut into cells this many chunks across; each has at most one tower, on its most prominent summit. */
    public static final int CELL = 44;
    /** How much higher than the country round it a summit must stand, in blocks, measured on a ring this far out. */
    static final int PROMINENCE = 16, RING = 128;
    /** How uneven the summit's top may be under the tower, in blocks. */
    static final int ROUGH = 4;
    /** How far from the summit found the tower may stand, to stand on level ground. */
    static final int SHIFT = 8;
    /** The spacing of the grid the cell's tops are looked for on. */
    static final int GRID = 64;
    /** How many of the highest points in lookout country are weighed for prominence. */
    static final int CANDIDATES = 6;
    /** The country a lookout is built in: forested and grassy mountains and hills, under the bare rock and snow. */
    static final net.minecraft.tags.TagKey<net.minecraft.world.level.biome.Biome> LOOKOUT_COUNTRY = net.minecraft.tags.TagKey.create(
            net.minecraft.core.registries.Registries.BIOME, new ResourceLocation(com.jeladastudios.feelthenature.FeelTheNature.MODID, "lookout_country"));

    private static final java.util.Map<Long, java.util.Optional<BlockPos>> SITES = new java.util.concurrent.ConcurrentHashMap<>();

    /** The summits found are the world's: forgotten when it closes, so another world of the same seed finds its own. */
    @net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid = com.jeladastudios.feelthenature.FeelTheNature.MODID)
    public static final class Forget {
        private Forget() {}

        @net.minecraftforge.eventbus.api.SubscribeEvent
        public static void onStopped(net.minecraftforge.event.server.ServerStoppedEvent event) {
            SITES.clear();
            SUMMITS.clear();
            PLANS.clear();
            landmarks = null;
        }
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        net.minecraft.world.level.ChunkPos chunk = new net.minecraft.world.level.ChunkPos(context.origin());
        var server = level.getLevel();
        java.util.function.IntBinaryOperator world = (x, z) ->
                level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        boolean placed = false;
        int cx = Math.floorDiv(chunk.x, CELL), cz = Math.floorDiv(chunk.z, CELL);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos site = site(level, new net.minecraft.world.level.ChunkPos((cx + dx) * CELL, (cz + dz) * CELL));
                if (site == null || Math.abs(site.getX() - chunk.getMiddleBlockX()) > 48 || Math.abs(site.getZ() - chunk.getMiddleBlockZ()) > 48) continue;
                if (model(server, site)) {
                    // By FT's Geology's ground model the tower's foot and its radio's enclosure are known before any
                    // ground is: each is built when the chunk it stands in is decorated, on that chunk's finished ground.
                    Plan plan = plan(server, site);
                    if (plan == null) continue;
                    if (plan.x() >> 4 == chunk.x && plan.z() >> 4 == chunk.z) {
                        log(site, true);
                        BlockPos at = new BlockPos(plan.x(), world.applyAsInt(plan.x(), plan.z()), plan.z());
                        placed |= build(level, at, plan.rotation(), context.random(), plan.view(), plan.spot());
                    }
                    Spot spot = plan.spot();
                    if (spot != null && spot.x() >> 4 == chunk.x && spot.z() >> 4 == chunk.z) {
                        placed |= enclosure(level, spot, context.random(), station(level, plan));
                    }
                } else if (site.getX() >> 4 == chunk.x && site.getZ() >> 4 == chunk.z) {
                    // Else by the world's own heightmap, from the summit's chunk, the enclosure where it can be built now.
                    int[] foot = foot(server, site, world);
                    log(site, foot != null);
                    if (foot == null) continue;
                    BlockPos at = new BlockPos(foot[0], world.applyAsInt(foot[0], foot[1]), foot[1]);
                    var view = nearestLandmark(server, foot[0], foot[1]);
                    placed |= build(level, at, rotation(level, at, view), context.random(), view, null);
                }
            }
        }
        return placed;
    }

    private static void log(BlockPos site, boolean built) {
        if (com.jeladastudios.feelthenature.dev.TestCommands.ON) {
            com.jeladastudios.feelthenature.FeelTheNature.LOGGER.info("LOOKOUT_TOWER {} near {} {}", built ? "built" : "not built",
                    site.getX(), site.getZ());
        }
    }

    /**
     * A lookout as the ground model lays it out: its foot, its turn, the mountain it looks to, and where its radio's
     * enclosure goes (null where nowhere near it will do).
     */
    record Plan(int x, int z, Rotation rotation, com.jeladastudios.feelthenature.GeologyBridge.Landmark view, Spot spot) {}

    /** Where a radio's enclosure stands: its middle, and its turn, its way in towards the tower. */
    record Spot(int x, int z, Rotation turn) {}

    private static final java.util.Map<BlockPos, java.util.Optional<Plan>> PLANS = new java.util.concurrent.ConcurrentHashMap<>();

    static Plan plan(net.minecraft.server.level.ServerLevel level, BlockPos site) {
        return PLANS.computeIfAbsent(site, s -> {
            var ground = ground(level);
            int[] foot = foot(level, s, ground);
            if (foot == null) return java.util.Optional.empty();
            var view = nearestLandmark(level, foot[0], foot[1]);
            Rotation rotation = view != null ? toward(view.x() - foot[0], view.z() - foot[1]) : rotation(level.getSeed(), foot[0], foot[1]);
            return java.util.Optional.of(new Plan(foot[0], foot[1], rotation, view, plannedSpot(ground, foot[0], foot[1], rotation)));
        }).orElse(null);
    }

    /**
     * The enclosure's place by the ground model: the first of the spots that lies wholly in one chunk, its square and
     * the growth cleared round it, and where the ground across it is level enough.
     */
    private static Spot plannedSpot(java.util.function.IntBinaryOperator ground, int fx, int fz, Rotation rotation) {
        for (Rotation q : QUARTERS) {
            Rotation turn = rotation.getRotated(q);
            for (int[] spot : SPOTS) {
                int[] o = TrailFeature.turn(turn, spot[0], spot[1]);
                int x = fx + o[0], z = fz + o[1];
                if ((x & 15) < 4 || (x & 15) > 11 || (z & 15) < 4 || (z & 15) > 11) continue;
                int y = ground.applyAsInt(x, z);
                boolean level = true;
                for (int[] corner : new int[][]{{-3, -3}, {3, -3}, {-3, 3}, {3, 3}}) {
                    if (Math.abs(ground.applyAsInt(x + corner[0], z + corner[1]) - y) > 3) level = false;
                }
                if (level) return new Spot(x, z, turn);
            }
        }
        return null;
    }

    private static final Rotation[] QUARTERS = {Rotation.NONE, Rotation.CLOCKWISE_90, Rotation.COUNTERCLOCKWISE_90};

    /** Where a planned lookout's radio station stands, as a column (its height found by looking down it). */
    private static int[] station(WorldGenLevel level, Plan plan) {
        int[] o = TrailFeature.turn(plan.rotation(), 1, 2);
        return new int[]{plan.x() + o[0], plan.z() + o[1]};
    }

    /**
     * Whether a lookout stands, or will stand, within a distance of a point: by the summit of the cell the point is in
     * and of the cells beside it, worked out the same way the tower is placed. The smaller buildings keep their
     * distance by it, so a tower built after them never comes down on them.
     */
    public static boolean nearTower(WorldGenLevel level, int x, int z, int distance) {
        int cx = Math.floorDiv(x >> 4, CELL), cz = Math.floorDiv(z >> 4, CELL);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                int ox = (cx + dx) * CELL * 16, oz = (cz + dz) * CELL * 16;
                // A cell too far for its tower to come within the distance is not worked out at all.
                int nx = Math.max(ox, Math.min(x, ox + CELL * 16 - 1)), nz = Math.max(oz, Math.min(z, oz + CELL * 16 - 1));
                if (Math.hypot(nx - x, nz - z) > distance + SHIFT) continue;
                BlockPos site = site(level, new net.minecraft.world.level.ChunkPos((cx + dx) * CELL, (cz + dz) * CELL));
                if (site != null && Math.hypot(site.getX() - x, site.getZ() - z) <= distance + SHIFT) return true;
            }
        }
        return false;
    }

    /** Whether FT's Geology gives its ground model at a point. */
    static boolean model(net.minecraft.server.level.ServerLevel level, BlockPos at) {
        return !Double.isNaN(com.jeladastudios.feelthenature.GeologyBridge.groundHeight(level, at.getX(), at.getZ()));
    }

    /** FT's Geology's ground model as heights. */
    static java.util.function.IntBinaryOperator ground(net.minecraft.server.level.ServerLevel level) {
        return (x, z) -> (int) Math.round(com.jeladastudios.feelthenature.GeologyBridge.groundHeight(level, x, z));
    }

    /**
     * Where the tower stands by a summit, {x, z, y}, or null where no spot near it is level enough: the most level spot
     * within {@link #SHIFT} blocks, the higher where two are as level. By FT's Geology's ground model where it gives
     * it, so the answer is the same before the ground is generated as after -- {@code /ftn locate lookout} and the
     * world agree -- else by the world's own heightmap.
     */
    static int[] foot(net.minecraft.server.level.ServerLevel level, BlockPos site, java.util.function.IntBinaryOperator height) {
        int fx = 0, fz = 0, fy = 0, rough = Integer.MAX_VALUE;
        for (int ox = -SHIFT; ox <= SHIFT; ox += 4) {
            for (int oz = -SHIFT; oz <= SHIFT; oz += 4) {
                int x = site.getX() + ox, z = site.getZ() + oz, lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE, mid = 0;
                for (int dx = -5; dx <= 5; dx += 5) {
                    for (int dz = -5; dz <= 5; dz += 5) {
                        int h = height.applyAsInt(x + dx, z + dz);
                        lo = Math.min(lo, h);
                        hi = Math.max(hi, h);
                        if (dx == 0 && dz == 0) mid = h;
                    }
                }
                if (hi - lo < rough || (hi - lo == rough && mid > fy)) {
                    rough = hi - lo;
                    fx = x;
                    fz = z;
                    fy = mid;
                }
            }
        }
        return rough > ROUGH ? null : new int[]{fx, fz, fy};
    }

    /** A lookout found: where its foot stands, the mountain it looks to if any, and whether it is known for certain. */
    public record Found(BlockPos foot, com.jeladastudios.feelthenature.GeologyBridge.Landmark view, boolean certain) {}

    /**
     * The nearest lookout to a point within {@code cells} cells of it, generated or yet to be: the cells round it in
     * rings outward, the nearest found in the first ring that has one and the next (a nearer one can lie a ring out).
     * Without FT's Geology's ground model a summit's levelness cannot be known before its ground is generated; the
     * summit is given, not certain.
     */
    public static Found locate(net.minecraft.server.level.ServerLevel level, BlockPos from, int cells) {
        int cx = Math.floorDiv(from.getX() >> 4, CELL), cz = Math.floorDiv(from.getZ() >> 4, CELL);
        Found best = null;
        double nearest = Double.MAX_VALUE;
        int stopAfter = Integer.MAX_VALUE;
        for (int r = 0; r <= cells && r <= stopAfter; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                    BlockPos site = site(level, new net.minecraft.world.level.ChunkPos((cx + dx) * CELL, (cz + dz) * CELL));
                    if (site == null) continue;
                    boolean model = model(level, site);
                    int[] foot = model ? foot(level, site, ground(level)) : new int[]{site.getX(), site.getZ(), site.getY()};
                    if (foot == null) continue;
                    double d = Math.hypot(foot[0] - from.getX(), foot[1] - from.getZ());
                    if (d < nearest) {
                        nearest = d;
                        best = new Found(new BlockPos(foot[0], foot[2], foot[1]), nearestLandmark(level, foot[0], foot[1]), model);
                        stopAfter = Math.min(stopAfter, r + 1);
                    }
                }
            }
        }
        return best;
    }

    /**
     * The summit a lookout stands on in the cell a chunk lies in, or null where there is none: of the tops of the cell's
     * ground on a coarse grid, in lookout country, the one standing furthest over a ring round it -- at least
     * {@link #PROMINENCE} -- closed in on. By the generator's own ground and biomes, so every chunk of the cell finds the
     * same summit; worked out once a cell.
     */
    public static BlockPos site(WorldGenLevel level, net.minecraft.world.level.ChunkPos chunk) {
        int cx = Math.floorDiv(chunk.x, CELL), cz = Math.floorDiv(chunk.z, CELL);
        long key = key(level, cx, cz);
        var known = SITES.get(key);
        if (known != null) return known.orElse(null);
        // A summit gives way to a better one within APART of it in a cell beside that has its lookout, so no two stand
        // nearer than that: the better the one standing further over its ring. Asked of the better ones only, so the
        // asking ends; the same answer whichever cell asks.
        Summit own = summit(level, cx, cz);
        BlockPos site = own == null ? null : own.at();
        for (int dx = -1; dx <= 1 && site != null; dx++) {
            for (int dz = -1; dz <= 1 && site != null; dz++) {
                if (dx == 0 && dz == 0) continue;
                Summit other = summit(level, cx + dx, cz + dz);
                if (other == null) continue;
                double d = Math.hypot(other.at().getX() - site.getX(), other.at().getZ() - site.getZ());
                boolean better = other.over() > own.over() || (other.over() == own.over() && key(level, cx + dx, cz + dz) > key);
                if (d < APART && better && site(level, new net.minecraft.world.level.ChunkPos((cx + dx) * CELL, (cz + dz) * CELL)) != null) site = null;
            }
        }
        SITES.putIfAbsent(key, java.util.Optional.ofNullable(site));
        return site;
    }

    /** The least distance between two lookouts, in blocks. */
    static final int APART = 660;

    private static long key(WorldGenLevel level, int cx, int cz) {
        return level.getSeed() * 31 + (((long) cx) << 32 ^ (cz & 0xFFFFFFFFL));
    }

    /** A cell's best summit, before its neighbours are weighed: where, and how far it stands over its ring. */
    private record Summit(BlockPos at, double over) {}

    private static final java.util.Map<Long, java.util.Optional<Summit>> SUMMITS = new java.util.concurrent.ConcurrentHashMap<>();

    private static Summit summit(WorldGenLevel level, int cx, int cz) {
        return SUMMITS.computeIfAbsent(key(level, cx, cz), k -> java.util.Optional.ofNullable(findSummit(level, cx, cz))).orElse(null);
    }

    private static Summit findSummit(WorldGenLevel level, int cx, int cz) {
        var source = level.getLevel().getChunkSource();
        var generator = source.getGenerator();
        var random = source.randomState();
        // FT's Geology's own model of its ground where it gives it, cheap and the same loaded or not; else the generator's.
        var server = level.getLevel();
        boolean model = !Double.isNaN(com.jeladastudios.feelthenature.GeologyBridge.groundHeight(server, cx * CELL * 16, cz * CELL * 16));
        java.util.function.IntBinaryOperator ground = model
                ? (x, z) -> (int) Math.round(com.jeladastudios.feelthenature.GeologyBridge.groundHeight(server, x, z))
                : (x, z) -> generator.getBaseHeight(x, z, net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE_WG, level, random);
        // The model is cheap enough to read twice as finely, and to weigh twice as many candidates.
        int grid = model ? GRID / 2 : GRID, candidates = model ? CANDIDATES * 2 : CANDIDATES;
        int x0 = cx * CELL * 16, z0 = cz * CELL * 16, n = CELL * 16 / grid;
        // Within sight of one of the named mountains a lesser top will do: a lookout is put where the view is.
        double need = nearestLandmark(server, x0 + CELL * 8, z0 + CELL * 8) != null ? PROMINENCE * 0.5 : PROMINENCE;
        // The cell's ground on a coarse grid, kept off its edge so the ring and the tower are the cell's own.
        int[][] g = new int[n][n];
        for (int i = 0; i < n; i++) for (int j = 0; j < n; j++) g[i][j] = ground.applyAsInt(x0 + grid / 2 + i * grid, z0 + grid / 2 + j * grid);
        // The highest of the grid's points in lookout country -- a summit or a high shoulder under the bare rock -- and of
        // those, the one standing furthest over the ring round it.
        var biomes = generator.getBiomeSource();
        java.util.List<int[]> country = new java.util.ArrayList<>();
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                int x = x0 + grid / 2 + i * grid, z = z0 + grid / 2 + j * grid, h = g[i][j];
                if (h <= level.getSeaLevel() + need || !country(biomes, random, x, h, z)) continue;
                country.add(new int[]{x, h, z});
            }
        }
        country.sort((a, b) -> b[1] - a[1]);
        int bx = 0, bz = 0, by = 0;
        double most = need;
        StringBuilder seen = new StringBuilder();
        for (int[] p : country.subList(0, Math.min(candidates, country.size()))) {
            double over = p[1] - ring(ground, p[0], p[2]);
            seen.append(p[1]).append("/").append((int) over).append(" ");
            if (over > most) {
                most = over;
                bx = p[0];
                by = p[1];
                bz = p[2];
            }
        }
        if (com.jeladastudios.feelthenature.dev.TestCommands.ON) {
            com.jeladastudios.feelthenature.FeelTheNature.LOGGER.info("LOOKOUT_CELL {} {} country={} best={} found={}", cx, cz,
                    country.size(), seen, by == 0 ? "none" : bx + " " + by + " " + bz + " over=" + (int) most);
        }
        if (by == 0) return null;
        // Closed in on the top of it, so long as the top is lookout country too.
        int sx = bx, sy = by, sz = bz;
        for (int step : new int[]{16, 4, 1}) {
            int px = bx, pz = bz;
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    int h = ground.applyAsInt(px + dx * step, pz + dz * step);
                    if (h > by) {
                        by = h;
                        bx = px + dx * step;
                        bz = pz + dz * step;
                    }
                }
            }
        }
        BlockPos top = country(biomes, random, bx, by, bz) ? new BlockPos(bx, by, bz) : new BlockPos(sx, sy, sz);
        // A summit with no level spot by it has no tower, and keeps no other away.
        if (model && foot(server, top, ground) == null) return null;
        return new Summit(top, most);
    }

    /** How far off a named mountain a lookout looks to it, in blocks. */
    static final int VIEW = 2000;
    private static volatile java.util.List<com.jeladastudios.feelthenature.GeologyBridge.Landmark> landmarks;

    /**
     * The named mountain nearest a point within {@link #VIEW}, or null. The mountains are asked of FT's Geology once a
     * world -- the first time takes it a few seconds -- and kept.
     */
    public static com.jeladastudios.feelthenature.GeologyBridge.Landmark nearestLandmark(net.minecraft.server.level.ServerLevel level, int x, int z) {
        var known = landmarks;
        if (known == null) {
            synchronized (LookoutTowerFeature.class) {
                if (landmarks == null) landmarks = com.jeladastudios.feelthenature.GeologyBridge.landmarks(level);
                known = landmarks;
            }
        }
        com.jeladastudios.feelthenature.GeologyBridge.Landmark best = null;
        double nearest = (double) VIEW * VIEW;
        for (var l : known) {
            double d = (double) (l.x() - x) * (l.x() - x) + (double) (l.z() - z) * (l.z() - z);
            if (d < nearest) {
                nearest = d;
                best = l;
            }
        }
        return best;
    }

    /** Whether a point is in lookout country, by the generator's own biomes. */
    private static boolean country(net.minecraft.world.level.biome.BiomeSource biomes, net.minecraft.world.level.levelgen.RandomState random,
                                   int x, int y, int z) {
        return biomes.getNoiseBiome(net.minecraft.core.QuartPos.fromBlock(x), net.minecraft.core.QuartPos.fromBlock(y),
                net.minecraft.core.QuartPos.fromBlock(z), random.sampler()).is(LOOKOUT_COUNTRY);
    }

    /** The mean height of the ground on a ring {@link #RING} blocks round a point. */
    private static double ring(java.util.function.IntBinaryOperator ground, int x, int z) {
        long sum = 0;
        for (int k = 0; k < 12; k++) {
            double a = Math.PI * 2 * k / 12;
            sum += ground.applyAsInt(x + (int) Math.round(Math.cos(a) * RING), z + (int) Math.round(Math.sin(a) * RING));
        }
        return sum / 12.0;
    }

    /**
     * The tower built with its foot at a point, turned one of four ways. The ground is left as it lies under it: what
     * grows there is cleared, a way is cut for the stair and the door to it, the legs stand down into the ground where it
     * is higher and on stone footings taken down to it where it is lower.
     */
    public static boolean build(WorldGenLevel level, BlockPos foot, RandomSource random) {
        return build(level, foot, random, null);
    }

    /**
     * The tower built facing a named mountain, if one is given: its door towards it, a board on the post beside the
     * door naming it.
     */
    public static boolean build(WorldGenLevel level, BlockPos foot, RandomSource random,
                                com.jeladastudios.feelthenature.GeologyBridge.Landmark view) {
        Rotation rotation = view == null ? Rotation.getRandom(random) : toward(view.x() - foot.getX(), view.z() - foot.getZ());
        return build(level, foot, rotation, random, view, null);
    }

    /**
     * The tower built turned a given way; its radio's enclosure, where Simple Radio is installed, at a spot planned for
     * it and built with its own chunk, or (where none is given) found beside it and built now.
     */
    static boolean build(WorldGenLevel level, BlockPos foot, Rotation rotation, RandomSource random,
                         com.jeladastudios.feelthenature.GeologyBridge.Landmark view, Spot spot) {
        Builder b = new Builder(level, foot, rotation);
        Builder.built("lookout", foot);
        b.clearGrowth(6, FLOOR + 10);
        // The stair's well, and the way in to its foot from the west.
        b.carve(-2, 2, LANE_NORTH, LANE_SOUTH, 0, FLOOR - 1);
        b.carve(-6, -3, LANE_NORTH, LANE_SOUTH, 0, 2);
        legs(b);
        stair(b);
        cab(b);
        roof(b);
        fit(b, random);
        if (view != null) nameBoard(b, view);
        radio(b, random, spot);
        weather(b, random);
        b.join();
        return true;
    }

    /** The rescue frequency the lookouts listen on, the 2 m band's calling frequency: what their boards and log name. */
    static final String RESCUE_FREQUENCY = "145.500";

    /**
     * With Simple Radio installed, the lookout kept as a radio station too, as the real ones are: a station on the cab's
     * south wall with its antenna tuner on it, set to the rescue frequency, its battery charged, linked by coax to a
     * dual-band antenna on a mast in an enclosure of iron bars beside the tower -- a lightning rod on a pole by it,
     * since the radio draws strikes -- and room in the enclosure for whoever comes to put power in. Its charge does
     * not last: a station left alone runs down, as an abandoned one would.
     */
    private static void radio(Builder b, RandomSource random, Spot planned) {
        if (!net.minecraftforge.fml.ModList.get().isLoaded("simpleradio")) return;
        Block station = radioBlock("station"), tuner = radioBlock("antenna_tuner");
        if (station == null || tuner == null || radioBlock("dual_band_antenna") == null) return;
        // The station on the cab's south wall, its tuner on it.
        int f = FLOOR + 1;
        BlockState s = station.defaultBlockState();
        if (s.hasProperty(HorizontalDirectionalBlock.FACING)) s = s.setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH);
        b.set(1, f, 2, s);
        BlockPos at = b.at(1, f, 2).immutable();
        b.set(1, f + 1, 2, tuner.defaultBlockState());
        // Beside it a little desk with the desk microphone on it, to talk through the station from (within four blocks
        // of it, as the microphone looks for its station).
        Block microphone = radioBlock("desk_microphone");
        if (microphone != null) {
            b.set(0, f, 2, Blocks.SPRUCE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP));
            BlockState mic = microphone.defaultBlockState();
            if (mic.hasProperty(HorizontalDirectionalBlock.FACING)) mic = mic.setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH);
            b.set(0, f + 1, 2, mic);
        }
        var entity = b.level.getBlockEntity(at);
        if (entity != null) {
            var tag = entity.saveWithoutMetadata();
            tag.putString("frequency", RESCUE_FREQUENCY);
            tag.putString("modulation", "FM");
            tag.putInt("watts", 25);
            tag.putDouble("energy", 10_000);
            entity.load(tag);
        }
        if (planned != null) {
            // The enclosure is built with its own chunk; if that came first, its antenna is there to link to.
            BlockPos aerial = antenna(b.level, planned);
            if (aerial != null) link(b.level, at, aerial);
            return;
        }
        Spot spot = enclosureNow(b);
        if (spot == null) {
            Builder.built("radio_station_none", b.origin);
            return;
        }
        enclosure(b.level, spot, random, new int[]{at.getX(), at.getZ()});
    }

    /**
     * The radio's enclosure built at its spot, on the ground as the chunk it stands in has it now: iron bars round it two
     * high, a way in towards the tower, gravel and coarse dirt underfoot, the mast with the antenna on it and a lightning
     * rod's pole by it, room for whoever comes to put power in; then linked by coax to the station in the cab, where that
     * is built. False where the ground there is under water.
     */
    private static boolean enclosure(WorldGenLevel level, Spot spot, RandomSource random, int[] station) {
        Block antenna = radioBlock("dual_band_antenna");
        if (antenna == null) return false;
        int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spot.x(), spot.z());
        Builder k = new Builder(level, new BlockPos(spot.x(), y, spot.z()), spot.turn());
        for (int[] corner : new int[][]{{0, 0}, {-3, -3}, {3, -3}, {-3, 3}, {3, 3}}) {
            if (!level.getFluidState(k.at(corner[0], -1, corner[1])).isEmpty()) return false;
        }
        k.clearGrowth(4, 12);
        k.carve(-3, 3, -3, 3, 0, 4);
        for (int x = -3; x <= 3; x++) {
            for (int z = -3; z <= 3; z++) {
                k.set(x, -1, z, random.nextBoolean() ? Blocks.GRAVEL.defaultBlockState() : Blocks.COARSE_DIRT.defaultBlockState());
                k.fillDown(x, -2, z, Blocks.COBBLESTONE.defaultBlockState());
                boolean ring = Math.abs(x) == 3 || Math.abs(z) == 3;
                // The bars round it, two high, a way in on the side towards the tower.
                if (ring && !(x == -3 && z == 0)) {
                    k.set(x, 0, z, Blocks.IRON_BARS.defaultBlockState());
                    k.set(x, 1, z, Blocks.IRON_BARS.defaultBlockState());
                }
            }
        }
        // The mast and the antenna on it; the lightning rod's pole two blocks off.
        for (int h = 0; h <= 8; h++) k.set(1, h, 1, Blocks.IRON_BARS.defaultBlockState());
        k.set(1, 9, 1, antenna.defaultBlockState());
        for (int h = 0; h <= 7; h++) k.set(1, h, -1, Blocks.IRON_BARS.defaultBlockState());
        k.set(1, 8, -1, Blocks.LIGHTNING_ROD.defaultBlockState());
        k.join();
        BlockPos aerial = k.at(1, 9, 1).immutable();
        Builder.built("radio_station", aerial);
        BlockPos at = find(level, station[0], station[1], radioBlock("station"));
        if (at != null) link(level, at, aerial);
        return true;
    }

    /** The antenna of a planned enclosure, where it is built yet. */
    private static BlockPos antenna(WorldGenLevel level, Spot spot) {
        int[] o = TrailFeature.turn(spot.turn(), 1, 1);
        return find(level, spot.x() + o[0], spot.z() + o[1], radioBlock("dual_band_antenna"));
    }

    /** The highest block of a kind in a column, or null; only in a chunk that may be built in. */
    private static BlockPos find(WorldGenLevel level, int x, int z, Block block) {
        if (block == null) return null;
        if (level instanceof net.minecraft.server.level.WorldGenRegion region) {
            net.minecraft.world.level.ChunkPos center = region.getCenter();
            if (Math.abs((x >> 4) - center.x) > 1 || Math.abs((z >> 4) - center.z) > 1) return null;
        }
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos(x, level.getMaxBuildHeight() - 1, z);
        for (; m.getY() > level.getMinBuildHeight(); m.move(Direction.DOWN)) {
            if (level.getBlockState(m).is(block)) return m.immutable();
        }
        return null;
    }

    /** The station linked by coax to the antenna, as a cable run by hand would link it (the cable's items not kept). */
    private static void link(WorldGenLevel level, BlockPos station, BlockPos aerial) {
        var entity = level.getBlockEntity(station);
        if (entity == null) return;
        var tag = entity.saveWithoutMetadata();
        var coax = new net.minecraft.nbt.CompoundTag();
        coax.put("antenna", net.minecraft.nbt.NbtUtils.writeBlockPos(aerial));
        coax.putInt("items", 0);
        coax.putDouble("length", Math.sqrt(station.distSqr(aerial)));
        tag.put("coax", coax);
        entity.load(tag);
        Builder.built("radio_link", station);
    }

    /**
     * Where the radio's enclosure goes when it is built with the tower: a few blocks out from the tower on its east side
     * as drawn, or failing that its south or north, never the west where the stair and the trail come in -- on ground
     * level enough across it, and all of it on ground that is done: the chunk being made, or one beside it already
     * decorated. Null where none of them will do.
     */
    private static Spot enclosureNow(Builder b) {
        for (Rotation q : QUARTERS) {
            for (int[] spot : SPOTS) {
                Rotation turn = b.rotation.getRotated(q);
                int[] o = TrailFeature.turn(turn, spot[0], spot[1]);
                BlockPos c = new BlockPos(b.origin.getX() + o[0], 0, b.origin.getZ() + o[1]);
                if (!b.writable(c, 4)) continue;
                int y = b.level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, c.getX(), c.getZ());
                boolean fits = true;
                for (int[] corner : new int[][]{{-3, -3}, {3, -3}, {-3, 3}, {3, 3}}) {
                    int h = b.level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, c.getX() + corner[0], c.getZ() + corner[1]);
                    if (Math.abs(h - y) > 3) fits = false;
                }
                if (fits) return new Spot(c.getX(), c.getZ(), turn);
            }
        }
        return null;
    }

    /** Where the enclosure's middle may go as drawn on the tower's east side, out from it and along it, best first. */
    private static final int[][] SPOTS = {{12, 6}, {12, -6}, {12, 0}, {10, 3}, {10, -3}, {14, 4}, {14, -4}, {11, 8}, {11, -8},
            {16, 0}, {16, 6}, {16, -6}, {13, 10}, {13, -10}, {18, 3}, {18, -3}};

    private static Block radioBlock(String name) {
        Block block = net.minecraftforge.registries.ForgeRegistries.BLOCKS.getValue(new net.minecraft.resources.ResourceLocation("simpleradio", name));
        return block == null || block == Blocks.AIR ? null : block;
    }

    /**
     * The years on it: the footings' stone mossy and cracked where it stands in the wet of the ground, and in a mild,
     * wet country vines up the legs' outer faces from the foot, a few blocks, more on one leg than another.
     */
    private static void weather(Builder b, RandomSource random) {
        int r = legAt(0);
        var biome = b.level.getBiome(b.at(0, 0, 0)).value();
        float downfall = biome.getModifiedClimateSettings().downfall(), warmth = biome.getBaseTemperature();
        boolean vines = warmth >= 0.4f && warmth < 1.5f && downfall >= 0.5f;
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                // The footing, from the leg down.
                for (int y = -1; y >= -12; y--) {
                    if (!b.get(sx * r, y, sz * r).is(Blocks.STONE_BRICKS)) break;
                    float f = random.nextFloat();
                    if (f < 0.4f) b.set(sx * r, y, sz * r, Blocks.MOSSY_STONE_BRICKS.defaultBlockState());
                    else if (f < 0.55f) b.set(sx * r, y, sz * r, Blocks.CRACKED_STONE_BRICKS.defaultBlockState());
                }
                if (!vines) continue;
                int up = 2 + random.nextInt(5);
                for (Direction out : new Direction[]{sx > 0 ? Direction.EAST : Direction.WEST, sz > 0 ? Direction.SOUTH : Direction.NORTH}) {
                    for (int y = 0; y < up; y++) {
                        int vx = sx * r + out.getStepX(), vz = sz * r + out.getStepZ();
                        if (!b.get(vx, y, vz).isAir() || random.nextFloat() < 0.2f) continue;
                        b.set(vx, y, vz, Blocks.VINE.defaultBlockState()
                                .setValue(net.minecraft.world.level.block.VineBlock.getPropertyForFace(out.getOpposite()), true));
                    }
                }
            }
        }
    }

    /**
     * The way a tower at a spot is turned: its door to the mountain it looks to, else one of four by the world's seed and
     * the spot -- the same answer every time it is asked, so its trail can be laid from its stair before it is built.
     */
    static Rotation rotation(WorldGenLevel level, BlockPos foot, com.jeladastudios.feelthenature.GeologyBridge.Landmark view) {
        if (view != null) return toward(view.x() - foot.getX(), view.z() - foot.getZ());
        return rotation(level.getSeed(), foot.getX(), foot.getZ());
    }

    private static Rotation rotation(long seed, int x, int z) {
        long h = seed ^ (x * 73428767L) ^ (z * 912931L);
        h ^= h >>> 29;
        return Rotation.values()[(int) Math.floorMod(h * 0x9E3779B97F4A7C15L >>> 33, 4L)];
    }

    /** The turn that brings the tower's east side, its door's, round to face a way across the ground. */
    static Rotation toward(int dx, int dz) {
        if (Math.abs(dx) >= Math.abs(dz)) return dx >= 0 ? Rotation.NONE : Rotation.CLOCKWISE_180;
        return dz >= 0 ? Rotation.CLOCKWISE_90 : Rotation.COUNTERCLOCKWISE_90;
    }

    /** A board on the corner post beside the door, at eye height on the walkway, naming the mountain it looks to. */
    private static void nameBoard(Builder b, com.jeladastudios.feelthenature.GeologyBridge.Landmark view) {
        b.set(4, FLOOR + 2, -3, Blocks.SPRUCE_WALL_SIGN.defaultBlockState()
                .setValue(net.minecraft.world.level.block.WallSignBlock.FACING, Direction.EAST));
        if (b.level.getBlockEntity(b.m) instanceof net.minecraft.world.level.block.entity.SignBlockEntity sign) {
            String name = switch (view.name()) {
                case "k2" -> "K2";
                case "everest" -> "Everest";
                case "matterhorn" -> "Matterhorn";
                default -> view.name();
            };
            var text = new net.minecraft.world.level.block.entity.SignText()
                    .setMessage(1, net.minecraft.network.chat.Component.literal(name))
                    .setColor(net.minecraft.world.item.DyeColor.BLACK);
            // Through its saved form: in a chunk being generated the sign has no level yet, and setting its text the
            // ordinary way tells a level that is not there, which brings the generation down.
            var tag = sign.saveWithoutMetadata();
            net.minecraft.world.level.block.entity.SignText.DIRECT_CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, text)
                    .result().ifPresent(t -> tag.put("front_text", t));
            tag.putBoolean("is_waxed", true);
            sign.load(tag);
        }
    }

    /** How far out a leg stands at a height. */
    static int legAt(int y) {
        int r = LEG[0][1];
        for (int[] l : LEG) if (y >= l[0]) r = l[1];
        return r;
    }

    private static void legs(Builder b) {
        BlockState post = Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState();
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                for (int y = 0; y < FLOOR; y++) {
                    int r = legAt(y);
                    b.set(sx * r, y, sz * r, post);
                    // Where a leg steps in, a block across the joint keeps it one piece.
                    if (y > 0 && legAt(y - 1) != r) {
                        int o = legAt(y - 1);
                        b.set(sx * o, y, sz * o, post);
                        b.set(sx * r, y, sz * o, post);
                        b.set(sx * o, y, sz * r, post);
                    }
                }
                b.pier(sx * legAt(0), sz * legAt(0), Blocks.STONE_BRICKS.defaultBlockState());
            }
        }
        // Girts round the frame at each step and under the walkway, a knee brace under each where it meets a leg.
        for (int y : GIRTS) {
            int r = legAt(y);
            girts(b, y, r);
            knees(b, y - 1, r);
        }
        // Brackets under the walkway's edge, from the top girts out.
        corbels(b, FLOOR - 1, 4);
        // An X across every face of every tier, girt to girt -- but the west face of the foot, where the way in is.
        int low = 0;
        for (int i = 0; i < GIRTS.length; i++) {
            int r = legAt(low);
            braces(b, low, GIRTS[i] - 1, r, i > 0);
            low = GIRTS[i] + 1;
        }
    }

    /**
     * The two diagonals of an X on the faces of a tier, from a leg's foot to the far leg's head, of stairs climbing the
     * way each diagonal climbs: a beam of timber at a slope, not a line of posts. Nothing that stands there is moved.
     */
    private static void braces(Builder b, int y0, int y1, int r, boolean west) {
        int w = 2 * r - 2, h = y1 - y0;
        if (w <= 0 || h < 2) return;
        for (int i = 0; i <= w; i++) {
            int a = -r + 1 + i;
            int up = y0 + Math.round((float) i * h / w), down = y0 + Math.round((float) (w - i) * h / w);
            // North and south faces run along x; east and west along z.
            for (int[] face : new int[][]{{0, -1}, {0, 1}, {1, 0}, {-1, 0}}) {
                if (face[0] == -1 && !west) continue;
                boolean alongX = face[0] == 0;
                int fx = alongX ? a : face[0] * r, fz = alongX ? face[1] * r : a;
                b.setIfAir(fx, up, fz, Blocks.SPRUCE_STAIRS.defaultBlockState()
                        .setValue(StairBlock.FACING, alongX ? Direction.EAST : Direction.SOUTH));
                b.setIfAir(fx, down, fz, Blocks.SPRUCE_STAIRS.defaultBlockState()
                        .setValue(StairBlock.FACING, alongX ? Direction.WEST : Direction.NORTH));
            }
        }
    }

    /** Upside-down stairs in the corners between the legs and a girt over them, their foot against the leg. */
    private static void knees(Builder b, int y, int r) {
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                b.setIfAir(sx * (r - 1), y, sz * r, upside(sx > 0 ? Direction.EAST : Direction.WEST));
                b.setIfAir(sx * r, y, sz * (r - 1), upside(sz > 0 ? Direction.SOUTH : Direction.NORTH));
            }
        }
    }

    /** A ring of upside-down stairs at a height, their foot inward: the walkway's edge carried out on brackets. */
    private static void corbels(Builder b, int y, int r) {
        for (int i = -r; i <= r; i++) {
            b.setIfAir(i, y, -r, upside(Direction.SOUTH));
            b.setIfAir(i, y, r, upside(Direction.NORTH));
            b.setIfAir(-r, y, i, upside(Direction.EAST));
            b.setIfAir(r, y, i, upside(Direction.WEST));
        }
    }

    private static BlockState upside(Direction facing) {
        return Blocks.SPRUCE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, facing)
                .setValue(StairBlock.HALF, net.minecraft.world.level.block.state.properties.Half.TOP);
    }

    private static void girts(Builder b, int y, int r) {
        for (int i = -r + 1; i < r; i++) {
            b.set(i, y, -r, log(Direction.Axis.X));
            b.set(i, y, r, log(Direction.Axis.X));
            b.set(-r, y, i, log(Direction.Axis.Z));
            b.set(r, y, i, log(Direction.Axis.Z));
        }
    }

    /**
     * The switchback stair up the middle: flights of three steps along x, eastward in the north lane and westward in the
     * one south of it, a landing across both lanes at each end. Each landing stands level with the flight's last step, so
     * the stair is walked from the ground to the cab without a jump; the last flight comes up through the cab's floor
     * along its north wall. A rail of fence posts between the lanes.
     */
    private static void stair(Builder b) {
        BlockState plank = Blocks.SPRUCE_PLANKS.defaultBlockState();
        BlockState east = Blocks.SPRUCE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.EAST);
        BlockState west = Blocks.SPRUCE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.WEST);
        int landing = -1;
        boolean eastward = true;
        while (landing < FLOOR) {
            int lx = eastward ? -2 : 2;
            for (int z = LANE_NORTH; z <= LANE_SOUTH; z++) b.set(lx, landing, z, plank);
            if (landing == -1) {
                for (int z = LANE_NORTH; z <= LANE_SOUTH; z++) b.fillDown(lx, landing - 1, z, Blocks.COBBLESTONE.defaultBlockState());
            }
            int lane = eastward ? LANE_NORTH : LANE_SOUTH;
            for (int s = 1; s <= 3; s++) b.set(eastward ? lx + s : lx - s, landing + s, lane, eastward ? east : west);
            landing += 3;
            eastward = !eastward;
        }
        for (int y = 0; y < FLOOR; y++) b.setIfAir(0, y, LANE_NORTH + 1, Blocks.SPRUCE_FENCE.defaultBlockState());
    }

    /** The stair's two lanes, north and south; the landings span from the one to the other. */
    private static final int LANE_NORTH = -2, LANE_SOUTH = 0;

    private static void cab(Builder b) {
        BlockState plank = Blocks.SPRUCE_PLANKS.defaultBlockState();
        // The walkway's floor, the cab's in it; open over the last flight, which comes up along the north wall, and over
        // the landing it starts from, so a head clears the floor as the flight is stepped onto.
        for (int x = -5; x <= 5; x++) {
            for (int z = -5; z <= 5; z++) {
                if (z == LANE_NORTH && x >= -2 && x <= 1) continue;
                b.set(x, FLOOR, z, plank);
            }
        }
        // The walkway's rail.
        BlockState fence = Blocks.SPRUCE_FENCE.defaultBlockState();
        for (int i = -5; i <= 5; i++) {
            b.set(i, FLOOR + 1, -5, fence);
            b.set(i, FLOOR + 1, 5, fence);
            b.set(-5, FLOOR + 1, i, fence);
            b.set(5, FLOOR + 1, i, fence);
        }
        // The cab: logs at the corners, a plank sill all round, windows over it on every side; the door to the east.
        BlockState pane = Blocks.GLASS_PANE.defaultBlockState();
        for (int y = FLOOR + 1; y <= FLOOR + 3; y++) {
            for (int i = -3; i <= 3; i++) {
                boolean corner = Math.abs(i) == 3;
                BlockState wall = corner ? log(Direction.Axis.Y) : y == FLOOR + 1 ? plank : pane;
                b.set(i, y, -3, wall);
                b.set(i, y, 3, wall);
                b.set(-3, y, i, wall);
                b.set(3, y, i, wall);
            }
        }
        BlockState door = Blocks.SPRUCE_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.WEST);
        b.set(3, FLOOR + 1, 0, door.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        b.set(3, FLOOR + 2, 0, door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        // The ceiling, open in the middle up into the lantern-room over the roof.
        for (int x = -3; x <= 3; x++) {
            for (int z = -3; z <= 3; z++) {
                if (Math.abs(x) > 1 || Math.abs(z) > 1) b.set(x, FLOOR + 4, z, plank);
            }
        }
    }

    /**
     * The roof, as the lookouts' are: a hipped roof of dark oak over the cab and the walkway, stepping in a block a course,
     * and up out of it a lantern-room -- a little glazed cupola with a hipped roof of its own over an overhang, its floor
     * open down into the cab so the light comes in from above. A whole block at its peak for the anemometer.
     */
    private static void roof(Builder b) {
        BlockState plank = Blocks.DARK_OAK_PLANKS.defaultBlockState();
        // The main roof: courses at four, three and two blocks out; the last only its ring, round the lantern-room.
        for (int c = 0; c <= 2; c++) hipCourse(b, FLOOR + 4 + c, 4 - c, c < 2 ? plank : null, c < 2 ? 1 : 99);
        // The lantern-room: a log at each corner, glass between, two blocks high.
        int base = FLOOR + 6;
        for (int y = base; y <= base + 1; y++) {
            for (int i = -1; i <= 1; i++) {
                for (int[] side : new int[][]{{i, -1}, {i, 1}, {-1, i}, {1, i}}) {
                    boolean corner = Math.abs(side[0]) == 1 && Math.abs(side[1]) == 1;
                    b.set(side[0], y, side[1], corner ? log(Direction.Axis.Y) : Blocks.GLASS_PANE.defaultBlockState());
                }
            }
        }
        // Its roof: out over an overhang, then up to the peak.
        hipCourse(b, base + 2, 2, plank, -1);
        hipCourse(b, base + 3, 1, plank, -1);
        b.set(0, base + 4, 0, plank);
        // A lantern hung under it, over the opening.
        b.set(0, base + 1, 0, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
    }

    /** The height of the block the anemometer stands on, at the lantern-room's peak. */
    static final int PEAK = FLOOR + 10;

    /**
     * One course of a hipped roof at a height, its ring of stairs facing in at {@code r} blocks out, its inside filled
     * with a block (or left open where {@code fill} is null) but for a hole {@code hole} blocks out from the middle (none
     * where it is -1).
     */
    private static void hipCourse(Builder b, int y, int r, BlockState fill, int hole) {
        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                int ax = Math.abs(x), az = Math.abs(z);
                if (ax < r && az < r) {
                    if (fill != null && (ax > hole || az > hole)) b.set(x, y, z, fill);
                    continue;
                }
                Direction in;
                StairsShape shape = StairsShape.STRAIGHT;
                if (ax == r && az == r) {
                    // A corner: the outer corner piece, facing in along x.
                    in = x > 0 ? Direction.WEST : Direction.EAST;
                    shape = (x > 0) == (z > 0) ? StairsShape.OUTER_LEFT : StairsShape.OUTER_RIGHT;
                } else if (ax == r) {
                    in = x > 0 ? Direction.WEST : Direction.EAST;
                } else {
                    in = z > 0 ? Direction.NORTH : Direction.SOUTH;
                }
                b.set(x, y, z, Blocks.DARK_OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, in).setValue(StairBlock.SHAPE, shape));
            }
        }
    }

    /**
     * What the cab is kept with, laid out as a lookout's is: the station's terminal at a desk along the west wall with
     * its barometer and hygrometer hung on the wall over it either side of a window and a chair before it, the fire
     * finder in the middle, the stove in the south-west corner with its chimney up through the roof, the bed along the
     * east wall, the log on a lectern at the head of the stair, barrels of stores. The north wall is the stair's, and
     * the way from it to the door is kept clear. Out on the walkway: the rain gauge on a corner, the thermometer hung on the cab's corner post that
     * faces the true north, a lightning rod and a lantern, a chair looking out; the anemometer on the roof's peak, within
     * the terminal's reach (FT's Geology's terminal reads twelve blocks up).
     */
    private static void fit(Builder b, RandomSource random) {
        int f = FLOOR + 1;
        // The desk: the terminal facing into the room, a chair before it, the board either side of the window over it
        // with the barometer and the hygrometer hung on it.
        b.geology(-2, f, 0, "weather_terminal", Direction.EAST);
        if (Builder.has("wall_barometer")) {
            BlockState desk = Blocks.SPRUCE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP);
            b.set(-2, f, -1, desk);
            b.set(-2, f, 1, desk);
            b.set(-3, f + 1, -1, Blocks.SPRUCE_PLANKS.defaultBlockState());
            b.set(-3, f + 1, 1, Blocks.SPRUCE_PLANKS.defaultBlockState());
            b.geology(-2, f + 1, -1, "wall_hygrometer", Direction.EAST);
            b.geology(-2, f + 1, 1, "wall_barometer", Direction.EAST);
        } else {
            // A version of FT's Geology before the instruments were hung: they stand on the desk.
            b.geology(-2, f, -1, "hygrometer", Direction.EAST);
            b.geology(-2, f, 1, "barometer", Direction.EAST);
        }
        b.set(-1, f, 0, Blocks.SPRUCE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.EAST));
        // The fire finder in the middle, under the lantern-room.
        b.set(0, f, 0, Blocks.CARTOGRAPHY_TABLE.defaultBlockState());
        // The stove in the corner.
        b.set(-2, f, 2, Blocks.FURNACE.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.EAST));
        // Its chimney built into the wall behind it, up past the eaves.
        chimney(b, -3, f, 2, FLOOR + 7);
        // The bed along the east wall, its head to the south; the way in from the door kept clear.
        BlockState bed = Blocks.WHITE_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH);
        b.set(2, f, 2, bed.setValue(BedBlock.PART, BedPart.HEAD));
        b.set(2, f, 1, bed.setValue(BedBlock.PART, BedPart.FOOT));
        // Stores beside the stove and by the head of the stair; the log on its lectern there, open to whoever comes up.
        b.barrel(-1, f, 2, LOOT, random);
        b.barrel(-1, f, -1, REGISTER, random);
        b.lectern(1, f, -1, Direction.EAST, Logbook.lookout(random));
        if (random.nextBoolean()) b.set(1, f, 2, Blocks.POTTED_FERN.defaultBlockState());
        // The walkway: the rain gauge open to the sky on a corner; the thermometer hung at eye height on a corner post of
        // the cab, on its face to the true north, out of the sun.
        b.geology(5, f, -5, "rain_gauge", Direction.SOUTH);
        if (Builder.has("wall_thermometer")) {
            Direction north = b.drawn(Direction.NORTH), along = north.getClockWise();
            int px = 3 * (north.getStepX() + along.getStepX()), pz = 3 * (north.getStepZ() + along.getStepZ());
            b.geology(px + north.getStepX(), f + 1, pz + north.getStepZ(), "wall_thermometer", north);
        } else {
            b.geology(-5, f, 5, "thermometer", Direction.NORTH);
            b.set(-5, f + 1, 5, Blocks.BIRCH_SLAB.defaultBlockState());
        }
        b.set(5, f + 1, 5, Blocks.LIGHTNING_ROD.defaultBlockState());
        b.set(-5, f + 1, -5, Blocks.LANTERN.defaultBlockState());
        b.set(-4, f, 1, Blocks.SPRUCE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.EAST));
        b.geology(0, PEAK + 1, 0, "anemometer", Direction.SOUTH);
    }

    /** The loot the lookout's barrels are filled from, and the visitors' book with a little of it. */
    static final net.minecraft.resources.ResourceLocation LOOT =
            new net.minecraft.resources.ResourceLocation(com.jeladastudios.feelthenature.FeelTheNature.MODID, "chests/lookout_tower");
    static final net.minecraft.resources.ResourceLocation REGISTER =
            new net.minecraft.resources.ResourceLocation(com.jeladastudios.feelthenature.FeelTheNature.MODID, "chests/lookout_register");

    /**
     * A stove's chimney from a height up to another: Advanced Chimneys' own where it is installed; else brick, with a
     * campfire smoking on top -- but not where Pollution of the Realms is, for whom a fire is a source of pollution.
     */
    static void chimney(Builder b, int x, int y0, int z, int y1) {
        Block flue = net.minecraftforge.registries.ForgeRegistries.BLOCKS.getValue(
                new net.minecraft.resources.ResourceLocation("adchimneys", "small_brick_chimney"));
        boolean chimneys = net.minecraftforge.fml.ModList.get().isLoaded("adchimneys") && flue != null && flue != Blocks.AIR;
        for (int y = y0; y <= y1; y++) b.set(x, y, z, chimneys ? flue.defaultBlockState() : Blocks.BRICKS.defaultBlockState());
        if (!chimneys && !net.minecraftforge.fml.ModList.get().isLoaded("adpother")) {
            b.set(x, y1 + 1, z, Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true)
                    .setValue(CampfireBlock.SIGNAL_FIRE, false));
        }
    }

    private static BlockState log(Direction.Axis axis) {
        return Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, axis);
    }
}
