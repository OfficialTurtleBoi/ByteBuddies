package net.turtleboi.bytebuddies.item;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.turtleboi.bytebuddies.ByteBuddies;
import net.turtleboi.bytebuddies.block.ModBlocks;

import java.util.function.Supplier;

public class ModCreativeModeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TAB =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ByteBuddies.MOD_ID);

    public static final Supplier<CreativeModeTab> BYTEBUDDIES_TAB = CREATIVE_MODE_TAB.register("bytebuddies_tab",
            () -> CreativeModeTab.builder().icon(() -> new ItemStack(ModItems.CHIP.get()))
                    .title(Component.translatable("creativetab.bytebuddies.bytebuddies_tab"))
                    .displayItems((itemDisplayParameters, pOutput) -> {
                        pOutput.accept(ModBlocks.ALUMINUM_BLOCK);
                        pOutput.accept(ModBlocks.BLUESTONE_ORE);
                        pOutput.accept(ModBlocks.DEEPSLATE_BLUESTONE_ORE);
                        pOutput.accept(ModBlocks.BLUESTONE_BLOCK);
                        pOutput.accept(ModItems.ALUMINUM_INGOT);
                        pOutput.accept(ModItems.ALUMINUM_NUGGET);
                        pOutput.accept(ModItems.CARBON_ALLOY);
                        pOutput.accept(ModItems.STEEL_INGOT);
                        pOutput.accept(ModItems.CHARGED_STEEL_INGOT);
                        pOutput.accept(ModItems.BLUESTONE);
                        pOutput.accept(ModItems.COPPER_PLATING);
                        pOutput.accept(ModItems.IRON_PLATING);
                        pOutput.accept(ModItems.GOLD_PLATING);
                        pOutput.accept(ModItems.ALUMINUM_PLATING);
                        pOutput.accept(ModItems.STEEL_PLATING);
                        pOutput.accept(ModItems.CHARGED_STEEL_PLATING);
                        pOutput.accept(ModItems.SIMPLE_BATTERY);
                        pOutput.accept(ModItems.ADVANCED_BATTERY);
                        pOutput.accept(ModItems.BIOCELL_BATTERY);
                        pOutput.accept(ModItems.REINFORCED_BATTERY);
                        pOutput.accept(ModItems.SUPER_CHARGED_BATTERY);
                        pOutput.accept(ModItems.CARBON_PASTE);
                        pOutput.accept(ModItems.CHIP);
                        pOutput.accept(ModItems.WRENCH);
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
