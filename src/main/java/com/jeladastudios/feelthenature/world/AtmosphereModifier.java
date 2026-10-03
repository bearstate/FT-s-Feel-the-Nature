package com.jeladastudios.feelthenature.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.sounds.Music;
import net.minecraft.world.level.biome.Biome;
import net.minecraftforge.common.world.BiomeModifier;
import net.minecraftforge.common.world.ModifiableBiomeInfo;

import java.util.Optional;

/**
 * What a biome sounds like beyond its weather: the music that comes now and then. Laid over FT's Geology's own biomes
 * from data, {@code data/fts_feelthenature/forge/biome_modifier/}, so its files are never replaced, only added to.
 */
public record AtmosphereModifier(HolderSet<Biome> biomes, Optional<Music> music) implements BiomeModifier {

    public static final Codec<AtmosphereModifier> CODEC = RecordCodecBuilder.create(i -> i.group(
            Biome.LIST_CODEC.fieldOf("biomes").forGetter(AtmosphereModifier::biomes),
            Music.CODEC.optionalFieldOf("music").forGetter(AtmosphereModifier::music)
    ).apply(i, AtmosphereModifier::new));

    @Override
    public void modify(Holder<Biome> biome, Phase phase, ModifiableBiomeInfo.BiomeInfo.Builder builder) {
        if (phase != Phase.MODIFY || !biomes.contains(biome)) return;
        music.ifPresent(m -> builder.getSpecialEffects().backgroundMusic(m));
    }

    @Override
    public Codec<? extends BiomeModifier> codec() {
        return ModRegistries.ATMOSPHERE.get();
    }
}
