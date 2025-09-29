package net.turtleboi.bytebuddies.item;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.turtleboi.bytebuddies.ByteBuddies;
import net.turtleboi.bytebuddies.entity.ModEntities;

public class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ByteBuddies.MOD_ID);

    public static final DeferredItem<Item> BATTERY = ITEMS.register("battery",
            () -> new Item(new Item.Properties()));

    public static final DeferredItem<Item> CHIP = ITEMS.register("chip",
            () -> new Item(new Item.Properties()));

    public static final DeferredItem<Item> BYTEBUDDY_SPAWN_EGG = ITEMS.register("bytebuddy_spawn_egg",
            ()-> new SpawnEggItem(ModEntities.BYTEBUDDY.get(),0x70747d,0x34a0bf,new Item.Properties()));

    public static void register(IEventBus eventBus){
        ITEMS.register(eventBus);
    }
}
