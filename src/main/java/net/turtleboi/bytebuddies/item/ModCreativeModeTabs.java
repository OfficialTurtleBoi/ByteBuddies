package net.turtleboi.bytebuddies.item;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
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
                        pOutput.accept(ModItems.BYTEBUDDY_SPAWN_EGG.get());
                        pOutput.accept(ModBlocks.DOCKING_STATION.get());
                        pOutput.accept(ModBlocks.GENERATOR.get());
                        pOutput.accept(ModBlocks.SOLAR_PANEL.get());
                        pOutput.accept(ModBlocks.ALUMINUM_BLOCK.get());
                        pOutput.accept(ModBlocks.STEEL_BLOCK.get());
                        pOutput.accept(ModBlocks.CHARGED_STEEL_BLOCK.get());
                        pOutput.accept(ModBlocks.BAUXITE_ORE.get());
                        pOutput.accept(ModBlocks.DEEPSLATE_BAUXITE_ORE.get());
                        pOutput.accept(ModBlocks.BLUESTONE_ORE.get());
                        pOutput.accept(ModBlocks.DEEPSLATE_BLUESTONE_ORE.get());
                        pOutput.accept(ModBlocks.BLUESTONE_BLOCK.get());
                        pOutput.accept(ModItems.BUSTER_SWORD.get());
                        pOutput.accept(ModItems.TERRABLADE.get());
                        pOutput.accept(ModItems.RAW_BAUXITE.get());
                        pOutput.accept(ModItems.ALUMINUM_INGOT.get());
                        pOutput.accept(ModItems.ALUMINUM_NUGGET.get());
                        pOutput.accept(ModItems.CARBON_ALLOY.get());
                        pOutput.accept(ModItems.STEEL_INGOT.get());
                        pOutput.accept(ModItems.STEEL_NUGGET.get());
                        pOutput.accept(ModItems.CHARGED_STEEL_INGOT.get());
                        pOutput.accept(ModItems.CHARGED_STEEL_NUGGET.get());
                        pOutput.accept(ModItems.BLUESTONE_DUST.get());
                        pOutput.accept(ModItems.COPPER_PLATING.get());
                        pOutput.accept(ModItems.IRON_PLATING.get());
                        pOutput.accept(ModItems.GOLD_PLATING.get());
                        pOutput.accept(ModItems.ALUMINUM_PLATING.get());
                        pOutput.accept(ModItems.STEEL_PLATING.get());
                        pOutput.accept(ModItems.CHARGED_STEEL_PLATING.get());
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
                        pOutput.accept(ModItems.CARBON_PASTE.get());
                        pOutput.accept(ModItems.CHIP.get());
                        pOutput.accept(ModItems.SUPER_CHIP.get());
                        pOutput.accept(ModItems.WRENCH.get());
                        pOutput.accept(ModItems.CLIPBOARD.get());
                        pOutput.accept(ModItems.PROPELLER_UNIT.get());
                        pOutput.accept(ModItems.AQUATIC_MOTOR.get());
                        pOutput.accept(ModItems.SOLAR_ARRAY.get());
                        pOutput.accept(ModItems.GYROSCOPIC_STABILIZER.get());
                        pOutput.accept(ModItems.ARC_WELDER.get());
                        pOutput.accept(ModItems.GEOTHERMAL_REGULATOR.get());
                        pOutput.accept(ModItems.DYNAMO_COIL.get());
                        pOutput.accept(ModItems.MAGNETIC_CRESCENT.get());
                        pOutput.accept(ModItems.BASIC_STORAGE_CELL.get());
                        pOutput.accept(ModItems.ADVANCED_STORAGE_CELL.get());
                        pOutput.accept(ModItems.ENDERLINK_STORAGE_CELL.get());
                        pOutput.accept(ModItems.REINFORCED_IRON_PLATING.get());
                        pOutput.accept(ModItems.REINFORCED_STEEL_PLATING.get());
                        pOutput.accept(ModItems.REINFORCED_NETHERITE_PLATING.get());
                        pOutput.accept(ModItems.REINFORCED_CHARGED_STEEL_PLATING.get());
                        for (String color : ModItems.COLORS) {
                            for (String tier : ModItems.TIERS) {
                                String key = tier + "_" + color + "_floppy";
                                var itemRegistryObject = ModItems.FLOPPY_DISKS.get(key);
                                if (itemRegistryObject != null) pOutput.accept(itemRegistryObject.get());
                            }
                        }
                    }).build());

    public static void register(IEventBus eventBus){
        CREATIVE_MODE_TAB.register(eventBus);
    }
}
