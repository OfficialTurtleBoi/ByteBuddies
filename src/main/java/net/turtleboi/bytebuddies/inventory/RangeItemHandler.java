package net.turtleboi.bytebuddies.inventory;

import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;

public class RangeItemHandler implements IItemHandler {
    private final ItemStackHandler itemStackHandler;
    private final int startSlot;
    private final int endSlot;

    public RangeItemHandler(ItemStackHandler itemStackHandler, int startSlot, int endSlot) {
        this.itemStackHandler = itemStackHandler;
        this.startSlot = startSlot;
        this.endSlot = endSlot;
    }

    @Override
    public int getSlots() {
        return endSlot - startSlot + 1;
    }

    private int map(int slot) {
        return startSlot + slot;
    }

    @Override
    public @NotNull ItemStack getStackInSlot(int slot) {
        if (slot < 0 || slot >= getSlots()) return ItemStack.EMPTY;
        return itemStackHandler.getStackInSlot(map(slot));
    }

    @Override
    public @NotNull ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
        if (slot < 0 || slot >= getSlots()) return stack;
        return itemStackHandler.insertItem(map(slot), stack, simulate);
    }

    @Override
    public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) {
        if (slot < 0 || slot >= getSlots()) return ItemStack.EMPTY;
        return itemStackHandler.extractItem(map(slot), amount, simulate);
    }

    @Override
    public int getSlotLimit(int slot) {
        if (slot < 0 || slot >= getSlots()) return 0;
        return itemStackHandler.getSlotLimit(map(slot));
    }

    @Override
    public boolean isItemValid(int slot, @NotNull ItemStack itemStack) {
        if (slot < 0 || slot >= getSlots()) return false;
        return itemStackHandler.isItemValid(map(slot), itemStack);
    }
}
