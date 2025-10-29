package net.turtleboi.turtlecore;

import com.mojang.logging.LogUtils;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.turtleboi.turtlecore.commands.InviteCommand;
import net.turtleboi.turtlecore.config.TurtleCoreConfig;
import net.turtleboi.turtlecore.effect.CoreEffects;
import net.turtleboi.turtlecore.enchantment.CoreEnchantments;
import net.turtleboi.turtlecore.entity.CoreEntities;
import net.turtleboi.turtlecore.init.CoreAttributes;
import net.turtleboi.turtlecore.item.CoreItems;
import net.turtleboi.turtlecore.network.CoreNetworking;
import net.turtleboi.turtlecore.particle.CoreParticles;
import net.turtleboi.turtlecore.potion.CorePotions;
import org.slf4j.Logger;

@Mod(TurtleCore.MOD_ID)
public class TurtleCore {
    public static final String MOD_ID = "turtlecore";
    private static final Logger LOGGER = LogUtils.getLogger();

    public TurtleCore() {
        IEventBus eventBus = FMLJavaModLoadingContext.get().getModEventBus();

        CoreItems.register(eventBus);

        CoreEntities.register(eventBus);
        CoreEffects.register(eventBus);
        CorePotions.register(eventBus);

        CoreEnchantments.register(eventBus);
        CoreParticles.register(eventBus);

        eventBus.addListener(this::commonSetup);
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, TurtleCoreConfig.SPEC, "turtlecore-client.toml");
        MinecraftForge.EVENT_BUS.register(this);

        CoreAttributes.REGISTRY.register(eventBus);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        CoreNetworking.register();

        //BrewingRecipeRegistry.addRecipe(new BetterBrewingRecipe(Potions.THICK,
        //        Items.SUGAR_CANE, ModPotions.LESSER_ENERGY_POTION.get()));
        //BrewingRecipeRegistry.addRecipe(new BetterBrewingRecipe(ModPotions.LESSER_ENERGY_POTION.get(),
        //        Items.GLOWSTONE_DUST, ModPotions.ENERGY_POTION.get()));
        //BrewingRecipeRegistry.addRecipe(new BetterBrewingRecipe(ModPotions.ENERGY_POTION.get(),
        //        Items.GLOWSTONE_DUST, ModPotions.GREATER_ENERGY_POTION.get()));
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        InviteCommand.register(event.getServer().getCommands().getDispatcher());
    }
}