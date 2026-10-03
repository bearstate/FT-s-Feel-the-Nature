package com.jeladastudios.feelthenature.world;

import com.jeladastudios.feelthenature.FeelTheNature;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

import java.util.EnumMap;
import java.util.Map;

/** The addon's items: an umbrella in each of the sixteen dye colours, {@code umbrella_red} and so on. */
@Mod.EventBusSubscriber(modid = FeelTheNature.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ModItems {

    private ModItems() {}

    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, FeelTheNature.MODID);
    public static final DeferredRegister<RecipeSerializer<?>> RECIPES = DeferredRegister.create(Registries.RECIPE_SERIALIZER, FeelTheNature.MODID);

    /** An umbrella re-dyed, its wear kept. */
    public static final RegistryObject<SimpleCraftingRecipeSerializer<UmbrellaDyeRecipe>> UMBRELLA_DYE =
            RECIPES.register("umbrella_dye", () -> new SimpleCraftingRecipeSerializer<>(UmbrellaDyeRecipe::new));

    /** Wear: a dozen or so times turned inside out by the wind before it is past mending. */
    private static final int UMBRELLA_WEAR = 96;

    public static final Map<DyeColor, RegistryObject<UmbrellaItem>> UMBRELLAS = new EnumMap<>(DyeColor.class);

    static {
        for (DyeColor color : DyeColor.values()) {
            UMBRELLAS.put(color, ITEMS.register("umbrella_" + color.getName(),
                    () -> new UmbrellaItem(color, new Item.Properties().durability(UMBRELLA_WEAR))));
        }
    }

    @SubscribeEvent
    public static void tabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() != CreativeModeTabs.TOOLS_AND_UTILITIES) return;
        for (DyeColor color : DyeColor.values()) event.accept(UMBRELLAS.get(color));
    }
}
