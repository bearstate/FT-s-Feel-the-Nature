package com.jeladastudios.feelthenature.world;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** When the wind turns an umbrella inside out: never in a breeze, sooner the harder it blows, never all at once. */
class UmbrellaWindTest {

    /** Ticks an umbrella lasts open, on average, in a steady wind. */
    private static float meanTicks(float wind) {
        return 1f / UmbrellaWind.chancePerTick(wind);
    }

    @Test
    void neverUnderBeaufortSix() {
        for (float w = 0f; w < UmbrellaWind.INVERTS_FROM; w += 0.1f) {
            assertEquals(0f, UmbrellaWind.chancePerTick(w), "turned over in a wind of " + w);
        }
    }

    @Test
    void harderWindSooner() {
        float last = 0f;
        for (float w = UmbrellaWind.INVERTS_FROM; w <= 20f; w += 0.1f) {
            float c = UmbrellaWind.chancePerTick(w);
            assertTrue(c >= last, "chance fell at wind " + w);
            last = c;
        }
    }

    @Test
    void timesAsDescribed() {
        // About half a minute at 12 m/s, a few seconds at 14, and never quicker than a second.
        assertTrue(meanTicks(12f) > 400 && meanTicks(12f) < 1000, "at 12: " + meanTicks(12f) + " ticks");
        assertTrue(meanTicks(14f) > 60 && meanTicks(14f) < 200, "at 14: " + meanTicks(14f) + " ticks");
        assertTrue(meanTicks(30f) >= 20, "at 30: " + meanTicks(30f) + " ticks");
    }
}
