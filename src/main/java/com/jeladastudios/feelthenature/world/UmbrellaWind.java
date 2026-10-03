package com.jeladastudios.feelthenature.world;

/**
 * When the wind turns an open umbrella inside out, apart from the game so it can be tested on its own.
 *
 * <p>From about Beaufort 6 -- 10.8 metres a second, the wind the scale itself describes as "umbrellas used with
 * difficulty" -- a gust can do it, and the harder it blows the sooner: about every half minute at 12, every few
 * seconds at 14 and over. A block is a metre, so the game's wind in blocks a second is metres a second.</p>
 */
public final class UmbrellaWind {

    private UmbrellaWind() {}

    /** The wind, blocks (metres) a second, from which a gust can turn an open umbrella inside out. */
    public static final float INVERTS_FROM = 10.8f;

    /** How likely an open umbrella is to be turned inside out in one tick, in a wind of this many blocks a second. */
    public static float chancePerTick(float wind) {
        if (wind < INVERTS_FROM) return 0f;
        float over = (wind - INVERTS_FROM) / 2f;
        return Math.min(0.05f, 0.004f * over * over);
    }
}
