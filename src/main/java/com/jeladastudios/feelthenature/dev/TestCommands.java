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
