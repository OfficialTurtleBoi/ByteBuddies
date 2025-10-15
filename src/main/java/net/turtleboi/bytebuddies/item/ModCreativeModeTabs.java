package net.turtleboi.bytebuddies.item;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.turtleboi.bytebuddies.ByteBuddies;
import net.turtleboi.bytebuddies.block.ModBlocks;
import net.turtleboi.bytebuddies.item.custom.BatteryItem;

import java.util.function.Supplier;

public class ModCreativeModeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TAB =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ByteBuddies.MOD_ID);

    public static final Supplier<CreativeModeTab> BYTEBUDDIES_TAB = CREATIVE_MODE_TAB.register("bytebuddies_tab",
            () -> CreativeModeTab.builder().icon(() -> new ItemStack(ModItems.CHIP.get()))
                    .title(Component.translatable("creativetab.bytebuddies.bytebuddies_tab"))
                    .displayItems((itemDisplayParameters, pOutput) -> {
                        pOutput.accept(ModBlocks.DOCKING_STATION);
                        pOutput.accept(ModBlocks.ALUMINUM_BLOCK);
                        pOutput.accept(ModBlocks.STEEL_BLOCK);
                        pOutput.accept(ModBlocks.CHARGED_STEEL_BLOCK);
                        pOutput.accept(ModBlocks.BLUESTONE_ORE);
                        pOutput.accept(ModBlocks.DEEPSLATE_BLUESTONE_ORE);
                        pOutput.accept(ModBlocks.BLUESTONE_BLOCK);
                        pOutput.accept(ModItems.BUSTER_SWORD);
                        pOutput.accept(ModItems.ALUMINUM_INGOT);
                        pOutput.accept(ModItems.ALUMINUM_NUGGET);
                        pOutput.accept(ModItems.CARBON_ALLOY);
                        pOutput.accept(ModItems.STEEL_INGOT);
                        pOutput.accept(ModItems.STEEL_NUGGET);
                        pOutput.accept(ModItems.CHARGED_STEEL_INGOT);
                        pOutput.accept(ModItems.CHARGED_STEEL_NUGGET);
                        pOutput.accept(ModItems.BLUESTONE_DUST);
                        pOutput.accept(ModItems.COPPER_PLATING);
                        pOutput.accept(ModItems.IRON_PLATING);
                        pOutput.accept(ModItems.GOLD_PLATING);
                        pOutput.accept(ModItems.ALUMINUM_PLATING);
                        pOutput.accept(ModItems.STEEL_PLATING);
                        pOutput.accept(ModItems.CHARGED_STEEL_PLATING);
                        ItemStack chargedSimpleBattery = new ItemStack(ModItems.SIMPLE_BATTERY.get());
                        ((BatteryItem) chargedSimpleBattery.getItem()).setEnergy(chargedSimpleBattery, ((BatteryItem) chargedSimpleBattery.getItem()).getCapacity());
                        pOutput.accept(chargedSimpleBattery);
                        ItemStack chargedAdvancedBattery = new ItemStack(ModItems.ADVANCED_BATTERY.get());
                        ((BatteryItem) chargedAdvancedBattery.getItem()).setEnergy(chargedAdvancedBattery, ((BatteryItem) chargedAdvancedBattery.getItem()).getCapacity());
                        pOutput.accept(chargedAdvancedBattery);
                        ItemStack chargedBioCellBattery = new ItemStack(ModItems.BIOCELL_BATTERY.get());
                        ((BatteryItem) chargedBioCellBattery.getItem()).setEnergy(chargedBioCellBattery, ((BatteryItem) chargedBioCellBattery.getItem()).getCapacity());
                        pOutput.accept(chargedBioCellBattery);
                        ItemStack chargedReinforcedBattery = new ItemStack(ModItems.REINFORCED_BATTERY.get());
                        ((BatteryItem) chargedReinforcedBattery.getItem()).setEnergy(chargedReinforcedBattery, ((BatteryItem) chargedReinforcedBattery.getItem()).getCapacity());
                        pOutput.accept(chargedReinforcedBattery);
                        ItemStack chargedSuperChargedBattery = new ItemStack(ModItems.SUPER_CHARGED_BATTERY.get());
                        ((BatteryItem) chargedSuperChargedBattery.getItem()).setEnergy(chargedSuperChargedBattery, ((BatteryItem) chargedSuperChargedBattery.getItem()).getCapacity());
                        pOutput.accept(chargedSuperChargedBattery);
                        pOutput.accept(ModItems.CARBON_PASTE);
                        pOutput.accept(ModItems.CHIP);
                        pOutput.accept(ModItems.SUPER_CHIP);
                        pOutput.accept(ModItems.WRENCH);
                        pOutput.accept(ModItems.AQUATIC_MOTOR);
                        pOutput.accept(ModItems.SOLAR_ARRAY);
                        pOutput.accept(ModItems.GYROSCOPIC_STABILIZER);
                        pOutput.accept(ModItems.REINFORCED_IRON_PLATING);
                        pOutput.accept(ModItems.REINFORCED_STEEL_PLATING);
                        pOutput.accept(ModItems.REINFORCED_NETHERITE_PLATING);
                        pOutput.accept(ModItems.REINFORCED_CHARGED_STEEL_PLATING);
                        for (String color : ModItems.COLORS) {
                            for (String tier : ModItems.TIERS) {
                                String key = tier + "_" + color + "_floppy";
                                var itemRegistryObject = ModItems.FLOPPY_DISKS.get(key);
                                if (itemRegistryObject != null) pOutput.accept(itemRegistryObject.get());
                            }
                        }
                        pOutput.accept(ModItems.BYTEBUDDY_SPAWN_EGG);
                    }).build());

    public static void register(IEventBus eventBus){
        CREATIVE_MODE_TAB.register(eventBus);
    }
}
