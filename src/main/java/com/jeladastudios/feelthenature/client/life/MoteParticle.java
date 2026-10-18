package com.jeladastudios.feelthenature.client.life;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

/**
 * A mote of dust in a shaft of sun under the trees: a speck lit warm white, hanging almost still, turning in the air
 * so it glints and fades and glints again. Drawn at full light, as a mote in the sun is.
 */
public class MoteParticle extends TextureSheetParticle {

    private final float glint;

    MoteParticle(ClientLevel level, double x, double y, double z, SpriteSet sprites) {
        super(level, x, y, z);
        pickSprite(sprites);
        quadSize = 0.025f + random.nextFloat() * 0.02f;
        lifetime = 160 + random.nextInt(160);
        gravity = 0f;
        hasPhysics = false;
        rCol = 1.0f;
        gCol = 0.96f;
        bCol = 0.82f;
        alpha = 0f;
        glint = random.nextFloat() * 6.28f;
    }

    @Override
    public void tick() {
        super.tick();
        xd = xd * 0.9 + (random.nextFloat() - 0.5f) * 0.0012f;
        yd = yd * 0.9 + (random.nextFloat() - 0.5f) * 0.0010f;
        zd = zd * 0.9 + (random.nextFloat() - 0.5f) * 0.0012f;
        float life = Math.min(1f, Math.min(age / 40f, (lifetime - age) / 40f));
        float shimmer = 0.45f + 0.55f * (0.5f + 0.5f * (float) Math.sin(glint + age * 0.12f));
        alpha = 0.8f * life * shimmer;
    }

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
            return new MoteParticle(level, x, y, z, sprites);
        }
    }
}
