package com.jeladastudios.feelthenature.client.sound;

/**
 * When and where the country's creatures are heard, apart from the game so it can be tested on its own: birds by day
 * in what grows round the player, a chorus at dawn, crickets on warm nights, frogs by still water, bees over flowers.
 *
 * <p>The day is the game's: 0 sunrise, 6000 noon, 12000 sunset, 18000 midnight. The year is Serene Seasons' twelve
 * sub-seasons (0 early spring ... 11 late winter), or none, which is summer. Temperatures are the biome's, cooled with
 * height as vanilla cools them: under 0.15 it snows.</p>
 */
public final class Fauna {

    private Fauna() {}

    /** The dawn chorus: from an hour before sunrise, strongest just after it, over an hour and a half later. */
    public static float dawn(long dayTime) {
        float t = hours(dayTime);
        // Hours past sunrise, -1.5 to +2: a quick rise before the sun, a slow fade after.
        float h = t > 20f ? t - 24f : t;
        return smooth((h + 1.2f) / 1f) * (1f - smooth((h - 0.6f) / 1.4f));
    }

    /** Daylight for the birds: from just after sunrise to just before sunset. */
    public static float day(long dayTime) {
        float t = hours(dayTime);
        return smooth((t - 0.3f) / 0.8f) * (1f - smooth((t - 11.4f) / 0.8f));
    }

    /** Night for the crickets and the owls: from dusk to the first light. */
    public static float night(long dayTime) {
        float t = hours(dayTime);
        return smooth((t - 12.2f) / 1f) * (1f - smooth((t - 22.4f) / 0.8f));
    }

    /** Dusk and night for the frogs: they begin before it is quite dark. */
    public static float evening(long dayTime) {
        float t = hours(dayTime);
        return smooth((t - 11.2f) / 1f) * (1f - smooth((t - 22.6f) / 0.8f));
    }

    /** Game hours past sunrise, 0 to 24. */
    static float hours(long dayTime) {
        return Math.floorMod(dayTime, 24000L) / 1000f;
    }

    // The year: index 0 early spring ... 11 late winter.
    /** Birds singing: fewest in winter, a chorus at its height in spring. */
    static final float[] BIRDS = {0.8f, 1f, 1f, 1f, 0.9f, 0.8f, 0.6f, 0.5f, 0.4f, 0.3f, 0.3f, 0.4f};
    static final float[] CHORUS = {0.7f, 1.2f, 1.3f, 1f, 0.8f, 0.6f, 0.3f, 0.2f, 0.1f, 0f, 0f, 0.2f};
    /** Crickets and bees: summer into early autumn, none in winter or early spring. */
    static final float[] INSECTS = {0f, 0.2f, 0.6f, 1f, 1f, 1f, 0.7f, 0.3f, 0f, 0f, 0f, 0f};
    /** Frogs: loudest in spring, quiet by mid autumn. */
    static final float[] FROGS = {0.6f, 1.2f, 1.2f, 1f, 0.8f, 0.6f, 0.3f, 0f, 0f, 0f, 0f, 0.1f};

    static float season(float[] table, int subSeason) {
        return subSeason < 0 || subSeason >= table.length ? 1f : table[subSeason];
    }

    /** A biome's temperature cooled with height, as vanilla cools it over sea level and a bit. */
    public static float temperature(float biome, double y) {
        return biome - (float) Math.max(0.0, y - 80.0) * 0.00125f;
    }

    /** What the place is like round the player, from what the soundscape already gathers. */
    public record Place(float leaves, float conifers, float open, float flowers, float stillWater, float sky) {}

    /** The weather and the year there. */
    public record Moment(long dayTime, int subSeason, float temperature, float rain, float wind) {}

    /**
     * How loud each bed is, 0 to 1, before its own gain: the broadleaf forest's birds, the conifers', a meadow's,
     * the dawn chorus in broadleaf and in conifers, crickets, frogs, bees.
     */
    public record Beds(float forest, float conifer, float meadow, float dawnBroadleaf, float dawnConifer,
                       float crickets, float frogs, float bees) {}

