package com.jeladastudios.feelthenature.client.sound;

/**
 * Follows the rain tick by tick and says when a real rain is dying away: one that fell steadily for a minute or more,
 * now falling back through a drizzle. Then the soundscape plays the recording of a rain ending. A passing shower, or a
 * rain that only wavers, does not end that way. Apart from the game so it can be tested on its own.
 */
public final class RainSpell {

    /** A rain at least this strong ... */
    public static final float STEADY = 0.2f;
    /** ... for at least this many ticks (a minute) ends with the recording of a rain ending ... */
    public static final int RAINED_FOR = 1200;
    /** ... as it falls back through this. */
    public static final float ENDING = 0.12f;

    private int steadyTicks;
    private boolean armed;
    private float last;

    /** Called every tick with the rain heard; true on the one tick a long rain begins to die away. */
    public boolean tick(float rain) {
        boolean falling = rain < last;
        last = rain;
        if (rain >= STEADY) {
            if (++steadyTicks >= RAINED_FOR) armed = true;
            return false;
        }
        steadyTicks = 0;
        if (armed && falling && rain < ENDING) {
            armed = false;
            return true;
        }
        return false;
    }

    public void reset() {
        steadyTicks = 0;
        armed = false;
        last = 0f;
    }
}
