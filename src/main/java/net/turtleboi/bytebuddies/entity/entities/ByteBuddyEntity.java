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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.*;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.turtleboi.bytebuddies.block.entity.DockingStationBlockEntity;
import net.turtleboi.bytebuddies.entity.ai.*;
import net.turtleboi.bytebuddies.item.custom.BatteryItem;
import net.turtleboi.bytebuddies.item.custom.FloppyDiskItem;
import net.turtleboi.bytebuddies.item.custom.FloppyDiskItem.*;
import net.turtleboi.bytebuddies.screen.custom.ByteBuddyMenu;
import net.turtleboi.bytebuddies.util.ModTags;
import net.turtleboi.bytebuddies.util.ToolUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.BooleanSupplier;

public class ByteBuddyEntity extends PathfinderMob implements IEnergyStorage {
    public enum BuddyRole {
        NONE, FARMER, MINER, COMBAT, POTION, STORAGE, ANIMAL
    }
    private final ItemStackHandler mainInv = new ItemStackHandler(9);
    private final ItemStackHandler augmentInv = new ItemStackHandler(4) {
        @Override
        public boolean isItemValid(int slot, ItemStack itemStack) {
            if (itemStack.isEmpty()) return false;
            return switch (slot) {
                case 0 -> isAnyTool(itemStack);
                case 1,2-> itemStack.is(ModTags.Items.AUGMENT);
                case 3 -> isBattery(itemStack);
                default -> false;
            };
        }

        @Override
        protected int getStackLimit(int slot, ItemStack itemStack) {
            return 1;
        }

        @Override
        protected void onContentsChanged(int slot) {
            ByteBuddyEntity.this.refreshEffects();
        }
    };
    private final ItemStackHandler upgradeInv = new ItemStackHandler(4) {
        @Override
        public boolean isItemValid(int slot, ItemStack itemStack) {
            return isFloppyDisk(itemStack);
        }

        @Override
        protected int getStackLimit(int slot, ItemStack itemStack) {
            return 1;
        }

        @Override
        protected void onContentsChanged(int slot) {
            ByteBuddyEntity.this.refreshEffects();
        }
    };
    private final EnergyStorage energyStorage = new EnergyStorage(16000, 256, 256);
    private BuddyRole buddyRole = BuddyRole.NONE;
    private final Set<Goal> roleGoals = new HashSet<>();
    @Nullable private BlockPos dockPos;
    private final DiskEffects diskEffects = new DiskEffects();

    private boolean farmingEnabled = false;
    private boolean harvestEnabled = false;
    private boolean plantEnabled = false;
    private boolean tillEnabled = false;

    private boolean miningEnabled = false;
    private boolean quarryEnabled = false;

    private static final int baseCooldown = 20;
    private long cooldownUntil = 0L;

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

    private static final EntityDataAccessor<Integer> DATA_ENERGY =
            SynchedEntityData.defineId(ByteBuddyEntity.class, EntityDataSerializers.INT);

    private final NonNullList<ItemStack> armorItems = NonNullList.withSize(4, ItemStack.EMPTY);
    public ByteBuddyEntity(EntityType<? extends PathfinderMob> entityType, Level level) {
        super(entityType, level);
    }

