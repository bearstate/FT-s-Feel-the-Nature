package com.jeladastudios.feelthenature.client.sound;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The soundscape's arithmetic: no strength of rain or wind falls silent between two beds, and caves stay quiet. */
class MixTest {

    private static final float EPS = 1e-4f;

    private static float power(float[] beds) {
        float p = 0f;
        for (float b : beds) p += b * b;
        return (float) Math.sqrt(p);
    }

    @Test
    void noRainNoBeds() {
        float[] b = Mix.rainBeds(0f);
        assertEquals(0f, power(b), EPS);
    }

    @Test
    void noBedBeforeTheRainBegins() {
        // The recording of a rain beginning starts once the rain passes WET, and only while no bed plays yet: a bed that
        // started under WET would have taken its place. (It did, until the two thresholds were one.)
        for (float r = 0f; r <= Mix.WET; r += 0.0005f) {
            assertEquals(0f, power(Mix.rainBeds(r)), EPS, "a bed sounds at rain " + r + ", under WET");
        }
        assertTrue(power(Mix.rainBeds(Mix.WET + 0.02f)) > 0.01f, "no bed just past WET");
    }

    @Test
    void aStormsEdgeIsHeard() {
        // FT's Geology draws a drizzle's few faint streaks from the first trace of rain: from there on a quiet patter is
        // heard, never louder than a steady rain. (Under 0.01 it was drawn and not heard, 9 Oct.)
        for (float r = 0.007f; r <= 0.08f; r += 0.0005f) {
            float p = power(Mix.rainBeds(r));
            assertTrue(p >= 0.15f, "a drizzle of " + r + " heard only at " + p);
        }
        float last = 0f;
        for (float r = 0f; r <= 1.0001f; r += 0.002f) {
            float h = Mix.rainHeard(r);
            assertTrue(h >= last - EPS && h <= 1f + EPS, "rain heard " + h + " at " + r);
            last = h;
        }
        assertEquals(1f, Mix.rainHeard(0.1f), EPS);
        assertTrue(Mix.rainHeard(0.005f) < 0.3f, "a storm's edge begins as loud as a rain: " + Mix.rainHeard(0.005f));
    }

    @Test
    void umbrellaFollowsTheRain() {
        // Silent before the rain begins, heard through every strength of it, the drumming only growing with it.
        for (float r = 0f; r <= Mix.WET; r += 0.0005f) assertEquals(0f, power(Mix.umbrellaBeds(r)), EPS, "patter at rain " + r);
        float last = -1f;
        for (float r = 0.08f; r <= 1.0001f; r += 0.005f) {
            float[] u = Mix.umbrellaBeds(r);
            assertTrue(power(u) >= 0.7f && power(u) <= 1.45f, "umbrella at rain " + r + ": " + power(u));
            assertTrue(u[1] >= last - EPS, "drumming fell at rain " + r);
            last = u[1];
        }
    }

    @Test
    void gustsSwellTheWindGently() {
        // A swell and a lull round the mean, never silence and never twice as loud, and 1 at the mean wind itself.
        assertEquals(1f, Mix.gusting(1f), EPS);
        float last = 0f;
        for (float g = 0f; g <= 3f; g += 0.05f) {
            float k = Mix.gusting(g);
            assertTrue(k >= 0.7f && k <= 1.35f, "gust " + g + " makes " + k);
            assertTrue(k >= last - EPS, "louder gust heard quieter at " + g);
            last = k;
        }
    }

    @Test
    void noRainOnAnUmbrellaUnderARoof() {
        assertEquals(0f, Mix.onUmbrella(0f, 0f), EPS);
        assertEquals(1f, Mix.onUmbrella(1f, 0f), EPS);
        // Under a crown, the drops gathered off the leaves, fewer than in the open.
        float crown = Mix.onUmbrella(0f, 1f);
        assertTrue(crown > 0.3f && crown < 1f, "under a crown: " + crown);
    }

