package com.jeladastudios.feelthenature.dev;

import com.jeladastudios.feelthenature.FeelTheNature;
import com.jeladastudios.feelthenature.GeologyBridge;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Commands for the automated game test only, there with {@code -Dfts_feelthenature.trace=true} and never otherwise.
 *
 * <p>{@code /ftn_test riverbank [radius]} takes the player to the nearest grassy bank of one of FT's Geology's rivers
 * in the country already loaded round them: a column of its river water on the surface with a grass bank beside it.
 * A biome named river is not enough to stand by, since the river network can name a column a river where the water was
 * never laid. Answers {@code FTN_TEST riverbank x y z} or {@code FTN_TEST riverbank none}.</p>
 *
 * <p>{@code /ftn_test umbrella open|close} opens or closes the umbrella in the player's main hand, as a use would:
 * the test window cannot be clicked.</p>
 *
 * <p>{@code /ftn_test tan} answers how long Tough As Nails has had the player dry ({@code FTN_TEST tan dry=N}).</p>
 *
 * <p>{@code /ftn_test coast <radius>} takes the player to the nearest sea shore by the generator's ground
 * ({@code FTN_TEST coast x z}); {@code /ftn_test snowslope <x> <z>} from a mountain's top down to a little over its snow
 * line ({@code FTN_TEST snowslope x y z line=N}).</p>
 *
 * <p>{@code /ftn_test volcano <radius> [x z]} answers whether a large volcano's crater lies within so many blocks of the
 * player, or of the column given ({@code FTN_TEST volcano 400 true}).</p>
 *
 * <p>{@code /ftn_test role} answers what FT's Geology's plates make of the player's column ({@code FTN_TEST role volcanic_highland}).</p>
 */
@Mod.EventBusSubscriber(modid = FeelTheNature.MODID)
public final class TestCommands {

    private TestCommands() {}

