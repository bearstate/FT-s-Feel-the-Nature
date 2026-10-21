package com.jeladastudios.feelthenature.client.sound;

import com.jeladastudios.feelthenature.ClientConfig;
import com.jeladastudios.feelthenature.FeelTheNature;
import com.jeladastudios.feelthenature.GeologyBridge;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.FluidState;

import java.util.EnumMap;
import java.util.Map;

/**
 * Running water heard from where it runs: a brook's babble, a river's murmur, a waterfall's roar, each placed at the
 * nearest water of its kind and fading with distance, as water does. A stream in a cave is heard in the cave; still
 * water is silent.
 *
 * <p>FT's Geology's rivers are their own water block and are heard as rivers; vanilla water that flows is heard as a
 * brook, and water that falls as a waterfall.</p>
 */
final class WaterVoices {

    private WaterVoices() {}

    enum Kind {
        STREAM("stream_small", 10f, 0.8f),
        RIVER("river_flow", 6f, 0.9f),
        FALL("waterfall_small", 14f, 1.0f);

        final ResourceLocation id;
        /** How quickly a share of moving water round the player reaches full volume, and the volume at full. */
        final float reach, gain;
        private SoundEvent event;

        Kind(String name, float reach, float gain) {
            this.id = new ResourceLocation(FeelTheNature.MODID, name);
            this.reach = reach;
            this.gain = gain;
        }

        SoundEvent event() {
            if (event == null) event = SoundEvent.createVariableRangeEvent(id);
            return event;
        }
    }

    private static final int SAMPLES = 48, REACH = 14, HEIGHT = 6;
    private static final RandomSource RANDOM = RandomSource.create();
    private static final Map<Kind, Voice> VOICES = new EnumMap<>(Kind.class);
    private static final float[] amount = new float[Kind.values().length];
    private static final BlockPos[] nearest = new BlockPos[Kind.values().length];
    private static int sinceLook = 99;

    static void tick(ClientLevel level, LocalPlayer player, long now, float underWater) {
        if (++sinceLook >= 10) {
            look(level, player);
            sinceLook = 0;
        }
        float v = underWater * ClientConfig.SURFACE_VOLUME.get().floatValue();
        var sounds = Minecraft.getInstance().getSoundManager();
        for (Kind k : Kind.values()) {
            float target = Math.min(1f, amount[k.ordinal()] * k.reach) * k.gain * v;
            BlockPos at = nearest[k.ordinal()];
            Voice voice = VOICES.get(k);
            if (voice != null && (voice.isStopped() || !sounds.isActive(voice) && now - voice.born > 40)) {
                VOICES.remove(k);
                voice = null;
            }
            if (voice == null && target > 0.01f && at != null) {
                voice = new Voice(k, at, now);
                voice.target = target;
                sounds.play(voice);
                VOICES.put(k, voice);
            } else if (voice != null) {
                voice.target = at == null ? 0f : target;
                if (at != null) voice.toward(at);
            }
        }
    }

    /** The surface round the player is looked at every this many blocks, out to {@link #REACH}. */
    private static final int GRID = 2;
    /** How far above or below the eye surface water is still heard: a river at the foot of a bank, a brook in a gully. */
    private static final int SURFACE_HEIGHT = 12;

