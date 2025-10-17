package net.turtleboi.bytebuddies.events;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.turtleboi.bytebuddies.ByteBuddies;
import net.turtleboi.bytebuddies.screen.ModMenuTypes;
import net.turtleboi.bytebuddies.screen.custom.ByteBuddyScreen;
import net.turtleboi.bytebuddies.screen.custom.DockingStationScreen;
import net.turtleboi.bytebuddies.screen.custom.GeneratorScreen;
import net.turtleboi.bytebuddies.screen.custom.SolarPanelScreen;

@EventBusSubscriber(modid = ByteBuddies.MOD_ID, value = Dist.CLIENT)
public class ClientModEvents {
    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenuTypes.BUDDY_MENU.get(), ByteBuddyScreen::new);
        event.register(ModMenuTypes.DOCKING_STATION_MENU.get(), DockingStationScreen::new);
        event.register(ModMenuTypes.GENERATOR_MENU.get(), GeneratorScreen::new);
        event.register(ModMenuTypes.SOLAR_PANEL_MENU.get(), SolarPanelScreen::new);
    }
}