    /** Whether the game runs for the automated test: the commands are there, and a few events are logged for it. */
    public static final boolean ON = Boolean.getBoolean("fts_feelthenature.trace");

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        if (!ON) return;
        event.getDispatcher().register(Commands.literal("ftn_test").requires(s -> s.hasPermission(2))
                .then(Commands.literal("riverbank")
                        .executes(c -> riverbank(c, 128))
                        .then(Commands.argument("radius", IntegerArgumentType.integer(8, 256))
                                .executes(c -> riverbank(c, IntegerArgumentType.getInteger(c, "radius")))))
                .then(Commands.literal("umbrella")
                        .then(Commands.literal("open").executes(c -> umbrella(c, true)))
                        .then(Commands.literal("close").executes(c -> umbrella(c, false))))
                .then(Commands.literal("tan").executes(TestCommands::tan))
                .then(Commands.literal("snowslope")
                        .then(Commands.argument("x", IntegerArgumentType.integer())
                                .then(Commands.argument("z", IntegerArgumentType.integer())
                                        .executes(c -> snowslope(c, IntegerArgumentType.getInteger(c, "x"),
                                                IntegerArgumentType.getInteger(c, "z"))))))
                .then(Commands.literal("floating")
                        .then(Commands.argument("radius", IntegerArgumentType.integer(1, 32)).executes(c -> {
                            // What the ground's carpets and plants stand on round the player: none of them on water.
                            ServerPlayer p = c.getSource().getPlayer();
                            if (p == null) return 0;
                            int r = IntegerArgumentType.getInteger(c, "radius"), onWater = 0, onGround = 0;
                            for (BlockPos at : BlockPos.betweenClosed(p.blockPosition().offset(-r, -4, -r), p.blockPosition().offset(r, 4, r))) {
                                if (!p.serverLevel().getBlockState(at).is(net.minecraft.world.level.block.Blocks.MOSS_CARPET)) continue;
                                if (p.serverLevel().getFluidState(at.below()).isEmpty()) onGround++;
                                else onWater++;
                            }
                            int w = onWater, g = onGround;
                            c.getSource().sendSuccess(() -> Component.literal("FTN_TEST floating moss=" + w + " grounded=" + g), false);
                            return 1;
                        })))
                .then(Commands.literal("biome")
                        .then(Commands.argument("id", net.minecraft.commands.arguments.ResourceLocationArgument.id())
                                .executes(c -> {
                                    // The nearest ground in the country loaded whose biome, as built, is the one named.
                                    ServerPlayer p = c.getSource().getPlayer();
                                    if (p == null) return 0;
                                    var id = net.minecraft.commands.arguments.ResourceLocationArgument.getId(c, "id");
                                    ServerLevel level = p.serverLevel();
                                    for (int r = 0; r <= 180; r += 4) {
                                        for (int dx = -r; dx <= r; dx += 4) {
                                            for (int dz = -r; dz <= r; dz += 4) {
                                                if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                                                int x = p.getBlockX() + dx, z = p.getBlockZ() + dz;
                                                if (!level.hasChunk(x >> 4, z >> 4)) continue;
                                                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                                                if (!level.getBiome(new BlockPos(x, y, z)).is(id)) continue;
                                                if (!openGround(level, x, y, z)) continue;
                                                p.teleportTo(level, x + 0.5, y + 1, z + 0.5, p.getYRot(), 30f);
                                                c.getSource().sendSuccess(() -> Component.literal("FTN_TEST biome " + x + " " + y + " " + z), false);
                                                return 1;
                                            }
                                        }
                                    }
                                    c.getSource().sendSuccess(() -> Component.literal("FTN_TEST biome none"), false);
                                    return 0;
                                })))
                .then(Commands.literal("log")
                        .then(Commands.argument("radius", IntegerArgumentType.integer(8, 160))
                                .executes(c -> fallenLog(c, IntegerArgumentType.getInteger(c, "radius")))))
                .then(Commands.literal("snowpatch")
                        .then(Commands.argument("radius", IntegerArgumentType.integer(8, 160))
                                .executes(c -> snowPatch(c, IntegerArgumentType.getInteger(c, "radius")))))
                .then(Commands.literal("cairn").then(Commands.argument("radius", IntegerArgumentType.integer(8, 160))
                        .executes(c -> cairn(c, IntegerArgumentType.getInteger(c, "radius")))))
                .then(Commands.literal("lakeshore").then(Commands.argument("radius", IntegerArgumentType.integer(8, 2000))
                        .executes(c -> lakeShore(c, IntegerArgumentType.getInteger(c, "radius")))))
                .then(Commands.literal("find").then(Commands.argument("block", com.mojang.brigadier.arguments.StringArgumentType.string())
                        .then(Commands.argument("radius", IntegerArgumentType.integer(8, 200))
                                .executes(c -> find(c, com.mojang.brigadier.arguments.StringArgumentType.getString(c, "block"),
                                        IntegerArgumentType.getInteger(c, "radius"))))))
                .then(Commands.literal("falls").then(Commands.argument("radius", IntegerArgumentType.integer(8, 2000))
                        .then(Commands.argument("drop", com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg(0, 100))
                                .executes(c -> fallsHere(c, IntegerArgumentType.getInteger(c, "radius"),
                                        com.mojang.brigadier.arguments.DoubleArgumentType.getDouble(c, "drop"))))))
                .then(Commands.literal("trail").executes(TestCommands::trail))
                .then(Commands.literal("tower").executes(TestCommands::tower))
                .then(Commands.literal("towersite").executes(TestCommands::towerSite))
                .then(Commands.literal("towersites").then(Commands.argument("radius", IntegerArgumentType.integer(0, 6))
                        .executes(c -> towerSites(c, IntegerArgumentType.getInteger(c, "radius")))))
                .then(Commands.literal("waterfall")
                        .then(Commands.argument("radius", IntegerArgumentType.integer(8, 160))
                                .executes(c -> waterfall(c, IntegerArgumentType.getInteger(c, "radius"), false))
                                .then(Commands.literal("wet").executes(c -> waterfall(c, IntegerArgumentType.getInteger(c, "radius"), true)))))
                .then(Commands.literal("seashore")
                        .then(Commands.argument("radius", IntegerArgumentType.integer(8, 160))
                                .executes(c -> seashore(c, IntegerArgumentType.getInteger(c, "radius")))))
                .then(Commands.literal("coast")
                        .then(Commands.argument("radius", IntegerArgumentType.integer(64, 8000))
                                .executes(c -> coast(c, IntegerArgumentType.getInteger(c, "radius")))))
                .then(Commands.literal("volcano")
                        .then(Commands.argument("radius", IntegerArgumentType.integer(1, 20000))
                                .executes(c -> volcano(c, null, null))
                                .then(Commands.argument("x", IntegerArgumentType.integer())
                                        .then(Commands.argument("z", IntegerArgumentType.integer())
                                                .executes(c -> volcano(c, IntegerArgumentType.getInteger(c, "x"),
                                                        IntegerArgumentType.getInteger(c, "z")))))))
                .then(Commands.literal("role").executes(c -> {
                    ServerPlayer p = c.getSource().getPlayer();
                    if (p == null) return 0;
                    String role = GeologyBridge.role(p.serverLevel(), p.getBlockX(), p.getBlockZ());
                    c.getSource().sendSuccess(() -> Component.literal("FTN_TEST role " + role), false);
                    return 1;
                })));
    }

    /**
     * Says once the server has started what Terralith's basalt cliff biome is left to place, after the biome modifiers:
     * the addon's columns, and none of Terralith's spires ({@code FTN_TEST basalt_cliffs ours=1 terralith=0}), or that
     * Terralith is not there.
     */
    @SubscribeEvent
    public static void basaltCliffs(net.minecraftforge.event.server.ServerStartedEvent event) {
        if (!ON) return;
        var biomes = event.getServer().registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.BIOME);
        var biome = biomes.get(new net.minecraft.resources.ResourceLocation("terralith", "basalt_cliffs"));
        if (biome == null) {
            FeelTheNature.LOGGER.info("FTN_TEST basalt_cliffs none");
            return;
        }
        int ours = 0, terralith = 0;
        for (var step : biome.getGenerationSettings().features()) {
            for (var feature : step) {
                String id = feature.unwrapKey().map(k -> k.location().toString()).orElse("");
                if (id.equals("fts_feelthenature:basalt_columns")) ours++;
                if (id.startsWith("terralith:cliffs/basalt/basalt_column")) terralith++;
            }
        }
        FeelTheNature.LOGGER.info("FTN_TEST basalt_cliffs ours={} terralith={}", ours, terralith);
    }

    /**
     * How long Tough As Nails has had the player dry, in ticks: it starts again from 0 every time the player is out in the
     * rain. Read by reflection, as the addon does not build against Tough As Nails.
     */
    private static int tan(CommandContext<CommandSourceStack> c) {
        ServerPlayer player = c.getSource().getPlayer();
        if (player == null) return 0;
        try {
            Object data = Class.forName("toughasnails.api.temperature.TemperatureHelper")
                    .getMethod("getTemperatureData", net.minecraft.world.entity.player.Player.class).invoke(null, player);
            int dry = (Integer) data.getClass().getMethod("getDryTicks").invoke(data);
            // And what it decides from: the world raining, and the player's block out in it (umbrella and all).
            var exposedTo = Class.forName("toughasnails.temperature.TemperatureHelperImpl").getDeclaredMethod("isExposedToRain",
                    net.minecraft.world.level.Level.class, BlockPos.class);
            exposedTo.setAccessible(true);
            boolean exposed = (Boolean) exposedTo.invoke(null, player.level(), player.blockPosition());
            boolean raining = player.level().isRaining();
            c.getSource().sendSuccess(() -> Component.literal("FTN_TEST tan dry=" + dry + " raining=" + raining + " exposed=" + exposed), false);
            return 1;
        } catch (ReflectiveOperationException | RuntimeException e) {
            c.getSource().sendSuccess(() -> Component.literal("FTN_TEST tan none"), false);
            return 0;
        }
    }

    /**
     * Takes the player to the nearest sea shore, by the generator's own ground (nothing need be generated): the land a
     * block from the water, where the sea floor drops under the sea's level and the ground beside it stands over it.
     * Rings out every 32 blocks, then the shore found to the block. Answers {@code FTN_TEST coast x z} or
     * {@code FTN_TEST coast none}.
     */
    private static int coast(CommandContext<CommandSourceStack> c, int radius) {
        ServerPlayer player = c.getSource().getPlayer();
        if (player == null) return 0;
        ServerLevel level = player.serverLevel();
        var source = level.getChunkSource();
        int sea = level.getSeaLevel(), px = player.getBlockX(), pz = player.getBlockZ();
        java.util.function.IntBinaryOperator floor = (x, z) -> source.getGenerator()
                .getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, level, source.randomState());
        int step = 32;
        for (int r = step; r <= radius; r += step) {
            for (int dx = -r; dx <= r; dx += step) {
                for (int dz = -r; dz <= r; dz += step) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                    int x = px + dx, z = pz + dz;
                    // The open sea, not a river's channel or a pond cut under the sea's level: deep a way round too.
                    if (floor.applyAsInt(x, z) >= sea - 3 || floor.applyAsInt(x + 24, z) >= sea - 2
                            || floor.applyAsInt(x - 24, z) >= sea - 2 || floor.applyAsInt(x, z + 24) >= sea - 2
                            || floor.applyAsInt(x, z - 24) >= sea - 2) continue;
                    // Nor one of FT's Geology's lakes, or a wide river's mouth: its own water, not the sea's.
                    if (GeologyBridge.river(x, z, 4) != null) continue;
                    // The sea here: walk back towards the player until the ground comes up out of it.
                    double len = Math.hypot(dx, dz);
                    for (int t = 1; t <= len; t++) {
                        int lx = x - (int) Math.round(dx / len * t), lz = z - (int) Math.round(dz / len * t);
                        int g = floor.applyAsInt(lx, lz);
                        if (g < sea) continue;
                        player.teleportTo(level, lx + 0.5, g + 2, lz + 0.5, player.getYRot(), 40f);
                        c.getSource().sendSuccess(() -> Component.literal("FTN_TEST coast " + lx + " " + lz), false);
                        return 1;
                    }
                }
            }
        }
        c.getSource().sendSuccess(() -> Component.literal("FTN_TEST coast none"), false);
        return 0;
    }

    /**
     * Takes the player to the nearest shore of the sea itself in the country loaded round them: dry land beside a
     * column whose top water, at the sea's level, is vanilla's with the open air over it. The generator's ground cannot
     * tell the sea from a valley under the sea's level that FT's Geology drains later; the world built can. Answers
     * {@code FTN_TEST seashore x z} or {@code FTN_TEST seashore none}.
     */
    private static int waterfall(CommandContext<CommandSourceStack> c, int radius, boolean wet) {
        ServerPlayer player = c.getSource().getPlayer();
        if (player == null) return 0;
        ServerLevel level = player.serverLevel();
        int px = player.getBlockX(), pz = player.getBlockZ();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        int[] drops = new int[5];
        for (int r = 0; r <= radius; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                    int x = px + dx, z = pz + dz;
                    if (!level.hasChunk(x >> 4, z >> 4)) continue;
                    int g = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                    m.set(x, g, z);
                    if (!level.getBlockState(m).is(net.minecraft.world.level.block.Blocks.MOSS_CARPET)) m.move(0, -1, 0);
                    // Over the edge, the lip's water first spreads a block before it falls.
                    if (!falling(level, m) && level.getFluidState(m).is(net.minecraft.tags.FluidTags.WATER)) m.move(0, -1, 0);
                    int top = m.getY(), drop = 0;
                    while (drop < 40 && falling(level, m)) {
                        drop++;
                        m.move(0, -1, 0);
                    }
                    if (drop > 0) drops[Math.min(drop, 4)]++;
                    if (drop < 4) continue;
                    if (wet) com.jeladastudios.feelthenature.world.RiparianFeature.spray(level, new BlockPos(x, top, z), level.random);
                    int fy = m.getY(), moss = 0;
                    for (BlockPos p : BlockPos.betweenClosed(x - 3, fy - 1, z - 3, x + 3, fy + 3, z + 3)) {
                        if (level.getBlockState(p).is(net.minecraft.world.level.block.Blocks.MOSSY_COBBLESTONE)) moss++;
                    }
                    player.teleportTo(level, x + 6.5, fy + 4, z + 6.5, 135f, 25f);
                    int mossy = moss;
                    c.getSource().sendSuccess(() -> Component.literal("FTN_TEST waterfall " + x + " " + fy + " " + z
                            + " drop=" + (top - fy) + " mossy=" + mossy), false);
                    return 1;
                }
            }
        }
        c.getSource().sendSuccess(() -> Component.literal("FTN_TEST waterfall none drops=" + java.util.Arrays.toString(drops)), false);
        return 0;
    }

    /**
     * Takes the player beside the nearest fallen log in the country loaded round them: a lying log with moss on it.
     * Answers {@code FTN_TEST log x y z podzol=N mushrooms=N} or {@code FTN_TEST log none}.
     */
    private static int fallenLog(CommandContext<CommandSourceStack> c, int radius) {
        ServerPlayer player = c.getSource().getPlayer();
        if (player == null) return 0;
        ServerLevel level = player.serverLevel();
        int px = player.getBlockX(), pz = player.getBlockZ();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int r = 0; r <= radius; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                    int x = px + dx, z = pz + dz;
                    if (!level.hasChunk(x >> 4, z >> 4)) continue;
                    int g = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                    m.set(x, g, z);
                    if (!level.getBlockState(m).is(net.minecraft.world.level.block.Blocks.MOSS_CARPET)) m.move(0, -1, 0);
                    var top = level.getBlockState(m);
                    if (!top.is(net.minecraft.world.level.block.Blocks.MOSS_CARPET)) continue;
                    var log = level.getBlockState(m.below());
                    if (!log.is(net.minecraft.tags.BlockTags.LOGS)
                            || !log.hasProperty(net.minecraft.world.level.block.RotatedPillarBlock.AXIS)
                            || log.getValue(net.minecraft.world.level.block.RotatedPillarBlock.AXIS) == Direction.Axis.Y) continue;
                    int y = m.getY() - 1, podzol = 0, mushrooms = 0;
                    for (BlockPos p : BlockPos.betweenClosed(x - 6, y - 2, z - 6, x + 6, y + 1, z + 6)) {
                        var s = level.getBlockState(p);
                        if (s.is(net.minecraft.world.level.block.Blocks.PODZOL)) podzol++;
                        if (s.is(net.minecraft.world.level.block.Blocks.BROWN_MUSHROOM) || s.is(net.minecraft.world.level.block.Blocks.RED_MUSHROOM)) mushrooms++;
                    }
                    player.teleportTo(level, x + 3.5, y + 3, z + 3.5, 135f, 35f);
                    int pd = podzol, mu = mushrooms;
                    c.getSource().sendSuccess(() -> Component.literal("FTN_TEST log " + x + " " + y + " " + z
                            + " podzol=" + pd + " mushrooms=" + mu), false);
                    return 1;
                }
            }
        }
        c.getSource().sendSuccess(() -> Component.literal("FTN_TEST log none"), false);
        return 0;
    }

    /**
     * Takes the player over the nearest patch of snow lying among bare ground in the country loaded round them: a snow
     * block on top with no snow on the ground six blocks to the west, east, north or south of it. Answers
     * {@code FTN_TEST snowpatch x y z biome} or {@code FTN_TEST snowpatch none}.
     */
    private static int snowPatch(CommandContext<CommandSourceStack> c, int radius) {
        ServerPlayer player = c.getSource().getPlayer();
        if (player == null) return 0;
        ServerLevel level = player.serverLevel();
        int px = player.getBlockX(), pz = player.getBlockZ();
        for (int r = 0; r <= radius; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                    int x = px + dx, z = pz + dz;
                    if (!level.hasChunk(x >> 4, z >> 4) || !snowTop(level, x, z, true)) continue;
                    boolean bare = false;
                    for (Direction d : Direction.Plane.HORIZONTAL) {
                        int bx = x + d.getStepX() * 6, bz = z + d.getStepZ() * 6;
                        if (level.hasChunk(bx >> 4, bz >> 4) && !snowTop(level, bx, bz, false)) bare = true;
                    }
                    if (!bare) continue;
                    int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                    String biome = level.getBiome(new BlockPos(x, y, z)).unwrapKey().map(k -> k.location().toString()).orElse("?");
                    player.teleportTo(level, x + 0.5, y + 8, z - 7.5, 0f, 40f);
                    c.getSource().sendSuccess(() -> Component.literal("FTN_TEST snowpatch " + x + " " + y + " " + z + " " + biome), false);
                    return 1;
                }
            }
        }
        c.getSource().sendSuccess(() -> Component.literal("FTN_TEST snowpatch none"), false);
        return 0;
    }

    /** Whether a column's top is snow: a snow block (firm) or any snow at all. */
    private static boolean snowTop(ServerLevel level, int x, int z, boolean firm) {
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        var top = level.getBlockState(new BlockPos(x, y - 1, z));
        if (top.is(net.minecraft.world.level.block.Blocks.SNOW_BLOCK)) return true;
        if (firm) return false;
        return level.getBlockState(new BlockPos(x, y, z)).is(net.minecraft.world.level.block.Blocks.SNOW)
                || top.is(net.minecraft.world.level.block.Blocks.SNOW);
    }

    /** Builds a lookout tower with its foot where the player stands. Answers {@code FTN_TEST tower x y z}. */
    private static int tower(CommandContext<CommandSourceStack> c) {
        ServerPlayer player = c.getSource().getPlayer();
        if (player == null) return 0;
        ServerLevel level = player.serverLevel();
        BlockPos foot = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, player.blockPosition());
        var view = com.jeladastudios.feelthenature.world.LookoutTowerFeature.nearestLandmark(level, foot.getX(), foot.getZ());
        com.jeladastudios.feelthenature.world.LookoutTowerFeature.build(level, foot, level.random, view);
        c.getSource().sendSuccess(() -> Component.literal("FTN_TEST tower " + foot.getX() + " " + foot.getY() + " " + foot.getZ()
                + " view=" + (view == null ? "none" : view.name() + "@" + view.x() + "," + view.z())), false);
        return 1;
    }

    /**
     * The summit the lookout tower of the player's cell stands on, by the generator's ground, and how long finding it
     * took. Answers {@code FTN_TEST towersite x y z ms=N} or {@code FTN_TEST towersite none ms=N}.
     */
    private static int towerSite(CommandContext<CommandSourceStack> c) {
        ServerPlayer player = c.getSource().getPlayer();
        if (player == null) return 0;
        ServerLevel level = player.serverLevel();
        long t0 = System.nanoTime();
        BlockPos site = com.jeladastudios.feelthenature.world.LookoutTowerFeature.site(level, new net.minecraft.world.level.ChunkPos(player.blockPosition()));
        long ms = (System.nanoTime() - t0) / 1_000_000;
        c.getSource().sendSuccess(() -> Component.literal("FTN_TEST towersite "
                + (site == null ? "none" : site.getX() + " " + site.getY() + " " + site.getZ()) + " ms=" + ms), false);
        return 1;
    }

    /**
     * The lookout summits of the cells round the player's, out to a radius in cells, and the nearest of them. Answers
     * {@code FTN_TEST towersites found=N of=M nearest=x y z} (or {@code nearest=none}).
     */
    private static int towerSites(CommandContext<CommandSourceStack> c, int radius) {
        ServerPlayer player = c.getSource().getPlayer();
        if (player == null) return 0;
        ServerLevel level = player.serverLevel();
        int cell = com.jeladastudios.feelthenature.world.LookoutTowerFeature.CELL;
        int pcx = Math.floorDiv(player.chunkPosition().x, cell), pcz = Math.floorDiv(player.chunkPosition().z, cell);
        int found = 0, of = 0;
        BlockPos nearest = null;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                of++;
                BlockPos s = com.jeladastudios.feelthenature.world.LookoutTowerFeature.site(level,
                        new net.minecraft.world.level.ChunkPos((pcx + dx) * cell, (pcz + dz) * cell));
                if (s == null) continue;
                found++;
                if (nearest == null || s.distSqr(player.blockPosition()) < nearest.distSqr(player.blockPosition())) nearest = s;
            }
        }
        BlockPos n = nearest;
        int f = found, o = of;
        c.getSource().sendSuccess(() -> Component.literal("FTN_TEST towersites found=" + f + " of=" + o + " nearest="
                + (n == null ? "none" : n.getX() + " " + n.getY() + " " + n.getZ())), false);
        return 1;
    }

    /**
     * Takes the player beside the nearest cairn in the country loaded round them: a wall or a slab crowning a column.
     * Answers {@code FTN_TEST cairn x y z biome} or {@code FTN_TEST cairn none}.
     */
    private static int cairn(CommandContext<CommandSourceStack> c, int radius) {
        ServerPlayer player = c.getSource().getPlayer();
        if (player == null) return 0;
        ServerLevel level = player.serverLevel();
        int px = player.getBlockX(), pz = player.getBlockZ();
        for (int r = 0; r <= radius; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                    int x = px + dx, z = pz + dz;
                    if (!level.hasChunk(x >> 4, z >> 4)) continue;
                    int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
                    var crown = level.getBlockState(new BlockPos(x, y, z)).getBlock();
                    if (!(crown instanceof net.minecraft.world.level.block.WallBlock || crown instanceof net.minecraft.world.level.block.SlabBlock)) continue;
                    String biome = level.getBiome(new BlockPos(x, y, z)).unwrapKey().map(k -> k.location().toString()).orElse("?");
                    player.teleportTo(level, x + 4.5, y + 1, z + 4.5, 135f, 15f);
                    c.getSource().sendSuccess(() -> Component.literal("FTN_TEST cairn " + x + " " + y + " " + z + " " + biome), false);
                    return 1;
                }
            }
        }
        c.getSource().sendSuccess(() -> Component.literal("FTN_TEST cairn none"), false);
        return 0;
    }

    /**
     * Takes the player to the nearest shore of one of FT's Geology's lakes, by its river network: from the nearest column in a
     * lake out toward the player, four blocks past its edge. Answers {@code FTN_TEST lakeshore x z} or {@code ... none}.
     */
    private static int lakeShore(CommandContext<CommandSourceStack> c, int radius) {
        ServerPlayer player = c.getSource().getPlayer();
        if (player == null) return 0;
        ServerLevel level = player.serverLevel();
        int px = player.getBlockX(), pz = player.getBlockZ();
        for (int r = 0; r <= radius; r += 2) {
            for (int dx = -r; dx <= r; dx += 2) {
                for (int dz = -r; dz <= r; dz += 2) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                    int x = px + dx, z = pz + dz;
                    var lake = com.jeladastudios.feelthenature.GeologyBridge.river(x, z, 1);
                    if (lake == null || !lake.lake()) continue;
                    // Out of the lake toward the player, to four blocks past its edge.
                    Direction out = Math.abs(dx) >= Math.abs(dz) ? (dx > 0 ? Direction.WEST : Direction.EAST) : (dz > 0 ? Direction.NORTH : Direction.SOUTH);
                    int sx = x, sz = z;
                    for (int i = 0; i < 128; i++) {
                        var w = com.jeladastudios.feelthenature.GeologyBridge.river(sx, sz, 1);
                        if (w == null || !w.lake()) break;
                        sx += out.getStepX();
                        sz += out.getStepZ();
                    }
                    int lx = sx + out.getStepX() * 4, lz = sz + out.getStepZ() * 4;
                    player.teleportTo(level, lx + 0.5, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, lx, lz) + 1, lz + 0.5,
                            player.getYRot(), 20f);
                    c.getSource().sendSuccess(() -> Component.literal("FTN_TEST lakeshore " + lx + " " + lz), false);
                    return 1;
                }
            }
        }
        c.getSource().sendSuccess(() -> Component.literal("FTN_TEST lakeshore none"), false);
        return 0;
    }

    /**
     * Takes the player to the nearest block of a kind at or a few blocks under the surface in the country loaded round
     * them. Answers {@code FTN_TEST find x y z} or {@code FTN_TEST find none}.
     */
    private static int find(CommandContext<CommandSourceStack> c, String id, int radius) {
        ServerPlayer player = c.getSource().getPlayer();
        if (player == null) return 0;
        ServerLevel level = player.serverLevel();
        var block = net.minecraftforge.registries.ForgeRegistries.BLOCKS.getValue(new net.minecraft.resources.ResourceLocation(id));
        int px = player.getBlockX(), pz = player.getBlockZ();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int r = 0; r <= radius; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                    int x = px + dx, z = pz + dz;
                    if (!level.hasChunk(x >> 4, z >> 4)) continue;
                    int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
                    for (int y = top; y > top - 8; y--) {
                        if (!level.getBlockState(m.set(x, y, z)).is(block)) continue;
                        int fy = y;
                        player.teleportTo(level, x + 5.5, top + 4, z + 5.5, 135f, 30f);
                        c.getSource().sendSuccess(() -> Component.literal("FTN_TEST find " + x + " " + fy + " " + z), false);
                        return 1;
                    }
                }
            }
        }
        c.getSource().sendSuccess(() -> Component.literal("FTN_TEST find none"), false);
        return 0;
    }

    /** FT's Geology's falls round the player and its prevailing wind. Answers {@code FTN_TEST falls n=N ... wind=x,z}. */
    private static int fallsHere(CommandContext<CommandSourceStack> c, int radius, double minDrop) {
        ServerPlayer player = c.getSource().getPlayer();
        if (player == null) return 0;
        ServerLevel level = player.serverLevel();
        var falls = com.jeladastudios.feelthenature.GeologyBridge.falls(level, player.getBlockX(), player.getBlockZ(), radius, minDrop);
        var w = com.jeladastudios.feelthenature.GeologyBridge.prevailingWind(level);
        StringBuilder s = new StringBuilder("FTN_TEST falls n=" + falls.size());
        for (int i = 0; i < Math.min(6, falls.size()); i++) {
            var f = falls.get(i);
            s.append(String.format(java.util.Locale.ROOT, " %d,%d:%.1f>%.1f", f.x(), f.z(), f.top(), f.bottom()));
        }
        s.append(String.format(java.util.Locale.ROOT, " wind=%.2f,%.2f ground=%.1f", w.x, w.z,
                com.jeladastudios.feelthenature.GeologyBridge.groundHeight(level, player.getBlockX(), player.getBlockZ())));
        String out = s.toString();
        c.getSource().sendSuccess(() -> Component.literal(out), false);
        return 1;
    }

    /** The trail of the nearest lookout: its points, length and foot; the player taken to its middle. */
    private static int trail(CommandContext<CommandSourceStack> c) {
        ServerPlayer player = c.getSource().getPlayer();
        if (player == null) return 0;
        ServerLevel level = player.serverLevel();
        var trails = com.jeladastudios.feelthenature.world.TrailFeature.nearPublic(level, player.getBlockX(), player.getBlockZ());
        if (trails.isEmpty()) {
            c.getSource().sendSuccess(() -> Component.literal("FTN_TEST trail none"), false);
            return 0;
        }
        int[] t = trails.get(0);
        player.teleportTo(level, t[4] + 0.5, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, t[4], t[5]) + 12, t[5] + 0.5, 0f, 70f);
        c.getSource().sendSuccess(() -> Component.literal("FTN_TEST trail points=" + t[0] + " from " + t[1] + " " + t[2]
                + " foot " + t[3] + " " + t[6] + " mid " + t[4] + " " + t[5]), false);
        return 1;
    }

    private static boolean falling(ServerLevel level, BlockPos at) {
        var f = level.getFluidState(at);
        return f.is(net.minecraft.tags.FluidTags.WATER) && f.hasProperty(net.minecraft.world.level.material.FlowingFluid.FALLING)
                && f.getValue(net.minecraft.world.level.material.FlowingFluid.FALLING);
    }

    private static int seashore(CommandContext<CommandSourceStack> c, int radius) {
        ServerPlayer player = c.getSource().getPlayer();
        if (player == null) return 0;
        ServerLevel level = player.serverLevel();
        int sea = level.getSeaLevel(), px = player.getBlockX(), pz = player.getBlockZ();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int r = 0; r <= radius; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                    int x = px + dx, z = pz + dz;
                    if (!level.hasChunk(x >> 4, z >> 4) || !openSea(level, m.set(x, sea - 1, z))) continue;
                    for (Direction d : Direction.Plane.HORIZONTAL) {
                        int lx = x + d.getStepX(), lz = z + d.getStepZ();
                        if (!level.hasChunk(lx >> 4, lz >> 4)) continue;
                        int g = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, lx, lz);
                        if (g < sea || !level.getFluidState(new BlockPos(lx, g - 1, lz)).isEmpty()) continue;
                        player.teleportTo(level, lx + 0.5, g + 1, lz + 0.5, player.getYRot(), 40f);
                        c.getSource().sendSuccess(() -> Component.literal("FTN_TEST seashore " + lx + " " + lz), false);
                        return 1;
                    }
                }
            }
        }
        c.getSource().sendSuccess(() -> Component.literal("FTN_TEST seashore none"), false);
        return 0;
    }

    /**
    /**
     * Whether the ground round a column is open and level enough for the test field: of sixteen points on a ring 26
     * blocks out, most stand within five blocks of its height. A field against a cliff, or in a narrow valley, hears
     * the weather and the wind otherwise than in the open.
     */
    private static boolean openGround(ServerLevel level, int x, int y, int z) {
        int near = 0;
        for (int way = 0; way < 16; way++) {
            double a = way * Math.PI / 8;
            int px = x + (int) Math.round(Math.cos(a) * 26), pz = z + (int) Math.round(Math.sin(a) * 26);
            if (!level.hasChunk(px >> 4, pz >> 4)) return false;
            if (Math.abs(level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, px, pz) - y) <= 5) near++;
        }
        return near >= 13;
    }

    /** Vanilla's water with the open air over it: the sea's top water, not a river's, a lake's or an aquifer's. */
    private static boolean openSea(ServerLevel level, BlockPos.MutableBlockPos at) {
        BlockState s = level.getBlockState(at);
        if (!s.getFluidState().is(net.minecraft.tags.FluidTags.WATER) || GeologyBridge.isRiverWater(s)) return false;
        return level.getBlockState(at.above()).isAir();
    }

    /**
     * Takes the player from a mountain's top down its flank to a little over its snow line: snowy ground still, but
     * under the storms that a summit stands over. Answers {@code FTN_TEST snowslope x y z line=N} or
     * {@code FTN_TEST snowslope none}.
     */
    private static int snowslope(CommandContext<CommandSourceStack> c, int x0, int z0) {
        ServerPlayer player = c.getSource().getPlayer();
        if (player == null) return 0;
        ServerLevel level = player.serverLevel();
        var source = level.getChunkSource();
        for (int way = 0; way < 16; way++) {
            double a = way * Math.PI / 8;
            for (int t = 8; t <= 2000; t += 8) {
                int x = x0 + (int) Math.round(Math.cos(a) * t), z = z0 + (int) Math.round(Math.sin(a) * t);
                int h = source.getGenerator().getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, level, source.randomState());
                int line = GeologyBridge.snowLine(level, x, z);
                if (h > line + 24) continue;
                if (h < line + 4) break;
                player.teleportTo(level, x + 0.5, h + 2, z + 0.5, player.getYRot(), 30f);
                c.getSource().sendSuccess(() -> Component.literal("FTN_TEST snowslope " + x + " " + h + " " + z + " line=" + line), false);
                return 1;
            }
        }
        c.getSource().sendSuccess(() -> Component.literal("FTN_TEST snowslope none"), false);
        return 0;
    }

    /** Whether a large volcano's crater lies within a radius of a column: the player's, or the one given. */
    private static int volcano(CommandContext<CommandSourceStack> c, Integer x, Integer z) {
        ServerPlayer player = c.getSource().getPlayer();
        if (player == null) return 0;
        int radius = IntegerArgumentType.getInteger(c, "radius");
        boolean near = GeologyBridge.nearVolcano(player.serverLevel(), x == null ? player.getBlockX() : x,
                z == null ? player.getBlockZ() : z, radius);
        c.getSource().sendSuccess(() -> Component.literal("FTN_TEST volcano " + radius + " " + near), false);
        return 1;
    }

    /** Opens or closes the umbrella in the player's main hand, as a use would, without the sound. */
    private static int umbrella(CommandContext<CommandSourceStack> c, boolean open) {
        ServerPlayer player = c.getSource().getPlayer();
        if (player == null) return 0;
        var stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof com.jeladastudios.feelthenature.world.UmbrellaItem)) {
            c.getSource().sendSuccess(() -> Component.literal("FTN_TEST umbrella none"), false);
            return 0;
        }
        com.jeladastudios.feelthenature.world.UmbrellaItem.setOpen(stack, open);
        c.getSource().sendSuccess(() -> Component.literal("FTN_TEST umbrella " + (open ? "open" : "closed")), false);
        return 1;
    }

    private static int riverbank(CommandContext<CommandSourceStack> c, int radius) {
        ServerPlayer player = c.getSource().getPlayer();
        if (player == null) return 0;
        ServerLevel level = player.serverLevel();
        int px = player.getBlockX(), pz = player.getBlockZ();
        // Ring by ring outward, so the first bank found is about the nearest.
        for (int r = 0; r <= radius; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                    BlockPos bank = bank(level, px + dx, pz + dz);
                    if (bank == null) continue;
                    player.teleportTo(level, bank.getX() + 0.5, bank.getY(), bank.getZ() + 0.5, player.getYRot(), 30f);
                    c.getSource().sendSuccess(() -> Component.literal(
                            "FTN_TEST riverbank " + bank.getX() + " " + bank.getY() + " " + bank.getZ()), false);
                    return 1;
                }
            }
        }
        c.getSource().sendSuccess(() -> Component.literal("FTN_TEST riverbank none"), false);
        return 0;
    }

    /** The air over a grass bank beside river water on the surface at this column, or null. */
    private static BlockPos bank(ServerLevel level, int x, int z) {
        if (!level.hasChunk(x >> 4, z >> 4)) return null;
        int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
        BlockPos water = new BlockPos(x, y, z);
        if (!GeologyBridge.isRiverWater(level.getBlockState(water))) return null;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            int bx = x + d.getStepX(), bz = z + d.getStepZ();
            if (!level.hasChunk(bx >> 4, bz >> 4)) continue;
            int by = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz);
            BlockState ground = level.getBlockState(new BlockPos(bx, by - 1, bz));
            if (Math.abs(by - 1 - y) > 1 || !ground.is(BlockTags.DIRT)) continue;
            GeologyBridge.River river = GeologyBridge.river(bx, bz, 3);
            if (river == null || river.lake() || river.fromBank() < 0 || river.fromBank() > 3) continue;
            return new BlockPos(bx, by, bz);
        }
        return null;
    }
}
