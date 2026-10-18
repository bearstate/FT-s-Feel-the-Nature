package com.jeladastudios.feelthenature.world;

import com.jeladastudios.feelthenature.FeelTheNature;
import com.mojang.serialization.Codec;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraftforge.common.world.BiomeModifier;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * What the addon adds to the game's registries: its world features, a placement filter, and the biome modifier type
 * that sets atmosphere.
 */
public final class ModRegistries {

    private ModRegistries() {}

    public static final DeferredRegister<Feature<?>> FEATURES = DeferredRegister.create(Registries.FEATURE, FeelTheNature.MODID);
    public static final DeferredRegister<Codec<? extends BiomeModifier>> BIOME_MODIFIERS =
            DeferredRegister.create(ForgeRegistries.Keys.BIOME_MODIFIER_SERIALIZERS, FeelTheNature.MODID);

    public static final RegistryObject<FallenLogFeature> FALLEN_LOG =
            FEATURES.register("fallen_log", () -> new FallenLogFeature(FallenLogFeature.Config.CODEC));
    public static final RegistryObject<BoulderFeature> BOULDER =
            FEATURES.register("boulder", () -> new BoulderFeature(BoulderFeature.Config.CODEC));
    public static final RegistryObject<RiparianFeature> RIPARIAN =
            FEATURES.register("riparian", () -> new RiparianFeature(net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration.CODEC));
    public static final RegistryObject<LookoutTowerFeature> LOOKOUT_TOWER =
            FEATURES.register("lookout_tower", () -> new LookoutTowerFeature(net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration.CODEC));
    public static final RegistryObject<CairnFeature> CAIRN =
            FEATURES.register("cairn", () -> new CairnFeature(net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration.CODEC));
    public static final RegistryObject<ViewBenchFeature> VIEW_BENCH =
            FEATURES.register("view_bench", () -> new ViewBenchFeature(net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration.CODEC));
    public static final RegistryObject<ShelterHutFeature> SHELTER_HUT =
            FEATURES.register("shelter_hut", () -> new ShelterHutFeature(net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration.CODEC));
    public static final RegistryObject<BirdHideFeature> BIRD_HIDE =
            FEATURES.register("bird_hide", () -> new BirdHideFeature(net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration.CODEC));
    public static final RegistryObject<FlagTreeFeature> FLAG_TREE =
            FEATURES.register("flag_tree", () -> new FlagTreeFeature(net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration.CODEC));
    public static final RegistryObject<RescueSignFeature> RESCUE_SIGN =
            FEATURES.register("rescue_sign", () -> new RescueSignFeature(net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration.CODEC));
    public static final RegistryObject<SnowPatchFeature> SNOW_PATCH =
            FEATURES.register("snow_patch", () -> new SnowPatchFeature(net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration.CODEC));
    public static final RegistryObject<BasaltColumnsFeature> BASALT_COLUMNS =
            FEATURES.register("basalt_columns", () -> new BasaltColumnsFeature(net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration.CODEC));

    public static final RegistryObject<Codec<AtmosphereModifier>> ATMOSPHERE =
            BIOME_MODIFIERS.register("atmosphere", () -> AtmosphereModifier.CODEC);

    public static final DeferredRegister<net.minecraft.world.level.levelgen.placement.PlacementModifierType<?>> PLACEMENTS =
            DeferredRegister.create(Registries.PLACEMENT_MODIFIER_TYPE, FeelTheNature.MODID);
    /** A placement only where FT's Geology's plates give the ground a role: what grows on a volcano goes by the volcano. */
    public static final RegistryObject<net.minecraft.world.level.levelgen.placement.PlacementModifierType<GeologyRoleFilter>> GEOLOGY_ROLE =
            PLACEMENTS.register("geology_role", () -> () -> GeologyRoleFilter.CODEC);
    /** A placement only round a large volcano's crater: what an eruption throws out lands round its vent. */
    public static final RegistryObject<net.minecraft.world.level.levelgen.placement.PlacementModifierType<NearVolcanoFilter>> NEAR_VOLCANO =
            PLACEMENTS.register("near_volcano", () -> () -> NearVolcanoFilter.CODEC);

    public static void register(IEventBus bus) {
        FEATURES.register(bus);
        BIOME_MODIFIERS.register(bus);
        PLACEMENTS.register(bus);
        ModParticles.PARTICLES.register(bus);
        ModItems.ITEMS.register(bus);
        ModItems.RECIPES.register(bus);
        ModSounds.SOUNDS.register(bus);
    }
}
