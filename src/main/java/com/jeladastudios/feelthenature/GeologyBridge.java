package com.jeladastudios.feelthenature;

import com.jeladastudios.ftsgeology.api.FtsGeologyApi;
import net.minecraft.world.level.Level;

/**
 * Every call the addon makes into FT's Geology, in one place. Most go through its API for addons, {@link FtsGeologyApi},
 * whose names and meanings stay put from one version to the next. A few it does not offer yet are called on its insides
 * -- whether a block is its river water, the server's wind and rain at a place, and the river at a column with its
 * sunk stretches told apart -- until its next API version brings them (CEKIRDEK_PROMPT, C11).
 *
 * <p>A newer FT's Geology may still have moved something the addon calls. That would end the game with a linkage
 * error in the middle of a tick, so each part -- the regional weather, the server's wind, the rivers, the world type --
 * is called behind a guard: the first time a call no longer links, the addon says so once in the log and that part
 * falls back to what vanilla offers, for the rest of the session. The rest goes on working.</p>
 */
public final class GeologyBridge {

    private GeologyBridge() {}

    private static volatile boolean weatherLinks = true, serverWindLinks = true, serverRainLinks = true, riversLinks = true,
            worldLinks = true;

    /** Says once that a part of FT's Geology no longer links, and answers false for the part's flag. */
    private static boolean lost(String what, LinkageError e) {
        FeelTheNature.LOGGER.warn("FT's Geology no longer offers {} the way this addon calls it ({}); the addon falls back "
                + "to vanilla's there. An update of the addon for this FT's Geology will bring it back.", what, e.toString());
        return false;
    }

    // === The client: the weather where the player stands ====================================================

    /** Whether FT's Geology tells this client the weather where the player stands, rather than the world's one flag. */
    public static boolean regionalWeather() {
        if (!weatherLinks) return false;
        try {
            return FtsGeologyApi.Client.active();
        } catch (LinkageError e) {
            weatherLinks = lost("its regional weather", e);
            return false;
        }
    }

    /**
     * The rain reaching the player, 0 to 1: FT's Geology's own where it tells it, else the world's. FT's Geology already
     * puts its regional rain into the level's rain level on the client, so the fallback agrees with it.
     */
    public static float rain(Level level, float partial) {
        if (regionalWeather()) {
            try {
                return FtsGeologyApi.Client.rain(partial);
            } catch (LinkageError e) {
                weatherLinks = lost("its regional weather", e);
            }
        }
        return level.getRainLevel(partial);
    }

    /** How hard the rain is coming to, before the easing: a downpour arriving all at once begins as one. */
    public static float rainComing(Level level) {
        if (regionalWeather()) {
            try {
                return FtsGeologyApi.Client.rainComing();
            } catch (LinkageError e) {
                weatherLinks = lost("its regional weather", e);
            }
        }
        return level.getRainLevel(1f);
    }

    public static float thunder(Level level, float partial) {
        if (regionalWeather()) {
            try {
                return FtsGeologyApi.Client.thunder(partial);
            } catch (LinkageError e) {
                weatherLinks = lost("its regional weather", e);
            }
        }
        return level.getThunderLevel(partial);
    }

    /**
     * The wind where the player stands, in blocks a second towards +x and +z. Without FT's Geology's weather the world
     * has no wind of its own, so a light one is made up from its rain and thunder: a breeze, more of it in a storm.
     */
    public static float windX(Level level) {
        if (regionalWeather()) {
            try {
                return FtsGeologyApi.Client.windX();
            } catch (LinkageError e) {
                weatherLinks = lost("its regional weather", e);
            }
        }
        return fallbackWind(level) * 0.8f;
    }

    public static float windZ(Level level) {
        if (regionalWeather()) {
            try {
                return FtsGeologyApi.Client.windZ();
            } catch (LinkageError e) {
                weatherLinks = lost("its regional weather", e);
            }
        }
        return fallbackWind(level) * 0.6f;
    }

    /**
     * How much of its mean strength the wind blows with now, about 0.3 to 1.8, rising and falling over a few seconds:
     * FT's Geology's gusts, the same at the same moment on every client. 1 without its regional weather.
     */
    public static float gust(float partial) {
        if (regionalWeather()) {
            try {
                return FtsGeologyApi.Client.gust(partial);
            } catch (LinkageError e) {
                weatherLinks = lost("its regional weather", e);
            }
        }
        return 1f;
    }