    /**
     * Where the moving water round the player is, and how much of it. Rivers and brooks run on the surface, so the
     * surface is looked at on a grid, the same columns every time, and a river beside the player fills its share of it
     * and is heard as loud as it is. Water underground or falling down a face is found by random points in the space
     * round them. Each kind takes the larger of its two shares.
     */
    private static void look(ClientLevel level, LocalPlayer player) {
        BlockPos eye = BlockPos.containing(player.getEyePosition());
        int kinds = Kind.values().length;
        int[] count = new int[kinds], surface = new int[kinds];
        double[] best = {Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE};
        BlockPos[] found = new BlockPos[kinds];
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        int cells = 0;
        for (int dx = -REACH; dx <= REACH; dx += GRID) {
            for (int dz = -REACH; dz <= REACH; dz += GRID) {
                int x = eye.getX() + dx, z = eye.getZ() + dz;
                m.set(x, eye.getY(), z);
                if (!level.hasChunkAt(m)) continue;
                cells++;
                m.setY(level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1);
                if (Math.abs(m.getY() - eye.getY()) > SURFACE_HEIGHT) continue;
                Kind k = kind(level, m);
                if (k == null) continue;
                surface[k.ordinal()]++;
                closer(k, m, eye, best, found);
            }
        }
        for (int i = 0; i < SAMPLES; i++) {
            m.set(eye.getX() + RANDOM.nextInt(2 * REACH + 1) - REACH, eye.getY() + RANDOM.nextInt(2 * HEIGHT + 1) - HEIGHT,
                    eye.getZ() + RANDOM.nextInt(2 * REACH + 1) - REACH);
            if (!level.hasChunkAt(m)) continue;
            Kind k = kind(level, m);
            if (k == null) continue;
            count[k.ordinal()]++;
            closer(k, m, eye, best, found);
        }
        for (Kind k : Kind.values()) {
            int i = k.ordinal();
            float share = Math.max(count[i] / (float) SAMPLES, cells == 0 ? 0f : surface[i] / (float) cells);
            // Eased, so the voice does not swell and drop with every handful of random points.
            amount[i] += (share - amount[i]) * 0.4f;
            if (found[i] != null) nearest[i] = found[i];
            else if (amount[i] < 0.002f) nearest[i] = null;
        }
    }

    /**
     * What a block's water is heard as: falling water, FT's Geology's river where it runs, a brook (vanilla water
     * running), or none: still water, a river stilled behind a dam among it.
     */
    private static Kind kind(net.minecraft.world.level.BlockGetter level, BlockPos at) {
        BlockState s = level.getBlockState(at);
        FluidState f = s.getFluidState();
        if (!f.is(FluidTags.WATER)) return null;
        if (f.hasProperty(FlowingFluid.FALLING) && f.getValue(FlowingFluid.FALLING)) return Kind.FALL;
        if (GeologyBridge.isRiverWater(s)) return GeologyBridge.isRunningRiverWater(level, at, s) ? Kind.RIVER : null;
        return f.isSource() ? null : Kind.STREAM;
    }

    private static void closer(Kind k, BlockPos at, BlockPos eye, double[] best, BlockPos[] found) {
        double d = at.distSqr(eye);
        if (d < best[k.ordinal()]) {
            best[k.ordinal()] = d;
            found[k.ordinal()] = at.immutable();
        }
    }

    static void stopAll() {
        var sounds = Minecraft.getInstance().getSoundManager();
        for (Voice v : VOICES.values()) sounds.stop(v);
        VOICES.clear();
        java.util.Arrays.fill(amount, 0f);
        java.util.Arrays.fill(nearest, null);
    }

    /** For the debug screen and the trace: each voice's volume. */
    static String describe() {
        StringBuilder b = new StringBuilder();
        for (Voice v : VOICES.values()) {
            b.append(v.kind.name().toLowerCase(java.util.Locale.ROOT)).append(':')
                    .append(String.format(java.util.Locale.ROOT, "%.2f", v.loudness())).append(',');
        }
        return b.toString();
    }

    /** One kind of water, heard from a place that moves, without a jump, to wherever the nearest such water now is. */
    static final class Voice extends AbstractTickableSoundInstance {
        final Kind kind;
        final long born;
        float target;
        private double tx, ty, tz;

        Voice(Kind kind, BlockPos at, long born) {
            super(kind.event(), SoundSource.AMBIENT, SoundInstance.createUnseededRandom());
            this.kind = kind;
            this.born = born;
            this.looping = true;
            this.delay = 0;
            this.relative = false;
            this.attenuation = SoundInstance.Attenuation.LINEAR;
            this.volume = 0.0001f;
            this.x = tx = at.getX() + 0.5;
            this.y = ty = at.getY() + 0.5;
            this.z = tz = at.getZ() + 0.5;
        }

        void toward(BlockPos at) {
            tx = at.getX() + 0.5;
            ty = at.getY() + 0.5;
            tz = at.getZ() + 0.5;
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }

        @Override
        public void tick() {
            x += (tx - x) * 0.1;
            y += (ty - y) * 0.1;
            z += (tz - z) * 0.1;
            volume += Math.max(-0.03f, Math.min(0.03f, target - volume));
            if (target <= 0f && volume <= 0.0005f) stop();
        }

        float loudness() {
            return volume;
        }
    }
}
