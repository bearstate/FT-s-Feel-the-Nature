package com.jeladastudios.feelthenature.client.sound;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** When a rain "ends": once, after a real rain, as it dies away; never after a shower, never while it wavers. */
class RainSpellTest {

    /** Feeds a run of ticks easing from one rain to another, and counts the endings. */
    private static int run(RainSpell s, float from, float to, int ticks) {
        int ends = 0;
        for (int i = 1; i <= ticks; i++) if (s.tick(from + (to - from) * i / ticks)) ends++;
        return ends;
    }

    @Test
    void aLongRainEndsOnce() {
        RainSpell s = new RainSpell();
        assertEquals(0, run(s, 0f, 0.5f, 200));
        assertEquals(0, run(s, 0.5f, 0.5f, 1500));
        assertEquals(1, run(s, 0.5f, 0f, 400), "a long rain dying away ends once");
        assertEquals(0, run(s, 0f, 0f, 400), "and not again in the dry");
    }

    @Test
    void aShowerDoesNotEnd() {
        RainSpell s = new RainSpell();
        assertEquals(0, run(s, 0f, 0.5f, 100));
        assertEquals(0, run(s, 0.5f, 0.5f, 600));
        assertEquals(0, run(s, 0.5f, 0f, 200), "half a minute of rain is a shower");
    }

    @Test
    void aWaveringRainEndsOnlyWhenItDies() {
        RainSpell s = new RainSpell();
        run(s, 0f, 0.4f, 100);
        run(s, 0.4f, 0.4f, 1300);
        // Dips to a drizzle and comes back: it ends there, once, and a second dip after too short a spell does not.
        assertEquals(1, run(s, 0.4f, 0.1f, 100));
        assertEquals(0, run(s, 0.1f, 0.4f, 100));
        assertEquals(0, run(s, 0.4f, 0.1f, 100));
    }

    @Test
    void risingRainNeverEnds() {
        RainSpell s = new RainSpell();
        run(s, 0f, 0.5f, 100);
        run(s, 0.5f, 0.5f, 1300);
        assertEquals(0, run(s, 0.5f, 0.9f, 400));
    }
}