    public static Beds beds(Place p, Moment m) {
        // Birds fall quiet in the rain and in a gale, and on frozen ground.
        float calm = (1f - smooth((m.rain() - 0.03f) / 0.12f)) * (1f - smooth((m.wind() - 6f) / 6f));
        float alive = smooth((m.temperature() + 0.1f) / 0.3f);
        float birds = calm * alive * season(BIRDS, m.subSeason());
        float chorus = calm * alive * season(CHORUS, m.subSeason()) * dawn(m.dayTime());
        float day = day(m.dayTime()) * (1f - 0.7f * Math.min(1f, chorus));
        float trees = Math.min(1f, Mix.share(p.leaves()) + Mix.share(p.conifers()));
        float broad = p.leaves() + p.conifers() <= 0f ? 0.5f : p.leaves() / (p.leaves() + p.conifers());
        float forest = birds * day * Mix.share(p.leaves());
        float conifer = birds * day * Mix.share(p.conifers());
        // Skylarks over open ground under the sky, where there are few trees.
        float meadow = birds * day * Mix.share(p.open()) * p.sky() * (1f - trees);
        float dawnBroadleaf = chorus * trees * broad;
        float dawnConifer = chorus * trees * (1f - broad);

        // Insects want a warm, dry, still night or day; none on cold ground or in winter.
        float warm = smooth((m.temperature() - 0.45f) / 0.3f);
        float dry = 1f - smooth((m.rain() - 0.02f) / 0.12f);
        float still = 1f - smooth((m.wind() - 5f) / 4f);
        float insects = warm * dry * still * season(INSECTS, m.subSeason());
        float crickets = insects * night(m.dayTime()) * Math.min(1f, Mix.share(p.open()) + 0.5f * trees);
        float bees = insects * day(m.dayTime()) * Mix.share(p.flowers()) * p.sky();
        // Frogs by still fresh water on mild evenings; a light rain does not stop them.
        float mild = smooth((m.temperature() - 0.3f) / 0.3f);
        float frogs = mild * (1f - smooth((m.rain() - 0.4f) / 0.3f)) * season(FROGS, m.subSeason())
                * evening(m.dayTime()) * Mix.share(p.stillWater());
        return new Beds(forest, conifer, meadow, dawnBroadleaf, dawnConifer, crickets, frogs, bees);
    }

    /** A hawk's call over open country by day, now and then: the chance in a tick. Every few minutes at most. */
    public static float hawkChance(Place p, Moment m, double y) {
        float calm = (1f - smooth((m.rain() - 0.04f) / 0.2f)) * (1f - smooth((m.wind() - 8f) / 6f));
        float open = p.sky() * (1f - Math.min(1f, p.leaves() + p.conifers()));
        float high = 0.3f + 0.7f * smooth((float) (y - 90) / 80f);
        return calm * day(m.dayTime()) * open * high * season(BIRDS, m.subSeason()) / (20f * 180f);
    }

    /** An owl's hoot in a wood at night: the chance in a tick, about once a minute or two where it is all wood. */
    public static float owlChance(Place p, Moment m) {
        float calm = (1f - smooth((m.rain() - 0.1f) / 0.3f)) * (1f - smooth((m.wind() - 6f) / 6f));
        float wood = Math.min(1f, p.leaves() + p.conifers());
        return calm * night(m.dayTime()) * smooth((wood - 0.15f) / 0.35f) / (20f * 90f);
    }

    /** A loon's call over a large lake in a cool country, at dusk, night or dawn: the chance in a tick. */
    public static float loonChance(Place p, Moment m) {
        float calm = (1f - smooth((m.rain() - 0.2f) / 0.3f)) * (1f - smooth((m.wind() - 5f) / 5f));
        float cool = 1f - smooth((m.temperature() - 0.7f) / 0.2f);
        float hour = Math.max(evening(m.dayTime()), dawn(m.dayTime()));
        float lake = smooth((p.stillWater() - 0.3f) / 0.3f);
        return calm * cool * hour * lake * (m.temperature() > 0.15f ? 1f : 0f) / (20f * 120f);
    }

    static float smooth(float t) {
        return Mix.smooth(t);
    }
}
