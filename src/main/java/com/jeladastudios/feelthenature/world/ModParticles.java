package com.jeladastudios.feelthenature.world;

import com.jeladastudios.feelthenature.FeelTheNature;
import com.mojang.serialization.Codec;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * The small life in the air: fireflies on a warm night, pollen over a meadow by day, a leaf coming down from a crown,
 * mist rising off still water on a cold morning, a mote of dust in a shaft of sun.
 * The types are registered on both sides; how they look and move is client-only ({@code client.life}). They are drawn
 * with the game's own textures -- its glow and dust sprites, and the leaves' own block texture -- not new art, but for the mist's soft puffs.
 */
public final class ModParticles {

    private ModParticles() {}

    public static final DeferredRegister<ParticleType<?>> PARTICLES = DeferredRegister.create(Registries.PARTICLE_TYPE, FeelTheNature.MODID);

    public static final RegistryObject<SimpleParticleType> FIREFLY = PARTICLES.register("firefly", () -> new SimpleParticleType(false));
    public static final RegistryObject<SimpleParticleType> POLLEN = PARTICLES.register("pollen", () -> new SimpleParticleType(false));
    public static final RegistryObject<SimpleParticleType> STEAM = PARTICLES.register("steam", () -> new SimpleParticleType(false));
    public static final RegistryObject<SimpleParticleType> MOTE = PARTICLES.register("mote", () -> new SimpleParticleType(false));

    /** A leaf of a given block, falling: drawn with that block's texture and tinted as the block is. */
    @SuppressWarnings("deprecation")
    public static final RegistryObject<ParticleType<BlockParticleOption>> FALLING_LEAF = PARTICLES.register("falling_leaf",
            () -> new ParticleType<BlockParticleOption>(false, BlockParticleOption.DESERIALIZER) {
                @Override
                public Codec<BlockParticleOption> codec() {
                    return BlockParticleOption.codec(this);
                }
            });
}
