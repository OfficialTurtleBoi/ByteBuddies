package net.turtleboi.bytebuddies.entity.entities;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.turtleboi.bytebuddies.entity.ai.FarmerGoal;
import net.turtleboi.bytebuddies.entity.ai.RandomWaveAtFriendGoal;
import net.turtleboi.bytebuddies.item.custom.FloppyDiskItem.*;
import net.turtleboi.bytebuddies.util.EnergyHooks;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class ByteBuddyEntity extends PathfinderMob implements IEnergyStorage {
    public enum BuddyRole {
        FARMER, MINER, COMBAT, POTION, STORAGE, ANIMAL
    }
    private final ItemStackHandler mainInv = new ItemStackHandler(27);
    private final ItemStackHandler upgradeInv = new ItemStackHandler(4);
    private final EnergyStorage energyStorage = new EnergyStorage(100_000, 400, 400);
    private BuddyRole buddyRole = BuddyRole.FARMER;
    private final Set<Goal> roleGoals = new HashSet<>();
    @Nullable private BlockPos dockPos;
    private final int baseRadius = 8;
    private final DiskEffects effects = new DiskEffects();

    private long lastProgressGameTime = 0L;     // last time we *know* the bot made real progress
    private long lastResetGameTime     = 0L;     // to avoid rapid-fire resets
    private static final int STALL_RESET_TICKS   = 200;  // 10s @20tps (tune)
    private static final int RESET_COOLDOWN_TICKS= 60;   // 3s between hard resets
    private TaskType activeTask = TaskType.NONE;

    private static final EntityDataAccessor<Boolean> DATA_SLEEPING =
            SynchedEntityData.defineId(ByteBuddyEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_WAKING =
            SynchedEntityData.defineId(ByteBuddyEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_WAVING =
            SynchedEntityData.defineId(ByteBuddyEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_WORKING =
            SynchedEntityData.defineId(ByteBuddyEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_SLAMMING =
            SynchedEntityData.defineId(ByteBuddyEntity.class, EntityDataSerializers.BOOLEAN);
    public static final EntityDataAccessor<Integer> DATA_ROLE =
            SynchedEntityData.defineId(ByteBuddyEntity.class, EntityDataSerializers.INT);

    private final NonNullList<ItemStack> armorItems = NonNullList.withSize(4, ItemStack.EMPTY);
    public ByteBuddyEntity(EntityType<? extends PathfinderMob> entityType, Level level) {
        super(entityType, level);
    }

    public static AttributeSupplier.Builder createAttributes(){
        return Animal.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 12D)
                .add(Attributes.MOVEMENT_SPEED, 0.25D)
                .add(Attributes.FOLLOW_RANGE, 24D);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new PanicGoal(this, 2.0D));
        this.goalSelector.addGoal(2, new FarmerGoal(this));
        this.goalSelector.addGoal(5, new RandomStrollGoal(this, 0.75D));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 3f));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        this.goalSelector.addGoal(8, new RandomWaveAtFriendGoal(this, 6.0D, 120, 40));
    }

    public AnimationState idleAnimationState = new AnimationState();
    private int idleAnimationTimeout = 0;
    public final AnimationState sleepPoseState = new AnimationState();
    public final AnimationState wakeUpState = new AnimationState();
    private int wakingAnimationTimeout = 0;
    public final AnimationState workingState = new AnimationState();
    public final AnimationState slamState = new AnimationState();
    public final AnimationState waveState = new AnimationState();

    @Override
    protected void updateWalkAnimation(float pPartialTick) {
        float f;
        if(this.getPose() == Pose.STANDING) {
            f = Math.min(pPartialTick * 6F, 1f);
        } else {
            f = 0f;
        }
        this.walkAnimation.update(f, 0.2f);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_SLEEPING, true);
        builder.define(DATA_WAKING, false);
        builder.define(DATA_WAVING, false);
        builder.define(DATA_WORKING, false);
        builder.define(DATA_SLAMMING, false);
        builder.define(DATA_ROLE, BuddyRole.FARMER.ordinal());
    }

    public boolean isSleeping() {
        return this.entityData.get(DATA_SLEEPING);
    }

    public void setSleeping(boolean sleeping) {
        this.entityData.set(DATA_SLEEPING, sleeping);
    }

    public boolean isWaking() {
        return this.entityData.get(DATA_WAKING);
    }

    private void setWaking(boolean wakening) {
        this.entityData.set(DATA_WAKING, wakening);
    }

    public boolean isWaving() {
        return this.entityData.get(DATA_WAVING);
    }

    public void setWaving(boolean waving) {
        this.entityData.set(DATA_WAVING, waving);
    }

    public boolean isWorking() {
        return this.entityData.get(DATA_WORKING);
    }

    public void setWorking(boolean working) {
        this.entityData.set(DATA_WORKING, working);
    }

    public boolean isSlamming() {
        return this.entityData.get(DATA_SLAMMING);
    }

    public void setSlamming(boolean slamming) {
        this.entityData.set(DATA_SLAMMING, slamming);
    }

    @Override
    public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
                                                  MobSpawnType spawnType, @Nullable SpawnGroupData spawnGroupData) {
        SpawnGroupData spawnData = super.finalizeSpawn(level, difficulty, spawnType, spawnGroupData);
        setSleeping(true);
        setNoAi(true);
        setPersistenceRequired();
        return spawnData;
    }

    @Override
    public Iterable<ItemStack> getArmorSlots() {
        return this.armorItems;
    }

    @Override
    public ItemStack getItemBySlot(EquipmentSlot slot) {
        return this.armorItems.get(slot.getIndex());
    }

    @Override
    public void setItemSlot(EquipmentSlot slot, ItemStack stack) {
        this.verifyEquippedItem(stack);
        this.onEquipItem(slot, this.armorItems.set(slot.getIndex(), stack), stack);
    }

    @Override
    public HumanoidArm getMainArm() {
        return HumanoidArm.RIGHT;
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!level().isClientSide) {
            if (isWaking()) {
                if (wakingAnimationTimeout++ >= 56) {
                    wakingAnimationTimeout = 0;
                    setWaking(false);
                }
            }

            if (tickCount % 10 == 0) {
                EnergyHooks.drainBatteries(this);
                if (supportAuraEnabled()) {
                    SupportAuras.tickSupportLattice(this);
                }
            }
        } else {
            setupAnimationStates();
        }
    }



    private void setupAnimationStates(){
        if (isSleeping()) {
            if (!sleepPoseState.isStarted()) {
                sleepPoseState.start(this.tickCount);
            }
        } else {
            sleepPoseState.stop();
        }

        if (!isSleeping()) {
            if (idleAnimationTimeout <= 0) {
                idleAnimationTimeout = 40;
                idleAnimationState.start(this.tickCount);
            } else {
                --idleAnimationTimeout;
            }
        }

        if (isWaking()) {
            if (!wakeUpState.isStarted()) {
                wakeUpState.start(this.tickCount);
                wakingAnimationTimeout = 56;
            } else if (wakingAnimationTimeout-- <= 0) {
                wakeUpState.stop();
            }
        } else {
            wakeUpState.stop();
        }

        if (isWaving()) {
            if (!waveState.isStarted()) {
                waveState.start(this.tickCount);
            }
        } else {
            if (waveState.isStarted()) waveState.stop();
        }

        if (isWorking()) {
            if (!workingState.isStarted()) {
                workingState.start(this.tickCount);
            }
        } else {
            if (workingState.isStarted()) workingState.stop();
        }

        if (isSlamming()) {
            if (!slamState.isStarted()) {
                slamState.start(this.tickCount);
            }
        } else {
            if (slamState.isStarted()) slamState.stop();
        }
    }

    public void onTaskSuccess(TaskType taskType, BlockPos blockPos) {
        DiskHooks.tryGiveByproduct(this, taskType, blockPos);
        DiskHooks.trySpawnHologram(this, taskType, blockPos);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag nbtData) {
        super.addAdditionalSaveData(nbtData);
        nbtData.putInt("role", buddyRole.ordinal());
        if (dockPos != null) {
            nbtData.put("dockPos", NbtUtils.writeBlockPos(dockPos));
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag nbtData) {
        super.readAdditionalSaveData(nbtData);
        setSleeping(nbtData.getBoolean("Sleeping"));
        buddyRole = BuddyRole.values()[nbtData.getInt("role")];
        dockPos = NbtUtils.readBlockPos(nbtData, "dockPos").orElse(null);
        setNoAi(isSleeping());
    }

    @Override
    protected @NotNull InteractionResult mobInteract(Player player, InteractionHand interactionHand) {
        if (!level().isClientSide) {
            if (isSleeping()) {
                awaken();
                return InteractionResult.CONSUME;
            } else if (player.getItemInHand(interactionHand).getItem() == Items.WHEAT) {
                setBuddyRole(BuddyRole.FARMER);
                player.getItemInHand(interactionHand).shrink(1);
                player.displayClientMessage(Component.literal("Set bot role to " + buddyRole), true);
                return InteractionResult.SUCCESS;
            }
        }
        return super.mobInteract(player, interactionHand);
    }

    public void awaken() {
        if (!level().isClientSide && isSleeping()) {
            setSleeping(false);
            setNoAi(false);
            setWaking(true);
        }
    }

    public ItemStackHandler getMainInv() {
        return mainInv;
    }

    public ItemStackHandler getUpgradeInv() {
        return upgradeInv;
    }

    public IEnergyStorage getEnergyStorage() {
        return energyStorage;
    }

    public BuddyRole getBuddyRole() {
        int roleData = entityData.get(DATA_ROLE);
        return BuddyRole.values()[Mth.clamp(roleData, 0, BuddyRole.values().length-1)];
    }

    public void setBuddyRole(BuddyRole newRole) {
        if (newRole == null) return;
        buddyRole = newRole;
        entityData.set(DATA_ROLE, newRole.ordinal());
        rebuildGoalsForRole();
        refreshEffects();
    }

    public Optional<BlockPos> getDock() {
        return Optional.ofNullable(dockPos);
    }

    public void setDock(BlockPos blockPos) {
        dockPos = blockPos.immutable();
        if (!level().isClientSide) {
            LogUtils.getLogger().info("[ByteBuddies] bot id={} dock set to {}", this.getId(), blockPos);
        }
    }


    public void clearDock() {
        dockPos = null;
    }

    public void refreshEffects() {
        effects.recomputeFrom(upgradeInv);
    }

    public int effectiveRadius() {
        return (int)Math.ceil(baseRadius * effects.radiusMultiplier());
    }

    public float actionSpeedMultiplier() {
        return effects.actionSpeedMultiplier();
    }

    public float energyCostMultiplier() {
        return effects.energyCostMultiplier();
    }

    public float toolWearMultiplier() {
        return effects.toolWearMultiplier();
    }

    public float yieldBonusChance() {
        return effects.yieldPrimaryChance();
    }

    public float byproductChance() {
        return effects.secondaryByproductChance();
    }

    public boolean supportAuraEnabled() {
        return effects.supportAuraEnabled();
    }

    public boolean hologramEnabled() {
        return effects.hologramEnabled();
    }

    public boolean consumeEnergy(int energyCost) {
        int adjustedEnergyCost = Math.max(1, Math.round(energyCost * energyCostMultiplier()));
        if (energyStorage.getEnergyStored() >= adjustedEnergyCost) {
            energyStorage.extractEnergy(adjustedEnergyCost, false);
            return true;
        }
        return false;
    }

    private void rebuildGoalsForRole() {
        for (Goal goals : roleGoals) this.goalSelector.removeGoal(goals);
        roleGoals.clear();

        switch (getBuddyRole()) {
            case FARMER -> {
                roleGoals.add(new FarmerGoal(this));
                // roleGoals.add(new TillingGoal(this));
                // roleGoals.add(new ForesterGoal(this));
            }
            case MINER -> {
                // roleGoals.add(new QuarryGoal(this));
                // roleGoals.add(new VeinGoal(this));
            }
            case COMBAT -> {
                // roleGoals.add(new GuardGoal(this));
                // roleGoals.add(new SentryGoal(this));
            }
            case POTION -> {
                //roleGoals.add(new BrewerGoal(this));
                // roleGoals.add(new DistributorGoal(this));
            }
            case STORAGE -> {
                //roleGoals.add(new SorterGoal(this));
            }
            case ANIMAL -> {
                //roleGoals.add(new HusbandryGoal(this));
            }
        }
        int priority = 2;
        for (Goal goals : roleGoals) this.goalSelector.addGoal(priority, goals);
    }

    @Override
    public int receiveEnergy(int toReceive, boolean simulate) {
        return 0;
    }

    @Override
    public int extractEnergy(int toExtract, boolean simulate) {
        return 0;
    }

    @Override
    public int getEnergyStored() {
        return 0;
    }

    @Override
    public int getMaxEnergyStored() {
        return 0;
    }

    @Override
    public boolean canExtract() {
        return false;
    }

    @Override
    public boolean canReceive() {
        return false;
    }

    public static final class SupportAuras {
        public static void tickSupportLattice(ByteBuddyEntity byteBuddy) {
            Level level = byteBuddy.level();
            BlockPos buddyPos = byteBuddy.blockPosition();
            int radius = 5;

            if (level.random.nextFloat() < 0.10f) {
                BlockPos.betweenClosedStream(
                        buddyPos.offset(-radius, -1, -radius),
                        buddyPos.offset(radius, 2, radius))
                        .limit(24).forEach(blockPos -> {
                    BlockState blockState = level.getBlockState(blockPos);
                    if (blockState.getBlock() instanceof CropBlock cropBlock && !cropBlock.isMaxAge(blockState)) {
                        if (level.random.nextFloat() < 0.05f) {
                            level.setBlock(blockPos, cropBlock.getStateForAge(cropBlock.getAge(blockState) + 1), 3);
                        }
                    }
                });
            }

            List<Player> players = level.getEntitiesOfClass(Player.class, new AABB(buddyPos).inflate(5));
            for (Player player : players) {
                player.addEffect(
                        new MobEffectInstance(
                                MobEffects.MOVEMENT_SPEED,
                                40,
                                0,
                                true,
                                false)
                );
            }
        }
    }

    public enum TaskType {
        HARVEST,
        FORESTRY,
        PLANT,
        TILL,
        MINE,
        COMBAT,
        BREW,
        DISTRIBUTE_POTION,
        SORT,
        PICKUP,
        DEPOSIT,
        SHEAR,
        MILK,
        BREED,
        WRANGLE,
        NONE
    }
}
