package com.jeladastudios.feelthenature;

import net.minecraftforge.common.ForgeConfigSpec;

/** What a player may tune for themselves: how loud each part of the soundscape is, and whether it plays at all. */
public final class ClientConfig {

    private ClientConfig() {}

    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue SOUNDSCAPE;
    public static final ForgeConfigSpec.DoubleValue RAIN_VOLUME, SURFACE_VOLUME, WIND_VOLUME, DRIP_VOLUME;
    public static final ForgeConfigSpec.IntValue MAX_LAYERS;
    public static final ForgeConfigSpec.BooleanValue DEBUG_LINE;
    public static final ForgeConfigSpec.BooleanValue AMBIENT_LIFE;
    public static final ForgeConfigSpec.DoubleValue LIFE_DENSITY;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.push("soundscape");
        SOUNDSCAPE = b.comment("Hear the weather where you stand: the rain by what it falls on, the wind by what it blows",
                        "through. Replaces vanilla's rain sound and FT's Geology's own rain and wind recordings.")
                .define("soundscape", true);
        RAIN_VOLUME = b.comment("The rain itself, at full strength. Also scaled by the game's Weather slider.")
                .defineInRange("rainVolume", 1.0, 0.0, 2.0);
        SURFACE_VOLUME = b.comment("The rain on what it falls on: leaves, water, rock, a roof over you.")
                .defineInRange("surfaceVolume", 1.0, 0.0, 2.0);
        WIND_VOLUME = b.comment("The wind, and what it moves: leaves, needles, grass.")
                .defineInRange("windVolume", 1.0, 0.0, 2.0);
        DRIP_VOLUME = b.comment("Water dripping from leaves and roofs after the rain, and trees creaking in a gale.")
                .defineInRange("dripVolume", 1.0, 0.0, 2.0);
        MAX_LAYERS = b.comment("At most this many layers play at once; when more are called for, the quietest give way",
                        "first. Each layer playing holds its recording in memory, a few megabytes.")
                .defineInRange("maxLayers", 8, 2, 16);
        DEBUG_LINE = b.comment("A line on the debug screen (F3) with what the soundscape hears and plays.")
                .define("debugLine", true);
        b.pop();
        b.push("life");
        AMBIENT_LIFE = b.comment("The small life in the air: fireflies on warm dry nights, pollen over flowers by day, leaves",
                        "falling from broadleaf crowns, snow blown off the surface in a strong wind.")
                .define("ambientLife", true);
        LIFE_DENSITY = b.comment("How much of it: 1 is the natural amount, 0.5 half, 2 twice.")
                .defineInRange("lifeDensity", 1.0, 0.0, 3.0);
        b.pop();
        SPEC = b.build();
    }
}
