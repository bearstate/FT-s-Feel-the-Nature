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
                        .then(Commands.literal("close").executes(c -> umbrella(c, false)))));
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
            GeologyBridge.River river = GeologyBridge.river(bx, bz);
            if (river == null || river.lake() || river.fromBank() < 0 || river.fromBank() > 3) continue;
            return new BlockPos(bx, by, bz);
        }
        return null;
    }
}
