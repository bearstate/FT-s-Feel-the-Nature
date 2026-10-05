package com.jeladastudios.feelthenature.world;

import com.jeladastudios.feelthenature.GeologyBridge;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementFilter;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;

/**
 * Lets a placement through only within {@code radius} blocks of the crater of one of FT's Geology's large volcanoes
 * (strato, shield, caldera, fissure, volcanic island), extinct ones too: what an eruption throws out lands round its
 * vent, whatever the climate has grown over the ground since. The bombs use it at the volcano's own size, not a real
 * one's: FT's Geology's large volcanoes are some 250 blocks to their foot, and a volcano's blocks lie on its cone and
 * apron, thickest towards the summit (its own eruptions throw them some 80 blocks past the rim). Asked once a chunk, from its middle (the radius widened
 * by the chunk's own half), as FT's Geology asks; it reads nothing loaded, so the world comes out the same however it
 * is generated. Nothing passes outside FT's Geology's own world.
 */
public class NearVolcanoFilter extends PlacementFilter {

    public static final Codec<NearVolcanoFilter> CODEC = RecordCodecBuilder.create(i -> i.group(
            ExtraCodecs.POSITIVE_INT.fieldOf("radius").forGetter(f -> f.radius)
    ).apply(i, NearVolcanoFilter::new));

    private final int radius;

    public NearVolcanoFilter(int radius) {
        this.radius = radius;
    }

    @Override
    protected boolean shouldPlace(PlacementContext context, RandomSource random, BlockPos pos) {
        int x = (pos.getX() & ~15) + 8, z = (pos.getZ() & ~15) + 8;
        return GeologyBridge.nearVolcano(context.getLevel().getLevel(), x, z, radius + 16);
    }

    @Override
    public PlacementModifierType<?> type() {
        return ModRegistries.NEAR_VOLCANO.get();
    }
}
