package com.jeladastudios.feelthenature.client.sound;

import com.jeladastudios.feelthenature.ClientConfig;
import com.jeladastudios.feelthenature.FeelTheNature;
import com.jeladastudios.feelthenature.GeologyBridge;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.CustomizeGuiOverlayEvent;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The weather as it sounds where the player stands, from one place, so that its parts never fight.
 *
 * <p>The rain is a bed in three strengths that hand over as it grows, with a beginning played once after a dry while.
 * Over it lies what the rain falls on round the player -- leaves, needles, water, bare rock -- and what covers the sky
 * over their head: a tree's crown, a wooden or a tin roof, glass, each in the share of the sky it covers. The wind is a
 * bed by how hard it blows, louder in the open and high up, with what it moves laid over it: leaves, needles, grass.
 * Water drips for a minute or two after the rain. All of it is heard only as far as the weather outside reaches the
 * player: in full under the open sky, a little through a doorway or a cave's mouth, not at all deep in a cave.</p>
 *
 * <p>Every layer follows the world continuously; none is switched on or off. The recordings are loaded whole rather than
 * streamed: the sound engine has only eight stream channels and other mods' ambience fills them, which left the rain
 * with none.</p>
 *
 * <p>Vanilla's rain and FT's Geology's own rain and wind recordings are silenced while this plays, so nothing is heard
 * twice.</p>
 */
@Mod.EventBusSubscriber(modid = FeelTheNature.MODID, value = Dist.CLIENT)
public final class Soundscape {

    private Soundscape() {}

    private static final Map<Layer, LayerTrack> TRACKS = new EnumMap<>(Layer.class);
    private static final Map<Layer, Float> TARGETS = new EnumMap<>(Layer.class);
    /** The game tick each layer was last started at, so a layer the engine refuses is not asked for again every tick. */
    private static final Map<Layer, Long> STARTED = new EnumMap<>(Layer.class);
    private static final Surroundings AROUND = new Surroundings();
    private static final RandomSource RANDOM = RandomSource.create();

    private static LayerTrack intro, outro;
    private static int introLength, introHandover;
    private static final RainSpell SPELL = new RainSpell();
    /** The recording of a rain ending, in ticks: 75 seconds. */
    private static final int OUTRO_LENGTH = 1500;
    private static int sinceLook = 99, dryTicks = Integer.MAX_VALUE / 2;
    /** How wet the world round the player still is: the rain, and after it a minute or two of dripping. */
    private static float wet;
    private static float heardRain, heardWind;
    /** Whether the player holds an open umbrella, this tick. */
    private static boolean umbrellaOpen;

    /** How often the surroundings are looked at, in ticks. */
    private static final int LOOK_EVERY = 5;
    /** How fast a layer's volume may move in a tick: from silence to full in about a second and a quarter. */
    private static final float STEP = 0.04f;
    /** A rain after this long dry begins with its recording's own beginning. */
    private static final int DRY_FOR_INTRO = 600;
    /** How long the world stays wet after the rain, in ticks: two minutes. */
    private static final int DRYING = 2400;

    private static final ResourceLocation CREAK = new ResourceLocation(FeelTheNature.MODID, "tree_creak");

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        LocalPlayer player = mc.player;
        if (level == null || player == null || !ClientConfig.SOUNDSCAPE.get()) {
            stopAll();
            return;
        }
        if (mc.isPaused()) return;
        if (++sinceLook >= LOOK_EVERY) {
            AROUND.look(level, player);
            sinceLook = 0;
        }
        TARGETS.clear();

        float rain = AROUND.precipitation == Biome.Precipitation.RAIN ? GeologyBridge.rain(level, 1f) : 0f;
        heardRain = rain;
        float underWater = player.isUnderWater() ? 0.15f : 1f;
        float hear = AROUND.hear();
        umbrellaOpen = com.jeladastudios.feelthenature.world.UmbrellaItem.heldOpen(player);

