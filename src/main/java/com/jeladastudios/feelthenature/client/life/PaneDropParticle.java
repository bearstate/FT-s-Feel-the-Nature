package com.jeladastudios.feelthenature.client.life;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

/**
 * A raindrop running down a window: it clings a moment where it struck, then slides down the glass, slowly and
 * unevenly, stopping and going, wandering a little to one side, and is gone at the sill. Drawn small and clear, a
 * glint of water held flat against the pane it is on.
 */
public class PaneDropParticle extends TextureSheetParticle {

    /** Which way the glass faces: the drop keeps to its plane, a hair out from it. */
    private final double nx, nz;
    private int clinging;

    PaneDropParticle(ClientLevel level, double x, double y, double z, double nx, double nz, SpriteSet sprites) {
        super(level, x, y, z);
        pickSprite(sprites);
        this.nx = nx;
        this.nz = nz;
        quadSize = 0.05f + random.nextFloat() * 0.04f;
        lifetime = 80 + random.nextInt(80);
        gravity = 0f;
        hasPhysics = false;
        rCol = 0.85f;
        gCol = 0.92f;
        bCol = 1.0f;
        alpha = 0.7f;
        clinging = 5 + random.nextInt(20);
        xd = yd = zd = 0;
    }

    @Override
    public void tick() {
        super.tick();
        if (clinging > 0) {
            clinging--;
            xd = yd = zd = 0;
            return;
        }
        // Down in fits and starts, now and then held up again on the glass.
        if (random.nextFloat() < 0.04f) clinging = 3 + random.nextInt(10);
        yd = -(0.008 + random.nextFloat() * 0.01);
        double sideways = (random.nextFloat() - 0.5f) * 0.004;
        // Sideways along the pane, never off it.
        xd = nz != 0 ? sideways : 0;
        zd = nx != 0 ? sideways : 0;
        float f = (float) age / lifetime;
        alpha = 0.7f * (f > 0.8f ? (1f - f) / 0.2f : 1f);
    }

    /** Lit by the daylight through the glass, not the dim room it is seen from. */
    @Override
    protected int getLightColor(float partialTick) {
        return 0xF000F0;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    public record Provider(SpriteSet sprites) implements ParticleProvider<SimpleParticleType> {
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                                       double dx, double dy, double dz) {
            return new PaneDropParticle(level, x, y, z, dx, dz, sprites);
        }
    }
}
