package net.turtleboi.bytebuddies.events;

import net.minecraft.world.InteractionResult;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.turtleboi.bytebuddies.ByteBuddies;
import net.turtleboi.bytebuddies.item.custom.ClipboardItem;

@Mod.EventBusSubscriber(modid = ByteBuddies.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ModForgeEvents {
    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        var stack = event.getItemStack();
        if (stack.getItem() instanceof ClipboardItem clipboard){
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.CONSUME);

            if (!event.getLevel().isClientSide()) {
                clipboard.handleClick(event.getEntity(), stack, event.getPos());
            }
        }
    }
}
