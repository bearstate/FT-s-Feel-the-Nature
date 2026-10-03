package com.jeladastudios.feelthenature.mixin;

import com.jeladastudios.feelthenature.GeologyBridge;
import com.jeladastudios.feelthenature.world.UmbrellaItem;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Tough As Nails asks whether a block is out in the rain -- the block a player stands in, to keep them wet and chill
 * them -- and it asks the world's one rain flag. Two answers change:
 *
 * <ul>
 *   <li>FT's Geology's rain is regional, and it keeps the world's flag up while any storm is out anywhere, so Tough As
 *   Nails wetted players standing in the sun: a block where FT's Geology has no rain falling is not out in the rain.</li>
 *   <li>Someone under an open umbrella is not out in the rain either.</li>
 * </ul>
 *
 * <p>Applied only with Tough As Nails installed ({@link CompatPlugin}); the target is named, not linked, so the addon
 * builds and runs without it.</p>
 */
@Pseudo
@Mixin(targets = "toughasnails.temperature.TemperatureHelperImpl", remap = false)
public abstract class TanRainMixin {

    /** FT's Geology's own line between dry and raining ({@code Storms.WET}). */
    private static final float WET = 0.05f;

    @Inject(method = "isExposedToRain", at = @At("HEAD"), cancellable = true, remap = false)
    private static void fts_feelthenature$onlyInTheRain(Level level, BlockPos pos, CallbackInfoReturnable<Boolean> answer) {
        if (level instanceof ServerLevel server) {
            float here = GeologyBridge.rainAt(server, pos.getX(), pos.getZ());
            if (here >= 0f && here < WET) {
                answer.setReturnValue(false);
                return;
            }
        }
        Player player = level.getNearestPlayer(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 1.0, false);
        if (player != null && player.blockPosition().equals(pos) && UmbrellaItem.heldOpen(player)) answer.setReturnValue(false);
    }
}
