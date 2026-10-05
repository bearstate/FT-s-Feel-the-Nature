package com.jeladastudios.feelthenature.world;

/**
 * When the wind turns an open umbrella inside out, apart from the game so it can be tested on its own.
 *
 * <p>It is a gust that does it, not the mean wind: from about 14 metres a second, the gusts that come with a Beaufort 6
 * wind (10.8 to 13.8 m/s, "umbrellas used with difficulty", its gusts a third to a half again as strong). The harder
 * the gust the sooner: a quarter of a minute's gusting at 16, a few seconds at 18 and over; and in the lull between two
 * gusts of a gale an umbrella holds. A block is a metre, so the game's wind in blocks a second is metres a second.</p>
 */
public final class UmbrellaWind {

    private UmbrellaWind() {}

    /** The gust, blocks (metres) a second, from which an open umbrella can be turned inside out. */
    public static final float INVERTS_FROM = 13.9f;

    /**
     * How likely an open umbrella is to be turned inside out in one tick, in a wind of this mean strength blowing with
     * {@code gust} of it now (FT's Geology's gusting, about 0.3 to 1.8; 1 without it).
     */
    public static float chancePerTick(float meanWind, float gust) {
        return chancePerTick(meanWind * gust);
    }

    /** How likely an open umbrella is to be turned inside out in one tick, in a gust of this many blocks a second. */
    public static float chancePerTick(float gustWind) {
        if (gustWind < INVERTS_FROM) return 0f;
        float over = (gustWind - INVERTS_FROM) / 2.5f;
        return Math.min(0.05f, 0.004f * over * over);
    }
}
