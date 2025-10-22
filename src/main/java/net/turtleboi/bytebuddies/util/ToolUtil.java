package net.turtleboi.bytebuddies.util;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.*;
import net.turtleboi.bytebuddies.entity.entities.ByteBuddyEntity;

public final class ToolUtil {
    public static void applyToolWear(ByteBuddyEntity byteBuddy, ToolType toolType, float wearMultiplier) {
        if (toolType == ToolType.EMPTY_HAND) return;

        int slot = byteBuddy.getHeldToolSlot();
        if (slot < 0) return;

        ItemStack toolStack = byteBuddy.getHeldTool();
        if (!toolStack.isDamageableItem()) return;

        int baseWear = 1;
        int damageValue = Math.max(1, Math.round(baseWear * wearMultiplier));
        ItemStack heldTool = byteBuddy.getHeldTool();
        if (!heldTool.isEmpty() && heldTool.isDamageableItem()) {
            heldTool.hurtAndBreak(damageValue, byteBuddy, EquipmentSlot.MAINHAND);
            byteBuddy.getMainInv().setStackInSlot(slot, heldTool);
        }
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
