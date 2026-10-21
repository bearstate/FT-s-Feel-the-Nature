package com.jeladastudios.feelthenature.world;

import com.jeladastudios.feelthenature.FeelTheNature;
import com.jeladastudios.feelthenature.GeologyBridge;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

import java.util.function.Consumer;

/**
 * An umbrella, in one of the sixteen dye colours: opened and closed with a use. Held open in the rain, the rain is heard
 * on its cloth over the head ({@code client.sound.Soundscape}).
 *
 * <p>A strong wind turns an open umbrella inside out, as it does ({@link UmbrellaWind}). It folds shut and a rib or two
 * bends, which costs it some of its wear; iron nuggets mend it. Put away (out of both hands), it is folded first.</p>
 */
public class UmbrellaItem extends Item {

    private static final String OPEN = "Open";

    public final DyeColor color;

    public UmbrellaItem(DyeColor color, Properties properties) {
        super(properties);
        this.color = color;
    }

    public static boolean isOpen(ItemStack stack) {
        return stack.getItem() instanceof UmbrellaItem && stack.hasTag() && stack.getTag().getBoolean(OPEN);
    }

    public static void setOpen(ItemStack stack, boolean open) {
        if (open) {
            stack.getOrCreateTag().putBoolean(OPEN, true);
        } else if (stack.hasTag()) {
            stack.getTag().remove(OPEN);
            if (stack.getTag().isEmpty()) stack.setTag(null);
        }
    }

    /** Whether someone holds an open umbrella in either hand. */
    public static boolean heldOpen(LivingEntity holder) {
        return isOpen(holder.getMainHandItem()) || isOpen(holder.getOffhandItem());
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        boolean open = !isOpen(stack);
        setOpen(stack, open);
        level.playSound(player, player.getX(), player.getEyeY(), player.getZ(),
                (open ? ModSounds.UMBRELLA_OPEN : ModSounds.UMBRELLA_CLOSE).get(),
                SoundSource.PLAYERS, 0.6f, 0.95f + 0.1f * level.random.nextFloat());
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (level.isClientSide() || !isOpen(stack) || !(entity instanceof LivingEntity holder)) return;
        if (holder.getMainHandItem() != stack && holder.getOffhandItem() != stack) {
            setOpen(stack, false);
            return;
        }
        ServerLevel server = (ServerLevel) level;
        // The wind as it blows now: its mean strength in the gust or the lull of this moment.
        float wind = GeologyBridge.serverWind(server, holder.getX(), holder.getZ())
                * GeologyBridge.serverGust(server, holder.getX(), holder.getZ());
        // Under a roof or deep in a forest's shelter, the gusts do not reach it.
        if (holder.getEyeY() < server.getHeight(Heightmap.Types.MOTION_BLOCKING, holder.getBlockX(), holder.getBlockZ())) {
            wind *= 0.15f;
        }
        if (level.random.nextFloat() >= UmbrellaWind.chancePerTick(wind)) return;
        setOpen(stack, false);
        InteractionHand hand = holder.getMainHandItem() == stack ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
        stack.hurtAndBreak(4 + level.random.nextInt(9), holder, e -> e.broadcastBreakEvent(hand));
        level.playSound(null, holder.getX(), holder.getEyeY(), holder.getZ(), ModSounds.UMBRELLA_INVERT.get(),
                SoundSource.PLAYERS, 0.8f, 0.9f + 0.2f * level.random.nextFloat());
        level.playSound(null, holder.getX(), holder.getEyeY(), holder.getZ(), ModSounds.UMBRELLA_SNAP.get(),
                SoundSource.PLAYERS, 0.7f, 0.95f + 0.1f * level.random.nextFloat());
        if (com.jeladastudios.feelthenature.dev.TestCommands.ON) {
            FeelTheNature.LOGGER.info("UMBRELLA_INVERTED gust={}", String.format(java.util.Locale.ROOT, "%.1f", wind));
        }
    }

    @Override
    public boolean isValidRepairItem(ItemStack stack, ItemStack repair) {
        return repair.is(Items.IRON_NUGGET);
    }

    @Override
    public int getEnchantmentValue() {
        return 0;
    }

    /** Opening and closing it is not taking out another one: no lowering and raising of the hand. */
    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || oldStack.getItem() != newStack.getItem();
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(com.jeladastudios.feelthenature.client.UmbrellaClient.EXTENSIONS);
    }
}
