package net.turtleboi.bytebuddies.events;

import net.minecraft.world.InteractionResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.turtleboi.bytebuddies.ByteBuddies;
import net.turtleboi.bytebuddies.entity.entities.ByteBuddyEntity;
import net.turtleboi.bytebuddies.item.custom.ClipboardItem;

@EventBusSubscriber(modid = ByteBuddies.MOD_ID)
public class ModEvents {
    @SubscribeEvent
    public static void onLivingFall(LivingFallEvent event) {
        if (event.getEntity() instanceof ByteBuddyEntity) {
           event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLivingHurt(LivingDamageEvent.Post event) {
        if (event.getEntity() instanceof ByteBuddyEntity byteBuddy) {
            //ByteBuddies.LOGGER.warn("[ByteBuddies] DAMAGE buddy={} healthNow={}", byteBuddy.getId(), byteBuddy.getHealth());
        }
    }

    @SubscribeEvent
    private static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
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
