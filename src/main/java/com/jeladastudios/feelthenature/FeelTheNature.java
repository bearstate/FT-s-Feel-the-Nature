package com.jeladastudios.feelthenature;

import com.jeladastudios.feelthenature.world.ModRegistries;
import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

/**
 * FT's Geology: Feel the Nature. FT's Geology works out why the ground is what it is; this addon is what living on it
 * feels like: the rain heard as it falls where the player stands, the wind in what grows round them, and the country
 * clothed the way its rock and climate would clothe it.
 *
 * <p>Everything here reads FT's Geology and never changes it. The calls into it go through {@link GeologyBridge}, so a
 * change in its internals breaks one file and not the addon.</p>
 */
@Mod(FeelTheNature.MODID)
public final class FeelTheNature {

    public static final String MODID = "fts_feelthenature";
    public static final Logger LOGGER = LogUtils.getLogger();

    public FeelTheNature() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);
        ModRegistries.register(FMLJavaModLoadingContext.get().getModEventBus());
    }
}
