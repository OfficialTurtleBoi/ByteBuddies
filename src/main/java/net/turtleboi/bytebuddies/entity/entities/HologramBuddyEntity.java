package net.turtleboi.bytebuddies.entity.entities;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.turtleboi.bytebuddies.item.custom.FloppyDiskItem.*;
import net.turtleboi.bytebuddies.util.InventoryUtil;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.EnumSet;
import java.util.UUID;

public class HologramBuddyEntity extends ByteBuddyEntity{
    @Nullable private UUID parentBuddyUUID = null;
    private int lifetimeTicks = 200;

    public HologramBuddyEntity(EntityType<? extends PathfinderMob> entityType, Level level) {
        super(entityType, level);
        this.noCulling = true;
        this.setInvulnerable(true);
    }

    public static @Nullable HologramBuddyEntity spawnFrom(ByteBuddyEntity sourceBuddy, EntityType<? extends ByteBuddyEntity> hologramType, BlockPos spawnAt, int lifetimeTicks) {
        HologramBuddyEntity created = null;
        if (sourceBuddy.level() instanceof ServerLevel serverLevel) {
            HologramBuddyEntity candidate = (HologramBuddyEntity) hologramType.create(serverLevel);
            if (candidate != null) {
                candidate.copyLightweightStateFrom(sourceBuddy);
                candidate.setLifetimeTicks(lifetimeTicks);
                candidate.setPos(spawnAt.getX() + 0.5D, spawnAt.getY(), spawnAt.getZ() + 0.5D);

                sourceBuddy.getOwnerUUID().ifPresent(candidate::setOwnerUUID);

                candidate.setSleeping(false);
                serverLevel.addFreshEntity(candidate);
                created = candidate;
            }
        }
        return created;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.lifetimeTicks > 0) {
            this.lifetimeTicks = this.lifetimeTicks - 1;
        }

        if (this.lifetimeTicks <= 0) {
            this.transferInvOnExpire();
            this.discard();
        }
    }

    private void copyLightweightStateFrom(ByteBuddyEntity source) {
        this.setBuddyRole(source.getBuddyRole());
        this.setParentBuddyUUID(source.getUUID());
        if (source.getDock().isPresent()) {
            this.setDock(source.getDock().get());
        }
        this.setDisplayColorRGB(source.getDisplayColorRGB());
        this.setChassisMaterial(source.getChassisMaterial());
        this.setStorageCellsTier(source.getStorageCellsTier());
        this.setMood(source.getMood());
        this.setAttackMode(source.getAttackMode());
        this.copyInventory(source);
        this.receiveEnergy(16000, false);
        this.refreshEffects();
    }

    public void setLifetimeTicks(int ticks) {
        this.lifetimeTicks = Math.max(ticks, 1);
    }

    private void copyInventory(ByteBuddyEntity source) {
        ItemStackHandler sourceHandler = source.getMainInv();
        ItemStackHandler targetHandler = this.getMainInv();
        int[] slotsToCopy = new int[] { 0, 1, 2, 3, 8 };

        for (int slotIndex : slotsToCopy) {
            ItemStack sourceStack = sourceHandler.getStackInSlot(slotIndex);
            if (!sourceStack.isEmpty()) {
                ItemStack copied = sourceStack.copy();
                int targetLimit = targetHandler.getSlotLimit(slotIndex);
                if (copied.getCount() > targetLimit) {
                    copied.setCount(targetLimit);
                }
                targetHandler.setStackInSlot(slotIndex, copied);
            } else {
                targetHandler.setStackInSlot(slotIndex, net.minecraft.world.item.ItemStack.EMPTY);
            }
        }
    }

    public void setParentBuddyUUID(@Nullable UUID uuid) {
        this.parentBuddyUUID = uuid;
    }

    @Nullable
    private ByteBuddyEntity resolveParentBuddy(ServerLevel serverLevel) {
        ByteBuddyEntity parent = null;
        if (this.parentBuddyUUID != null) {
            Entity entity = serverLevel.getEntity(this.parentBuddyUUID);
            if (entity instanceof ByteBuddyEntity byteBuddy) {
                parent = byteBuddy;
            }
        }
        return parent;
    }

    private void transferInvOnExpire() {
        if (this.level() instanceof ServerLevel serverLevel) {
            ByteBuddyEntity parent = resolveParentBuddy(serverLevel);

            ItemStackHandler sourceHandler = this.getMainInv();
            ItemStackHandler targetHandler = (parent != null) ? parent.getMainInv() : null;

            double dropX;
            double dropY;
            double dropZ;

            if (parent != null) {
                dropX = parent.getX() + 0.5D;
                dropY = parent.getY() + 0.75D;
                dropZ = parent.getZ() + 0.5D;
            } else {
                dropX = this.getX() + 0.5D;
                dropY = this.getY() + 0.75D;
                dropZ = this.getZ() + 0.5D;
            }

            for (int slotIndex = 9; slotIndex <= 35; slotIndex++) {
                ItemStack stackInSlot = sourceHandler.getStackInSlot(slotIndex);
                if (!stackInSlot.isEmpty()) {
                    ItemStack toInsert = stackInSlot.copy();
                    ItemStack leftover = toInsert;

                    if (targetHandler != null) {
                        leftover = InventoryUtil.mergeInto(targetHandler, toInsert);
                    }

                    if (leftover.isEmpty()) {
                        sourceHandler.setStackInSlot(slotIndex, ItemStack.EMPTY);
                    } else {
                        sourceHandler.setStackInSlot(slotIndex, ItemStack.EMPTY);
                        Containers.dropItemStack(serverLevel, dropX, dropY, dropZ, leftover);
                    }
                }
            }
        }
    }

    @Override
    protected @NotNull InteractionResult mobInteract(Player player, InteractionHand interactionHand) {
        return InteractionResult.CONSUME;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void doPush(Entity entity) {

    }

    @Override
    public boolean canBeCollidedWith() {
        return false;
    }

    @Override
    protected void pushEntities() {

    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel serverLevel, DamageSource damageSource, boolean recentlyHit) {

    }

    @Override
    public @NotNull AABB getBoundingBoxForCulling() {
        return super.getBoundingBoxForCulling().inflate(0.25D);
    }

    @Override
    public void onTaskSuccess(TaskType taskType, BlockPos blockPos) {
        DiskHooks.tryGiveByproduct(this, taskType, blockPos);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("HoloLife", this.lifetimeTicks);
        if (this.parentBuddyUUID != null) {
            tag.putUUID("HoloParent", this.parentBuddyUUID);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.lifetimeTicks = Math.max(1, tag.getInt("HoloLife"));
        if (tag.hasUUID("HoloParent")) {
            this.parentBuddyUUID = tag.getUUID("HoloParent");
        }
    }
}
