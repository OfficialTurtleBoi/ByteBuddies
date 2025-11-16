package net.turtleboi.bytebuddies.events;

import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.particle.SpellParticle;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.world.InteractionResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.turtleboi.bytebuddies.ByteBuddies;
import net.turtleboi.bytebuddies.entity.ModEntities;
import net.turtleboi.bytebuddies.entity.entities.ByteBuddyEntity;
import net.turtleboi.bytebuddies.entity.entities.HologramBuddyEntity;
import net.turtleboi.bytebuddies.entity.models.ByteBuddyModel;
import net.turtleboi.bytebuddies.entity.renderers.ByteBuddyRenderer;
import net.turtleboi.bytebuddies.entity.renderers.HologramBuddyRenderer;
import net.turtleboi.bytebuddies.entity.renderers.SwordSweepRenderer;
import net.turtleboi.bytebuddies.init.ModItemProperties;
import net.turtleboi.bytebuddies.item.custom.ClipboardItem;
import net.turtleboi.bytebuddies.particle.ModParticles;
import net.turtleboi.bytebuddies.screen.ModMenuTypes;
import net.turtleboi.bytebuddies.screen.custom.*;

@Mod.EventBusSubscriber(modid = ByteBuddies.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientModBusEvents {
    @SubscribeEvent
    public static void onClientSetupEvent(FMLClientSetupEvent event) {
        EntityRenderers.register(ModEntities.BYTEBUDDY.get(), ByteBuddyRenderer::new);
        EntityRenderers.register(ModEntities.HOLOBUDDY.get(), HologramBuddyRenderer::new);
        EntityRenderers.register(ModEntities.SWORD_SWEEP.get(), SwordSweepRenderer::new);
        ModItemProperties.addCustomItemProperties();

        event.enqueueWork(() -> {
            MenuScreens.register(ModMenuTypes.BUDDY_MENU.get(), ByteBuddyScreen::new);
            MenuScreens.register(ModMenuTypes.BUDDY_DOUBLE_MENU.get(), ByteBuddyDoubleScreen::new);
            MenuScreens.register(ModMenuTypes.BUDDY_TRIPLE_MENU.get(), ByteBuddyTripleScreen::new);
            MenuScreens.register(ModMenuTypes.DOCKING_STATION_MENU.get(), DockingStationScreen::new);
            MenuScreens.register(ModMenuTypes.GENERATOR_MENU.get(), GeneratorScreen::new);
            MenuScreens.register(ModMenuTypes.SOLAR_PANEL_MENU.get(), SolarPanelScreen::new);
        });
    }

    @SubscribeEvent
    public static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(ModEntities.BYTEBUDDY.get(), ByteBuddyEntity.createAttributes().build());
        event.put(ModEntities.HOLOBUDDY.get(), HologramBuddyEntity.createAttributes().build());
    }

    @SubscribeEvent
    public static void registerEntityLayer(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(ByteBuddyModel.BYTEBUDDY_LAYER, ByteBuddyModel::createBodyLayer);
    }

    @SubscribeEvent
    public static void registerParticleFactories(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModParticles.SUPERCHARGED_PARTICLE.get(), SpellParticle.Provider::new);
    }
}
