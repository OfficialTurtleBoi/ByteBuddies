package net.turtleboi.bytebuddies.screen;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.network.IContainerFactory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.turtleboi.bytebuddies.ByteBuddies;
import net.turtleboi.bytebuddies.screen.custom.menu.ByteBuddyMenu;
import net.turtleboi.bytebuddies.screen.custom.menu.DockingStationMenu;
import net.turtleboi.bytebuddies.screen.custom.menu.GeneratorMenu;
import net.turtleboi.bytebuddies.screen.custom.menu.SolarPanelMenu;

public class ModMenuTypes {
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, ByteBuddies.MOD_ID);

    public static final DeferredHolder<MenuType<?>, MenuType<ByteBuddyMenu>> BUDDY_MENU =
            registerMenuType("buddy_menu", ByteBuddyMenu::clientFactory);

    public static final DeferredHolder<MenuType<?>, MenuType<DockingStationMenu>> DOCKING_STATION_MENU =
            registerMenuType("docking_station_menu", DockingStationMenu::clientFactory);

    public static final DeferredHolder<MenuType<?>, MenuType<GeneratorMenu>> GENERATOR_MENU =
            registerMenuType("generator_menu", GeneratorMenu::clientFactory);

    public static final DeferredHolder<MenuType<?>, MenuType<SolarPanelMenu>> SOLAR_PANEL_MENU =
            registerMenuType("solar_panel_menu", SolarPanelMenu::clientFactory);

    private static <T extends AbstractContainerMenu> DeferredHolder<MenuType<?>,
            MenuType<T>> registerMenuType(String name, IContainerFactory<T> factory) {
        return MENUS.register(name, () -> IMenuTypeExtension.create(factory));
    }

    public static void register(IEventBus bus) {
        MENUS.register(bus);
    }
}
