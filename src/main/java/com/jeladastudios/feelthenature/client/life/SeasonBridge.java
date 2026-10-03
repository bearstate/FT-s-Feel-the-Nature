package com.jeladastudios.feelthenature.client.life;

import com.jeladastudios.feelthenature.FeelTheNature;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.Method;

/**
 * Serene Seasons' sub-season, if it is installed: read through its API by reflection, so the addon neither needs it to
 * build nor to run. Asked at most once a second, as a season lasts days. A biome Serene Seasons gives a tropical year
 * (a wet and a dry season, no autumn) is left as in summer.
 */
public final class SeasonBridge {

    private SeasonBridge() {}

    private static boolean tried, works;
    private static Method getState, getSubSeason, usesTropical;
    private static int cached = -1;
    private static long cachedAt = -100;

    /** 0 early spring ... 11 late winter, or -1 with no Serene Seasons or in a tropical biome. */
    public static int subSeason(Level level, BlockPos at) {
        if (!tried) link();
        if (!works) return -1;
        long now = level.getGameTime();
        if (now - cachedAt < 20 && now >= cachedAt) return cached;
        cachedAt = now;
        try {
            if ((Boolean) usesTropical.invoke(null, level.getBiome(at))) return cached = -1;
            Object state = getState.invoke(null, level);
            return cached = ((Enum<?>) getSubSeason.invoke(state)).ordinal();
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            works = false;
            FeelTheNature.LOGGER.warn("Serene Seasons' season could not be read ({}); the small life keeps a summer's", e.toString());
            return cached = -1;
        }
    }

    private static void link() {
        tried = true;
        if (!ModList.get().isLoaded("sereneseasons")) return;
        try {
            Class<?> helper = Class.forName("sereneseasons.api.season.SeasonHelper");
            getState = helper.getMethod("getSeasonState", Level.class);
            usesTropical = helper.getMethod("usesTropicalSeasons", net.minecraft.core.Holder.class);
            getSubSeason = Class.forName("sereneseasons.api.season.ISeasonState").getMethod("getSubSeason");
            works = true;
        } catch (ReflectiveOperationException | LinkageError e) {
            FeelTheNature.LOGGER.warn("Serene Seasons is installed but its season API was not found ({}); the small life keeps a summer's", e.toString());
        }
    }
}
