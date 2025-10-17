package net.turtleboi.bytebuddies.screen.custom.menu;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.SlotItemHandler;
import net.turtleboi.bytebuddies.block.entity.DockingStationBlockEntity;
import net.turtleboi.bytebuddies.entity.entities.ByteBuddyEntity;
import net.turtleboi.bytebuddies.screen.ModMenuTypes;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

public class DockingStationMenu extends AbstractContainerMenu {
    public final DockingStationBlockEntity dockBlock;
    public final Level level;
    private final IntList buddyEntityIds = new IntArrayList();
    private int clientEnergy;
    private int clientMaxEnergy;

    public DockingStationMenu(int containerId, Inventory playerInv, BlockEntity blockEntity) {
        super(ModMenuTypes.DOCKING_STATION_MENU.get(), containerId);
        this.dockBlock = ((DockingStationBlockEntity) blockEntity);
        this.level = playerInv.player.level();

        addPlayerInventory(playerInv);
        addPlayerHotbar(playerInv);

        addStationInventory();
        addBatterySlot();

        this.addDataSlot(new DataSlot() {
            @Override public int get() {
                return dockBlock.getEnergyStorage().getEnergyStored();
            }
            @Override public void set(int value) {
                clientEnergy = value;
            }
        });

        this.addDataSlot(new DataSlot() {
            @Override public int get() {
                return dockBlock.getEnergyStorage().getMaxEnergyStored();
            }
            @Override public void set(int value) {
                clientMaxEnergy = value;
            }
        });
    }

    public static DockingStationMenu clientFactory(int containerId, Inventory inventory, FriendlyByteBuf byteBuf) {
        DockingStationMenu dockingStationMenu = new DockingStationMenu(containerId, inventory, inventory.player.level().getBlockEntity(byteBuf.readBlockPos()));
        int idCount = byteBuf.readVarInt();
        for (int i = 0; i < idCount; i++) {
            dockingStationMenu.getBuddyEntityIds().add(byteBuf.readVarInt());
        }
        return dockingStationMenu;
    }

    public BlockEntity getDockingStation() {
        return dockBlock;
    }

    public IntList getBuddyEntityIds() {
        return buddyEntityIds;
    }

    public int getBuddyCount() {
        return buddyEntityIds.size();
    }

    @Nullable
    public ByteBuddyEntity getBuddyByIndexClient(int index) {
        if (level.isClientSide) {
        if (index < 0 || index >= buddyEntityIds.size()) return null;
        int id = buddyEntityIds.getInt(index);
        Entity buddyEntity = level.getEntity(id);
        return (buddyEntity instanceof ByteBuddyEntity byteBuddy) ? byteBuddy : null;
        }
        return null;
    }

    public Component getBuddyNameByIndexClient(int index) {
        ByteBuddyEntity b = getBuddyByIndexClient(index);
        return (b != null) ? b.getDisplayName() : Component.literal("Loading…");
    }

    // CREDIT GOES TO: diesieben07 | https://github.com/diesieben07/SevenCommons
    // must assign a slot number to each of the slots used by the GUI.
    // For this container, we can see both the tile inventory's slots as well as the player inventory slots and the hotbar.
    // Each time we add a Slot to the container, it automatically increases the slotIndex, which means
    //  0 - 8 = hotbar slots (which will map to the InventoryPlayer slot numbers 0 - 8)
    //  9 - 35 = player inventory slots (which map to the InventoryPlayer slot numbers 9 - 35)
    //  36 - 44 = TileInventory slots, which map to our TileEntity slot numbers 0 - 8)
    private static final int HOTBAR_SLOT_COUNT = 9;
    private static final int PLAYER_INVENTORY_ROW_COUNT = 3;
    private static final int PLAYER_INVENTORY_COLUMN_COUNT = 9;
    private static final int PLAYER_INVENTORY_SLOT_COUNT = PLAYER_INVENTORY_COLUMN_COUNT * PLAYER_INVENTORY_ROW_COUNT;
    private static final int VANILLA_SLOT_COUNT = HOTBAR_SLOT_COUNT + PLAYER_INVENTORY_SLOT_COUNT;
    private static final int VANILLA_FIRST_SLOT_INDEX = 0;
    private static final int TE_INVENTORY_FIRST_SLOT_INDEX = VANILLA_FIRST_SLOT_INDEX + VANILLA_SLOT_COUNT;

