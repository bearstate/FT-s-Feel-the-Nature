package com.jeladastudios.feelthenature.client.sound;

/**
 * The soundscape's arithmetic, apart from the game so it can be tested on its own: how the rain's three strengths and the
 * wind's three hand over, and how much of the weather outside reaches the ear.
 */
public final class Mix {

    private Mix() {}

    /**
     * The rain strength over which it is raining to the ear: no bed sounds at or under it, and a rain that rises past it
     * after a dry while begins with the recording of a rain beginning. One number for both, so the beds can never have
     * started before the beginning is played.
     */
    public static final float WET = 0.01f;

    /**
     * How loud the light, steady and heavy rain beds are at a rain strength from 0 to 1, before their own gains. They
     * overlap where they hand over, so no strength of rain falls between two beds and goes quiet.
     */
    public static float[] rainBeds(float rain) {
        float l = smooth((rain - WET) / 0.07f) * (1f - smooth((rain - 0.3f) / 0.2f));
        float m = smooth((rain - 0.25f) / 0.15f) * (1f - smooth((rain - 0.6f) / 0.2f));
        float h = smooth((rain - 0.55f) / 0.2f);
        return new float[]{l, m, h};
    }

    /**
     * How loud the rain on an open umbrella is, a light patter and a heavy drumming, at a rain strength from 0 to 1:
     * the one hands over to the other as the rain grows, and neither sounds before the rain does ({@link #WET}).
     */
    public static float[] umbrellaBeds(float rain) {
        float light = smooth((rain - WET) / 0.07f) * (1f - smooth((rain - 0.4f) / 0.25f));
        float heavy = smooth((rain - 0.3f) / 0.25f);
        return new float[]{light, heavy};
    }

    /**
     * How much rain reaches an umbrella over the head: what falls from the open sky over the player, or, under a crown,
     * the bigger drops gathered off its leaves. None under a roof.
     */
    public static float onUmbrella(float sky, float leavesOver) {
        return Math.max(sky, 0.6f * leavesOver);
    }

    /** How loud the breeze, the strong wind and the storm are at a wind speed in blocks a second, before their gains. */
    public static float[] windBeds(float speed) {
        float breeze = smooth((speed - 0.2f) / 1.0f) * (1f - smooth((speed - 3f) / 2f));
        float strong = smooth((speed - 2f) / 2.5f) * (1f - smooth((speed - 7f) / 3f));
        float storm = smooth((speed - 6f) / 4f);
        return new float[]{breeze, strong, storm};
    }

    /**
     * How a gust (FT's Geology's, about 0.3 to 1.8 of the mean wind) changes the wind's loudness: by much less than its
     * strength, 0.72 to 1.32 -- the ear hears a gust as a swell, not as twice the wind -- and never past that.
     */
    public static float gusting(float gust) {
        float g = Math.max(0.3f, Math.min(1.8f, gust));
        return 0.6f + 0.4f * g;
    }

    /**
     * How much of the weather outside reaches the ear: the open sky over the player, or the open air they can hear
     * through a doorway or a cave's mouth, a little less. Nothing deep in a cave or in a closed room.
     */
    public static float hear(float sky, float openAir) {
        return Math.max(sky, 0.8f * openAir);
    }

    /** A share of the sky covered overhead, as loud as its roof sounds: half a roof over the head is most of its sound. */
    public static float cover(float share) {
        return smooth(share / 0.6f);
    }

    /** A share of the ground, made audible sooner: half the ground under trees is a forest to the ear. */
    public static float share(float s) {
        return Math.min(1f, s * 1.8f);
    }

    /** Smoothstep, clamped to 0..1. */
    public static float smooth(float t) {
        t = Math.max(0f, Math.min(1f, t));
        return t * t * (3f - 2f * t);
    }
}