    public static AttributeSupplier.Builder createAttributes(){
        return Animal.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 12D)
                .add(Attributes.MOVEMENT_SPEED, 0.25D)
                .add(Attributes.FOLLOW_RANGE, 24D)
                .add(Attributes.SAFE_FALL_DISTANCE, Integer.MAX_VALUE);
    }

    @Override
    protected void checkInsideBlocks() {

    }

    @Override
    public @NotNull ItemStack getMainHandItem() {
        return augmentInv.getStackInSlot(0);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new PanicGoal(this, 2.0D));
        this.goalSelector.addGoal(2, new GatedGoal(this, this::isPlantEnabled, new PlantGoal(this)));
        this.goalSelector.addGoal(3, new GatedGoal(this, this::isHarvestEnabled, new HarvestGoal(this)));
        this.goalSelector.addGoal(4, new GatedGoal(this, this::isTillEnabled, new TillGoal(this)));
        //this.goalSelector.addGoal(2, new GatedGoal(this, this::isQuarryEnabled, new QuarryGoal(this)));
        //this.goalSelector.addGoal(3, new GatedGoal(this, this::isHarvestEnabled, new HarvestGoal(this)));
        //this.goalSelector.addGoal(4, new GatedGoal(this, this::isTillEnabled, new TillGoal(this)));
        this.goalSelector.addGoal(5, new RandomStrollGoal(this, 0.75D));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 3f));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        this.goalSelector.addGoal(8, new RandomWaveAtFriendGoal(this, 6.0D, 120, 40));
    }

    private static final class GatedGoal extends Goal {
        private final ByteBuddyEntity byteBuddy;
        private final BooleanSupplier requirement;
        private final Goal lockedGoal;

        GatedGoal(ByteBuddyEntity byteBuddy, BooleanSupplier requirement, Goal lockedGoal) {
            this.byteBuddy = byteBuddy;
            this.requirement = requirement;
            this.lockedGoal = lockedGoal;
            this.setFlags(lockedGoal.getFlags());
        }

        @Override public boolean canUse() {
            if (!(byteBuddy.level() instanceof ServerLevel sl)) return false;
            if (!requirement.getAsBoolean()) return false;
            if (byteBuddy.cooldownActive(sl)) return false;
            return lockedGoal.canUse();
        }

        @Override public boolean canContinueToUse() {
            return lockedGoal.canContinueToUse();
        }

        @Override public void start() {
            lockedGoal.start();
        }

        @Override public void tick() {
            lockedGoal.tick();
        }

        @Override public void stop() {
            lockedGoal.stop();
            if (byteBuddy.level() instanceof ServerLevel serverLevel) {
                byteBuddy.armCooldown(serverLevel, 8);
            }
        }

        @Override public boolean isInterruptable() {
            return lockedGoal.isInterruptable();
        }
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
        builder.define(DATA_ROLE, BuddyRole.NONE.ordinal());
        builder.define(DATA_ENERGY, 0);
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

    public void setFarmingEnabled(boolean farmingEnabled) {
        this.farmingEnabled = farmingEnabled;
    }

    public void setHarvestEnabled(boolean harvestEnabled) {
        this.harvestEnabled = harvestEnabled;
    }

    public void setPlantEnabled(boolean plantingEnabled) {
        this.plantEnabled = plantingEnabled;
    }

    public void setTillEnabled(boolean tillingEnabled) {
        this.tillEnabled = tillingEnabled;
    }

    public boolean isFarmingEnabled() {
        return this.farmingEnabled;
    }

    public boolean isHarvestEnabled() {
        return this.farmingEnabled && this.harvestEnabled;
    }

    public boolean isPlantEnabled()   {
        return this.farmingEnabled && this.plantEnabled;
    }

    public boolean isTillEnabled()    {
        return this.farmingEnabled && this.tillEnabled;
    }

    public boolean cooldownActive(ServerLevel serverLevel) {
        return serverLevel.getGameTime() < this.cooldownUntil;
    }

    public void armCooldown(ServerLevel serverLevel, int ticks) {
        this.cooldownUntil = serverLevel.getGameTime() + ticks;
    }

    public void setMiningEnabled(boolean miningEnabled) {
        this.miningEnabled = miningEnabled;
    }

    private boolean isMiningEnabled() {
        return this.miningEnabled;
    }

    public void setQuarryEnabled(boolean quarryEnabled) {
        this.quarryEnabled = quarryEnabled;
    }

    private boolean isQuarryEnabled() {
        return this.miningEnabled & this.quarryEnabled;
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
        if (slot == EquipmentSlot.MAINHAND) return augmentInv.getStackInSlot(0);
        if (slot == EquipmentSlot.OFFHAND)  return ItemStack.EMPTY;
        return this.armorItems.get(slot.getIndex());
    }

    @Override
    public void setItemSlot(EquipmentSlot slot, ItemStack itemStack) {
        if (slot == EquipmentSlot.MAINHAND) {
            if (isAnyTool(itemStack)) {
                ItemStack originalItemStack = augmentInv.getStackInSlot(0);
                ItemStack newItemStack = itemStack.copy();
                newItemStack.setCount(1);
                augmentInv.setStackInSlot(0, newItemStack);
                this.onEquipItem(slot, originalItemStack, newItemStack);
                return;
            }
            return;
        }

        if (slot == EquipmentSlot.OFFHAND) {
            return;
        }

        this.verifyEquippedItem(itemStack);
        this.onEquipItem(slot, this.armorItems.set(slot.getIndex(), itemStack), itemStack);
    }

    @Override
    public @NotNull HumanoidArm getMainArm() {
        return HumanoidArm.RIGHT;
    }

    public int getHeldToolSlot() {
        return 0;
    }

    public ItemStack getHeldTool() {
        return augmentInv.getStackInSlot(0);
    }


    @Override
    public void aiStep() {
        super.aiStep();
        if (!level().isClientSide) {
            if (isWaking()) {
                if (this.wakingAnimationTimeout++ >= 56) {
                    this.wakingAnimationTimeout = 0;
                    setWaking(false);
                }
            }

            if (tickCount % 10 == 0) {
                consumeEnergy(1);
                BatteryItem.drainBatteries(this);
                if (supportAuraEnabled()) {
                    SupportAuras.tickSupportLattice(this);
                }
            }

            int currentEnergy = this.energyStorage.getEnergyStored();
            if (currentEnergy != getSyncedEnergy()) {
                setSyncedEnergy(currentEnergy);
            }

        } else {
            setupAnimationStates();
        }
    }

    private void setupAnimationStates(){
        if (isSleeping()) {
            if (!this.sleepPoseState.isStarted()) {
                this.sleepPoseState.start(this.tickCount);
            }
        } else {
            this.sleepPoseState.stop();
        }

        if (!isSleeping()) {
            if (this.idleAnimationTimeout <= 0) {
                this.idleAnimationTimeout = 40;
                this.idleAnimationState.start(this.tickCount);
            } else {
                --this.idleAnimationTimeout;
            }
        }

        if (isWaking()) {
            if (!this.wakeUpState.isStarted()) {
                this.wakeUpState.start(this.tickCount);
                this.wakingAnimationTimeout = 56;
            } else if (this.wakingAnimationTimeout-- <= 0) {
                this.wakeUpState.stop();
            }
        } else {
            this.wakeUpState.stop();
        }

        if (isWaving()) {
            if (!this.waveState.isStarted()) {
                this.waveState.start(this.tickCount);
            }
        } else {
            if (this.waveState.isStarted()) this.waveState.stop();
        }

        if (isWorking()) {
            if (!this.workingState.isStarted()) {
                this.workingState.start(this.tickCount);
            }
        } else {
            if (this.workingState.isStarted()) this.workingState.stop();
        }

        if (isSlamming()) {
            if (!this.slamState.isStarted()) {
                this.slamState.start(this.tickCount);
            }
        } else {
            if (this.slamState.isStarted()) this.slamState.stop();
        }
    }

    public void onTaskSuccess(TaskType taskType, BlockPos blockPos) {
        DiskHooks.tryGiveByproduct(this, taskType, blockPos);
        DiskHooks.trySpawnHologram(this, taskType, blockPos);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag nbtData) {
        super.addAdditionalSaveData(nbtData);
        nbtData.putInt("role", this.buddyRole.ordinal());
        if (this.dockPos != null) {
            nbtData.put("dockPos", NbtUtils.writeBlockPos(this.dockPos));
        }
        nbtData.putBoolean("FarmingEnabled", this.farmingEnabled);
        nbtData.putBoolean("HarvestEnabled", this.harvestEnabled);
        nbtData.putBoolean("PlantEnabled", this.plantEnabled);
        nbtData.putBoolean("TillEnabled", this.tillEnabled);

        nbtData.putBoolean("MiningEnabled", this.miningEnabled);
        nbtData.putBoolean("QuarryEnabled", this.quarryEnabled);

        var provider = level().registryAccess();
        nbtData.put("MainInv", this.mainInv.serializeNBT(provider));
        nbtData.put("AugmentInv", this.augmentInv.serializeNBT(provider));
        nbtData.put("UpgradeInv", this.upgradeInv.serializeNBT(provider));

        nbtData.putInt("Energy", this.energyStorage.getEnergyStored());
    }

    @Override
    public void readAdditionalSaveData(@NotNull CompoundTag nbtData) {
        super.readAdditionalSaveData(nbtData);
        setSleeping(nbtData.getBoolean("Sleeping"));
        setNoAi(isSleeping());
        this.buddyRole = BuddyRole.values()[nbtData.getInt("role")];
        setBuddyRole(this.buddyRole);
        this.dockPos = NbtUtils.readBlockPos(nbtData, "dockPos").orElse(null);
        if (nbtData.contains("FarmingEnabled")) {
            this.farmingEnabled = nbtData.getBoolean("FarmingEnabled");
        }
        if (nbtData.contains("HarvestEnabled")) {
            this.harvestEnabled = nbtData.getBoolean("HarvestEnabled");
        }
        if (nbtData.contains("PlantEnabled")) {
            this.plantEnabled = nbtData.getBoolean("PlantEnabled");
        }
        if (nbtData.contains("TillEnabled")) {
            this.tillEnabled = nbtData.getBoolean("TillEnabled");
        }

        if (nbtData.contains("MiningEnabled")) {
            this.miningEnabled = nbtData.getBoolean("MiningEnabled");
        }
        if (nbtData.contains("QuarryEnabled")) {
            this.quarryEnabled = nbtData.getBoolean("QuarryEnabled");
        }

        var provider = level().registryAccess();
        if (nbtData.contains("MainInv")) {
            this.mainInv.deserializeNBT(provider, nbtData.getCompound("MainInv"));
        }
        if (nbtData.contains("AugmentInv")) {
            this.augmentInv.deserializeNBT(provider, nbtData.getCompound("AugmentInv"));
        }
        if (nbtData.contains("UpgradeInv")) {
            this.upgradeInv.deserializeNBT(provider, nbtData.getCompound("UpgradeInv"));
        }
        if (nbtData.contains("Energy")) {
            setEnergyUnsafe(nbtData.getInt("Energy"));
        }
    }

    @Override
    protected @NotNull InteractionResult mobInteract(Player player, InteractionHand interactionHand) {
        if (!level().isClientSide) {
            if (isSleeping()) {
                awaken();
                return InteractionResult.CONSUME;
            }

            ItemStack inHand = player.getItemInHand(interactionHand);
            ItemStack mainHand = player.getMainHandItem();
            ItemStack offHand = player.getOffhandItem();
            if (inHand.is(Items.WHEAT)) {
                setBuddyRole(BuddyRole.FARMER);
                inHand.shrink(1);
                setFarmingEnabled(true);
                setHarvestEnabled(true);
                setPlantEnabled(true);
                setTillEnabled(true);
                player.displayClientMessage(Component.literal("Set bot role to " + buddyRole), true);
                return InteractionResult.sidedSuccess(level().isClientSide);
            } else if (inHand.is(Items.COBBLESTONE)) {
                setBuddyRole(BuddyRole.MINER);
                inHand.shrink(1);
                setMiningEnabled(true);
                setQuarryEnabled(true);
                player.displayClientMessage(Component.literal("Set bot role to " + buddyRole), true);
                return InteractionResult.sidedSuccess(level().isClientSide);
            }

            if (!isSleeping() && mainHand.isEmpty() && offHand.isEmpty()) {
                if (player instanceof ServerPlayer serverPlayer) {
                    serverPlayer.openMenu(new SimpleMenuProvider((containerId, inventory, interactingPlayer) ->
                                    new ByteBuddyMenu(containerId, inventory, this),
                                    Component.literal("ByteBuddy")),
                            buf -> buf.writeInt(this.getId())
                    );
                    return InteractionResult.sidedSuccess(level().isClientSide);
                }
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
        return this.mainInv;
    }

    public ItemStackHandler getUpgradeInv() {
        return this.upgradeInv;
    }

    public ItemStackHandler getAugmentInv() {
        return this.augmentInv;
    }

    public IEnergyStorage getEnergyStorage() {
        return this.energyStorage;
    }

    private void setEnergyUnsafe(int value) {
        try {
            var storedEnergy = EnergyStorage.class.getDeclaredField("energy");
            storedEnergy.setAccessible(true);
            storedEnergy.setInt(this.energyStorage, Mth.clamp(value, 0, this.energyStorage.getMaxEnergyStored()));
        } catch (Exception ignored) {}
    }

    public BuddyRole getBuddyRole() {
        int roleData = this.entityData.get(DATA_ROLE);
        return BuddyRole.values()[Mth.clamp(roleData, 0, BuddyRole.values().length-1)];
    }

    public void setBuddyRole(BuddyRole newRole) {
        if (newRole == null) return;
        this.buddyRole = newRole;
        this.entityData.set(DATA_ROLE, newRole.ordinal());
        resetGoals();
        rebuildGoalsForRole();
        refreshEffects();
    }

    public Optional<BlockPos> getDock() {
        return Optional.ofNullable(this.dockPos);
    }

    public void setDock(BlockPos blockPos) {
        this.dockPos = blockPos.immutable();
        if (!level().isClientSide) {
            LogUtils.getLogger().info("[ByteBuddies] bot id={} dock set to {}", this.getId(), blockPos);
        }
    }

    public void clearDock() {
        this.dockPos = null;
    }

    public void refreshEffects() {
        this.diskEffects.recomputeFrom(upgradeInv);
    }

    public int effectiveRadius() {
        Optional<BlockPos> dockOpt = getDock();
        if (dockOpt.isEmpty()) {
            return 0;
        }

        BlockPos dockPos = dockOpt.get();
        if (level().getBlockEntity(dockPos) instanceof DockingStationBlockEntity dockBlock) {
            return (int) Math.ceil(dockBlock.dockBaseRadius * this.diskEffects.radiusMultiplier());
        }

        return 0;
    }

    public float actionSpeedMultiplier() {
        return this.diskEffects.actionSpeedMultiplier();
    }

    public float energyCostMultiplier() {
        return this.diskEffects.energyCostMultiplier();
    }

    public float toolWearMultiplier() {
        return this.diskEffects.toolWearMultiplier();
    }

    public float yieldBonusChance() {
        return this.diskEffects.yieldPrimaryChance();
    }

    public float byproductChance() {
        return this.diskEffects.secondaryByproductChance();
    }

    public boolean supportAuraEnabled() {
        return this.diskEffects.supportAuraEnabled();
    }

    public boolean hologramEnabled() {
        return this.diskEffects.hologramEnabled();
    }

    public boolean consumeEnergy(int energyCost) {
        int adjustedEnergyCost = Math.max(1, Math.round(energyCost * energyCostMultiplier()));
        if (this.energyStorage.getEnergyStored() >= adjustedEnergyCost) {
            this.energyStorage.extractEnergy(adjustedEnergyCost, false);
            return true;
        }
        return false;
    }

    private void rebuildGoalsForRole() {
        for (Goal goals : this.roleGoals) this.goalSelector.removeGoal(goals);
        this.roleGoals.clear();

        switch (getBuddyRole()) {
            case FARMER -> {
                this.roleGoals.add(new GatedGoal(this, this::isPlantEnabled, new PlantGoal(this)));
                this.roleGoals.add(new GatedGoal(this, this::isHarvestEnabled, new HarvestGoal(this)));
                this.roleGoals.add(new GatedGoal(this, this::isTillEnabled, new TillGoal(this)));
            }
            case MINER -> {
                this.roleGoals.add(new GatedGoal(this, this::isQuarryEnabled, new QuarryGoal(this)));
            }
            case COMBAT -> {
                // combat goals later
            }
            case POTION -> {
                // brewing goals later
            }
            case STORAGE -> {
                // storage goals later
            }
            case ANIMAL -> {
                // husbandry goals later
            }
        }

        int priority = 2;
        for (Goal goals : roleGoals) this.goalSelector.addGoal(priority++, goals);
    }

    private void resetGoals () {
        setFarmingEnabled(false);
        setHarvestEnabled(false);
        setPlantEnabled(false);
        setTillEnabled(false);
        setMiningEnabled(false);
        setQuarryEnabled(false);
    }

    public static final class PlantRequest {
        public final BlockPos blockPos;
        public final BlockState blockState;
        public final Item seedItem;
        public PlantRequest(BlockPos blockPos, BlockState blockState, Item seedItem) {
            this.blockPos = blockPos; this.blockState = blockState; this.seedItem = seedItem;
        }
    }

    @Nullable private PlantRequest pendingPlantRequest;

    public void requestImmediatePlant(BlockPos pos, BlockState state, Item seed) {
        this.pendingPlantRequest = new PlantRequest(pos, state, seed);
    }

    @Nullable
    public PlantRequest pollPlantRequest() {
        PlantRequest plantRequest = this.pendingPlantRequest;
        this.pendingPlantRequest = null;
        return plantRequest;
    }

    private long replantHoldUntil = 0L;
    public void holdHarvestForReplant(ServerLevel serverLevel, int ticks) {
        this.replantHoldUntil = serverLevel.getGameTime() + Math.max(1, ticks);
    }

    public boolean harvestOnHold(ServerLevel serverLevel) {
        return serverLevel.getGameTime() < this.replantHoldUntil;
    }

    private static List<BlockPos> pathBlockPositions(Path path, int from, int to) {
        int start = Math.max(0, from);
        int end = Math.min(path.getNodeCount(), to);
        List<BlockPos> out = new ArrayList<>(Math.max(0, end - start));
        for (int i = start; i < end; i++) out.add(path.getNodePos(i));
        return out;
    }

    private static List<BlockPos> pathAhead(Path path, int lookAhead) {
        int start = Math.min(path.getNextNodeIndex(), path.getNodeCount());
        int end = Math.min(start + Math.max(0, lookAhead), path.getNodeCount());
        return pathBlockPositions(path, start, end);
    }

    public static void reservePathAhead(ByteBuddyEntity byteBuddy, ServerLevel serverLevel, Path path, int lookAhead) {
        Optional<BlockPos> dockOpt = byteBuddy.getDock();
        if (dockOpt.isEmpty()) {
            return;
        }

        BlockPos dockPos = dockOpt.get();
        if (serverLevel.getBlockEntity(dockPos) instanceof DockingStationBlockEntity dockBlock) {
            for (BlockPos node : pathAhead(path, lookAhead)) {
                dockBlock.tryClaim(serverLevel, TaskType.MOVE, node, byteBuddy.getUUID(), 20);
            }
        }

    }

    public void renewPathAhead(ServerLevel serverLevel, Path path, int lookAhead) {
        Optional<BlockPos> dockOpt = getDock();
        if (dockOpt.isEmpty()) {
            return;
        }

        BlockPos dockPos = dockOpt.get();
        if (level().getBlockEntity(dockPos) instanceof DockingStationBlockEntity dockBlock) {
            for (BlockPos node : pathAhead(path, lookAhead)) {
                dockBlock.renewClaim(serverLevel, TaskType.MOVE, node, this.getUUID(), 20);
            }
        }
    }

    public void releasePath(Path path) {
        Optional<BlockPos> dockOpt = getDock();
        if (dockOpt.isEmpty()) {
            return;
        }

        BlockPos dockPos = dockOpt.get();
        if (level().getBlockEntity(dockPos) instanceof DockingStationBlockEntity dockBlock) {
            for (int i = 0; i < path.getNodeCount(); i++) {
                dockBlock.releaseClaim(TaskType.MOVE, path.getNodePos(i), this.getUUID());
            }
        }
    }

    public static boolean isStandableTerrain(Level level, BlockPos blockPos) {
        if (!level.isLoaded(blockPos)) return false;

        BlockState below = level.getBlockState(blockPos.below());
        boolean solidFloor = !below.getCollisionShape(level, blockPos.below()).isEmpty();

        BlockState feet = level.getBlockState(blockPos);
        boolean feetFree = feet.getCollisionShape(level, blockPos).isEmpty();

        BlockState head = level.getBlockState(blockPos.above());
        boolean headFree = head.getCollisionShape(level, blockPos.above()).isEmpty();

        boolean noLiquid = level.getFluidState(blockPos).isEmpty()
                && level.getFluidState(blockPos.above()).isEmpty();

        return solidFloor && feetFree && headFree && noLiquid;
    }

    public static boolean isStandableForMove(ByteBuddyEntity byteBuddy, Level level, BlockPos blockPos) {
        if (!isStandableTerrain(level, blockPos)) return false;

        if (!(level instanceof ServerLevel serverLevel)) return true;
        BlockPos dockPos = byteBuddy.getDock().orElse(null);
        if (dockPos == null) return true;

        if (!(level.getBlockEntity(dockPos) instanceof DockingStationBlockEntity dockBlock)) return true;
        boolean reserved = dockBlock.isReserved(serverLevel, TaskType.MOVE, blockPos);
        if (!reserved) return true;

        return dockBlock.isReservedBy(serverLevel, TaskType.MOVE, blockPos, byteBuddy.getUUID());
    }

    public static boolean isAnyTool(ItemStack stack) {
        for (ToolUtil.ToolType toolType : ToolUtil.ToolType.values()) {
            if (toolType == ToolUtil.ToolType.EMPTY_HAND) continue;
            if (ToolUtil.matchesToolType(stack, toolType)) return true;
        }
        return false;
    }

    public static boolean isBattery(ItemStack itemStack) {
        Item item = itemStack.getItem();
        return item instanceof BatteryItem;
    }

    public static boolean isFloppyDisk(ItemStack itemStack) {
        Item item = itemStack.getItem();
        return item instanceof FloppyDiskItem;
    }

    @Override
    public int receiveEnergy(int toReceive, boolean simulate) {
        return this.energyStorage.receiveEnergy(toReceive, simulate);
    }

    @Override
    public int extractEnergy(int toExtract, boolean simulate) {
        return this.energyStorage.extractEnergy(toExtract, simulate);
    }

    @Override
    public int getEnergyStored() {
        return this.energyStorage.getEnergyStored();
    }

    @Override
    public int getMaxEnergyStored() {
        return this.energyStorage.getMaxEnergyStored();
    }

    @Override
    public boolean canExtract() {
        return this.energyStorage.canExtract();
    }

    @Override
    public boolean canReceive() {
        return this.energyStorage.canReceive();
    }

    public void setSyncedEnergy(int value) {
        if (!level().isClientSide) {
            this.entityData.set(DATA_ENERGY, Math.max(0, value));
        }
    }


    public int getSyncedEnergy() {
        return this.entityData.get(DATA_ENERGY);
    }


    private void onEnergyChanged() {
        if (!level().isClientSide) {
            setSyncedEnergy(this.energyStorage.getEnergyStored());
        }
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

    public int scaledCooldownTicks() {
        float speedMultiplier = Math.max(0.25f, this.actionSpeedMultiplier());
        return Math.max(4, Math.round(baseCooldown / speedMultiplier));
    }

    public enum TaskType {
        HARVEST,
        FORESTRY,
        PLANT,
        TILL,
        MOVE,
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