    // THIS YOU HAVE TO DEFINE!
    private static final int TE_INVENTORY_SLOT_COUNT = 28;  // must be the number of slots you have!
    @Override
    public @NotNull ItemStack quickMoveStack(@NotNull Player playerIn, int pIndex) {
        Slot sourceSlot = slots.get(pIndex);
        if (sourceSlot == null || !sourceSlot.hasItem()) return ItemStack.EMPTY;  //EMPTY_ITEM
        ItemStack sourceStack = sourceSlot.getItem();
        ItemStack copyOfSourceStack = sourceStack.copy();

        // Check if the slot clicked is one of the vanilla container slots
        if (pIndex < VANILLA_FIRST_SLOT_INDEX + VANILLA_SLOT_COUNT) {
            // This is a vanilla container slot so merge the stack into the tile inventory
            if (!moveItemStackTo(sourceStack, TE_INVENTORY_FIRST_SLOT_INDEX, TE_INVENTORY_FIRST_SLOT_INDEX
                    + TE_INVENTORY_SLOT_COUNT, false)) {
                return ItemStack.EMPTY;  // EMPTY_ITEM
            }
        } else if (pIndex < TE_INVENTORY_FIRST_SLOT_INDEX + TE_INVENTORY_SLOT_COUNT) {
            // This is a TE slot so merge the stack into the players inventory
            if (!moveItemStackTo(sourceStack, VANILLA_FIRST_SLOT_INDEX, VANILLA_FIRST_SLOT_INDEX + VANILLA_SLOT_COUNT, false)) {
                return ItemStack.EMPTY;
            }
        } else {
            System.out.println("Invalid slotIndex:" + pIndex);
            return ItemStack.EMPTY;
        }
        // If stack size == 0 (the entire stack was moved) set slot contents to null
        if (sourceStack.getCount() == 0) {
            sourceSlot.set(ItemStack.EMPTY);
        } else {
            sourceSlot.setChanged();
        }
        sourceSlot.onTake(playerIn, sourceStack);
        return copyOfSourceStack;
    }

    @Override
    public boolean stillValid(@NotNull Player player) {
        return this.dockBlock != null && player.distanceToSqr(
                dockBlock.getBlockPos().getX() + 0.5, dockBlock.getBlockPos().getY() + 0.5, dockBlock.getBlockPos().getZ() + 0.5) <= 64.0;
    }

    public List<ByteBuddyEntity> getVisibleBuddiesClient(Player player) {
        if (player.level().isClientSide) {
            List<ByteBuddyEntity> buddyList = new ArrayList<>();
            for (int id : buddyEntityIds) {
                Entity buddyEntity = player.level().getEntity(id);
                if (buddyEntity instanceof ByteBuddyEntity byteBuddy) {
                    buddyList.add(byteBuddy);
                }
            }
            return buddyList;
        }
        return List.of();
    }

    private static final int SLOT_SIZE = 18;
    private void addPlayerInventory(Inventory playerInventory) {
        int startX = 40;
        int startY = 145;
        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                this.addSlot(new Slot(
                        playerInventory,
                        col + row * 9 + 9,
                        startX + col * SLOT_SIZE,
                        startY + row * SLOT_SIZE
                ));
            }
        }
    }

    private void addPlayerHotbar(Inventory playerInventory) {
        int startX = 40;
        int startY = 203;
        for (int i = 0; i < 9; ++i) {
            this.addSlot(new Slot(
                    playerInventory,
                    i,
                    startX + i * SLOT_SIZE,
                    startY
            ));
        }
    }

    private void addStationInventory() {
        if (dockBlock == null) return;
        int startX = 40;
        int startY = 63;
        int slot = 0;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new SlotItemHandler(
                        dockBlock.getMainInv(),
                        slot++,
                        startX + col * SLOT_SIZE,
                        startY + row * SLOT_SIZE
                ));
            }
        }
    }

    private void addBatterySlot() {
        if (dockBlock == null) return;
        this.addSlot(new SlotItemHandler(
                dockBlock.getBatterySlot(),
                0,
                6,
                109
        ));
    }

    public int getEnergyStored() {
        return clientEnergy;
    }

    public int getMaxEnergyStored() {
        return clientMaxEnergy;
    }
}
