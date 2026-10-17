package com.jeladastudios.feelthenature.client.sound;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The creatures keep their hours, their seasons and their places. */
class FaunaTest {

    private static final float EPS = 1e-4f;
    private static final Fauna.Place WOOD = new Fauna.Place(0.6f, 0f, 0.2f, 0f, 0f, 0.5f);
    private static final Fauna.Place PINES = new Fauna.Place(0f, 0.6f, 0.2f, 0f, 0f, 0.5f);
    private static final Fauna.Place MEADOW = new Fauna.Place(0f, 0f, 0.8f, 0.3f, 0f, 1f);
    private static final Fauna.Place POND = new Fauna.Place(0.1f, 0f, 0.4f, 0f, 0.5f, 1f);

    private static Fauna.Moment at(long dayTime) {
        return new Fauna.Moment(dayTime, -1, 0.8f, 0f, 1f);
    }

    @Test
    void birdsByDayNotByNight() {
        assertTrue(Fauna.beds(WOOD, at(6000)).forest() > 0.5f, "no birds in a wood at noon");
        assertTrue(Fauna.beds(PINES, at(6000)).conifer() > 0.5f, "no birds in the pines at noon");
        assertTrue(Fauna.beds(MEADOW, at(6000)).meadow() > 0.5f, "no skylarks over a meadow at noon");
        Fauna.Beds midnight = Fauna.beds(WOOD, at(18000));
        assertEquals(0f, midnight.forest() + midnight.dawnBroadleaf(), EPS, "birds singing at midnight");
    }

    @Test
    void theChorusIsAtDawn() {
        Fauna.Beds dawn = Fauna.beds(WOOD, at(200));
        Fauna.Beds noon = Fauna.beds(WOOD, at(6000));
        assertTrue(dawn.dawnBroadleaf() > 0.5f, "no chorus just after sunrise: " + dawn.dawnBroadleaf());
        assertEquals(0f, noon.dawnBroadleaf(), EPS, "a chorus at noon");
        // In the pines, the conifers' chorus, not the broadleaf wood's.
        Fauna.Beds pines = Fauna.beds(PINES, at(200));
        assertTrue(pines.dawnConifer() > pines.dawnBroadleaf(), "the pines sing a broadleaf chorus");
    }

    @Test
    void cricketsOnWarmNightsOnly() {
        assertTrue(Fauna.beds(MEADOW, at(18000)).crickets() > 0.5f, "no crickets on a warm night");
        assertEquals(0f, Fauna.beds(MEADOW, at(6000)).crickets(), EPS, "crickets at noon");
        assertEquals(0f, Fauna.beds(MEADOW, new Fauna.Moment(18000, -1, 0.2f, 0f, 1f)).crickets(), EPS, "crickets on a cold night");
        assertEquals(0f, Fauna.beds(MEADOW, new Fauna.Moment(18000, 10, 0.8f, 0f, 1f)).crickets(), EPS, "crickets in winter");
        assertEquals(0f, Fauna.beds(MEADOW, new Fauna.Moment(18000, -1, 0.8f, 0.5f, 1f)).crickets(), EPS, "crickets in the rain");
    }

    @Test
    void frogsByStillWater() {
        assertTrue(Fauna.beds(POND, at(14000)).frogs() > 0.4f, "no frogs by a pond in the evening");
        assertEquals(0f, Fauna.beds(MEADOW, at(14000)).frogs(), EPS, "frogs with no water");
        assertEquals(0f, Fauna.beds(POND, at(6000)).frogs(), EPS, "frogs at noon");
        // A light rain does not stop them; a downpour does.
        assertTrue(Fauna.beds(POND, new Fauna.Moment(14000, -1, 0.8f, 0.2f, 1f)).frogs() > 0.3f);
        assertEquals(0f, Fauna.beds(POND, new Fauna.Moment(14000, -1, 0.8f, 0.9f, 1f)).frogs(), EPS);
    }

    @Test
    void beesOverFlowersOnDryDays() {
        assertTrue(Fauna.beds(MEADOW, at(6000)).bees() > 0.3f, "no bees over a flowering meadow");
        assertEquals(0f, Fauna.beds(WOOD, at(6000)).bees(), EPS, "bees with no flowers");
        assertEquals(0f, Fauna.beds(MEADOW, new Fauna.Moment(6000, -1, 0.8f, 0.5f, 1f)).bees(), EPS, "bees in the rain");
    }

    @Test
    void rainAndGalesQuietTheBirds() {
        assertEquals(0f, Fauna.beds(WOOD, new Fauna.Moment(6000, -1, 0.8f, 0.6f, 1f)).forest(), EPS, "birds in a downpour");
        assertEquals(0f, Fauna.beds(WOOD, new Fauna.Moment(6000, -1, 0.8f, 0f, 14f)).forest(), EPS, "birds in a gale");
        assertTrue(Fauna.beds(WOOD, new Fauna.Moment(6000, -1, -0.5f, 0f, 1f)).forest() < 0.05f, "birds on frozen ground");
    }

    @Test
    void callsKeepTheirHoursAndPlaces() {
        assertTrue(Fauna.hawkChance(MEADOW, at(6000), 160) > 0f);
        assertEquals(0f, Fauna.hawkChance(MEADOW, at(18000), 160), EPS, "a hawk at midnight");
        assertTrue(Fauna.owlChance(WOOD, at(18000)) > 0f);
        assertEquals(0f, Fauna.owlChance(WOOD, at(6000)), EPS, "an owl at noon");
        assertEquals(0f, Fauna.owlChance(MEADOW, at(18000)), EPS, "an owl with no wood");
        Fauna.Place lake = new Fauna.Place(0f, 0.1f, 0.1f, 0f, 0.7f, 1f);
        assertTrue(Fauna.loonChance(lake, new Fauna.Moment(14000, -1, 0.5f, 0f, 1f)) > 0f);
        assertEquals(0f, Fauna.loonChance(lake, new Fauna.Moment(14000, -1, 1.0f, 0f, 1f)), EPS, "a loon in the tropics");
        assertEquals(0f, Fauna.loonChance(POND, new Fauna.Moment(6000, -1, 0.5f, 0f, 1f)), EPS, "a loon at noon");
        // Seldom: a hawk at most every few minutes, in the best place.
        assertTrue(Fauna.hawkChance(MEADOW, at(6000), 250) < 1f / 20f / 120f);
    }

    @Test
    void everyBedInRange() {
        Fauna.Place[] places = {WOOD, PINES, MEADOW, POND, new Fauna.Place(1f, 1f, 1f, 1f, 1f, 1f)};
        for (Fauna.Place p : places) {
            for (long t = 0; t < 24000; t += 250) {
                Fauna.Beds b = Fauna.beds(p, new Fauna.Moment(t, 2, 0.9f, 0f, 0f));
                for (float v : new float[]{b.forest(), b.conifer(), b.meadow(), b.dawnBroadleaf(), b.dawnConifer(),
                        b.crickets(), b.frogs(), b.bees()}) {
                    assertTrue(v >= 0f && v <= 1.31f, "a bed at " + v + " at " + t);
                }
            }
        }
    }
}
