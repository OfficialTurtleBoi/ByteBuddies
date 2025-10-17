package net.turtleboi.bytebuddies.util;

import net.minecraft.world.item.*;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.turtleboi.bytebuddies.item.custom.BatteryItem;
import net.turtleboi.bytebuddies.item.custom.FloppyDiskItem;

public final class InventoryUtil {
    public static ItemStack mergeInto(ItemStackHandler inventoryHandler, ItemStack itemStack) {
        ItemStack copiedStack = itemStack.copy();
        for (int i=0; i < inventoryHandler.getSlots(); i++) {
            copiedStack = inventoryHandler.insertItem(i, copiedStack, false);
        }
        return copiedStack;
    }

    public static ItemStack findItem(ItemStackHandler inventoryHandler, Item item) {
        for (int i = 0; i < inventoryHandler.getSlots(); i++) {
            ItemStack itemStack = inventoryHandler.getStackInSlot(i);
            if (!itemStack.isEmpty() && itemStack.is(item)) return itemStack;
        }
        return ItemStack.EMPTY;
    }

    public static boolean isBattery(ItemStack itemStack) {
        Item item = itemStack.getItem();
        return item instanceof BatteryItem;
    }

    public static boolean isFloppyDisk(ItemStack itemStack) {
        Item item = itemStack.getItem();
        return item instanceof FloppyDiskItem;
    }

}

