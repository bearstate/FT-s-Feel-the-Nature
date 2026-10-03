package com.jeladastudios.feelthenature.client.life;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

/**
 * A firefly: a yellow-green point that wanders slowly a little over the grass and flashes on and off, each to its own
 * rhythm, as the males of most species do over a summer meadow. It glows in the dark, so it is drawn at full light.
 */
public class FireflyParticle extends TextureSheetParticle {

    private final float phase, rate;

    FireflyParticle(ClientLevel level, double x, double y, double z, SpriteSet sprites) {
        super(level, x, y, z);
        pickSprite(sprites);
        quadSize = 0.045f + random.nextFloat() * 0.03f;
        lifetime = 160 + random.nextInt(200);
        gravity = 0f;
        hasPhysics = false;
        rCol = 0.78f;
        gCol = 1.0f;
        bCol = 0.32f;
        phase = random.nextFloat() * 6.283f;
        rate = 0.10f + random.nextFloat() * 0.08f;
        xd = (random.nextFloat() - 0.5f) * 0.02f;
        yd = (random.nextFloat() - 0.5f) * 0.01f;
        zd = (random.nextFloat() - 0.5f) * 0.02f;
        alpha = 0f;
    }

    @Override
    public void tick() {
        super.tick();
        // A slow, wavering flight.
        xd = (xd + (random.nextFloat() - 0.5f) * 0.006f) * 0.95f;
        yd = (yd + (random.nextFloat() - 0.5f) * 0.004f) * 0.95f;
        zd = (zd + (random.nextFloat() - 0.5f) * 0.006f) * 0.95f;
        // A flash: dark most of the time, a soft glow for a moment, fading in and out over the first and last seconds.
        float flash = (float) Math.max(0.0, Math.sin(age * rate + phase));
        float life = Math.min(1f, Math.min(age / 20f, (lifetime - age) / 20f));
        alpha = (0.08f + 0.92f * flash * flash * flash) * life;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Override
    protected int getLightColor(float partialTick) {
        return 0xF000F0;
    }

    public record Provider(SpriteSet sprites) implements ParticleProvider<SimpleParticleType> {
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                                       double dx, double dy, double dz) {
            return new FireflyParticle(level, x, y, z, sprites);
        }
    }
}
