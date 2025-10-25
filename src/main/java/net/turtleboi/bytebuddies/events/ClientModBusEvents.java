package net.turtleboi.bytebuddies.events;

import net.minecraft.client.renderer.entity.EntityRenderers;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.turtleboi.bytebuddies.ByteBuddies;
import net.turtleboi.bytebuddies.entity.ModEntities;
import net.turtleboi.bytebuddies.entity.entities.ByteBuddyEntity;
import net.turtleboi.bytebuddies.entity.models.ByteBuddyModel;
import net.turtleboi.bytebuddies.entity.renderers.ByteBuddyRenderer;
import net.turtleboi.bytebuddies.entity.renderers.SwordSweepRenderer;
import net.turtleboi.bytebuddies.init.ModItemProperties;

@EventBusSubscriber(modid = ByteBuddies.MOD_ID, value = Dist.CLIENT)
public class ClientModBusEvents {
    @SubscribeEvent
    public static void onClientSetupEvent(FMLClientSetupEvent event) {
        EntityRenderers.register(ModEntities.BYTEBUDDY.get(), ByteBuddyRenderer::new);
        EntityRenderers.register(ModEntities.SWORD_SWEEP.get(), SwordSweepRenderer::new);
        ModItemProperties.addCustomItemProperties();
    }

    @SubscribeEvent
    public static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(ModEntities.BYTEBUDDY.get(), ByteBuddyEntity.createAttributes().build());
    }

    @SubscribeEvent
    public static void registerEntityLayer(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(ByteBuddyModel.BYTEBUDDY_LAYER, ByteBuddyModel::createBodyLayer);
    }
}
