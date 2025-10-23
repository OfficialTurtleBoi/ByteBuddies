package net.turtleboi.bytebuddies;

import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.turtleboi.bytebuddies.block.ModBlockEntities;
import net.turtleboi.bytebuddies.block.ModBlocks;
import net.turtleboi.bytebuddies.component.ModDataComponents;
import net.turtleboi.bytebuddies.effects.ModEffects;
import net.turtleboi.bytebuddies.entity.ModEntities;
import net.turtleboi.bytebuddies.item.ModCreativeModeTabs;
import net.turtleboi.bytebuddies.item.ModItems;
import net.turtleboi.bytebuddies.particle.ModParticles;
import net.turtleboi.bytebuddies.particle.custom.CyberSweepParticle;
import net.turtleboi.bytebuddies.screen.ModMenuTypes;
import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;

@Mod(ByteBuddies.MOD_ID)
public class ByteBuddies {
    public static final String MOD_ID = "bytebuddies";
    public static final Logger LOGGER = LogUtils.getLogger();
    public ByteBuddies(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::commonSetup);
        NeoForge.EVENT_BUS.register(this);

        ModItems.register(modEventBus);
        ModBlocks.register(modEventBus);
        ModBlockEntities.register(modEventBus);
        ModCreativeModeTabs.register(modEventBus);
        ModEntities.register(modEventBus);
        ModMenuTypes.register(modEventBus);
        ModDataComponents.register(modEventBus);
        modEventBus.addListener(this::addCreative);
        ModEffects.register(modEventBus);
        ModParticles.register(modEventBus);
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    private void commonSetup(FMLCommonSetupEvent event) {

    }

    private void addCreative(BuildCreativeModeTabContentsEvent event) {

    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {

    }
}
