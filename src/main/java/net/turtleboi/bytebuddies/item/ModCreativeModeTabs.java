package net.turtleboi.bytebuddies.item;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.turtleboi.bytebuddies.ByteBuddies;

import java.util.function.Supplier;

public class ModCreativeModeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TAB =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ByteBuddies.MOD_ID);

    public static final Supplier<CreativeModeTab> BYTEBUDDIES_TAB = CREATIVE_MODE_TAB.register("bytebuddies_tab",
            () -> CreativeModeTab.builder().icon(() -> new ItemStack(Items.ACACIA_PLANKS))
                    .title(Component.translatable("creativetab.bytebuddies.bytebuddies_tab"))
                    .displayItems((itemDisplayParameters, output) -> {
                        output.accept(ModItems.BATTERY);
                        output.accept(ModItems.CHIP);
                        output.accept(ModItems.BYTEBUDDY_SPAWN_EGG);
                    }).build());

    public static void register(IEventBus eventBus){
        CREATIVE_MODE_TAB.register(eventBus);
    }
}
