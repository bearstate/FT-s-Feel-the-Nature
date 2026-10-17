package com.jeladastudios.feelthenature.client.life;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

/**
 * A wisp of mist off still water on a cold morning: faint, wide, rising slowly and spreading as it thins, leaning
 * with what air moves. Drawn with the game's own soft smoke sprites, whitened and nearly clear.
 */
public class SteamParticle extends TextureSheetParticle {

    private final double windX, windZ;
    private final float size, peak;

    SteamParticle(ClientLevel level, double x, double y, double z, double windX, double windZ, SpriteSet sprites) {
        super(level, x, y, z);
        pickSprite(sprites);
        size = 0.45f + random.nextFloat() * 0.45f;
        quadSize = size;
        lifetime = 70 + random.nextInt(60);
        gravity = 0f;
        hasPhysics = false;
        rCol = 0.93f;
        gCol = 0.95f;
        bCol = 1.0f;
        alpha = 0f;
        peak = 0.18f + random.nextFloat() * 0.12f;
        roll = oRoll = random.nextFloat() * (float) (Math.PI * 2);
        this.windX = windX * 0.006;
        this.windZ = windZ * 0.006;
        yd = 0.005 + random.nextFloat() * 0.006;
    }

    @Override
    public void tick() {
        super.tick();
        xd = xd * 0.92 + windX * 0.08 + (random.nextFloat() - 0.5f) * 0.0015f;
        yd = yd * 0.985 + 0.0001;
        zd = zd * 0.92 + windZ * 0.08 + (random.nextFloat() - 0.5f) * 0.0015f;
        oRoll = roll;
        roll += 0.004f;
        float f = (float) age / lifetime;
        quadSize = size * (1f + 0.9f * f);
        // In over the first quarter of its life, out over the rest.
        alpha = peak * (f < 0.25f ? f / 0.25f : (1f - f) / 0.75f);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    public record Provider(SpriteSet sprites) implements ParticleProvider<SimpleParticleType> {
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                                       double dx, double dy, double dz) {
            return new SteamParticle(level, x, y, z, dx, dz, sprites);
        }
    }
}
