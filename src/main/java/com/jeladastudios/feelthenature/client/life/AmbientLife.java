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
 *   <li>snow blown off the surface by a strong wind, and shaken off the crowns that hold it by a gust;</li>
 *   <li>mist rising off still water on a calm, cold morning ({@link Steam});</li>
 *   <li>motes of dust glinting in the shafts of sun through a wood's crowns on a still, dry day.</li>
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
    static int fireflies, pollen, leaves, drift, steam, motes, clumps, drops;
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
            event.registerSpriteSet(ModParticles.STEAM.get(), SteamParticle.Provider::new);
            event.registerSpriteSet(ModParticles.MOTE.get(), MoteParticle.Provider::new);
            event.registerSpriteSet(ModParticles.PANE_DROP.get(), PaneDropParticle.Provider::new);
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
            // More of them torn off in a gust.
            float chance = (0.04f + 0.05f * Math.min(wind, 8f)) * (float) density * Seasons.leaves(season)
                    * com.jeladastudios.feelthenature.client.sound.Mix.gusting(GeologyBridge.gust(1f));
            for (int i = 0; i < 2; i++) if (RANDOM.nextFloat() < chance) leaf(level, player, wx, wz);
        }
        if (wind > 4.5f) {
            float chance = Math.min(1f, (wind - 4.5f) / 5f) * (float) density;
            for (int i = 0; i < 3; i++) if (RANDOM.nextFloat() < chance) spindrift(level, player, wx, wz);
        }
        if (wind > 2f) {
            // A gust shakes the snow off the crowns that hold it.
            float chance = Math.min(1f, (wind - 2f) / 8f) * 0.25f * (float) density
                    * com.jeladastudios.feelthenature.client.sound.Mix.gusting(GeologyBridge.gust(1f));
            for (int i = 0; i < 3; i++) if (RANDOM.nextFloat() < chance) snowClump(level, player, wx, wz);
        }
        float temperature = com.jeladastudios.feelthenature.client.sound.Fauna.temperature(biome.getBaseTemperature(), player.getY());
        float mist = Steam.strength(time, temperature, wind, rain, GeologyBridge.fog(), season);
        // Motes in the sun under the trees: a still, dry day, most of them when the sun is low and its shafts slant in.
        if (day && rain < 0.02f && wind < 4f && !level.isThundering()) {
            float low = 1f - Math.min(1f, Math.min(Math.abs(time - 1000), Math.abs(time - 11500)) / 3000f);
            float chance = (0.35f + 0.45f * low) * (float) density;
            for (int i = 0; i < 4; i++) if (RANDOM.nextFloat() < chance) mote(level, player);
        }
        // Rain running down the windows round a player indoors: more the harder it rains.
        if (rain > 0.05f) {
            float chance = Math.min(1f, rain * 1.6f) * (float) density;
            for (int i = 0; i < 40; i++) if (RANDOM.nextFloat() < chance) paneDrop(level, player);
        }
        if (mist > 0.01f) {
            for (int i = 0; i < 24; i++) if (RANDOM.nextFloat() < 0.8f * mist * (float) density) steam(level, player, wx, wz);
        }
    }

    /**
     * A mote in a shaft of sun: a point of air that sees the sky straight up, with a crown of leaves a couple of blocks
     * to one side of it -- a gap in a wood's canopy, where the light comes down through the dust.
     */
    private static void mote(ClientLevel level, LocalPlayer player) {
        int x = player.getBlockX() + RANDOM.nextInt(17) - 8, z = player.getBlockZ() + RANDOM.nextInt(17) - 8;
        BlockPos p = new BlockPos(x, player.getBlockY() + RANDOM.nextInt(5) - 1, z);
        if (!level.hasChunkAt(p) || !level.getBlockState(p).isAir() || !level.canSeeSky(p)) return;
        boolean crown = false;
        for (net.minecraft.core.Direction d : net.minecraft.core.Direction.Plane.HORIZONTAL) {
            BlockPos.MutableBlockPos m = p.relative(d, 2).mutable();
            for (int up = 1; up <= 10 && !crown; up++) crown = level.getBlockState(m.move(0, 1, 0)).is(BlockTags.LEAVES);
            if (crown) break;
        }
        if (!crown) return;
        level.addParticle(ModParticles.MOTE.get(), x + RANDOM.nextDouble(), p.getY() + RANDOM.nextDouble(), z + RANDOM.nextDouble(), 0, 0, 0);
        motes++;
    }

    /**
     * A raindrop on the inside face of a pane near the player, where the rain beats on its outside: a pane or a glass
     * block with the open sky over its other side, read from the player's side. The drop is set a hair in from the glass,
     * so it is seen through it running down.
     */
    private static void paneDrop(ClientLevel level, LocalPlayer player) {
        int x = player.getBlockX() + RANDOM.nextInt(11) - 5, y = player.getBlockY() + RANDOM.nextInt(4) - 1;
        int z = player.getBlockZ() + RANDOM.nextInt(11) - 5;
        BlockPos p = new BlockPos(x, y, z);
        BlockState s = level.getBlockState(p);
        if (!s.is(net.minecraftforge.common.Tags.Blocks.GLASS_PANES)
                && !s.is(net.minecraftforge.common.Tags.Blocks.GLASS)) return;
        // The side of it the player is on, and the open, rained-on side opposite.
        for (net.minecraft.core.Direction d : net.minecraft.core.Direction.Plane.HORIZONTAL) {
            BlockPos inside = p.relative(d), outside = p.relative(d.getOpposite());
            // Open to the rain outside, or under eaves the wind drives it in under: the sky within two blocks out.
            boolean open = level.canSeeSky(outside) || level.canSeeSky(outside.relative(d.getOpposite()))
                    || level.canSeeSky(outside.relative(d.getOpposite(), 2));
            if (!level.getBlockState(inside).isAir() || level.canSeeSky(inside) || !level.getBlockState(outside).isAir() || !open) continue;
            double inset = s.is(net.minecraftforge.common.Tags.Blocks.GLASS) ? 0.52 : 0.08;
            double px = p.getX() + 0.5 + d.getStepX() * inset + (d.getStepZ() != 0 ? RANDOM.nextDouble() - 0.5 : 0) * 0.9;
            double pz = p.getZ() + 0.5 + d.getStepZ() * inset + (d.getStepX() != 0 ? RANDOM.nextDouble() - 0.5 : 0) * 0.9;
            level.addParticle(ModParticles.PANE_DROP.get(), px, p.getY() + 0.3 + RANDOM.nextDouble() * 0.65, pz,
                    d.getStepX(), 0, d.getStepZ());
            drops++;
            return;
        }
    }

    private static void steam(ClientLevel level, LocalPlayer player, float wx, float wz) {
        BlockPos top = column(level, player, 28, Heightmap.Types.MOTION_BLOCKING);
        if (top == null) return;
        // Still, open water: a lake's or a pond's surface, or a river's, under the sky -- not a waterfall's curtain.
        var water = level.getFluidState(top.below());
        if (!water.is(FluidTags.WATER) || !water.isSource() || !level.getBlockState(top).isAir() || !level.canSeeSky(top)) return;
        level.addParticle(ModParticles.STEAM.get(), top.getX() + RANDOM.nextDouble(), top.getY() - 0.05 + RANDOM.nextDouble() * 0.25,
                top.getZ() + RANDOM.nextDouble(), wx, 0, wz);
        steam++;
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

    /**
     * Snow lying on a crown shaken loose: a clump of it falling from the crown's underside, spreading as it falls and
     * carried with the wind. Only from a crown with snow on it.
     */
    private static void snowClump(ClientLevel level, LocalPlayer player, float wx, float wz) {
        int x = player.getBlockX() + RANDOM.nextInt(25) - 12, z = player.getBlockZ() + RANDOM.nextInt(25) - 12;
        int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
        if (Math.abs(top - player.getBlockY()) > 24) return;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos(x, top, z);
        if (!level.getBlockState(m).is(Blocks.SNOW) || !level.getBlockState(m.move(0, -1, 0)).is(BlockTags.LEAVES)) return;
        int depth = 0;
        while (depth < 10 && level.getBlockState(m.move(0, -1, 0)).is(BlockTags.LEAVES)) depth++;
        if (!level.getBlockState(m).isAir()) return;
        double cx = x + 0.2 + RANDOM.nextDouble() * 0.6, cy = m.getY() + 0.9, cz = z + 0.2 + RANDOM.nextDouble() * 0.6;
        int n = 8 + RANDOM.nextInt(8);
        for (int i = 0; i < n; i++) {
            level.addParticle(ParticleTypes.SNOWFLAKE, cx + (RANDOM.nextDouble() - 0.5) * 0.4, cy + RANDOM.nextDouble() * 0.3,
                    cz + (RANDOM.nextDouble() - 0.5) * 0.4, wx * 0.02 + (RANDOM.nextDouble() - 0.5) * 0.03,
                    -0.05 - RANDOM.nextDouble() * 0.08, wz * 0.02 + (RANDOM.nextDouble() - 0.5) * 0.03);
        }
        clumps++;
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
        String s = "fireflies:" + fireflies + ",pollen:" + pollen + ",leaves:" + leaves + ",drift:" + drift + ",steam:" + steam
                + ",motes:" + motes + ",clumps:" + clumps + ",drops:" + drops + ",season:" + season;
        fireflies = pollen = leaves = drift = steam = motes = clumps = drops = 0;
        return s;
    }
}
