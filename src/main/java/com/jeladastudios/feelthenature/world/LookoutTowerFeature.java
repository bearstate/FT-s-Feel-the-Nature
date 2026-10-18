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
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.StairsShape;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * A fire lookout on a summit: a glazed cab on four splayed timber legs, braced in X between girts, a switchback stair
 * up the middle, a walkway round the cab under the eaves, a hipped roof. Kept as a weather station as much as a lookout:
 * FT's Geology's instruments -- the terminal and the barometer and hygrometer inside, the rain gauge and thermometer on
 * the walkway's corner posts in the open, the anemometer on the roof's peak -- and lived in: a bed, a stove and its
 * chimney, a lantern, the fire finder on its stand in the middle of the floor.
 *
 * <p>Built in place from blocks, turned to one of four ways at random. The legs stand on piers taken down to the ground,
 * so a slope under the tower does not leave it hanging; where FT's Geology is not installed its instruments are left
 * out.</p>
 */
public class LookoutTowerFeature extends Feature<NoneFeatureConfiguration> {

    /** The walkway's floor, in blocks over the ground; the cab stands on it. */
    static final int FLOOR = 15;
    /** How far out the legs stand at the foot, the middle and the top, and from which height each holds. */
    private static final int[][] LEG = {{0, 5}, {5, 4}, {10, 3}};

