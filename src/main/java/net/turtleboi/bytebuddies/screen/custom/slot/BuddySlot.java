package net.turtleboi.bytebuddies.screen.custom.slot;

import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.SlotItemHandler;

import java.util.function.BooleanSupplier;

public class BuddySlot extends SlotItemHandler {
    private final BooleanSupplier active;

    public BuddySlot(IItemHandler handler, int index, int x, int y, BooleanSupplier active) {
        super(handler, index, x, y);
        this.active = active;
    }

    @Override
    public boolean isActive() {
        return active.getAsBoolean();
    }
}