        long now = level.getGameTime();
        rain(level, now, rain, hear, underWater);
        surfaces(rain, hear, underWater);
        drips(rain, hear, underWater);
        wind(level, player, hear, underWater);
        apply(now);
        WaterVoices.tick(level, player, now, underWater);
        if (TRACE && now % 20 == 0) trace(player);
    }

    // === Trace, for the automated game test =====================================

    /** With {@code -Dfts_feelthenature.trace=true}, one line a second in the log with what is heard and played. */
    private static final boolean TRACE = Boolean.getBoolean("fts_feelthenature.trace");
    /** AmbientSounds' sounds started since the last trace line: none of its rain or wind should be among them. */
    private static final java.util.Set<String> AMBIENT_SEEN = java.util.concurrent.ConcurrentHashMap.newKeySet();

    private static void trace(LocalPlayer player) {
        StringBuilder b = new StringBuilder(String.format(java.util.Locale.ROOT,
                "SOUNDSCAPE_TRACE pos=%d,%d,%d precip=%s rain=%.2f wind=%.2f hear=%.2f sky=%.2f open=%.2f"
                        + " leavesOver=%.2f wood=%.2f metal=%.2f glass=%.2f leaves=%.2f needles=%.2f water=%.2f rock=%.2f grass=%.2f layers=",
                player.getBlockX(), player.getBlockY(), player.getBlockZ(), AROUND.precipitation, heardRain, heardWind,
                AROUND.hear(), AROUND.sky, AROUND.openNear, AROUND.leavesOver, AROUND.woodOver, AROUND.metalOver,
                AROUND.glassOver, AROUND.leaves, AROUND.conifers, AROUND.water, AROUND.rock, AROUND.grass));
        // Each track as name:volume, with a "v" after one fading out: the cap is on the tracks asked to play, and one
        // that lost its place is still heard for the moment it takes to fade.
        if (intro != null) traceTrack(b, intro);
        if (outro != null) traceTrack(b, outro);
        for (LayerTrack t : TRACKS.values()) traceTrack(b, t);
        b.append(" water=").append(WaterVoices.describe());
        boolean holding = player.getMainHandItem().getItem() instanceof com.jeladastudios.feelthenature.world.UmbrellaItem
                || player.getOffhandItem().getItem() instanceof com.jeladastudios.feelthenature.world.UmbrellaItem;
        b.append(" umbrella=").append(umbrellaOpen ? "open" : holding ? "closed" : "none");
        b.append(" life=").append(com.jeladastudios.feelthenature.client.life.AmbientLife.countsAndReset());
        b.append(" ambient=").append(String.join(",", AMBIENT_SEEN));
        AMBIENT_SEEN.clear();
        FeelTheNature.LOGGER.info(b.toString());
    }

    private static void traceTrack(StringBuilder b, LayerTrack t) {
        b.append(t.layer.name().toLowerCase(java.util.Locale.ROOT)).append(':')
                .append(String.format(java.util.Locale.ROOT, "%.2f", t.loudness())).append(t.target <= 0f ? "v," : ",");
    }

    // === The rain ===============================================================

    private static void rain(ClientLevel level, long now, float rain, float hear, float underWater) {
        float gain = hear * underWater * ClientConfig.RAIN_VOLUME.get().floatValue();
        // The strengths hand over as the rain grows: a drizzle, a steady rain, a downpour.
        float[] beds = Mix.rainBeds(rain);
        float l = beds[0], m = beds[1], h = beds[2];

        // A rain after a dry while begins with its recording's own beginning; the beds come in over its last seconds.
        boolean heard = intro != null || TRACKS.containsKey(Layer.RAIN_LIGHT) || TRACKS.containsKey(Layer.RAIN_MEDIUM)
                || TRACKS.containsKey(Layer.RAIN_HEAVY);
        if (rain > Mix.WET && !heard && dryTicks > DRY_FOR_INTRO && gain > 0.02f) {
            boolean hard = GeologyBridge.rainComing(level) >= 0.5f;
            Layer which = hard ? Layer.RAIN_START_HEAVY : Layer.RAIN_START_LIGHT;
            introLength = hard ? 900 : 800;
            introHandover = hard ? 660 : 560;
            intro = new LayerTrack(which, which.gain * gain, STEP, now);
            Minecraft.getInstance().getSoundManager().play(intro);
        }
        dryTicks = rain > Mix.WET ? 0 : dryTicks + 1;
        float hold = 1f;
        if (intro != null) {
            if (intro.isStopped() || now - intro.born > introLength + 40
                    || !Minecraft.getInstance().getSoundManager().isActive(intro) && now - intro.born > 40) {
                intro = null;
            } else {
                hold = smooth((intro.age - introHandover) / (float) (introLength - introHandover));
                intro.target = rain <= Mix.WET ? 0f : intro.layer.gain * gain;
            }
        }
        // A long rain dying away ends with the recording of a rain ending: the last of the beds fade into it over its
        // first seconds, and its drops thin out to the drips that go on after. A rain that comes back cuts it short.
        if (SPELL.tick(rain) && outro == null && gain > 0.02f) {
            outro = new LayerTrack(Layer.RAIN_END, Layer.RAIN_END.gain * gain, STEP, now);
            Minecraft.getInstance().getSoundManager().play(outro);
        }
        if (outro != null) {
            if (outro.isStopped() || now - outro.born > OUTRO_LENGTH + 40
                    || !Minecraft.getInstance().getSoundManager().isActive(outro) && now - outro.born > 40) {
                outro = null;
            } else {
                hold *= 1f - smooth(outro.age / 160f);
                outro.target = rain >= RainSpell.STEADY ? 0f : outro.layer.gain * gain;
            }
        }
        ask(Layer.RAIN_LIGHT, l * gain * hold);
        ask(Layer.RAIN_MEDIUM, m * gain * hold);
        ask(Layer.RAIN_HEAVY, h * gain * hold);
    }

    private static void surfaces(float rain, float hear, float underWater) {
        float g = underWater * ClientConfig.SURFACE_VOLUME.get().floatValue();
        float some = smooth(rain / 0.5f), hard = smooth((rain - 0.4f) / 0.4f);
        // Rain on the ground round the player is heard as far as the outside reaches them.
        float round = hear * some * g;
        ask(Layer.ON_LEAVES, share(AROUND.leaves) * round);
        ask(Layer.ON_CONIFERS, share(AROUND.conifers) * round);
        ask(Layer.ON_WATER_LIGHT, share(AROUND.water) * (1f - hard) * round);
        ask(Layer.ON_WATER_HEAVY, share(AROUND.water) * hard * round);
        ask(Layer.ON_ROCK, share(AROUND.rock) * round);
        // What covers the sky overhead, each in the share of it it covers: a step out from under it is a fade, not a cut.
        float over = some * g;
        ask(Layer.UNDER_TREE, cover(AROUND.leavesOver) * over);
        ask(Layer.ROOF_WOOD, cover(AROUND.woodOver) * over);
        ask(Layer.ROOF_METAL, cover(AROUND.metalOver) * over);
        ask(Layer.WINDOW, cover(AROUND.glassOver) * over);
        // An open umbrella in hand: the rain drums on its cloth just over the head. The rain all round is still heard.
        if (umbrellaOpen) {
            float[] u = Mix.umbrellaBeds(rain);
            float on = Mix.onUmbrella(AROUND.sky, AROUND.leavesOver) * g;
            ask(Layer.UMBRELLA_LIGHT, u[0] * on);
            ask(Layer.UMBRELLA_HEAVY, u[1] * on);
        }
    }

    /** A share of the ground, made audible sooner: half the ground under trees is a forest to the ear. */
    private static float share(float s) {
        return Mix.share(s);
    }

    /** A share of the sky covered overhead: half a roof over the head is most of its sound. */
    private static float cover(float s) {
        return Mix.cover(s);
    }

    private static void drips(float rain, float hear, float underWater) {
        wet = rain > 0.05f ? Math.max(wet, Math.min(1f, rain * 1.5f)) : Math.max(0f, wet - 1f / DRYING);
        float roofs = AROUND.woodOver + AROUND.metalOver + AROUND.glassOver;
        float near = Math.max(share(AROUND.leaves + AROUND.conifers) * hear,
                Math.max(cover(AROUND.leavesOver), 0.7f * cover(roofs)));
        // Heard most as the rain eases off and after it; a downpour drowns them.
        float masked = 1f - smooth((rain - 0.15f) / 0.35f);
        ask(Layer.DRIPS, wet * near * masked * underWater * ClientConfig.DRIP_VOLUME.get().floatValue());
    }

    // === The wind ===============================================================

    private static void wind(ClientLevel level, LocalPlayer player, float hear, float underWater) {
        float wx = GeologyBridge.windX(level), wz = GeologyBridge.windZ(level);
        float speed = Mth.sqrt(wx * wx + wz * wz);
        heardWind = speed;
        float v = underWater * ClientConfig.WIND_VOLUME.get().floatValue();
        // In the open the wind is all round; in a hollow, a room or a cave it is mostly gone. More of it high up.
        float exposed = hear * hear;
        float height = 0.4f + 0.6f * smooth((float) (player.getY() - 70) / 90f);
        float bed = v * exposed * height;
        float[] beds = Mix.windBeds(speed);
        ask(Layer.WIND_BREEZE, bed * beds[0]);
        ask(Layer.WIND_STRONG, bed * beds[1]);
        ask(Layer.WIND_STORM, bed * beds[2]);
        ask(Layer.WIND_ALPINE, v * exposed * smooth((float) (player.getY() - 163) / 100f) * (0.4f + 0.6f * smooth(speed / 4f)));
        // What the wind moves is heard under the canopy as much as in the open; in a closed room or a cave, not at all.
        float reach = Math.min(1f, hear + AROUND.leavesOver);
        float moving = v * smooth((speed - 0.3f) / 2.5f) * reach;
        ask(Layer.WIND_LEAVES, share(AROUND.leaves) * moving);
        ask(Layer.WIND_CONIFERS, share(AROUND.conifers) * moving);
        ask(Layer.WIND_GRASS, share(AROUND.grass) * moving * hear);

        // In a gale a tree creaks now and then: seldom, quiet, and from a tree, not from everywhere.
        float trees = AROUND.leaves + AROUND.conifers;
        if (speed > 4.5f && trees > 0.3f && AROUND.tree != null && reach > 0.3f
                && RANDOM.nextFloat() < (speed - 4.5f) / 6f / 400f) {
            Minecraft.getInstance().getSoundManager().play(new SimpleSoundInstance(CREAK, SoundSource.AMBIENT,
                    0.3f * ClientConfig.DRIP_VOLUME.get().floatValue() * underWater,
                    0.9f + 0.2f * RANDOM.nextFloat(), RANDOM, false, 0, SoundInstance.Attenuation.LINEAR,
                    AROUND.tree.getX() + 0.5, AROUND.tree.getY() + 0.5, AROUND.tree.getZ() + 0.5, false));
        }
    }

    // === Playing ================================================================

    private static void ask(Layer layer, float loudness) {
        TARGETS.put(layer, Math.max(0f, loudness * layer.gain));
    }

    /**
     * Gives every layer its volume. At most the loudest few play at once; the rest fade out and come back when there is
     * room, so a crowded moment loses its quietest details rather than its rain.
     */
    private static void apply(long now) {
        int room = ClientConfig.MAX_LAYERS.get() - (intro != null ? 1 : 0) - (outro != null ? 1 : 0);
        List<Map.Entry<Layer, Float>> wanted = new ArrayList<>();
        for (Map.Entry<Layer, Float> e : TARGETS.entrySet()) if (e.getValue() > 0.002f) wanted.add(e);
        wanted.sort((a, b) -> Float.compare(b.getValue(), a.getValue()));
        var sounds = Minecraft.getInstance().getSoundManager();
        for (int i = 0; i < wanted.size(); i++) {
            Layer layer = wanted.get(i).getKey();
            float v = i < room ? wanted.get(i).getValue() : 0f;
            LayerTrack t = TRACKS.get(layer);
            if (t != null && gone(t, sounds, now)) {
                TRACKS.remove(layer);
                t = null;
            }
            if (t == null && v > 0.002f && now - STARTED.getOrDefault(layer, Long.MIN_VALUE / 2) > 40) {
                t = new LayerTrack(layer, 0f, STEP, now);
                t.target = v;
                sounds.play(t);
                TRACKS.put(layer, t);
                STARTED.put(layer, now);
            } else if (t != null) {
                t.target = v;
            }
        }
        // Layers no longer asked for fade out, and are forgotten once silent -- or once it is clear the engine never
        // started them, which would otherwise leave them on the list for good.
        TRACKS.entrySet().removeIf(e -> {
            Float v = TARGETS.get(e.getKey());
            if (v == null || v <= 0.002f) e.getValue().end();
            return gone(e.getValue(), sounds, now);
        });
    }

    /** Whether a track is over: stopped, or never taken up by the engine after two seconds of asking. */
    private static boolean gone(LayerTrack t, net.minecraft.client.sounds.SoundManager sounds, long now) {
        return t.isStopped() || !sounds.isActive(t) && now - t.born > 40;
    }

    private static void stopAll() {
        var sounds = Minecraft.getInstance().getSoundManager();
        for (LayerTrack t : TRACKS.values()) sounds.stop(t);
        TRACKS.clear();
        if (intro != null) sounds.stop(intro);
        if (outro != null) sounds.stop(outro);
        intro = null;
        outro = null;
        SPELL.reset();
        WaterVoices.stopAll();
        wet = 0f;
        dryTicks = Integer.MAX_VALUE / 2;
    }

    private static float smooth(float t) {
        return Mix.smooth(t);
    }

    // === Hooks ==================================================================

    /** Vanilla's rain and FT's Geology's own recordings are not played while the soundscape plays the weather. */
    @SubscribeEvent
    public static void onPlaySound(PlaySoundEvent event) {
        if (event.getSound() == null) return;
        ResourceLocation id = event.getSound().getLocation();
        if (TRACE && id.getNamespace().equals("ambientsounds")) AMBIENT_SEEN.add(id.getPath());
        if (!ClientConfig.SOUNDSCAPE.get()) return;
        if (id.getNamespace().equals("fts_geology_rain")) {
            event.setSound(null);
        } else if (id.getNamespace().equals("minecraft")
                && (id.getPath().equals("weather.rain") || id.getPath().equals("weather.rain.above"))) {
            event.setSound(null);
        }
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        stopAll();
    }

    /** Two lines on the debug screen (F3): what is heard, and what each layer is doing. */
    @SubscribeEvent
    public static void onDebugText(CustomizeGuiOverlayEvent.DebugText event) {
        Minecraft mc = Minecraft.getInstance();
        if (!mc.options.renderDebug || !ClientConfig.DEBUG_LINE.get() || mc.level == null) return;
        event.getLeft().add(String.format(java.util.Locale.ROOT,
                "Soundscape: rain %.2f wind %.1f hear %.2f (sky %.2f, open air %.2f) over: leaves %.2f wood %.2f metal %.2f glass %.2f wet %.2f",
                heardRain, heardWind, AROUND.hear(), AROUND.sky, AROUND.openNear, AROUND.leavesOver, AROUND.woodOver,
                AROUND.metalOver, AROUND.glassOver, wet));
        event.getLeft().add(String.format(java.util.Locale.ROOT,
                "  ground: leaves %.2f needles %.2f water %.2f rock %.2f grass %.2f",
                AROUND.leaves, AROUND.conifers, AROUND.water, AROUND.rock, AROUND.grass));
        StringBuilder l = new StringBuilder("  playing:");
        if (intro != null) l.append(' ').append(intro.layer.name().toLowerCase(java.util.Locale.ROOT))
                .append(String.format(java.util.Locale.ROOT, " %.2f", intro.loudness()));
        for (LayerTrack t : TRACKS.values()) {
            l.append(' ').append(t.layer.name().toLowerCase(java.util.Locale.ROOT))
                    .append(String.format(java.util.Locale.ROOT, " %.2f", t.loudness()));
        }
        event.getLeft().add(l.toString());
        event.getLeft().add("  water: " + WaterVoices.describe());
    }
}
