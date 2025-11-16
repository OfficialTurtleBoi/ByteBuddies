package net.turtleboi.bytebuddies.screen;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.network.IContainerFactory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import net.turtleboi.bytebuddies.ByteBuddies;
import net.turtleboi.bytebuddies.screen.custom.menu.*;

public class ModMenuTypes {
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, ByteBuddies.MOD_ID);

    public static final RegistryObject<MenuType<ByteBuddyMenu>> BUDDY_MENU =
            registerMenuTypes("buddy_menu", ByteBuddyMenu::clientFactory);

    public static final RegistryObject<MenuType<ByteBuddyDoubleMenu>> BUDDY_DOUBLE_MENU =
            registerMenuTypes("buddy_double_menu", ByteBuddyDoubleMenu::clientFactory);

    public static final RegistryObject<MenuType<ByteBuddyTripleMenu>> BUDDY_TRIPLE_MENU =
            registerMenuTypes("buddy_triple_menu", ByteBuddyTripleMenu::clientFactory);

    public static final RegistryObject<MenuType<DockingStationMenu>> DOCKING_STATION_MENU =
            registerMenuTypes("docking_station_menu", DockingStationMenu::clientFactory);

    public static final RegistryObject<MenuType<GeneratorMenu>> GENERATOR_MENU =
            registerMenuTypes("generator_menu", GeneratorMenu::clientFactory);

    public static final RegistryObject<MenuType<SolarPanelMenu>> SOLAR_PANEL_MENU =
            registerMenuTypes("solar_panel_menu", SolarPanelMenu::clientFactory);

    private static <T extends AbstractContainerMenu>RegistryObject<MenuType<T>> registerMenuTypes(String name, IContainerFactory<T> factory){
        return MENUS.register(name, () -> IForgeMenuType.create(factory));
    }

    public static void register(IEventBus bus) {
        MENUS.register(bus);
    }
}
