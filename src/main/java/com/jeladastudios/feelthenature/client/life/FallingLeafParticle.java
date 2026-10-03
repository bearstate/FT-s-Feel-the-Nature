package com.jeladastudios.feelthenature.client.life;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A leaf come loose from a crown: a scrap of the leaves' own texture, tinted as the leaves are where it fell from,
 * swinging from side to side as it comes down slowly, carried with the wind, and lying a moment on the ground before it
 * is gone.
 */
public class FallingLeafParticle extends TextureSheetParticle {

    private final float uo, vo, swing, phase;
    private final double windX, windZ;
    private int grounded;

    FallingLeafParticle(ClientLevel level, double x, double y, double z, double windX, double windZ, BlockState state) {
        super(level, x, y, z);
        Minecraft mc = Minecraft.getInstance();
        setSprite(mc.getBlockRenderer().getBlockModelShaper().getParticleIcon(state));
        int tint = mc.getBlockColors().getColor(state, level, BlockPos.containing(x, y, z), 0);
        if (tint != -1) {
            rCol = (tint >> 16 & 255) / 255f;
            gCol = (tint >> 8 & 255) / 255f;
            bCol = (tint & 255) / 255f;
        }
        quadSize = 0.07f + random.nextFloat() * 0.05f;
        lifetime = 400;
        gravity = 0.0035f;
        hasPhysics = true;
        friction = 0.92f;
        uo = random.nextFloat() * 3f;
        vo = random.nextFloat() * 3f;
        swing = 0.015f + random.nextFloat() * 0.02f;
        phase = random.nextFloat() * 6.283f;
        this.windX = windX * 0.008;
        this.windZ = windZ * 0.008;
        yd = -0.01;
    }

    @Override
    public void tick() {
        super.tick();
        if (onGround) {
            xd = zd = 0;
            if (++grounded > 40) remove();
            alpha = Math.max(0f, 1f - grounded / 40f);
            return;
        }
        double s = Math.sin(age * 0.12 + phase);
        xd = s * swing + windX;
        zd = Math.cos(age * 0.09 + phase) * swing * 0.6 + windZ;
        // Falling slowly, a little faster at the bottom of each swing.
        yd = Math.max(yd, -0.045 - Math.abs(s) * 0.01);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.TERRAIN_SHEET;
    }

    // A quarter of the texture, so each leaf is a scrap of foliage and not the whole block face.
    @Override
    protected float getU0() {
        return sprite.getU((uo + 1f) / 4f * 16f);
    }

    @Override
    protected float getU1() {
        return sprite.getU(uo / 4f * 16f);
    }

    @Override
    protected float getV0() {
        return sprite.getV(vo / 4f * 16f);
    }

    @Override
    protected float getV1() {
        return sprite.getV((vo + 1f) / 4f * 16f);
    }

    public static final class Provider implements ParticleProvider<BlockParticleOption> {
        @Override
        public Particle createParticle(BlockParticleOption option, ClientLevel level, double x, double y, double z,
                                       double dx, double dy, double dz) {
            return new FallingLeafParticle(level, x, y, z, dx, dz, option.getState());
        }
    }
}
