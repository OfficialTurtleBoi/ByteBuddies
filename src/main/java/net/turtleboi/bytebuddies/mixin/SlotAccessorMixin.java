package net.turtleboi.bytebuddies.mixin;

import net.minecraft.world.inventory.Slot;
import net.turtleboi.bytebuddies.api.ISlotAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(Slot.class)
public abstract class SlotAccessorMixin implements ISlotAccessor {
    @Shadow @Mutable public int x;
    @Shadow @Mutable public int y;

    @Override
    public void setSlotPosition(int x, int y) {
        this.x = x;
        this.y = y;
    }

    @Override
    public int getX() {
        return this.x;
    }

    @Override
    public int getY() {
        return this.y;
    }
}
