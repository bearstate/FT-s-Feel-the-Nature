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
 * A fire lookout on a summit: a glazed cab on four splayed timber legs, girts and knee braces at each step, a
 * switchback stair up the middle, a walkway round the cab under the eaves carried on brackets, a hipped roof. Kept as a
 * weather station as much as a lookout: FT's Geology's instruments -- the terminal and the barometer and hygrometer
 * inside, the rain gauge and thermometer on the walkway's corner posts in the open, the anemometer on the roof's peak --
 * and lived in: a bed, a stove and its chimney, lanterns, the fire finder on its stand in the middle of the floor.
 *
 * <p>Built in place from blocks, turned to one of four ways at random, the ground under it left as it lies: the legs
 * stand down into it where it is higher and on stone footings taken down to it where it is lower. Where FT's Geology is
 * not installed its instruments are left out.</p>
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
            landmarks = null;
        }
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        net.minecraft.world.level.ChunkPos chunk = new net.minecraft.world.level.ChunkPos(context.origin());
        BlockPos site = site(level, chunk);
        if (site == null || site.getX() >> 4 != chunk.x || site.getZ() >> 4 != chunk.z) return false;
        // The tower stands on the summit as the world built it: the most level spot within a few blocks of the summit
        // found, the higher where two are as level, if any is level enough; its foot at the ground in its middle, where the
        // stair comes down.
        int fx = 0, fz = 0, fy = 0, rough = Integer.MAX_VALUE;
        for (int ox = -SHIFT; ox <= SHIFT; ox += 4) {
            for (int oz = -SHIFT; oz <= SHIFT; oz += 4) {
                int x = site.getX() + ox, z = site.getZ() + oz, lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE, mid = 0;
                for (int dx = -5; dx <= 5; dx += 5) {
                    for (int dz = -5; dz <= 5; dz += 5) {
                        int h = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x + dx, z + dz);
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
        if (com.jeladastudios.feelthenature.dev.TestCommands.ON) {
            com.jeladastudios.feelthenature.FeelTheNature.LOGGER.info("LOOKOUT_TOWER {} at {} {} {} (rough {})",
                    rough > ROUGH ? "not built" : "built", fx, fy, fz, rough);
        }
        if (rough > ROUGH) return false;
        return build(level, new BlockPos(fx, fy, fz), context.random(), nearestLandmark(level.getLevel(), fx, fz));
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
        if (!country(biomes, random, bx, by, bz)) return new BlockPos(sx, sy, sz);
        return new BlockPos(bx, by, bz);
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
        Builder b = new Builder(level, foot, rotation);
        b.clearGrowth(6, FLOOR + 10);
        // The stair's well, and the way in to its foot from the west.
        b.carve(-2, 2, -1, 1, 0, FLOOR - 1);
        b.carve(-6, -3, -1, 1, 0, 2);
        legs(b);
        stair(b);
        cab(b);
        roof(b);
        fit(b, random);
        if (view != null) nameBoard(b, view);
        return true;
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
            sign.setText(text, true);
            sign.setWaxed(true);
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
        for (int y : new int[]{4, 9, FLOOR - 1}) {
            int r = legAt(y);
            girts(b, y, r);
            knees(b, y - 1, r);
        }
        // Brackets under the walkway's edge, from the top girts out.
        corbels(b, FLOOR - 1, 4);
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
        // Lanterns on the other two corner posts.
        b.set(-5, FLOOR + 2, -5, Blocks.LANTERN.defaultBlockState());
        b.set(5, FLOOR + 2, 5, Blocks.LANTERN.defaultBlockState());
        // A pot of something green on the sill by the door.
        if (random.nextBoolean()) b.set(2, f, 1, Blocks.POTTED_FERN.defaultBlockState());
    }

    private static BlockState log(Direction.Axis axis) {
        return Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, axis);
    }
}
