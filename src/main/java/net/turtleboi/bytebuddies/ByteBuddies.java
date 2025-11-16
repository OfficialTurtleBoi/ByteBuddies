package net.turtleboi.bytebuddies;

import com.mojang.logging.LogUtils;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.turtleboi.bytebuddies.block.ModBlockEntities;
import net.turtleboi.bytebuddies.block.ModBlocks;
import net.turtleboi.bytebuddies.effects.ModEffects;
import net.turtleboi.bytebuddies.entity.ModEntities;
import net.turtleboi.bytebuddies.item.ModCreativeModeTabs;
import net.turtleboi.bytebuddies.item.ModItems;
import net.turtleboi.bytebuddies.network.ModNetworking;
import net.turtleboi.bytebuddies.particle.ModParticles;
import net.turtleboi.bytebuddies.screen.ModMenuTypes;
import org.slf4j.Logger;

@Mod(ByteBuddies.MOD_ID)
public class ByteBuddies {
    public static final String MOD_ID = "bytebuddies";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ByteBuddies() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        ModParticles.register(modEventBus);
        ModCreativeModeTabs.register(modEventBus);

        ModItems.register(modEventBus);
        ModBlocks.register(modEventBus);
        ModBlockEntities.register(modEventBus);

        ModEntities.register(modEventBus);
        ModMenuTypes.register(modEventBus);

        ModEffects.register(modEventBus);

        modEventBus.addListener(this::commonSetup);

        MinecraftForge.EVENT_BUS.register(this);
        modEventBus.addListener(this::addCreative);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {

        });
        ModNetworking.register();
    }

    private void addCreative(BuildCreativeModeTabContentsEvent event) {

    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {

    }
}
