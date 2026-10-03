package com.jeladastudios.feelthenature.client.life;

/**
 * How the year changes the small life, by Serene Seasons' twelve sub-seasons, early spring to late winter. Apart from
 * the game so it can be tested on its own; {@link SeasonBridge} asks Serene Seasons which sub-season it is.
 *
 * <ul>
 *   <li>Leaves fall most in mid autumn; a few in summer, fewer in winter, when the broadleaf crowns are bare, and in
 *   spring, the last of the old ones.</li>
 *   <li>Fireflies are out from late spring through summer, a few into early autumn, none the rest of the year.</li>
 *   <li>Pollen begins with the catkins of early spring, is thickest with the flowers of late spring and early summer,
 *   and is gone by late autumn.</li>
 * </ul>
 *
 * <p>Without Serene Seasons there is no year, and everything is as it is in summer: a factor of 1.</p>
 */
public final class Seasons {

    private Seasons() {}

    /** Index: 0 early spring, 1 mid spring, 2 late spring, 3 early summer ... 11 late winter. */
    static final float[] LEAVES = {0.3f, 0.3f, 0.4f, 0.6f, 0.7f, 0.9f, 2.0f, 3.0f, 2.5f, 0.6f, 0.2f, 0.2f};
    static final float[] FIREFLIES = {0f, 0f, 0.5f, 1f, 1f, 0.8f, 0.4f, 0f, 0f, 0f, 0f, 0f};
    static final float[] POLLEN = {0.5f, 1.5f, 2f, 1.5f, 1f, 0.7f, 0.3f, 0.1f, 0f, 0f, 0f, 0f};

    /** The factor for a sub-season, or 1 where there is none (no Serene Seasons, or a tropical biome's year). */
    static float of(float[] table, int subSeason) {
        return subSeason < 0 || subSeason >= table.length ? 1f : table[subSeason];
    }

    public static float leaves(int subSeason) {
        return of(LEAVES, subSeason);
    }

    public static float fireflies(int subSeason) {
        return of(FIREFLIES, subSeason);
    }

    public static float pollen(int subSeason) {
        return of(POLLEN, subSeason);
    }
}
