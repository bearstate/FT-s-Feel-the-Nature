package com.jeladastudios.feelthenature.world;

import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/**
 * An umbrella and a dye make the umbrella in the dye's colour: the same umbrella, its wear kept, so dyeing never mends
 * it. A recipe of its own because a plain shapeless one hands out a new umbrella.
 */
public class UmbrellaDyeRecipe extends CustomRecipe {

    public UmbrellaDyeRecipe(ResourceLocation id, CraftingBookCategory category) {
        super(id, category);
    }

    @Override
    public boolean matches(CraftingContainer grid, Level level) {
        return !assemble(grid, null).isEmpty();
    }

    @Override
    public ItemStack assemble(CraftingContainer grid, RegistryAccess access) {
        ItemStack umbrella = ItemStack.EMPTY;
        DyeColor dye = null;
        for (int i = 0; i < grid.getContainerSize(); i++) {
            ItemStack s = grid.getItem(i);
            if (s.isEmpty()) continue;
            if (s.getItem() instanceof UmbrellaItem && umbrella.isEmpty()) umbrella = s;
            else if (s.getItem() instanceof DyeItem d && dye == null) dye = d.getDyeColor();
            else return ItemStack.EMPTY;
        }
        if (umbrella.isEmpty() || dye == null || ((UmbrellaItem) umbrella.getItem()).color == dye) return ItemStack.EMPTY;
        ItemStack out = new ItemStack(ModItems.UMBRELLAS.get(dye).get());
        if (umbrella.hasTag()) out.setTag(umbrella.getTag().copy());
        UmbrellaItem.setOpen(out, false);
        return out;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 2;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModItems.UMBRELLA_DYE.get();
    }
}
