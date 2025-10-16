package net.turtleboi.bytebuddies.events;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.turtleboi.bytebuddies.ByteBuddies;
import net.turtleboi.bytebuddies.entity.entities.ByteBuddyEntity;

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
            ByteBuddies.LOGGER.warn("[ByteBuddies] DAMAGE buddy={} healthNow={}", byteBuddy.getId(), byteBuddy.getHealth());
        }
    }
}
