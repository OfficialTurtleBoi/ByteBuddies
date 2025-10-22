package net.turtleboi.bytebuddies.screen.custom.slot;

import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

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