    @Test
    void everyRainIsHeard() {
        // From a drizzle on, the beds together never dip far: no hole where one hands over to the next.
        for (float r = 0.08f; r <= 1.0001f; r += 0.005f) {
            float p = power(Mix.rainBeds(r));
            assertTrue(p >= 0.7f, "rain " + r + " heard only at " + p);
            assertTrue(p <= 1.45f, "rain " + r + " too loud at " + p);
        }
    }

    @Test
    void heavyRainOnlyGrows() {
        float last = -1f;
        for (float r = 0f; r <= 1.0001f; r += 0.01f) {
            float h = Mix.rainBeds(r)[2];
            assertTrue(h >= last - EPS, "heavy bed fell at rain " + r);
            last = h;
        }
    }

    @Test
    void rainEndsAreTheRightBeds() {
        float[] drizzle = Mix.rainBeds(0.15f);
        assertEquals(1f, drizzle[0], EPS);
        assertEquals(0f, drizzle[2], EPS);
        float[] downpour = Mix.rainBeds(1f);
        assertEquals(0f, downpour[0], EPS);
        assertEquals(1f, downpour[2], EPS);
    }

    @Test
    void stillAirIsQuiet() {
        assertEquals(0f, power(Mix.windBeds(0f)), EPS);
    }

    @Test
    void everyWindIsHeard() {
        for (float s = 1.2f; s <= 15.0001f; s += 0.05f) {
            float p = power(Mix.windBeds(s));
            assertTrue(p >= 0.7f, "wind " + s + " heard only at " + p);
            assertTrue(p <= 1.45f, "wind " + s + " too loud at " + p);
        }
    }

    @Test
    void stormOnlyGrows() {
        float last = -1f;
        for (float s = 0f; s <= 15.0001f; s += 0.05f) {
            float storm = Mix.windBeds(s)[2];
            assertTrue(storm >= last - EPS, "storm fell at wind " + s);
            last = storm;
        }
    }

    @Test
    void caveHearsNothingOpenHearsAll() {
        assertEquals(0f, Mix.hear(0f, 0f), EPS);
        assertEquals(1f, Mix.hear(1f, 0f), EPS);
        assertEquals(1f, Mix.hear(1f, 1f), EPS);
        // A doorway: no sky overhead, some open air within reach.
        float door = Mix.hear(0f, 0.3f);
        assertTrue(door > 0.1f && door < 0.5f, "doorway heard at " + door);
    }

    @Test
    void hearingNeverFallsAsItOpensUp() {
        for (float sky = 0f; sky <= 1.0001f; sky += 0.05f) {
            float last = -1f;
            for (float air = 0f; air <= 1.0001f; air += 0.05f) {
                float h = Mix.hear(sky, air);
                assertTrue(h >= last - EPS);
                assertTrue(h >= 0f && h <= 1f + EPS);
                last = h;
            }
        }
    }

    @Test
    void helpersStayInRange() {
        for (float t = -1f; t <= 2.0001f; t += 0.01f) {
            float s = Mix.smooth(t), c = Mix.cover(t), sh = Mix.share(t);
            assertTrue(s >= 0f && s <= 1f);
            assertTrue(c >= 0f && c <= 1f);
            assertTrue(sh <= 1f);
        }
        assertEquals(1f, Mix.cover(0.6f), EPS);
        assertEquals(0f, Mix.cover(0f), EPS);
    }

    @Test
    void bedsAreContinuous() {
        // No jump between neighbouring strengths: a volume that jumps is heard as a click.
        for (float r = 0f; r < 1f; r += 0.001f) {
            float[] a = Mix.rainBeds(r), b = Mix.rainBeds(r + 0.001f);
            for (int i = 0; i < 3; i++) assertTrue(Math.abs(a[i] - b[i]) < 0.05f, "rain bed " + i + " jumps at " + r);
        }
        for (float s = 0f; s < 15f; s += 0.01f) {
            float[] a = Mix.windBeds(s), b = Mix.windBeds(s + 0.01f);
            for (int i = 0; i < 3; i++) assertTrue(Math.abs(a[i] - b[i]) < 0.05f, "wind bed " + i + " jumps at " + s);
        }
    }
}
