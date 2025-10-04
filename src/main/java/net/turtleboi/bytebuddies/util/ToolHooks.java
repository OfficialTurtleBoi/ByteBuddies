package net.turtleboi.bytebuddies.util;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.*;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.turtleboi.bytebuddies.entity.entities.ByteBuddyEntity;

public final class ToolHooks {
    public static void applyToolWear(ByteBuddyEntity byteBuddy, ToolType kind, float wearMultiplier) {
        if (kind == ToolType.EMPTY_HAND) return;

        int slot = findToolSlot(byteBuddy.getMainInv(), kind);
        if (slot < 0) return;

        ItemStack tool = byteBuddy.getMainInv().getStackInSlot(slot);
        if (!tool.isDamageableItem()) return;

        int baseWear = 1;
        int damage = Math.max(1, Math.round(baseWear * wearMultiplier));
        tool.hurtAndBreak(damage, byteBuddy, EquipmentSlot.MAINHAND);
        byteBuddy.getMainInv().setStackInSlot(slot, tool);
    }

    public static int findToolSlot(ItemStackHandler inventoryHandler, ToolType toolType) {
        for (int i = 0; i < inventoryHandler.getSlots(); i++) {
            ItemStack itemStack = inventoryHandler.getStackInSlot(i);
            if (!itemStack.isEmpty() && matchesToolType(itemStack, toolType)) return i;
        }
        return -1;
    }

    public static boolean matchesToolType(ItemStack toolStack, ToolType toolType) {
        Item toolItem = toolStack.getItem();
        return switch (toolType) {
            case HOE -> toolItem instanceof HoeItem;
            case AXE -> toolItem instanceof AxeItem;
            case PICKAXE -> toolItem instanceof PickaxeItem;
            case SHOVEL -> toolItem instanceof ShovelItem;
            case SHEARS -> toolItem instanceof ShearsItem;
            case SWORD -> toolItem instanceof SwordItem;
            case BOW -> toolItem instanceof BowItem;
            case CROSSBOW -> toolItem instanceof CrossbowItem;
            case EMPTY_HAND -> false;
        };
    }

    public enum ToolType {
        HOE,
        AXE,
        PICKAXE,
        SHOVEL,
        SHEARS,
        SWORD,
        BOW,
        CROSSBOW,
        EMPTY_HAND
    }
}
