package net.turtleboi.bytebuddies.screen.custom.menu;

import com.mojang.datafixers.util.Pair;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.SlotItemHandler;
import net.turtleboi.bytebuddies.ByteBuddies;
import net.turtleboi.bytebuddies.entity.entities.ByteBuddyEntity;
import net.turtleboi.bytebuddies.screen.ModMenuTypes;
import net.turtleboi.bytebuddies.util.InventoryUtil;
import net.turtleboi.bytebuddies.util.ModTags;
import org.jetbrains.annotations.NotNull;

public class ByteBuddyMenu extends AbstractContainerMenu {
    public final ByteBuddyEntity buddy;
    public final Inventory playerInv;

    public ByteBuddyMenu(int containerId, Inventory playerInv, ByteBuddyEntity buddy) {
        super(ModMenuTypes.BUDDY_MENU.get(), containerId);
        this.playerInv = playerInv;
        this.buddy = buddy;

        addPlayerInventory(playerInv);
        addPlayerHotbar(playerInv);

        addBuddyInventory();
        addBuddyAugments();
        addBuddyUpgrades();
    }

    public static ByteBuddyMenu clientFactory(int buddyId, Inventory inventory, FriendlyByteBuf byteBuf) {
        int entityId = byteBuf.readInt();
        var entity = inventory.player.level().getEntity(entityId);
        return new ByteBuddyMenu(buddyId, inventory, (entity instanceof ByteBuddyEntity byteBuddy) ? byteBuddy : null);
    }

    public LivingEntity getByteBuddy() {
        return buddy;
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
    private static final int TE_INVENTORY_SLOT_COUNT = 11;  // must be the number of slots you have!
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
        return buddy != null && buddy.isAlive() && player.distanceTo(buddy) < 4.0;
    }

    private static final int SLOT_SIZE = 18;
    private void addPlayerInventory(Inventory playerInventory) {
        int startX = 21;
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
        int startX = 21;
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

    private void addBuddyInventory() {
        if (buddy == null) return;
        int startX = 75;
        int startY = 63;
        int slot = 0;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                this.addSlot(new SlotItemHandler(
                        buddy.getMainInv(),
                        slot++,
                        startX + col * SLOT_SIZE,
                        startY + row * SLOT_SIZE
                ));
            }
        }
    }

    private void addBuddyAugments() {
        if (buddy == null) return;
        int startX = 33;
        int startY = 54;

        this.addSlot(new SlotItemHandler(buddy.getAugmentInv(), 0, startX, startY) {
            @Override public boolean mayPlace(ItemStack itemStack) {
                return ByteBuddyEntity.isAnyTool(itemStack);
            }
            @Override public int getMaxStackSize() {
                return 1;
            }
            @Override
            public Pair<ResourceLocation, ResourceLocation> getNoItemIcon() {
                return Pair.of(InventoryMenu.BLOCK_ATLAS,
                        ResourceLocation.fromNamespaceAndPath(ByteBuddies.MOD_ID, "item/empty_slot_tool"));
            }
        });

        this.addSlot(new SlotItemHandler(buddy.getAugmentInv(), 1, startX, startY + SLOT_SIZE) {
            @Override public boolean mayPlace(ItemStack itemStack) {
                if (itemStack.isEmpty()) return false;
                if (!itemStack.is(ModTags.Items.AUGMENT)) return false;

                if (itemStack.is(ModTags.Items.PLATING)) {
                    ItemStack other = buddy.getAugmentInv().getStackInSlot(2);
                    if (!other.isEmpty() && other.is(ModTags.Items.PLATING)) return false;
                }
                return true;
            }
            @Override public int getMaxStackSize() {
                return 1;
            }
            @Override
            public Pair<ResourceLocation, ResourceLocation> getNoItemIcon() {
                return Pair.of(InventoryMenu.BLOCK_ATLAS,
                        ResourceLocation.fromNamespaceAndPath(ByteBuddies.MOD_ID, "item/empty_slot_augment"));
            }
        });

        this.addSlot(new SlotItemHandler(buddy.getAugmentInv(), 2, startX, startY + 2 * SLOT_SIZE) {
            @Override public boolean mayPlace(ItemStack itemStack) {
                if (itemStack.isEmpty()) return false;
                if (!itemStack.is(ModTags.Items.AUGMENT)) return false;

                if (itemStack.is(ModTags.Items.PLATING)) {
                    ItemStack other = buddy.getAugmentInv().getStackInSlot(1);
                    if (!other.isEmpty() && other.is(ModTags.Items.PLATING)) return false;
                }
                return true;
            }
            @Override public int getMaxStackSize() {
                return 1;
            }
            @Override
            public Pair<ResourceLocation, ResourceLocation> getNoItemIcon() {
                return Pair.of(InventoryMenu.BLOCK_ATLAS,
                        ResourceLocation.fromNamespaceAndPath(ByteBuddies.MOD_ID, "item/empty_slot_augment"));
            }
        });

        this.addSlot(new SlotItemHandler(buddy.getAugmentInv(), 3, startX, startY + 3 * SLOT_SIZE) {
            @Override public boolean mayPlace(ItemStack itemStack) {
                return InventoryUtil.isBattery(itemStack);
            }

            @Override public int getMaxStackSize() {
                return 1;
            }
            @Override
            public Pair<ResourceLocation, ResourceLocation> getNoItemIcon() {
                return Pair.of(InventoryMenu.BLOCK_ATLAS,
                        ResourceLocation.fromNamespaceAndPath(ByteBuddies.MOD_ID, "item/empty_slot_battery"));
            }
        });
    }

    private void addBuddyUpgrades() {
        if (buddy == null) return;
        int startX = 153;
        int startY = 54;
        for (int u = 0; u < 4; u++) {
            this.addSlot(new SlotItemHandler(
                    buddy.getUpgradeInv(),
                    u,
                    startX,
                    startY + u * SLOT_SIZE) {
                @Override public boolean mayPlace(ItemStack itemStack) {
                    return InventoryUtil.isFloppyDisk(itemStack);
                }
                @Override public int getMaxStackSize() {
                    return 1;
                }
                @Override
                public Pair<ResourceLocation, ResourceLocation> getNoItemIcon() {
                    return Pair.of(InventoryMenu.BLOCK_ATLAS,
                            ResourceLocation.fromNamespaceAndPath(ByteBuddies.MOD_ID, "item/empty_slot_floppy_disk"));
                }
            });
        }
    }

    public boolean farmingEnabled() {
        return buddy != null && buddy.isFarmingEnabled();
    }

    public boolean harvestEnabled() {
        return buddy != null && buddy.isHarvestEnabled();
    }

    public boolean plantEnabled() {
        return buddy != null && buddy.isPlantEnabled();
    }

    public boolean tillEnabled() {
        return buddy != null && buddy.isTillEnabled();
    }

    public int getEnergyStored() {
        if (this.buddy.level().isClientSide) {
            return buddy.getSyncedEnergy();
        } else {
            return buddy.getEnergyStorage().getEnergyStored();
        }
    }

    public int getMaxEnergyStored() {
        return buddy != null ? buddy.getEnergyStorage().getMaxEnergyStored() : 0;
    }


}
