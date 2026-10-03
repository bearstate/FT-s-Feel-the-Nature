package com.jeladastudios.feelthenature.client.life;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The year's small life: fireflies in summer only, pollen in spring, leaves in autumn; no year, no change. */
class SeasonsTest {

    private static final int EARLY_SPRING = 0, LATE_SPRING = 2, MID_SUMMER = 4, MID_AUTUMN = 7, LATE_AUTUMN = 8,
            EARLY_WINTER = 9, LATE_WINTER = 11;

    @Test
    void noYearNoChange() {
        assertEquals(1f, Seasons.leaves(-1));
        assertEquals(1f, Seasons.fireflies(-1));
        assertEquals(1f, Seasons.pollen(-1));
    }

    @Test
    void twelveSubSeasonsEach() {
        assertEquals(12, Seasons.LEAVES.length);
        assertEquals(12, Seasons.FIREFLIES.length);
        assertEquals(12, Seasons.POLLEN.length);
    }

    @Test
    void firefliesOnlyInTheWarmMonths() {
        for (int s = EARLY_WINTER; s <= LATE_WINTER; s++) assertEquals(0f, Seasons.fireflies(s), "fireflies in winter " + s);
        assertEquals(0f, Seasons.fireflies(EARLY_SPRING));
        assertEquals(0f, Seasons.fireflies(MID_AUTUMN));
        assertEquals(1f, Seasons.fireflies(MID_SUMMER));
    }

    @Test
    void leavesFallMostInAutumn() {
        float autumn = Seasons.leaves(MID_AUTUMN);
        for (int s = 0; s < 12; s++) assertTrue(Seasons.leaves(s) <= autumn, "more leaves at " + s + " than in mid autumn");
        assertTrue(Seasons.leaves(MID_AUTUMN) > 2 * Seasons.leaves(MID_SUMMER));
        assertTrue(Seasons.leaves(LATE_WINTER) < Seasons.leaves(MID_SUMMER), "bare crowns in winter");
    }

    @Test
    void pollenIsASpringThing() {
        float spring = Seasons.pollen(LATE_SPRING);
        for (int s = 0; s < 12; s++) assertTrue(Seasons.pollen(s) <= spring, "more pollen at " + s + " than in late spring");
        for (int s = LATE_AUTUMN; s <= LATE_WINTER; s++) assertEquals(0f, Seasons.pollen(s), "pollen at " + s);
    }

    @Test
    void neverNegative() {
        for (int s = 0; s < 12; s++) {
            assertTrue(Seasons.leaves(s) >= 0 && Seasons.fireflies(s) >= 0 && Seasons.pollen(s) >= 0);
        }
    }
}