    public LookoutTowerFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    /** The country is cut into cells this many chunks across; each has at most one tower, on its most prominent summit. */
    public static final int CELL = 32;
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
        }
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        net.minecraft.world.level.ChunkPos chunk = new net.minecraft.world.level.ChunkPos(context.origin());
        BlockPos site = site(level, chunk);
        if (site == null || site.getX() >> 4 != chunk.x || site.getZ() >> 4 != chunk.z) return false;
        // The tower stands on the summit as the world built it: the most level spot within a few blocks of the summit
        // found, the higher where two are as level, if any is level enough.
        int fx = 0, fz = 0, fy = 0, rough = Integer.MAX_VALUE;
        for (int ox = -SHIFT; ox <= SHIFT; ox += 4) {
            for (int oz = -SHIFT; oz <= SHIFT; oz += 4) {
                int x = site.getX() + ox, z = site.getZ() + oz, lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
                for (int dx = -5; dx <= 5; dx += 5) {
                    for (int dz = -5; dz <= 5; dz += 5) {
                        int h = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x + dx, z + dz);
                        lo = Math.min(lo, h);
                        hi = Math.max(hi, h);
                    }
                }
                if (hi - lo < rough || (hi - lo == rough && lo > fy)) {
                    rough = hi - lo;
                    fx = x;
                    fz = z;
                    fy = lo;
                }
            }
        }
        if (com.jeladastudios.feelthenature.dev.TestCommands.ON) {
            com.jeladastudios.feelthenature.FeelTheNature.LOGGER.info("LOOKOUT_TOWER {} at {} {} {} (rough {})",
                    rough > ROUGH ? "not built" : "built", fx, fy, fz, rough);
        }
        if (rough > ROUGH) return false;
        return build(level, new BlockPos(fx, fy, fz), context.random());
    }

    /**
     * The summit a lookout stands on in the cell a chunk lies in, or null where there is none: of the tops of the cell's
     * ground on a coarse grid, in lookout country, the one standing furthest over a ring round it -- at least
     * {@link #PROMINENCE} -- closed in on. By the generator's own ground and biomes, so every chunk of the cell finds the
     * same summit; worked out once a cell.
     */
    public static BlockPos site(WorldGenLevel level, net.minecraft.world.level.ChunkPos chunk) {
        int cx = Math.floorDiv(chunk.x, CELL), cz = Math.floorDiv(chunk.z, CELL);
        long key = level.getSeed() * 31 + (((long) cx) << 32 ^ (cz & 0xFFFFFFFFL));
        return SITES.computeIfAbsent(key, k -> java.util.Optional.ofNullable(summit(level, cx, cz))).orElse(null);
    }

    private static BlockPos summit(WorldGenLevel level, int cx, int cz) {
        var source = level.getLevel().getChunkSource();
        var generator = source.getGenerator();
        var random = source.randomState();
        java.util.function.IntBinaryOperator ground = (x, z) -> generator.getBaseHeight(x, z,
                net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE_WG, level, random);
        int x0 = cx * CELL * 16, z0 = cz * CELL * 16, n = CELL * 16 / GRID;
        // The cell's ground on a coarse grid, kept off its edge so the ring and the tower are the cell's own.
        int[][] g = new int[n][n];
        for (int i = 0; i < n; i++) for (int j = 0; j < n; j++) g[i][j] = ground.applyAsInt(x0 + GRID / 2 + i * GRID, z0 + GRID / 2 + j * GRID);
        // The highest of the grid's points in lookout country -- a summit or a high shoulder under the bare rock -- and of
        // those, the one standing furthest over the ring round it.
        var biomes = generator.getBiomeSource();
        java.util.List<int[]> country = new java.util.ArrayList<>();
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                int x = x0 + GRID / 2 + i * GRID, z = z0 + GRID / 2 + j * GRID, h = g[i][j];
                if (h <= level.getSeaLevel() + PROMINENCE || !country(biomes, random, x, h, z)) continue;
                country.add(new int[]{x, h, z});
            }
        }
        country.sort((a, b) -> b[1] - a[1]);
        int bx = 0, bz = 0, by = 0;
        double most = PROMINENCE;
        StringBuilder seen = new StringBuilder();
        for (int[] p : country.subList(0, Math.min(CANDIDATES, country.size()))) {
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
        if (!country(biomes, random, bx, by, bz)) return new BlockPos(sx, sy, sz);
        return new BlockPos(bx, by, bz);
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

    /** The tower built with its foot at a point, turned one of four ways. */
    public static boolean build(WorldGenLevel level, BlockPos foot, RandomSource random) {
        Builder b = new Builder(level, foot, Rotation.getRandom(random));
        b.clear(5, FLOOR + 10);
        legs(b);
        stair(b);
        cab(b);
        roof(b);
        fit(b, random);
        return true;
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
                    }
                }
                b.pier(sx * legAt(0), sz * legAt(0), Blocks.STONE_BRICKS.defaultBlockState());
            }
        }
        // Girts round the frame at each step, and under the walkway.
        for (int y : new int[]{4, 9, FLOOR - 1}) girts(b, y, legAt(y));
        // X-bracing on each face between the girts.
        brace(b, 0, 4, legAt(0));
        brace(b, 5, 9, legAt(5));
        brace(b, 10, FLOOR - 1, legAt(10));
    }

    private static void girts(Builder b, int y, int r) {
        for (int i = -r + 1; i < r; i++) {
            b.set(i, y, -r, log(Direction.Axis.X));
            b.set(i, y, r, log(Direction.Axis.X));
            b.set(-r, y, i, log(Direction.Axis.Z));
            b.set(r, y, i, log(Direction.Axis.Z));
        }
    }

    /** A pair of diagonals of fence posts across each face, from girt to girt. */
    private static void brace(Builder b, int y0, int y1, int r) {
        BlockState fence = Blocks.SPRUCE_FENCE.defaultBlockState();
        int span = 2 * r - 2;
        if (span <= 0 || y1 - y0 < 2) return;
        for (int i = 0; i <= span; i++) {
            int a = -r + 1 + i;
            int up = y0 + 1 + Math.round((float) i * (y1 - y0 - 2) / span), down = y1 - 1 - (up - y0 - 1);
            for (int y : new int[]{up, down}) {
                b.setIfAir(a, y, -r, fence);
                b.setIfAir(a, y, r, fence);
                b.setIfAir(-r, y, a, fence);
                b.setIfAir(r, y, a, fence);
            }
        }
    }

    /**
     * The switchback stair up the middle: flights of three steps along x, one lane each way, landings across both lanes
     * at the ends; from a landing at the foot to the cab's floor. A rail of fence posts between the lanes.
     */
    private static void stair(Builder b) {
        BlockState plank = Blocks.SPRUCE_PLANKS.defaultBlockState();
        BlockState east = Blocks.SPRUCE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.EAST);
        BlockState west = Blocks.SPRUCE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.WEST);
        int landing = -1;
        boolean eastward = true;
        while (landing < FLOOR) {
            int lx = eastward ? -2 : 2;
            for (int z = -1; z <= 1; z++) b.set(lx, landing, z, plank);
            if (landing == -1) for (int z = -1; z <= 1; z++) b.fillDown(lx, landing - 1, z, Blocks.COBBLESTONE.defaultBlockState());
            int lane = eastward ? -1 : 1;
            for (int s = 1; s <= 3; s++) {
                int x = eastward ? lx + s : lx - s;
                b.set(x, landing + s, lane, eastward ? east : west);
            }
            landing += 4;
            eastward = !eastward;
        }
        for (int y = 0; y < FLOOR; y++) b.setIfAir(0, y, 0, Blocks.SPRUCE_FENCE.defaultBlockState());
    }

    private static void cab(Builder b) {
        BlockState plank = Blocks.SPRUCE_PLANKS.defaultBlockState();
        // The walkway's floor, the cab's in it; a hole where the last flight comes up through it.
        for (int x = -5; x <= 5; x++) {
            for (int z = -5; z <= 5; z++) {
                if (z == 1 && (x == -1 || x == 0)) continue;
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
        // The ceiling.
        for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++) b.set(x, FLOOR + 4, z, plank);
    }

    /** A hipped roof over the cab and the walkway, in dark oak, stepping in a block a course. */
    private static void roof(Builder b) {
        BlockState plank = Blocks.DARK_OAK_PLANKS.defaultBlockState();
        for (int c = 0; c <= 3; c++) {
            int y = FLOOR + 4 + c, r = 4 - c;
            for (int x = -r; x <= r; x++) {
                for (int z = -r; z <= r; z++) {
                    int ax = Math.abs(x), az = Math.abs(z);
                    if (ax < r && az < r) {
                        b.set(x, y, z, plank);
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
                    b.set(x, y, z, Blocks.DARK_OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, in)
                            .setValue(StairBlock.SHAPE, shape));
                }
            }
        }
        b.set(0, FLOOR + 8, 0, Blocks.DARK_OAK_SLAB.defaultBlockState());
    }

    /** What the cab is kept with: the station's instruments, the bed, the stove and its chimney, the fire finder. */
    private static void fit(Builder b, RandomSource random) {
        int f = FLOOR + 1;
        // The stove in the north-west corner, its chimney up through the roof, smoke from its top.
        b.set(-2, f, -2, Blocks.FURNACE.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.SOUTH));
        for (int y = f + 1; y <= FLOOR + 7; y++) b.set(-2, y, -2, Blocks.BRICKS.defaultBlockState());
        b.set(-2, FLOOR + 8, -2, Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true)
                .setValue(CampfireBlock.SIGNAL_FIRE, false));
        // The bed along the east wall, its head to the north.
        BlockState bed = Blocks.WHITE_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.NORTH);
        b.set(2, f, -2, bed.setValue(BedBlock.PART, BedPart.HEAD));
        b.set(2, f, -1, bed.setValue(BedBlock.PART, BedPart.FOOT));
        // The fire finder on its stand in the middle, a lantern over it.
        b.set(0, f, 0, Blocks.CARTOGRAPHY_TABLE.defaultBlockState());
        b.set(0, FLOOR + 3, 0, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
        b.set(2, f, 2, Blocks.BARREL.defaultBlockState());
        // The station: its terminal and the indoor instruments on the north wall, the rest in the open.
        b.geology(0, f, -2, "weather_terminal", Direction.SOUTH);
        b.geology(-1, f, -2, "barometer", Direction.SOUTH);
        b.geology(1, f, -2, "hygrometer", Direction.SOUTH);
        b.geology(5, FLOOR + 2, -5, "rain_gauge", Direction.SOUTH);
        b.geology(-5, FLOOR + 2, 5, "thermometer", Direction.NORTH);
        b.geology(0, FLOOR + 9, 0, "anemometer", Direction.SOUTH);
        // A pot of something green on the sill by the door.
        if (random.nextBoolean()) b.set(2, f, 1, Blocks.POTTED_FERN.defaultBlockState());
    }

    private static BlockState log(Direction.Axis axis) {
        return Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, axis);
    }
}
