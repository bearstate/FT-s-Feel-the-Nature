package com.jeladastudios.feelthenature.client.sound;

import com.jeladastudios.feelthenature.FeelTheNature;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

/**
 * One recording the soundscape can play, and how loud it is at full strength next to the others. Every recording was
 * levelled to the same loudness when it was cut, so these gains are the whole of the balance between them.
 */
public enum Layer {
    // The rain itself, by how hard it falls.
    RAIN_LIGHT("rain_bed_light", Group.RAIN, 0.55f, true),
    RAIN_MEDIUM("rain_bed_medium", Group.RAIN, 0.75f, true),
    RAIN_HEAVY("rain_bed_heavy", Group.RAIN, 1.0f, true),
    // A rain's beginning after a dry while, played once.
    RAIN_START_LIGHT("rain_start_light", Group.RAIN, 0.55f, false),
    RAIN_START_HEAVY("rain_start_heavy", Group.RAIN, 0.9f, false),
    // A long rain's end, played once as it dies away.
    RAIN_END("rain_end", Group.RAIN, 1.0f, false),
    // What the rain falls on round the player.
    ON_LEAVES("rain_on_leaves", Group.SURFACE, 0.6f, true),
    ON_CONIFERS("rain_on_conifers", Group.SURFACE, 0.5f, true),
    ON_WATER_LIGHT("rain_on_water_light", Group.SURFACE, 0.6f, true),
    ON_WATER_HEAVY("rain_on_water_heavy", Group.SURFACE, 0.7f, true),
    ON_ROCK("rain_on_rock", Group.SURFACE, 0.5f, true),
    // What is over the player's head.
    UNDER_TREE("rain_under_tree", Group.SURFACE, 0.8f, true),
    ROOF_WOOD("rain_roof_wood", Group.SURFACE, 0.8f, true),
    ROOF_METAL("rain_roof_metal", Group.SURFACE, 0.8f, true),
    WINDOW("rain_window", Group.SURFACE, 0.6f, true),
    // An open umbrella in the player's hand: the rain on its cloth, just over the head.
    UMBRELLA_LIGHT("rain_on_umbrella_light", Group.SURFACE, 0.65f, true),
    UMBRELLA_HEAVY("rain_on_umbrella_heavy", Group.SURFACE, 0.8f, true),
    // Water still dripping while the world round is wet.
    DRIPS("drips_loop", Group.DRIP, 0.5f, true),
    // The wind, by how hard it blows, and what it blows through.
    WIND_BREEZE("wind_breeze", Group.WIND, 0.25f, true),
    WIND_STRONG("wind_strong", Group.WIND, 0.5f, true),
    WIND_STORM("wind_storm", Group.WIND, 0.65f, true),
    WIND_ALPINE("wind_alpine", Group.WIND, 0.4f, true),
    WIND_LEAVES("wind_leaves", Group.WIND, 0.3f, true),
    WIND_CONIFERS("wind_conifers", Group.WIND, 0.38f, true),
    WIND_GRASS("wind_grass", Group.WIND, 0.3f, true),
    // The creatures: birds by day in what grows round, the dawn chorus, crickets, frogs and bees.
    BIRDS_FOREST("birds_forest", Group.FAUNA, 0.35f, true),
    BIRDS_CONIFER("birds_conifer", Group.FAUNA, 0.3f, true),
    BIRDS_MEADOW("birds_meadow", Group.FAUNA, 0.3f, true),
    DAWN_BROADLEAF("dawn_broadleaf", Group.FAUNA, 0.4f, true),
    DAWN_CONIFER("dawn_conifer", Group.FAUNA, 0.4f, true),
    CRICKETS("crickets_night", Group.FAUNA, 0.3f, true),
    FROGS("frogs_pond", Group.FAUNA, 0.35f, true),
    BEES("bees_meadow", Group.FAUNA, 0.25f, true);

    public enum Group { RAIN, SURFACE, WIND, DRIP, FAUNA }

    public final ResourceLocation id;
    public final Group group;
    public final float gain;
    public final boolean loop;
    private SoundEvent event;

    Layer(String name, Group group, float gain, boolean loop) {
        this.id = new ResourceLocation(FeelTheNature.MODID, name);
        this.group = group;
        this.gain = gain;
        this.loop = loop;
    }

    public SoundEvent event() {
        if (event == null) event = SoundEvent.createVariableRangeEvent(id);
        return event;
    }

    /** The game's own volume slider each layer answers to. */
    public SoundSource source() {
        return group == Group.DRIP || group == Group.FAUNA ? SoundSource.AMBIENT : SoundSource.WEATHER;
    }
}
