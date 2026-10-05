package com.jeladastudios.feelthenature.world;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** When the wind turns an umbrella inside out: in a gust, never in a breeze or a lull, sooner the harder it blows. */
class UmbrellaWindTest {

    /** Ticks an umbrella lasts open, on average, in a steady gust. */
    private static float meanTicks(float gust) {
        return 1f / UmbrellaWind.chancePerTick(gust);
    }

    @Test
    void neverUnderAStrongGust() {
        for (float w = 0f; w < UmbrellaWind.INVERTS_FROM; w += 0.1f) {
            assertEquals(0f, UmbrellaWind.chancePerTick(w), "turned over in a gust of " + w);
        }
    }

    @Test
    void harderGustSooner() {
        float last = 0f;
        for (float w = UmbrellaWind.INVERTS_FROM; w <= 30f; w += 0.1f) {
            float c = UmbrellaWind.chancePerTick(w);
            assertTrue(c >= last, "chance fell at gust " + w);
            last = c;
        }
    }

    @Test
    void timesAsDescribed() {
        // About a quarter of a minute's gusting at 16 m/s, a few seconds at 18, and never quicker than a second.
        assertTrue(meanTicks(16f) > 200 && meanTicks(16f) < 800, "at 16: " + meanTicks(16f) + " ticks");
        assertTrue(meanTicks(18f) > 40 && meanTicks(18f) < 200, "at 18: " + meanTicks(18f) + " ticks");
        assertTrue(meanTicks(40f) >= 20, "at 40: " + meanTicks(40f) + " ticks");
    }

    @Test
    void theGustDoesItNotTheMean() {
        // A Beaufort 6 wind: blowing steadily it does not turn an umbrella; its gusts do.
        assertEquals(0f, UmbrellaWind.chancePerTick(12f, 1f));
        assertTrue(UmbrellaWind.chancePerTick(12f, 1.5f) > 0f, "a gust of a Beaufort 6 wind spared it");
        // A gale: in the lull between two gusts the umbrella holds.
        assertEquals(0f, UmbrellaWind.chancePerTick(15f, 0.6f));
        assertTrue(UmbrellaWind.chancePerTick(15f, 1.4f) > UmbrellaWind.chancePerTick(15f, 1.0f));
        // A breeze never, however it gusts.
        assertEquals(0f, UmbrellaWind.chancePerTick(6f, 1.8f));
    }
}