    /**
     * What falls from the sky where the player stands: nothing, rain or snow, by FT's Geology's local weather and the
     * biome; without its regional weather, by the world's rain and the biome.
     */
    public static net.minecraft.world.level.biome.Biome.Precipitation precipitation(Level level,
                                                                                    net.minecraft.core.BlockPos at) {
        if (regionalWeather()) {
            try {
                return switch (FtsGeologyApi.Client.precipitation()) {
                    case RAIN -> net.minecraft.world.level.biome.Biome.Precipitation.RAIN;
                    case SNOW, HAIL -> net.minecraft.world.level.biome.Biome.Precipitation.SNOW;
                    case NONE -> net.minecraft.world.level.biome.Biome.Precipitation.NONE;
                };
            } catch (LinkageError e) {
                weatherLinks = lost("its regional weather", e);
            }
        }
        if (level.getRainLevel(1f) <= 0.01f) return net.minecraft.world.level.biome.Biome.Precipitation.NONE;
        return level.getBiome(at).value().getPrecipitationAt(at);
    }

    // === The server side: FT's Geology's own world, its wind and its rivers ==================================

    /**
     * What FT's Geology's plates make of a column, by name -- {@code volcanic_highland}, {@code rift_valley},
     * {@code geothermal_basin}, ... -- or {@code none}, outside its world too. Cached on its side; cheap enough to ask
     * once a chunk while the world generates.
     */
    public static String role(net.minecraft.server.level.ServerLevel level, int x, int z) {
        if (!worldLinks) return "none";
        try {
            return FtsGeologyApi.role(level, x, z);
        } catch (LinkageError e) {
            worldLinks = lost("its world's plates", e);
            return "none";
        }
    }

    /**
     * The wind over a place on the server, in blocks a second: FT's Geology's own where its regional weather runs (the
     * same wind its clients are told, a hold set with its command included), else the made-up one from the world's rain
     * and thunder, as on the client.
     */
    public static float serverWind(net.minecraft.server.level.ServerLevel level, double x, double z) {
        if (serverWindLinks) {
            try {
                if (com.jeladastudios.ftsgeology.weather.Storms.on(level)) {
                    double[] w = com.jeladastudios.ftsgeology.weather.Atmosphere.wind(level, x, z);
                    return (float) (Math.hypot(w[0], w[1]) * 20.0);
                }
            } catch (LinkageError e) {
                serverWindLinks = lost("its wind", e);
            }
        }
        return fallbackWind(level);
    }

    /**
     * How hard it rains at a place on the server, 0 to 1, by FT's Geology's regional weather; -1 where the rain is not
     * regional (another dimension, or its regional rain turned off), where the world's one weather stands. On the
     * server's own thread only; elsewhere FT's Geology answers with the world's weather.
     */
    public static float rainAt(net.minecraft.server.level.ServerLevel level, int x, int z) {
        if (!serverRainLinks) return -1f;
        try {
            if (!com.jeladastudios.ftsgeology.weather.Storms.on(level)) return -1f;
            return com.jeladastudios.ftsgeology.weather.Storms.intensityAt(level, x, z);
        } catch (LinkageError e) {
            serverRainLinks = lost("its regional rain", e);
            return -1f;
        }
    }

    /** Whether a level is made by FT's Geology's own world type, where its rivers and rock are. */
    public static boolean isGeologyWorld(net.minecraft.server.level.ServerLevel level) {
        if (!worldLinks) return false;
        try {
            return FtsGeologyApi.isGeologyWorld(level);
        } catch (LinkageError e) {
            worldLinks = lost("its world type", e);
            return false;
        }
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
        if (!riversLinks) return null;
        try {
            if (!com.jeladastudios.ftsgeology.hydrology.RiverNetwork.ready()) return null;
            var at = com.jeladastudios.ftsgeology.hydrology.RiverNetwork.at(x, z);
            if (at.distance() == Double.MAX_VALUE || at.sunk()) return null;
            return new River(at.distance(), at.halfWidth(), at.lake());
        } catch (LinkageError e) {
            riversLinks = lost("its river network", e);
            return null;
        }
    }

    /** Whether a block is FT's Geology's river water, which runs down its traced channels. */
    public static boolean isRiverWater(net.minecraft.world.level.block.state.BlockState state) {
        if (!riversLinks) return false;
        try {
            return state.is(com.jeladastudios.ftsgeology.registry.ModBlocks.RIVER_WATER.get());
        } catch (LinkageError e) {
            riversLinks = lost("its river water", e);
            return false;
        }
    }

    /**
     * Whether a block of FT's Geology's river water is running. Its flow is 0 where the water stands still: behind a dam
     * (FT's Water stills water that slows under 0.05 m/s), in a lake. Still river water is as silent as a pond.
     */
    public static boolean isRunningRiverWater(net.minecraft.world.level.block.state.BlockState state) {
        if (!isRiverWater(state)) return false;
        try {
            var fluid = state.getFluidState();
            var flow = com.jeladastudios.ftsgeology.fluid.RiverWaterFluid.FLOW;
            return !fluid.hasProperty(flow) || fluid.getValue(flow) != 0;
        } catch (LinkageError e) {
            riversLinks = lost("its river water's flow", e);
            return true;
        }
    }

    private static float fallbackWind(Level level) {
        return 1.0f + 2.0f * level.getRainLevel(1f) + 5.0f * level.getThunderLevel(1f);
    }
}
