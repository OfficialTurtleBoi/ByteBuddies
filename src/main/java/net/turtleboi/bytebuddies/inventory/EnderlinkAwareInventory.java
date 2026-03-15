package net.turtleboi.bytebuddies.inventory;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.items.ItemStackHandler;
import net.turtleboi.bytebuddies.block.entity.DockingStationBlockEntity;
import net.turtleboi.bytebuddies.entity.entities.ByteBuddyEntity;
import net.turtleboi.bytebuddies.init.ModTags;
import net.turtleboi.bytebuddies.util.InventoryUtil;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;

public class EnderlinkAwareInventory extends ItemStackHandler {
    public static final int CARGO_FIRST_SLOT = 9;
    public static final int TOTAL_SLOTS = 36;

    private final ByteBuddyEntity buddy;

    public EnderlinkAwareInventory(ByteBuddyEntity buddy) {
        super(TOTAL_SLOTS);
        this.buddy = buddy;
    }

    private static final int DOCK_MAIN_INV_START = DockingStationBlockEntity.clipboardSlot + 1;

    @Nullable
    private ItemStackHandler resolveRemoteInventory() {
        if (!buddy.isEnderlinkActive()) return null;
        if (buddy.level().isClientSide()) return null;
        if (!(buddy.level() instanceof ServerLevel serverLevel)) return null;

        BlockPos dockPosition = buddy.getDock().orElse(null);
        if (dockPosition == null) return null;

        if (!(serverLevel.getBlockEntity(dockPosition) instanceof DockingStationBlockEntity dock)) return null;
        if (dock.getEnergyStored() <= 0) return null;

        return dock.getMainInv();
    }

    private boolean isCargoSlot(int slot) {
        return slot >= CARGO_FIRST_SLOT && slot < TOTAL_SLOTS;
    }

    private int toDockSlot(int buddyCargoSlot) {
        return buddyCargoSlot - CARGO_FIRST_SLOT + DOCK_MAIN_INV_START;
    }

    @Override
    public @NotNull ItemStack getStackInSlot(int slot) {
        if (isCargoSlot(slot)) {
            ItemStackHandler remoteInventory = resolveRemoteInventory();
            if (remoteInventory != null) {
                return remoteInventory.getStackInSlot(toDockSlot(slot));
            }
        }
        return super.getStackInSlot(slot);
    }

    @Override
    public void setStackInSlot(int slot, @NotNull ItemStack stack) {
        if (isCargoSlot(slot)) {
            ItemStackHandler remoteInventory = resolveRemoteInventory();
            if (remoteInventory != null) {
                remoteInventory.setStackInSlot(toDockSlot(slot), stack);
                return;
            }
        }
        super.setStackInSlot(slot, stack);
    }

    @Override
    public @NotNull ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
        if (isCargoSlot(slot)) {
            ItemStackHandler remoteInventory = resolveRemoteInventory();
            if (remoteInventory != null) {
                return remoteInventory.insertItem(toDockSlot(slot), stack, simulate);
            }
        }
        return super.insertItem(slot, stack, simulate);
    }

    @Override
    public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) {
        if (isCargoSlot(slot)) {
            ItemStackHandler remoteInventory = resolveRemoteInventory();
            if (remoteInventory != null) {
                return remoteInventory.extractItem(toDockSlot(slot), amount, simulate);
            }
        }
        return super.extractItem(slot, amount, simulate);
    }

    @Override
    public int getSlotLimit(int slot) {
        if (isCargoSlot(slot)) {
            ItemStackHandler remoteInventory = resolveRemoteInventory();
            if (remoteInventory != null) {
                return remoteInventory.getSlotLimit(toDockSlot(slot));
            }
        }
        return switch (slot) {
            case 0, 1, 2, 3, 4, 5, 6, 7, 8 -> 1;
            default -> 64;
        };
    }

    @Override
    public boolean isItemValid(int slot, @NotNull ItemStack itemStack) {
        if (itemStack.isEmpty()) return false;

        if (isCargoSlot(slot)) {
            ItemStackHandler remoteInventory = resolveRemoteInventory();
            if (remoteInventory != null) {
                return remoteInventory.isItemValid(toDockSlot(slot), itemStack);
            }
        }

        return switch (slot) {
            case 0 -> ByteBuddyEntity.isAnyTool(itemStack);
            case 1, 2 -> {
                if (!itemStack.is(ModTags.Items.AUGMENT)) yield false;

                if (itemStack.is(ModTags.Items.PLATING)) {
                    int otherSlot = (slot == 1) ? 2 : 1;
                    ItemStack otherStack = super.getStackInSlot(otherSlot);
                    if (!otherStack.isEmpty() && otherStack.is(ModTags.Items.PLATING)) yield false;
                }

                if (itemStack.is(ModTags.Items.STORAGE_CELL)) {
                    int otherSlot = (slot == 1) ? 2 : 1;
                    ItemStack otherStack = super.getStackInSlot(otherSlot);
                    if (!otherStack.isEmpty() && otherStack.is(ModTags.Items.STORAGE_CELL)) yield false;
                }

                yield true;
            }
            case 3 -> InventoryUtil.isBattery(itemStack);
            case 4, 5, 6, 7 -> InventoryUtil.isFloppyDisk(itemStack);
            case 8 -> InventoryUtil.isClipboard(itemStack);
            case 9, 10, 11, 12, 13, 14, 15, 16, 17 -> true;
            case 18, 19, 20, 21, 22, 23, 24, 25, 26 -> buddy.getStorageCellsExtraSlots() >= 9;
            case 27, 28, 29, 30, 31, 32, 33, 34, 35 -> buddy.getStorageCellsExtraSlots() >= 18;
            default -> false;
        };
    }

    @Override
    protected void onContentsChanged(int slot) {
        if (!buddy.level().isClientSide()) {
            buddy.refreshEffects();
            buddy.computeChassis();
        }
    }

    public void migrateLocalCargoToDock() {
        ItemStackHandler remoteInventory = resolveRemoteInventory();
        if (remoteInventory == null) return;

        for (int buddySlot = CARGO_FIRST_SLOT; buddySlot < TOTAL_SLOTS; buddySlot++) {
            ItemStack localItem = super.getStackInSlot(buddySlot);
            if (localItem.isEmpty()) continue;

            ItemStack leftover = ItemHandlerHelper.insertItemStacked(remoteInventory, localItem.copy(), false);
            super.setStackInSlot(buddySlot, ItemStack.EMPTY);

            if (!leftover.isEmpty()) {
                ItemEntity droppedItem = new ItemEntity(
                        buddy.level(),
                        buddy.getX(), buddy.getY(), buddy.getZ(),
                        leftover
                );
                buddy.level().addFreshEntity(droppedItem);
            }
        }
    }
}
