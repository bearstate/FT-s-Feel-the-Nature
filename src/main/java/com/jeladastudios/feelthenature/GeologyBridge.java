package com.jeladastudios.feelthenature;

import com.jeladastudios.ftsgeology.api.FtsGeologyApi;
import net.minecraft.world.level.Level;

/**
 * Every call the addon makes into FT's Geology, in one place. Most go through its API for addons, {@link FtsGeologyApi},
 * whose names and meanings stay put from one version to the next; nothing else of FT's Geology is called.
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

    /** How thick FT's Geology's ground fog lies round the player, 0 to 1; 0 without its regional weather. */
    public static float fog() {
        if (regionalWeather()) {
            try {
                return FtsGeologyApi.Client.fog();
            } catch (LinkageError e) {
                weatherLinks = lost("its regional weather", e);
            }
        }
        return 0f;
    }

    /**
     * What falls from the sky where the player stands: nothing, rain or snow, by FT's Geology's local weather and the
     * biome; without its regional weather, by the world's rain and the biome.
     */
    public static net.minecraft.world.level.biome.Biome.Precipitation precipitation(Level level,
                                                                                    net.minecraft.core.BlockPos at) {
        // From the least rain FT's Geology draws: its drizzle's few streaks are seen, so they are heard. Its own answer
        // starts only at 0.01, and a storm's edge under that was drawn falling and heard dry.
        if (rain(level, 1f) <= com.jeladastudios.feelthenature.client.sound.Mix.WET) {
            return net.minecraft.world.level.biome.Biome.Precipitation.NONE;
        }
        if (regionalWeather()) {
            try {
                switch (FtsGeologyApi.Client.precipitation()) {
                    case SNOW:
                        return net.minecraft.world.level.biome.Biome.Precipitation.SNOW;
                    case RAIN:
                    case HAIL:
                        // Hail drums as hard as a downpour; of the two sounds the addon has, it is the rain's.
                        return net.minecraft.world.level.biome.Biome.Precipitation.RAIN;
                    default:
                        // Under its own 0.01: what the biome lets fall, as it would answer over it.
                        break;
                }
            } catch (LinkageError e) {
                weatherLinks = lost("its regional weather", e);
            }
        }
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
     * Whether FT's Geology's regional weather runs in a level on the server: its wind, rain and gusts there rather than
     * the world's one weather. Only the overworld, with its regional rain on.
     */
    private static boolean regionalOnServer(net.minecraft.server.level.ServerLevel level) {
        if (!serverWindLinks) return false;
        try {
            return FtsGeologyApi.weatherActive(level);
        } catch (LinkageError e) {
            serverWindLinks = lost("its regional weather on the server", e);
            return false;
        }
    }

    /**
     * The mean wind over a place on the server, in blocks a second, gusts not counted: FT's Geology's own where its
     * regional weather runs (the same wind its clients are told, a hold set with its command included), else the made-up
     * one from the world's rain and thunder, as on the client.
     */
    public static float serverWind(net.minecraft.server.level.ServerLevel level, double x, double z) {
        if (regionalOnServer(level)) {
            try {
                var w = FtsGeologyApi.windAt(level, x, z);
                return (float) Math.hypot(w.x, w.z);
            } catch (LinkageError e) {
                serverWindLinks = lost("its wind", e);
            }
        }
        return fallbackWind(level);
    }

    /**
     * How much of its mean strength the wind blows with at a place on the server now, about 0.3 to 1.8: the same gust a
     * client standing there hears at the same tick. 1 where the weather is not regional. Server thread.
     */
    public static float serverGust(net.minecraft.server.level.ServerLevel level, double x, double z) {
        if (regionalOnServer(level)) {
            try {
                return FtsGeologyApi.gustAt(level, x, z);
            } catch (LinkageError e) {
                serverWindLinks = lost("its gusts", e);
            }
        }
        return 1f;
    }

    /**
     * How hard it rains at a place on the server, 0 to 1, by FT's Geology's regional weather; -1 where the rain is not
     * regional (another dimension, or its regional rain turned off), where the world's one weather stands. Server thread.
     */
    public static float rainAt(net.minecraft.server.level.ServerLevel level, int x, int z) {
        if (!serverRainLinks || !regionalOnServer(level)) return -1f;
        try {
            return (float) FtsGeologyApi.rainAt(level, x, z);
        } catch (LinkageError e) {
            serverRainLinks = lost("its regional rain", e);
            return -1f;
        }
    }

    /** The height of the snow line over a column, from FT's Geology's climate; out of reach where it cannot say. */
    public static int snowLine(net.minecraft.server.level.ServerLevel level, int x, int z) {
        if (!worldLinks) return Integer.MAX_VALUE;
        try {
            return FtsGeologyApi.snowLine(level, x, z);
        } catch (LinkageError e) {
            worldLinks = lost("its climate", e);
            return Integer.MAX_VALUE;
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

    /**
     * Whether one of FT's Geology's large volcanoes -- strato, shield, caldera, fissure, volcanic island -- has its
     * crater within {@code radius} blocks of a column, extinct ones too. It reads nothing loaded: safe, and the same,
     * while the world generates. Cached on its side; ask once a chunk.
     */
    public static boolean nearVolcano(net.minecraft.server.level.ServerLevel level, int x, int z, int radius) {
        if (!worldLinks) return false;
        try {
            return FtsGeologyApi.nearVolcano(level, x, z, radius, false);
        } catch (LinkageError e) {
            worldLinks = lost("its volcanoes", e);
            return false;
        }
    }

    // FT's Geology's API 3 and 4: the ground, the landmarks, the rock and the falls. Missing from an older core, where
    // each answers as if it did not know (NaN, nothing, plain stone) and the addon goes the old way.
    private static volatile boolean groundLinks = true, rockLinks = true, fallsLinks = true;

    /** The ground the terrain lays at a column, before any river is cut into it; NaN where it is not known. Safe in a feature. */
    public static double groundHeight(net.minecraft.server.level.ServerLevel level, int x, int z) {
        if (!groundLinks) return Double.NaN;
        try {
            return FtsGeologyApi.groundHeight(level, x, z);
        } catch (LinkageError e) {
            groundLinks = lost("its ground", e);
            return Double.NaN;
        }
    }

    /** How far a column's ground stands over the ground {@code reach} blocks round it; NaN where it is not known. */
    public static double prominence(net.minecraft.server.level.ServerLevel level, int x, int z, int reach) {
        if (!groundLinks) return Double.NaN;
        try {
            return FtsGeologyApi.prominence(level, x, z, reach);
        } catch (LinkageError e) {
            groundLinks = lost("its ground", e);
            return Double.NaN;
        }
    }

    /** The world's prevailing wind, a unit vector across the ground; zero where it is not known. */
    public static net.minecraft.world.phys.Vec3 prevailingWind(net.minecraft.server.level.ServerLevel level) {
        if (!groundLinks) return net.minecraft.world.phys.Vec3.ZERO;
        try {
            return FtsGeologyApi.prevailingWind(level);
        } catch (LinkageError e) {
            groundLinks = lost("its prevailing wind", e);
            return net.minecraft.world.phys.Vec3.ZERO;
        }
    }

    /** A named mountain: its name, where its crop's middle stands, and which way its long axis runs. */
    public record Landmark(String name, int x, int z, double bearing) {}

    /** The named mountains of the world; empty where there are none or they are not known. Asked once and kept. */
    public static java.util.List<Landmark> landmarks(net.minecraft.server.level.ServerLevel level) {
        if (!groundLinks) return java.util.List.of();
        try {
            return FtsGeologyApi.landmarks(level).stream().map(l -> new Landmark(l.name(), l.x(), l.z(), l.bearing())).toList();
        } catch (LinkageError e) {
            groundLinks = lost("its landmarks", e);
            return java.util.List.of();
        }
    }

    /** The rock the lithology puts at a point, read through a feature's own region; null where it cannot say. */
    public static net.minecraft.world.level.block.state.BlockState surfaceRock(net.minecraft.world.level.WorldGenLevel region,
                                                                                int x, int y, int z) {
        if (!rockLinks) return null;
        try {
            return FtsGeologyApi.surfaceRock(region, x, y, z);
        } catch (LinkageError e) {
            rockLinks = lost("its rock", e);
            return null;
        }
    }

    /** Where a river falls: the foot of the fall, and the water's level over it and under it. */
    public record Fall(int x, int z, double top, double bottom) {}

    /** The falls starting within a radius whose water drops at least {@code minDrop}; empty where none are known. */
    public static java.util.List<Fall> falls(net.minecraft.server.level.ServerLevel level, int x, int z, int radius, double minDrop) {
        if (!fallsLinks) return java.util.List.of();
        try {
            return FtsGeologyApi.falls(level, x, z, radius, minDrop).stream()
                    .map(f -> new Fall(f.x(), f.z(), f.top(), f.bottom())).toList();
        } catch (LinkageError e) {
            fallsLinks = lost("its falls", e);
            return java.util.List.of();
        }
    }

    /** A river or lake near a column: how far from its channel's middle, how wide it is, and whether it is a lake. */
    public record River(double distance, double halfWidth, boolean lake) {
        /** Blocks out from the edge of the water: negative in the water, zero at its edge. */
        public double fromBank() {
            return distance - halfWidth;
        }
    }

    /**
     * The river or lake nearest a column, within {@code within} blocks past its bed; null where none runs so near, where
     * the river network is not worked out there yet, or where the river runs underground, in a cave under a dry valley:
     * no bank grows by a river nobody sees. Cheap enough to ask of every column while the world generates.
     */
    public static River river(int x, int z, double within) {
        if (!riversLinks) return null;
        try {
            var r = FtsGeologyApi.river(x, z, within);
            if (r.isEmpty() || FtsGeologyApi.riverSunk(x, z, within)) return null;
            return new River(r.get().distance(), r.get().halfWidth(), r.get().lake());
        } catch (LinkageError e) {
            riversLinks = lost("its river network", e);
            return null;
        }
    }

    /** Whether a block is FT's Geology's river water, which runs down its traced channels. */
    public static boolean isRiverWater(net.minecraft.world.level.block.state.BlockState state) {
        if (!riversLinks) return false;
        try {
            return FtsGeologyApi.isRiverWater(state);
        } catch (LinkageError e) {
            riversLinks = lost("its river water", e);
            return false;
        }
    }

    /**
     * From what push river water counts as running. FT's Geology's rule is 5 cm a second; where FT's Water runs the
     * water, the fluid's push is its speed over 1.4 (what carries a swimmer at the water's speed), so 5 cm a second
     * pushes 0.036. FT's Geology's own water pushes 0 standing and 0.5 running, well clear of it either way.
     */
    private static final double RUNNING = 0.05 / 1.4;

    /**
     * Whether a block of FT's Geology's river water is running, on the client: its flow across the ground faster than
     * 5 cm a second. Still where it stands behind a dam or in a lake, or where FT's Water has slowed it; still river
     * water is as silent as a pond. The flow is the fluid's own push ({@code FluidState.getFlow}), which FT's Geology
     * answers for its water, and FT's Water for the water it runs.
     */
    public static boolean isRunningRiverWater(net.minecraft.world.level.BlockGetter level,
                                              net.minecraft.core.BlockPos pos,
                                              net.minecraft.world.level.block.state.BlockState state) {
        if (!isRiverWater(state)) return false;
        return state.getFluidState().getFlow(level, pos).horizontalDistanceSqr() > RUNNING * RUNNING;
    }

    /**
     * The made-up wind where FT's Geology's is not: a breeze under a sky, more of it in rain and thunder; none in a
     * world without a sky of its own (the Nether, the End), as FT's Geology has none there either.
     */
    private static float fallbackWind(Level level) {
        if (!level.dimensionType().hasSkyLight()) return 0f;
        return 1.0f + 2.0f * level.getRainLevel(1f) + 5.0f * level.getThunderLevel(1f);
    }
}
