package com.jeladastudios.feelthenature.world;

import com.jeladastudios.feelthenature.FeelTheNature;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * The sounds the server plays to everyone near: an umbrella opened, an umbrella turned inside out. The soundscape's
 * own layers are the client's alone and are not registered ({@code client.sound.Layer}).
 */
public final class ModSounds {

    private ModSounds() {}

    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, FeelTheNature.MODID);

    public static final RegistryObject<SoundEvent> UMBRELLA_OPEN = register("umbrella_open");
    /** For now the opening's own snap, played lower (sounds.json): no recording of an umbrella blowing out yet. */
    public static final RegistryObject<SoundEvent> UMBRELLA_INVERT = register("umbrella_invert");

    private static RegistryObject<SoundEvent> register(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(FeelTheNature.MODID, name)));
    }
}
