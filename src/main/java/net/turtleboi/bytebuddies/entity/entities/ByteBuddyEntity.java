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
import net.turtleboi.bytebuddies.item.custom.FloppyDiskItem.*;
import net.turtleboi.bytebuddies.screen.custom.ByteBuddyMenu;
import net.turtleboi.bytebuddies.util.EnergyHooks;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.BooleanSupplier;

public class ByteBuddyEntity extends PathfinderMob implements IEnergyStorage {
    public enum BuddyRole {
        FARMER, MINER, COMBAT, POTION, STORAGE, ANIMAL
    }
    private final ItemStackHandler mainInv = new ItemStackHandler(9);
    private final ItemStackHandler augmentInv = new ItemStackHandler(4);
    private final ItemStackHandler upgradeInv = new ItemStackHandler(4);
    private final EnergyStorage energyStorage = new EnergyStorage(16000, 256, 256);
    private BuddyRole buddyRole = BuddyRole.FARMER;
    private final Set<Goal> roleGoals = new HashSet<>();
    @Nullable private BlockPos dockPos;
    private final DiskEffects effects = new DiskEffects();

    private boolean farmingEnabled = true;
    private boolean harvestEnabled = true;
    private boolean plantEnabled = true;
    private boolean tillEnabled = true;

    private static final int BASE_ACTION_COOLDOWN_TICKS = 20;
    private long farmGoalLockUntil = 0L;

