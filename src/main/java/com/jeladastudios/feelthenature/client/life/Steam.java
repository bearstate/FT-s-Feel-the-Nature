package com.jeladastudios.feelthenature.client.life;

/**
 * When still water steams: on a calm, clear morning after a cold night, the air over a lake or a slow river colder than
 * the water, which gives up its warmth as wisps of mist rising off the surface and fading a little way up. Thickest
 * just after sunrise, in autumn, when the water still holds the summer's warmth and the nights have turned cold; gone
 * in a wind, which mixes the air, in rain, and in the heat. Where FT's Geology's own ground fog already lies, the fog
 * is the mist, and none is added.
 *
 * <p>Apart from the game so it can be tested on its own.</p>
 */
public final class Steam {

    private Steam() {}

    /** Index: 0 early spring ... 11 late winter (Serene Seasons' sub-seasons). Much of the water is ice in winter. */
    static final float[] SEASON = {0.8f, 0.7f, 0.5f, 0.4f, 0.3f, 0.6f, 1.0f, 1.2f, 1.0f, 0.5f, 0.4f, 0.4f};

    /**
     * How strongly still water steams, 0 to about 1.2.
     *
     * @param dayTime     the time of day, 0 at sunrise
     * @param temperature the air's temperature on the biome scale, height taken in ({@code Fauna.temperature})
     * @param wind        the wind, m/s
     * @param rain        the rain falling, 0 to 1
     * @param fog         FT's Geology's ground fog, 0 to 1
     * @param subSeason   Serene Seasons' sub-season, or -1
     */
    public static float strength(long dayTime, float temperature, float wind, float rain, float fog, int subSeason) {
        if (rain > 0.02f) return 0f;
        // From an hour before sunrise, strongest half an hour after it, gone two and a half hours after.
        long t = Math.floorMod(dayTime + 1000L, 24000L);
        float morning;
        if (t > 3500) return 0f;
        else if (t < 1500) morning = smooth(t / 1500f);
        else morning = 1f - smooth((t - 1500) / 2000f);
        // Air colder than the water: a temperate country's morning will do; none in the hot lands, none where the water is
        // long frozen.
        float cold = (1f - smooth((temperature - 0.85f) / 0.35f)) * smooth((temperature + 0.4f) / 0.3f);
        float calm = 1f - smooth((wind - 1.5f) / 2.5f);
        float clear = 1f - smooth(fog / 0.4f);
        float season = subSeason < 0 || subSeason >= SEASON.length ? 1f : SEASON[subSeason];
        return morning * cold * calm * clear * season;
    }

    static float smooth(float x) {
        float t = Math.max(0f, Math.min(1f, x));
        return t * t * (3f - 2f * t);
    }
}
