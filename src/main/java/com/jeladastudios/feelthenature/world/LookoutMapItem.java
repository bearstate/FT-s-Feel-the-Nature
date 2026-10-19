package com.jeladastudios.feelthenature.world;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.maps.MapDecoration;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A mountain shelter's map, pinned to its wall, with the nearest fire lookout marked on it: folded until it is used,
 * when it opens into a map of the country round the lookout, the lookout marked with a red cross, as an explorer's map
 * is. Where it points is worked out when the shelter is built, from the lookouts' own siting.
 */
public class LookoutMapItem extends Item {

    /** The scale it opens at: a map four blocks to a pixel, half a kilometre across. */
    private static final byte SCALE = 2;

    public LookoutMapItem(Properties properties) {
        super(properties);
    }

    /** A folded map pointing to a lookout's foot. */
    public static ItemStack to(int x, int z) {
        ItemStack stack = new ItemStack(ModItems.LOOKOUT_MAP.get());
        CompoundTag tag = stack.getOrCreateTag();
        tag.putInt("LookoutX", x);
        tag.putInt("LookoutZ", z);
        return stack;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains("LookoutX")) return InteractionResultHolder.pass(stack);
        if (!(level instanceof ServerLevel server)) return InteractionResultHolder.success(stack);
        int x = tag.getInt("LookoutX"), z = tag.getInt("LookoutZ");
        ItemStack map = MapItem.create(server, x, z, SCALE, true, true);
        MapItem.renderBiomePreviewMap(server, map);
        MapItemSavedData.addTargetDecoration(map, new BlockPos(x, 0, z), "+", MapDecoration.Type.RED_X);
        map.setHoverName(Component.translatable("item.fts_feelthenature.lookout_map"));
        level.playSound(null, player.getX(), player.getY(), player.getZ(), net.minecraft.sounds.SoundEvents.BOOK_PAGE_TURN,
                net.minecraft.sounds.SoundSource.PLAYERS, 0.8f, 1f);
        stack.shrink(1);
        if (stack.isEmpty()) return InteractionResultHolder.consume(map);
        if (!player.getInventory().add(map)) player.drop(map, false);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("item.fts_feelthenature.lookout_map.desc").withStyle(ChatFormatting.GRAY));
    }
}
