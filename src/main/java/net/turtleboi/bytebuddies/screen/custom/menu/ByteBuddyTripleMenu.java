package net.turtleboi.bytebuddies.screen.custom.menu;

import com.mojang.datafixers.util.Pair;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
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
import net.turtleboi.bytebuddies.init.ModTags;
import net.turtleboi.bytebuddies.screen.ModMenuTypes;
import net.turtleboi.bytebuddies.screen.custom.slot.BuddySlot;
import net.turtleboi.bytebuddies.util.InventoryUtil;
import org.jetbrains.annotations.NotNull;

public class ByteBuddyTripleMenu extends AbstractContainerMenu {
    public final ByteBuddyEntity byteBuddy;
    public final Inventory playerInv;

    public ByteBuddyTripleMenu(int containerId, Inventory playerInv, ByteBuddyEntity byteBuddy) {
        super(ModMenuTypes.BUDDY_TRIPLE_MENU.get(), containerId);
        this.playerInv = playerInv;
        this.byteBuddy = byteBuddy;

        if (!playerInv.player.level().isClientSide && this.byteBuddy != null) {
            this.byteBuddy.onMenuOpened(playerInv.player);
        }

        addPlayerInventory(playerInv);
        addPlayerHotbar(playerInv);

        addBuddyInventory();
        addBuddyAugments();
        addBuddyUpgrades();
        addBuddyClipboard();
    }

    public static ByteBuddyTripleMenu clientFactory(int buddyId, Inventory inventory, FriendlyByteBuf byteBuf) {
        int entityId = byteBuf.readInt();
        var entity = inventory.player.level().getEntity(entityId);
        return new ByteBuddyTripleMenu(buddyId, inventory, (entity instanceof ByteBuddyEntity byteBuddy) ? byteBuddy : null);
    }

    public LivingEntity getByteBuddy() {
        return byteBuddy;
    }

    @Override
    public void removed(@NotNull Player player) {
        super.removed(player);
        if (!player.level().isClientSide && this.byteBuddy != null) {
            this.byteBuddy.onMenuClosed(player);
        }
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
    private static final int TE_INVENTORY_SLOT_COUNT = 36;  // must be the number of slots you have!
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
        return byteBuddy != null && byteBuddy.isAlive() && player.distanceTo(byteBuddy) < 4.0;
    }

    private static final int SLOT_SIZE = 18;
    private void addPlayerInventory(Inventory playerInventory) {
        int startX = 69;
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
        int startX = 69;
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
        if (byteBuddy == null) return;
        int baseX = 69, baseY = 63;
        int slot = 9;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                this.addSlot(new SlotItemHandler(byteBuddy.getMainInv(), slot++,
                        baseX + col * SLOT_SIZE, baseY + row * SLOT_SIZE));
            }
        }

        int add9X = 123;
        for (int i = 18; i <= 26; i++) {
            int idx = i - 18;
            int x = add9X + (idx % 3) * SLOT_SIZE;
            int y = baseY + (idx / 3) * SLOT_SIZE;
            this.addSlot(new BuddySlot(
                    byteBuddy.getMainInv(), i, x, y,
                    () -> byteBuddy.getStorageCellsExtraSlots() >= 9
            ));
        }

        int add18X = 177;
        for (int i = 27; i <= 35; i++) {
            int idx = i - 27;
            int x = add18X + (idx % 3) * SLOT_SIZE;
            int y = baseY + (idx / 3) * SLOT_SIZE;
            this.addSlot(new BuddySlot(
                    byteBuddy.getMainInv(), i, x, y,
                    () -> byteBuddy.getStorageCellsExtraSlots() == 18
            ));
        }
    }

    private void addBuddyAugments() {
        if (byteBuddy == null) return;
        int startX = 33;
        int startY = 54;

        this.addSlot(new SlotItemHandler(byteBuddy.getMainInv(), 0, startX, startY) {
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

        this.addSlot(new SlotItemHandler(byteBuddy.getMainInv(), 1, startX, startY + SLOT_SIZE) {
            @Override public boolean mayPlace(ItemStack itemStack) {
                if (itemStack.isEmpty()) return false;
                if (!itemStack.is(ModTags.Items.AUGMENT)) return false;

                if (itemStack.is(ModTags.Items.PLATING)) {
                    ItemStack other = byteBuddy.getMainInv().getStackInSlot(2);
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

        this.addSlot(new SlotItemHandler(byteBuddy.getMainInv(), 2, startX, startY + 2 * SLOT_SIZE) {
            @Override public boolean mayPlace(ItemStack itemStack) {
                if (itemStack.isEmpty()) return false;
                if (!itemStack.is(ModTags.Items.AUGMENT)) return false;

                if (itemStack.is(ModTags.Items.PLATING)) {
                    ItemStack other = byteBuddy.getMainInv().getStackInSlot(1);
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

        this.addSlot(new SlotItemHandler(byteBuddy.getMainInv(), 3, startX, startY + 3 * SLOT_SIZE) {
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
        if (byteBuddy == null) return;
        int startX = 249;
        int startY = 54;
        for (int u = 4; u < 8; u++) {
            this.addSlot(new SlotItemHandler(
                    byteBuddy.getMainInv(),
                    u,
                    startX,
                    startY + (u - 4) * SLOT_SIZE) {
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

    private void addBuddyClipboard() {
        if (byteBuddy == null) return;
        int startX = 184;
        int startY = 17;
        this.addSlot(new SlotItemHandler(
                byteBuddy.getMainInv(),
                8,
                startX,
                startY) {
            @Override public boolean mayPlace(ItemStack itemStack) {
                return InventoryUtil.isClipboard(itemStack);
            }
            @Override public int getMaxStackSize() {
                return 1;
            }
            @Override
            public Pair<ResourceLocation, ResourceLocation> getNoItemIcon() {
                return Pair.of(InventoryMenu.BLOCK_ATLAS,
                        ResourceLocation.fromNamespaceAndPath(ByteBuddies.MOD_ID, "item/empty_slot_clipboard"));
            }
        });

    }

    public boolean farmingEnabled() {
        return byteBuddy != null && byteBuddy.isFarmingEnabled();
    }

    public boolean harvestEnabled() {
        return byteBuddy != null && byteBuddy.isHarvestEnabled();
    }

    public boolean plantEnabled() {
        return byteBuddy != null && byteBuddy.isPlantEnabled();
    }

    public boolean tillEnabled() {
        return byteBuddy != null && byteBuddy.isTillEnabled();
    }

    public int getEnergyStored() {
        if (this.byteBuddy.level().isClientSide) {
            return byteBuddy.getSyncedEnergy();
        } else {
            return byteBuddy.getEnergyStorage().getEnergyStored();
        }
    }

    public int getMaxEnergyStored() {
        return byteBuddy != null ? byteBuddy.getEnergyStorage().getMaxEnergyStored() : 0;
    }

    public double getHealth() {
        if (this.byteBuddy.level().isClientSide) {
            return byteBuddy.getHealth();
        }
        return 0;
    }

    public double getMaxHealth() {
        return byteBuddy != null ? byteBuddy.getMaxHealth() : 0;
    }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();

        if (!(playerInv.player instanceof ServerPlayer serverPlayer)) return;
        if (byteBuddy == null || !byteBuddy.isAlive()) return;

        int extraSlots = byteBuddy.getStorageCellsExtraSlots();
        boolean mismatch = extraSlots != 18;

        if (mismatch) {
            serverPlayer.closeContainer();
            ByteBuddyEntity.openStorageMenu(serverPlayer, byteBuddy);
        }
    }
}
