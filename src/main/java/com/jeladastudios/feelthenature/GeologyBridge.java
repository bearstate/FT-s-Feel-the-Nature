package com.jeladastudios.feelthenature;

import com.jeladastudios.ftsgeology.weather.LocalWeather;
import net.minecraft.world.level.Level;

/**
 * Every call the addon makes into FT's Geology, in one place. Its internals move as it grows; when they do, this file is
 * the one that changes. Once FT's Geology has a public API for addons these calls move onto it.
 */
public final class GeologyBridge {

    private GeologyBridge() {}

    /** Whether FT's Geology tells this client the weather where the player stands, rather than the world's one flag. */
    public static boolean regionalWeather() {
        return LocalWeather.active();
    }

    /**
     * The rain reaching the player, 0 to 1: FT's Geology's own where it tells it, else the world's. FT's Geology already
     * puts its regional rain into the level's rain level on the client, so the fallback agrees with it.
     */
    public static float rain(Level level, float partial) {
        return regionalWeather() ? LocalWeather.rain(partial) : level.getRainLevel(partial);
    }

    /** How hard the rain is coming to, before the easing: a downpour arriving all at once begins as one. */
    public static float rainComing(Level level) {
        return regionalWeather() ? LocalWeather.rainComing() : level.getRainLevel(1f);
    }

    public static float thunder(Level level, float partial) {
        return regionalWeather() ? LocalWeather.thunder(partial) : level.getThunderLevel(partial);
    }

    /**
     * The wind where the player stands, in blocks a second towards +x and +z. Without FT's Geology's weather the world
     * has no wind of its own, so a light one is made up from its rain and thunder: a breeze, more of it in a storm.
     */
    public static float windX(Level level) {
        return regionalWeather() ? LocalWeather.windX() : fallbackWind(level) * 0.8f;
    }

    public static float windZ(Level level) {
        return regionalWeather() ? LocalWeather.windZ() : fallbackWind(level) * 0.6f;
    }

    // === The server side: FT's Geology's own world and its rivers ===========================================

    /** Whether a level is made by FT's Geology's own world type, where its rivers and rock are. */
    public static boolean isGeologyWorld(net.minecraft.server.level.ServerLevel level) {
        return com.jeladastudios.ftsgeology.worldgen.terrain.GeologyWorld.isOwn(level);
    }

    /** A river or lake near a column: how far from its channel's middle, how wide it is, and whether it is a lake. */
    public record River(double distance, double halfWidth, boolean lake) {
        /** Blocks out from the edge of the water: negative in the water, zero at its edge. */
        public double fromBank() {
            return distance - halfWidth;
        }
    }

    /** The river nearest a column, or null where none runs near or the river network is not worked out yet. */
    public static River river(int x, int z) {
        if (!com.jeladastudios.ftsgeology.hydrology.RiverNetwork.ready()) return null;
        var at = com.jeladastudios.ftsgeology.hydrology.RiverNetwork.at(x, z);
        if (at.distance() == Double.MAX_VALUE || at.sunk()) return null;
        return new River(at.distance(), at.halfWidth(), at.lake());
    }

    /** Whether a block is FT's Geology's river water, which runs down its traced channels. */
    public static boolean isRiverWater(net.minecraft.world.level.block.state.BlockState state) {
        return state.is(com.jeladastudios.ftsgeology.registry.ModBlocks.RIVER_WATER.get());
    }

    private static float fallbackWind(Level level) {
        return 1.0f + 2.0f * level.getRainLevel(1f) + 5.0f * level.getThunderLevel(1f);
    }
}
