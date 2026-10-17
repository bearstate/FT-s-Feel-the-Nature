package com.jeladastudios.feelthenature.client.life;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Mist off still water: a calm, cold, clear morning; none in a wind, rain, fog, the heat or the day. */
class SteamTest {

    private static final long SUNRISE = 0, HALF_HOUR_AFTER = 500, NOON = 6000, NIGHT = 18000;
    private static final float COOL = 0.25f, CALM = 0.5f;
    private static final int MID_AUTUMN = 7, MID_SUMMER = 4;

    @Test
    void aCalmColdMorning() {
        assertTrue(Steam.strength(HALF_HOUR_AFTER, COOL, CALM, 0f, 0f, -1) > 0.8f);
    }

    @Test
    void onlyInTheMorning() {
        assertEquals(0f, Steam.strength(NOON, COOL, CALM, 0f, 0f, -1));
        assertEquals(0f, Steam.strength(NIGHT, COOL, CALM, 0f, 0f, -1));
        assertTrue(Steam.strength(SUNRISE, COOL, CALM, 0f, 0f, -1) > 0.3f, "already rising at sunrise");
        assertTrue(Steam.strength(23200, COOL, CALM, 0f, 0f, -1) < Steam.strength(SUNRISE, COOL, CALM, 0f, 0f, -1),
                "thinner before sunrise");
    }

    @Test
    void aWindMixesItAway() {
        assertEquals(0f, Steam.strength(HALF_HOUR_AFTER, COOL, 5f, 0f, 0f, -1), 1e-6);
        assertTrue(Steam.strength(HALF_HOUR_AFTER, COOL, 2.5f, 0f, 0f, -1) < Steam.strength(HALF_HOUR_AFTER, COOL, CALM, 0f, 0f, -1));
    }

    @Test
    void noneInRainFogOrHeat() {
        assertEquals(0f, Steam.strength(HALF_HOUR_AFTER, COOL, CALM, 0.3f, 0f, -1));
        assertEquals(0f, Steam.strength(HALF_HOUR_AFTER, COOL, CALM, 0f, 0.6f, -1), 1e-6);
        assertEquals(0f, Steam.strength(HALF_HOUR_AFTER, 1.5f, CALM, 0f, 0f, -1), 1e-6);
        assertTrue(Steam.strength(HALF_HOUR_AFTER, 0.8f, CALM, 0f, 0f, -1) > 0.8f, "a temperate plain steams");
    }

    @Test
    void noneOverLongFrozenWater() {
        assertEquals(0f, Steam.strength(HALF_HOUR_AFTER, -0.5f, CALM, 0f, 0f, -1), 1e-6);
    }

    @Test
    void thickestInAutumn() {
        assertTrue(Steam.strength(HALF_HOUR_AFTER, COOL, CALM, 0f, 0f, MID_AUTUMN)
                > Steam.strength(HALF_HOUR_AFTER, COOL, CALM, 0f, 0f, MID_SUMMER));
        assertEquals(12, Steam.SEASON.length);
    }
}
