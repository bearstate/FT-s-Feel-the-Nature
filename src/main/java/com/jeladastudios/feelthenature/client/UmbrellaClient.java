package com.jeladastudios.feelthenature.client;

import com.jeladastudios.feelthenature.FeelTheNature;
import com.jeladastudios.feelthenature.world.ModItems;
import com.jeladastudios.feelthenature.world.UmbrellaItem;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * How an umbrella looks: its canopy in its dye's colour over one grey cloth texture, the open or the furled model, and
 * the arm raised to hold it over the head while it is open.
 */
@Mod.EventBusSubscriber(modid = FeelTheNature.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class UmbrellaClient {

    private UmbrellaClient() {}

    /** The hand in front of the chest and a little in, so the shaft stands over the head. */
    private static final HumanoidModel.ArmPose HOLDING_OPEN = HumanoidModel.ArmPose.create("FTN_UMBRELLA_OPEN", false,
            (model, entity, arm) -> {
                ModelPart part = arm == HumanoidArm.RIGHT ? model.rightArm : model.leftArm;
                part.xRot = -1.2f;
                part.yRot = arm == HumanoidArm.RIGHT ? -0.5f : 0.5f;
                part.zRot = 0f;
            });

    public static final IClientItemExtensions EXTENSIONS = new IClientItemExtensions() {
        @Override
        public HumanoidModel.ArmPose getArmPose(LivingEntity entity, InteractionHand hand, ItemStack stack) {
            return UmbrellaItem.isOpen(stack) ? HOLDING_OPEN : null;
        }
    };

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        ResourceLocation open = new ResourceLocation(FeelTheNature.MODID, "open");
        event.enqueueWork(() -> ModItems.UMBRELLAS.values().forEach(item ->
                ItemProperties.register(item.get(), open, (stack, level, entity, seed) -> UmbrellaItem.isOpen(stack) ? 1f : 0f)));
    }

    /** The canopy (tint 0) in the dye's colour; the handle and the metal as they are. */
    @SubscribeEvent
    public static void colours(RegisterColorHandlersEvent.Item event) {
        ModItems.UMBRELLAS.values().forEach(item -> {
            float[] c = item.get().color.getTextureDiffuseColors();
            int rgb = ((int) (c[0] * 255) << 16) | ((int) (c[1] * 255) << 8) | (int) (c[2] * 255);
            event.register((stack, tint) -> tint == 0 ? rgb : -1, item.get());
        });
    }
}
