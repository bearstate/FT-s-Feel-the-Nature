package com.jeladastudios.feelthenature.client.life;

import com.jeladastudios.feelthenature.ClientConfig;
import com.jeladastudios.feelthenature.FeelTheNature;
import com.jeladastudios.feelthenature.GeologyBridge;
import com.jeladastudios.feelthenature.world.ModParticles;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The small life in the air round the player, where and when it would be:
 *
 * <ul>
 *   <li>fireflies on a warm, dry night, low over grass and by water -- never in the cold or the rain;</li>
 *   <li>pollen over flowers on a dry day;</li>
 *   <li>leaves coming down from broadleaf crowns, more in a wind, carried with it -- needles hardly fall;</li>
 *   <li>snow blown off the surface by a strong wind.</li>
 * </ul>
 *
 * <p>Nothing is spawned with the game's particles set to minimal, and only a few tries are made a tick, so the counts
 * stay modest whatever the place.</p>
 */
public final class AmbientLife {

    private AmbientLife() {}

    private static final RandomSource RANDOM = RandomSource.create();
    private static final int REACH = 16;

    /** Counts since the last trace line, for the automated game test. */
    static int fireflies, pollen, leaves, drift;
    /** Serene Seasons' sub-season the last tick, or -1. */
    private static int season = -1;
    private static long costNanos, costMax;
    private static int costTicks;

    @Mod.EventBusSubscriber(modid = FeelTheNature.MODID, value = Dist.CLIENT)
    public static final class Ticks {
        private Ticks() {}