    @Nullable private HarvestGoal harvestGoal;
    @Nullable private PlantGoal plantGoal;
    @Nullable private TillGoal tillGoal;

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
                .add(Attributes.FOLLOW_RANGE, 24D);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new PanicGoal(this, 2.0D));
        this.goalSelector.addGoal(2, new GatedGoal(this, this::isPlantEnabled, new PlantGoal(this)));
        this.goalSelector.addGoal(3, new GatedGoal(this, this::isHarvestEnabled, new HarvestGoal(this)));
        this.goalSelector.addGoal(4, new GatedGoal(this, this::isTillEnabled, new TillGoal(this)));
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
            if (byteBuddy.farmLockActive(sl)) return false;
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
                byteBuddy.armFarmLock(serverLevel, 8);
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
        builder.define(DATA_ROLE, BuddyRole.FARMER.ordinal());
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
        tillEnabled = tillingEnabled;
    }

    public boolean isFarmingEnabled() {
        return farmingEnabled;
    }

    public boolean isHarvestEnabled() {
        return farmingEnabled && harvestEnabled;
    }

    public boolean isPlantEnabled()   {
        return farmingEnabled && plantEnabled;
    }

    public boolean isTillEnabled()    {
        return farmingEnabled && tillEnabled;
    }

    public boolean farmLockActive(ServerLevel serverLevel) {
        return serverLevel.getGameTime() < farmGoalLockUntil;
    }

    public void armFarmLock(ServerLevel serverLevel, int ticks) {
        farmGoalLockUntil = serverLevel.getGameTime() + ticks;
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
                consumeEnergy(1);
                EnergyHooks.drainBatteries(this);
                if (supportAuraEnabled()) {
                    SupportAuras.tickSupportLattice(this);
                }
            }

            int currentEnergy = energyStorage.getEnergyStored();
            if (currentEnergy != getSyncedEnergy()) {
                setSyncedEnergy(currentEnergy);
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
        nbtData.putBoolean("FarmingEnabled", farmingEnabled);
        nbtData.putBoolean("HarvestEnabled", harvestEnabled);
        nbtData.putBoolean("PlantEnabled", plantEnabled);
        nbtData.putBoolean("TillEnabled", tillEnabled);

        var provider = level().registryAccess();
        nbtData.put("MainInv", mainInv.serializeNBT(provider));
        nbtData.put("AugmentInv",augmentInv.serializeNBT(provider));
        nbtData.put("UpgradeInv",upgradeInv.serializeNBT(provider));

        nbtData.putInt("Energy", energyStorage.getEnergyStored());
    }

    @Override
    public void readAdditionalSaveData(@NotNull CompoundTag nbtData) {
        super.readAdditionalSaveData(nbtData);
        setSleeping(nbtData.getBoolean("Sleeping"));
        buddyRole = BuddyRole.values()[nbtData.getInt("role")];
        dockPos = NbtUtils.readBlockPos(nbtData, "dockPos").orElse(null);
        if (nbtData.contains("FarmingEnabled")) {
            farmingEnabled = nbtData.getBoolean("FarmingEnabled");
        }
        if (nbtData.contains("HarvestEnabled")) {
            harvestEnabled = nbtData.getBoolean("HarvestEnabled");
        }
        if (nbtData.contains("PlantEnabled")) {
            plantEnabled = nbtData.getBoolean("PlantEnabled");
        }
        if (nbtData.contains("TillEnabled")) {
            tillEnabled = nbtData.getBoolean("TillEnabled");
        }
        setNoAi(isSleeping());

        var provider = level().registryAccess();
        if (nbtData.contains("MainInv")) {
            mainInv.deserializeNBT(provider, nbtData.getCompound("MainInv"));
        }
        if (nbtData.contains("AugmentInv")) {
            augmentInv.deserializeNBT(provider, nbtData.getCompound("AugmentInv"));
        }
        if (nbtData.contains("UpgradeInv")) {
            upgradeInv.deserializeNBT(provider, nbtData.getCompound("UpgradeInv"));
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
        return mainInv;
    }

    public ItemStackHandler getUpgradeInv() {
        return upgradeInv;
    }

    public ItemStackHandler getAugmentInv() {
        return augmentInv;
    }

    public IEnergyStorage getEnergyStorage() {
        return energyStorage;
    }

    private void setEnergyUnsafe(int value) {
        try {
            var storedEnergy = EnergyStorage.class.getDeclaredField("energy");
            storedEnergy.setAccessible(true);
            storedEnergy.setInt(energyStorage, Mth.clamp(value, 0, energyStorage.getMaxEnergyStored()));
        } catch (Exception ignored) {}
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
        Optional<BlockPos> dockOpt = getDock();
        if (dockOpt.isEmpty()) {
            return 0;
        }

        BlockPos dockPos = dockOpt.get();
        if (level().getBlockEntity(dockPos) instanceof DockingStationBlockEntity dockBlock) {
            return (int) Math.ceil(dockBlock.dockBaseRadius * effects.radiusMultiplier());
        }

        return 0;
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
                roleGoals.add(new GatedGoal(this, this::isPlantEnabled, new PlantGoal(this)));
                roleGoals.add(new GatedGoal(this, this::isHarvestEnabled, new HarvestGoal(this)));
                roleGoals.add(new GatedGoal(this, this::isTillEnabled, new TillGoal(this)));
            }
            case MINER -> {
                // add mining goals later
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
        replantHoldUntil = serverLevel.getGameTime() + Math.max(1, ticks);
    }

    public boolean harvestOnHold(ServerLevel serverLevel) {
        return serverLevel.getGameTime() < replantHoldUntil;
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

    @Override
    public int receiveEnergy(int toReceive, boolean simulate) {
        return energyStorage.receiveEnergy(toReceive, simulate);
    }

    @Override
    public int extractEnergy(int toExtract, boolean simulate) {
        return energyStorage.extractEnergy(toExtract, simulate);
    }

    @Override
    public int getEnergyStored() {
        return energyStorage.getEnergyStored();
    }

    @Override
    public int getMaxEnergyStored() {
        return energyStorage.getMaxEnergyStored();
    }

    @Override
    public boolean canExtract() {
        return energyStorage.canExtract();
    }

    @Override
    public boolean canReceive() {
        return energyStorage.canReceive();
    }

    public void setSyncedEnergy(int value) {
        if (!level().isClientSide) {
            this.entityData.set(DATA_ENERGY, Math.max(0, value));
        }
    }

    /** Client-safe getter: on the client returns the last synced value. On server, it’s also available. */
    public int getSyncedEnergy() {
        return this.entityData.get(DATA_ENERGY);
    }

    /** Call this whenever the real FE changes on the server (charge/discharge). */
    private void onEnergyChanged() {
        if (!level().isClientSide) {
            setSyncedEnergy(energyStorage.getEnergyStored());
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
        return Math.max(4, Math.round(BASE_ACTION_COOLDOWN_TICKS / speedMultiplier));
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
