package com.jeladastudios.feelthenature.world;

import com.jeladastudios.feelthenature.GeologyBridge;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementFilter;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;

import java.util.List;

/**
 * Lets a placement through only where FT's Geology's plates give the ground one of the named roles --
 * {@code volcanic_highland}, {@code rift_valley}, ... ({@code FtsGeologyApi.role}). What grows on a volcano goes by the
 * volcano, not by the biome the climate laid over it: a belted volcano's flanks are montane forest and meadow, its
 * ground is still a volcano's. Nothing passes outside FT's Geology's own world.
 */
public class GeologyRoleFilter extends PlacementFilter {

    public static final Codec<GeologyRoleFilter> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.listOf().fieldOf("roles").forGetter(f -> f.roles)
    ).apply(i, GeologyRoleFilter::new));

    private final List<String> roles;

    public GeologyRoleFilter(List<String> roles) {
        this.roles = roles;
    }

    @Override
    protected boolean shouldPlace(PlacementContext context, RandomSource random, BlockPos pos) {
        return roles.contains(GeologyBridge.role(context.getLevel().getLevel(), pos.getX(), pos.getZ()));
    }

    @Override
    public PlacementModifierType<?> type() {
        return ModRegistries.GEOLOGY_ROLE.get();
    }
}