        @SubscribeEvent
        public static void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;
            Minecraft mc = Minecraft.getInstance();
            ClientLevel level = mc.level;
            LocalPlayer player = mc.player;
            if (level == null || player == null || mc.isPaused() || !ClientConfig.AMBIENT_LIFE.get()) return;
            if (mc.options.particles().get() == ParticleStatus.MINIMAL) return;
            long t0 = System.nanoTime();
            spawn(level, player);
            long spent = System.nanoTime() - t0;
            costNanos += spent;
            costMax = Math.max(costMax, spent);
            costTicks++;
        }
    }

    @Mod.EventBusSubscriber(modid = FeelTheNature.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Providers {
        private Providers() {}

        @SubscribeEvent
        public static void register(RegisterParticleProvidersEvent event) {
            event.registerSpriteSet(ModParticles.FIREFLY.get(), FireflyParticle.Provider::new);
            event.registerSpriteSet(ModParticles.POLLEN.get(), PollenParticle.Provider::new);
            event.registerSpecial(ModParticles.FALLING_LEAF.get(), new FallingLeafParticle.Provider());
        }
    }

    private static void spawn(ClientLevel level, LocalPlayer player) {
        double density = ClientConfig.LIFE_DENSITY.get();
        long time = level.getDayTime() % 24000L;
        boolean night = time > 13000 && time < 23000, day = time > 1000 && time < 11500;
        float rain = GeologyBridge.rain(level, 1f);
        float wx = GeologyBridge.windX(level), wz = GeologyBridge.windZ(level);
        float wind = Mth.sqrt(wx * wx + wz * wz);
        BlockPos here = player.blockPosition();
        Biome biome = level.getBiome(here).value();
        boolean warm = !biome.coldEnoughToSnow(here) && biome.getBaseTemperature() >= 0.5f;
        // The time of year, with Serene Seasons: fireflies in summer, pollen in spring, leaves in autumn.
        season = SeasonBridge.subSeason(level, here);

        if (night && rain < 0.05f && warm && wind < 5f) {
            float chance = 0.07f * (float) density * Seasons.fireflies(season);
            for (int i = 0; i < 2; i++) if (RANDOM.nextFloat() < chance) firefly(level, player);
        }
        if (day && rain < 0.05f && biome.getBaseTemperature() >= 0.3f) {
            if (RANDOM.nextFloat() < 0.5f * (float) density * Seasons.pollen(season)) pollen(level, player, wx, wz);
        }
        if (rain < 0.8f) {
            float chance = (0.04f + 0.05f * Math.min(wind, 8f)) * (float) density * Seasons.leaves(season);
            for (int i = 0; i < 2; i++) if (RANDOM.nextFloat() < chance) leaf(level, player, wx, wz);
        }
        if (wind > 4.5f) {
            float chance = Math.min(1f, (wind - 4.5f) / 5f) * (float) density;
            for (int i = 0; i < 3; i++) if (RANDOM.nextFloat() < chance) spindrift(level, player, wx, wz);
        }
    }

    /**
     * The air over the ground in a column round the player, within reach of their height, or null. The client keeps only
     * the world-surface and motion-blocking heightmaps, so under a tree the crown is stepped down through to the ground.
     */
    private static BlockPos ground(ClientLevel level, LocalPlayer player, int reach) {
        int x = player.getBlockX() + RANDOM.nextInt(2 * reach + 1) - reach;
        int z = player.getBlockZ() + RANDOM.nextInt(2 * reach + 1) - reach;
        if (!level.hasChunkAt(new BlockPos(x, player.getBlockY(), z))) return null;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z), z);
        for (int d = 0; d < 16 && (level.getBlockState(m.below()).is(BlockTags.LEAVES) || level.getBlockState(m.below()).isAir()); d++) {
            m.move(0, -1, 0);
        }
        if (Math.abs(m.getY() - player.getBlockY()) > 14) return null;
        return m.immutable();
    }

    /** The top of a column round the player, within reach of their height, or null. */
    private static BlockPos column(ClientLevel level, LocalPlayer player, int reach, Heightmap.Types map) {
        int x = player.getBlockX() + RANDOM.nextInt(2 * reach + 1) - reach;
        int z = player.getBlockZ() + RANDOM.nextInt(2 * reach + 1) - reach;
        if (!level.hasChunkAt(new BlockPos(x, player.getBlockY(), z))) return null;
        int y = level.getHeight(map, x, z);
        if (Math.abs(y - player.getBlockY()) > 14) return null;
        return new BlockPos(x, y, z);
    }

    private static void firefly(ClientLevel level, LocalPlayer player) {
        BlockPos top = ground(level, player, REACH);
        if (top == null) return;
        BlockState ground = level.getBlockState(top.below());
        BlockState on = level.getBlockState(top);
        boolean grassy = ground.is(Blocks.GRASS_BLOCK) || ground.is(Blocks.MOSS_BLOCK) || on.is(Blocks.GRASS)
                || on.is(Blocks.TALL_GRASS) || on.is(BlockTags.FLOWERS) || on.is(Blocks.FERN);
        boolean wet = ground.getFluidState().is(FluidTags.WATER);
        if (!grassy && !wet) return;
        // Not under a roof or in a cave: in the open or under trees, where the night air is.
        if (!level.canSeeSky(top) && !level.getBlockState(top.above(3)).is(BlockTags.LEAVES)) return;
        level.addParticle(ModParticles.FIREFLY.get(), top.getX() + RANDOM.nextDouble(), top.getY() + 0.4 + RANDOM.nextDouble() * 2.2,
                top.getZ() + RANDOM.nextDouble(), 0, 0, 0);
        fireflies++;
    }

    private static void pollen(ClientLevel level, LocalPlayer player, float wx, float wz) {
        BlockPos top = ground(level, player, 12);
        if (top == null) return;
        BlockState on = level.getBlockState(top);
        if (!on.is(BlockTags.SMALL_FLOWERS) && !on.is(BlockTags.TALL_FLOWERS)) return;
        if (!level.canSeeSky(top)) return;
        level.addParticle(ModParticles.POLLEN.get(), top.getX() + RANDOM.nextDouble(), top.getY() + 0.3 + RANDOM.nextDouble() * 1.5,
                top.getZ() + RANDOM.nextDouble(), wx, 0, wz);
        pollen++;
    }

    private static void leaf(ClientLevel level, LocalPlayer player, float wx, float wz) {
        int x = player.getBlockX() + RANDOM.nextInt(21) - 10, z = player.getBlockZ() + RANDOM.nextInt(21) - 10;
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - 1;
        if (Math.abs(top - player.getBlockY()) > 20) return;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos(x, top, z);
        BlockState crown = level.getBlockState(m);
        if (!crown.is(BlockTags.LEAVES) || needles(crown)) return;
        // Down through the crown to its underside, where a leaf comes loose into the air.
        int depth = 0;
        while (depth < 8 && level.getBlockState(m.move(0, -1, 0)).is(BlockTags.LEAVES)) depth++;
        if (!level.getBlockState(m).isAir()) return;
        level.addParticle(new BlockParticleOption(ModParticles.FALLING_LEAF.get(), crown), x + RANDOM.nextDouble(),
                m.getY() + 0.9, z + RANDOM.nextDouble(), wx, 0, wz);
        leaves++;
    }

    private static void spindrift(ClientLevel level, LocalPlayer player, float wx, float wz) {
        BlockPos top = column(level, player, 10, Heightmap.Types.MOTION_BLOCKING);
        if (top == null) return;
        BlockState s = level.getBlockState(top.below()), on = level.getBlockState(top);
        boolean snow = s.is(Blocks.SNOW_BLOCK) || s.is(Blocks.POWDER_SNOW) || on.is(Blocks.SNOW);
        if (!snow || !level.canSeeSky(top)) return;
        level.addParticle(ParticleTypes.SNOWFLAKE, top.getX() + RANDOM.nextDouble(), top.getY() + 0.1 + RANDOM.nextDouble() * 0.6,
                top.getZ() + RANDOM.nextDouble(), wx * 0.04, 0.01 + RANDOM.nextDouble() * 0.03, wz * 0.04);
        drift++;
    }

    private static boolean needles(BlockState s) {
        String p = BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath();
        return p.contains("spruce") || p.contains("pine") || p.contains("fir") || p.contains("hemlock")
                || p.contains("cedar") || p.contains("larch") || p.contains("redwood");
    }

    /** For the trace: what was spawned since the last call. */
    /** For the trace: microseconds a tick spawning, on average and at the most, since the last call. */
    public static String costAndReset() {
        String s = "life:" + costNanos / Math.max(1, costTicks) / 1000 + "/" + costMax / 1000;
        costNanos = costMax = 0;
        costTicks = 0;
        return s;
    }

    public static String countsAndReset() {
        String s = "fireflies:" + fireflies + ",pollen:" + pollen + ",leaves:" + leaves + ",drift:" + drift + ",season:" + season;
        fireflies = pollen = leaves = drift = 0;
        return s;
    }
}
