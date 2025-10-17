package net.turtleboi.bytebuddies.screen.custom.menu;

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
import net.turtleboi.bytebuddies.block.entity.SolarPanelBlockEntity;
import net.turtleboi.bytebuddies.entity.entities.ByteBuddyEntity;
import net.turtleboi.bytebuddies.screen.ModMenuTypes;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

public class SolarPanelMenu extends AbstractContainerMenu {
    public final SolarPanelBlockEntity solarPanelBlock;
    public final Level level;
    private int clientEnergy;
    private int clientMaxEnergy;

    public SolarPanelMenu(int containerId, Inventory playerInv, BlockEntity blockEntity) {
        super(ModMenuTypes.SOLAR_PANEL_MENU.get(), containerId);
        this.solarPanelBlock = ((SolarPanelBlockEntity) blockEntity);
        this.level = playerInv.player.level();

        addPlayerInventory(playerInv);
        addPlayerHotbar(playerInv);

        addBatterySlot();

        this.addDataSlot(new DataSlot() {
            @Override public int get() {
                return solarPanelBlock.getEnergyStorage().getEnergyStored();
            }
            @Override public void set(int value) {
                clientEnergy = value;
            }
        });

        this.addDataSlot(new DataSlot() {
            @Override public int get() {
                return solarPanelBlock.getEnergyStorage().getMaxEnergyStored();
            }
            @Override public void set(int value) {
                clientMaxEnergy = value;
            }
        });
    }

    public static SolarPanelMenu clientFactory(int containerId, Inventory inventory, FriendlyByteBuf byteBuf) {
        return new SolarPanelMenu(containerId, inventory, inventory.player.level().getBlockEntity(byteBuf.readBlockPos()));
    }

    public BlockEntity getSolarPanel() {
        return solarPanelBlock;
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
    private static final int TE_INVENTORY_SLOT_COUNT = 1;  // must be the number of slots you have!
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
        return this.solarPanelBlock != null && player.distanceToSqr(
                solarPanelBlock.getBlockPos().getX() + 0.5, solarPanelBlock.getBlockPos().getY() + 0.5, solarPanelBlock.getBlockPos().getZ() + 0.5) <= 64.0;
    }

    private static final int SLOT_SIZE = 18;
    private void addPlayerInventory(Inventory playerInventory) {
        int startX = 8;
        int startY = 123;
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
        int startX = 8;
        int startY = 181;
        for (int i = 0; i < 9; ++i) {
            this.addSlot(new Slot(
                    playerInventory,
                    i,
                    startX + i * SLOT_SIZE,
                    startY
            ));
        }
    }

    private void addBatterySlot() {
        if (solarPanelBlock == null) return;
        this.addSlot(new SlotItemHandler(
                solarPanelBlock.getBatterySlot(),
                0,
                80,
                79
        ));
    }

    public int getEnergyStored() {
        return clientEnergy;
    }

    public int getMaxEnergyStored() {
        return clientMaxEnergy;
    }
}
