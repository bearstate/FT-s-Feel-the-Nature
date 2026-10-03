package com.jeladastudios.feelthenature.client.sound;

import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;

/**
 * One layer playing: not placed anywhere, its volume eased a tick at a time towards what the soundscape asks of it, so
 * no layer ever starts or stops at once. A layer asked for silence fades out and then lets its channel go.
 */
final class LayerTrack extends AbstractTickableSoundInstance {

    final Layer layer;
    /** The volume asked for, and how far the volume may move towards it in a tick. */
    float target;
    private final float step;
    /** Ticks it has played, and the game tick it was asked to start at (it may never play, if the engine refuses it). */
    int age;
    final long born;

    LayerTrack(Layer layer, float start, float step, long born) {
        super(layer.event(), layer.source(), SoundInstance.createUnseededRandom());
        this.layer = layer;
        this.looping = layer.loop;
        this.delay = 0;
        this.relative = true;
        this.attenuation = SoundInstance.Attenuation.NONE;
        this.volume = Math.max(start, 0.0001f);
        this.target = start;
        this.step = step;
        this.born = born;
    }

    @Override
    public boolean canStartSilent() {
        return true;
    }

    @Override
    public void tick() {
        age++;
        float d = target - volume;
        volume += Math.max(-step, Math.min(step, d));
        if (target <= 0f && volume <= 0.0005f) stop();
    }

    /** Fades out and stops. */
    void end() {
        target = 0f;
    }

    float loudness() {
        return volume;
    }
}
