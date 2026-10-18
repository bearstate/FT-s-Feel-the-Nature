package com.jeladastudios.feelthenature;

import com.jeladastudios.feelthenature.world.LookoutTowerFeature;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.core.BlockPos;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The addon's commands for players with the right to cheat: {@code /ftn locate lookout} finds the nearest fire lookout,
 * whether the ground it stands on is generated yet or not, the way {@code /locate} finds a structure.
 */
@Mod.EventBusSubscriber(modid = FeelTheNature.MODID)
public final class FtnCommands {

    private FtnCommands() {}

    /** How many of the lookouts' cells out the search goes: 6 cells of 704 blocks, about four thousand blocks. */
    private static final int CELLS = 6;

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("ftn").requires(s -> s.hasPermission(2))
                .then(Commands.literal("locate")
                        .then(Commands.literal("lookout").executes(c -> lookout(c.getSource())))));
    }

    private static int lookout(CommandSourceStack source) {
        BlockPos from = BlockPos.containing(source.getPosition());
        LookoutTowerFeature.Found found = LookoutTowerFeature.locate(source.getLevel(), from, CELLS);
        if (found == null) {
            source.sendFailure(Component.translatable("command.fts_feelthenature.locate.lookout.none"));
            return 0;
        }
        BlockPos at = found.foot();
        int distance = (int) Math.round(Math.hypot(at.getX() - from.getX(), at.getZ() - from.getZ()));
        Component where = ComponentUtils.wrapInSquareBrackets(Component.translatable("chat.coordinates", at.getX(), "~", at.getZ()))
                .withStyle(s -> s.withColor(ChatFormatting.GREEN)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND,
                                "/tp @s " + at.getX() + " " + (at.getY() + 20) + " " + at.getZ()))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.translatable("chat.coordinates.tooltip"))));
        Component message = Component.translatable("command.fts_feelthenature.locate.lookout", where, distance);
        if (found.view() != null) {
            message = message.copy().append(" ").append(Component.translatable("command.fts_feelthenature.locate.lookout.view",
                    name(found.view().name())));
        }
        if (!found.certain()) {
            message = message.copy().append(" ").append(Component.translatable("command.fts_feelthenature.locate.lookout.uncertain")
                    .withStyle(ChatFormatting.GRAY));
        }
        Component out = message;
        source.sendSuccess(() -> out, false);
        return distance;
    }

    private static String name(String id) {
        return switch (id) {
            case "k2" -> "K2";
            case "everest" -> "Everest";
            case "matterhorn" -> "Matterhorn";
            default -> id;
        };
    }
}
