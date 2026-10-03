package com.jeladastudios.feelthenature.client.life;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

/**
 * A mote of pollen over a meadow on a sunny day: tiny, pale, drifting with the wind and hardly falling, caught by the
 * light now and then. The wind it drifts with is given when it is made.
 */
public class PollenParticle extends TextureSheetParticle {

    private final double windX, windZ;

    PollenParticle(ClientLevel level, double x, double y, double z, double windX, double windZ, SpriteSet sprites) {
        super(level, x, y, z);
        pickSprite(sprites);
        quadSize = 0.015f + random.nextFloat() * 0.015f;
        lifetime = 200 + random.nextInt(200);
        gravity = 0f;
        hasPhysics = false;
        rCol = 1.0f;
        gCol = 0.95f;
        bCol = 0.62f;
        alpha = 0f;
        this.windX = windX * 0.012;
        this.windZ = windZ * 0.012;
    }

    @Override
    public void tick() {
        super.tick();
        xd = xd * 0.9 + windX * 0.1 + (random.nextFloat() - 0.5f) * 0.002f;
        yd = yd * 0.9 + (random.nextFloat() - 0.52f) * 0.0015f;
        zd = zd * 0.9 + windZ * 0.1 + (random.nextFloat() - 0.5f) * 0.002f;
        float life = Math.min(1f, Math.min(age / 30f, (lifetime - age) / 30f));
        alpha = 0.75f * life;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    public record Provider(SpriteSet sprites) implements ParticleProvider<SimpleParticleType> {
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                                       double dx, double dy, double dz) {
            return new PollenParticle(level, x, y, z, dx, dz, sprites);
        }
    }
}
