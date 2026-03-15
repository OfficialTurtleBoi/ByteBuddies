package net.turtleboi.bytebuddies.entity.entities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundTakeItemEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.ByIdMap;
import net.minecraft.util.Mth;
import net.minecraft.world.*;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.EnergyStorage;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fluids.FluidType;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.network.NetworkHooks;
import net.turtleboi.bytebuddies.ByteBuddies;
import net.turtleboi.bytebuddies.block.entity.DockingStationBlockEntity;
import net.turtleboi.bytebuddies.inventory.EnderlinkAwareInventory;
import net.turtleboi.bytebuddies.inventory.RangeItemHandler;
import net.turtleboi.bytebuddies.entity.ai.*;
import net.turtleboi.bytebuddies.entity.ai.combat.BuddyMeleeAttackGoal;
import net.turtleboi.bytebuddies.entity.ai.combat.BuddyOwnerHurtByTargetGoal;
import net.turtleboi.bytebuddies.entity.ai.combat.BuddyOwnerHurtTargetGoal;
import net.turtleboi.bytebuddies.entity.ai.combat.BuddyHurtByTargetGoal;
import net.turtleboi.bytebuddies.entity.ai.farmer.HarvestGoal;
import net.turtleboi.bytebuddies.entity.ai.farmer.PlantGoal;
import net.turtleboi.bytebuddies.entity.ai.farmer.TillGoal;
import net.turtleboi.bytebuddies.entity.ai.miner.QuarryGoal;
import net.turtleboi.bytebuddies.entity.ai.storage.HaulerGoal;
import net.turtleboi.bytebuddies.entity.ai.animal.AnimalHarvestGoal;
import net.turtleboi.bytebuddies.entity.ai.animal.AnimalManageGoal;
import net.turtleboi.bytebuddies.entity.ai.animal.LeashHerdGoal;
import net.turtleboi.bytebuddies.init.ModTags;
import net.turtleboi.bytebuddies.item.ModItems;
import net.turtleboi.bytebuddies.item.custom.AugmentItem.AugmentEffects;
import net.turtleboi.bytebuddies.item.custom.BatteryItem;
import net.turtleboi.bytebuddies.item.custom.ClipboardItem;
import net.turtleboi.bytebuddies.item.custom.FloppyDiskItem.DiskEffects;
import net.turtleboi.bytebuddies.item.custom.FloppyDiskItem.DiskHooks;
import net.turtleboi.bytebuddies.screen.custom.menu.ByteBuddyDoubleMenu;
import net.turtleboi.bytebuddies.screen.custom.menu.ByteBuddyMenu;
import net.turtleboi.bytebuddies.screen.custom.menu.ByteBuddyTripleMenu;
import net.turtleboi.bytebuddies.util.*;
import net.turtleboi.turtlecore.init.CoreAttributeModifiers;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.IntFunction;

public class ByteBuddyEntity extends PathfinderMob implements IEnergyStorage {

    public enum BuddyRole {
        NONE(0), FARMER(1), MINER(2), COMBAT(3), POTION(4), STORAGE(5), ANIMAL(6);

        private final int id;
        BuddyRole(int id) {
            this.id = id;
        }

        public int getId() {
            return id;
        }

        private static final IntFunction<BuddyRole> BY_ID =
                ByIdMap.continuous(BuddyRole::getId, values(), ByIdMap.OutOfBoundsStrategy.ZERO);

        public static BuddyRole byId(int id) {
            return BY_ID.apply(id);
        }
    }
    public enum ChassisMaterial {
        ALUMINUM, IRON, STEEL, NETHERITE, CHARGED_STEEL
    }
    public enum StorageCellsTier {
        NONE, BASIC, ADVANCED, ENDER_LINK
    }
    public enum Mood {
        NEUTRAL, HAPPY, ANNOYED, SLEEP, CONFUSED, CRYING, EVIL, PLEASED, SAD, SURPRISED
    }
    public enum AttackMode {
        PASSIVE, ASSIST, AGGRESSIVE
    }

    public ByteBuddyEntity(EntityType<? extends PathfinderMob> entityType, Level level) {
        super(entityType, level);
    }

    private final EnderlinkAwareInventory mainInv = new EnderlinkAwareInventory(this);
    private final EnergyStorage energyStorage = new EnergyStorage(16000, 256, 256);
    private BuddyRole buddyRole = BuddyRole.NONE;
    private final Set<Goal> roleGoals = new HashSet<>();
    @Nullable private BlockPos dockPos;
    private final DiskEffects diskEffects = new DiskEffects();
    private final AugmentEffects augmentEffects = new AugmentEffects();
    private static final ChassisMaterial defaultChassis = ChassisMaterial.ALUMINUM;
    private double augmentPrevX;
    private double augmentPrevY;
    private double augmentPrevZ;
    private double augmentMoveAccumMeters;
    private static final String momentumUntil = "dynamo_momentum_until";

    private boolean farmingEnabled = false;
    private boolean harvestEnabled = false;
    private boolean plantEnabled = false;
    private boolean tillEnabled = false;

    private boolean miningEnabled = false;
    private boolean quarryEnabled = false;

    private boolean haulingEnabled = false;

    private boolean animalEnabled = false;
    private boolean shearEnabled = false;
    private boolean milkEnabled = false;
    private boolean breedEnabled = false;
    private boolean cullEnabled = false;
    private boolean leashEnabled = false;

    private static final int baseCooldown = 20;
    private long cooldownUntil = 0L;

    private static final EntityDataAccessor<Optional<UUID>> OWNER_UUID =
            SynchedEntityData.defineId(ByteBuddyEntity.class, EntityDataSerializers.OPTIONAL_UUID);

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
    private static final EntityDataAccessor<Boolean> DATA_SLICING =
            SynchedEntityData.defineId(ByteBuddyEntity.class, EntityDataSerializers.BOOLEAN);
    public static final EntityDataAccessor<Integer> DATA_ROLE =
            SynchedEntityData.defineId(ByteBuddyEntity.class, EntityDataSerializers.INT);

    private static final EntityDataAccessor<Integer> DATA_ENERGY =
            SynchedEntityData.defineId(ByteBuddyEntity.class, EntityDataSerializers.INT);

    private static final EntityDataAccessor<Integer> CHASSIS_MATERIAL =
            SynchedEntityData.defineId(ByteBuddyEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> STORAGE_CELLS_TIER =
            SynchedEntityData.defineId(ByteBuddyEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DISPLAY_RGB =
            SynchedEntityData.defineId(ByteBuddyEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> MOOD_ID =
            SynchedEntityData.defineId(ByteBuddyEntity.class, EntityDataSerializers.INT);

    private static final EntityDataAccessor<Integer> ATTACK_MODE =
            SynchedEntityData.defineId(ByteBuddyEntity.class, EntityDataSerializers.INT);

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(OWNER_UUID, Optional.empty());
        this.entityData.define(DATA_SLEEPING, true);
        this.entityData.define(DATA_WAKING, false);
        this.entityData.define(DATA_WAVING, false);
        this.entityData.define(DATA_WORKING, false);
        this.entityData.define(DATA_SLAMMING, false);
        this.entityData.define(DATA_SLICING, false);
        this.entityData.define(DATA_ROLE, BuddyRole.NONE.ordinal());
        this.entityData.define(CHASSIS_MATERIAL, defaultChassis.ordinal());
        this.entityData.define(STORAGE_CELLS_TIER, StorageCellsTier.NONE.ordinal());
        this.entityData.define(DATA_ENERGY, 0);
        this.entityData.define(DISPLAY_RGB, DyeColor.CYAN.getFireworkColor());
        this.entityData.define(MOOD_ID, Mood.NEUTRAL.ordinal());
        this.entityData.define(ATTACK_MODE, AttackMode.PASSIVE.ordinal());
    }

    private final NonNullList<ItemStack> armorItems = NonNullList.withSize(4, ItemStack.EMPTY);

    public static AttributeSupplier.Builder createAttributes() {
        return Animal.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 16.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.24D)
                .add(Attributes.FOLLOW_RANGE, 18.0D)
                .add(Attributes.ATTACK_DAMAGE, 1.0D)
                .add(Attributes.ATTACK_SPEED, 1.6D)
                .add(Attributes.ATTACK_KNOCKBACK, 0.5D)
                .add(Attributes.ARMOR, 0.0D)
                .add(Attributes.ARMOR_TOUGHNESS, 0.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.05D);
    }

    @Override
    public void onAddedToWorld() {
        super.onAddedToWorld();
        if (!level().isClientSide) {
            augmentPrevX = this.getX();
            augmentPrevY = this.getY();
            augmentPrevZ = this.getZ();
            refreshEffects();
            refreshCombatGoals();
        }
    }

    @Override
    protected void checkInsideBlocks() {

    }

    public void reloadBuddy(){
        disableTasks();
        rebuildGoalsForRole();
        refreshEffects();
    }

    @Override
    public @NotNull ItemStack getMainHandItem() {
        return mainInv.getStackInSlot(0);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new AccessInventoryGoal(this));
        this.goalSelector.addGoal(0, new GatedGoal(
                this, () -> this.canAct() && this.canSwim(), new FloatGoal(this), 0));
        this.goalSelector.addGoal(1, new GatedGoal(
                this, () -> this.canAct() && this.canPanic(), new PanicGoal(this, 2.0D), 0));
        this.goalSelector.addGoal(2, new GatedGoal(
                this, () -> this.canAct() && this.getDock().isEmpty() && (this.getTarget() == null || !this.getTarget().isAlive()), new BuddyFollowOwnerGoal(this, 1.05, 4.0f, 16.0f, true), 0));
        this.goalSelector.addGoal(5, new GatedGoal(
                this, this::canAct, new BuddyPickUpItemGoal(this, 1.2, 6.0, 0.9, 10), 8));
        this.goalSelector.addGoal(6, new GatedGoal(
                this, () -> this.canAct() && this.isInventoryFull(), new DepositToDockGoal(this), 0));
        this.goalSelector.addGoal(7, new GatedGoal(
                this, this::canAct, new RandomStrollGoal(this, 0.75D), 0));
        this.goalSelector.addGoal(8, new GatedGoal(
                this, this::canAct, new LookAtPlayerGoal(this, Player.class, 3f), 0));
        this.goalSelector.addGoal(9, new GatedGoal(
                this, this::canAct, new RandomLookAroundGoal(this), 0));
        this.goalSelector.addGoal(10, new GatedGoal(this, this::canAct, new RandomWaveAtFriendGoal(this, 6.0D, 120, 40), 0));
    }

    public FreezableAnimationState idleAnimationState = new FreezableAnimationState();
    private int idleAnimationTimeout = 0;
    public final FreezableAnimationState sleepPoseState = new FreezableAnimationState();
    public final FreezableAnimationState wakeUpState = new FreezableAnimationState();
    private int wakingAnimationTimeout = 0;
    public final FreezableAnimationState workingState = new FreezableAnimationState();
    public final FreezableAnimationState slamState = new FreezableAnimationState();
    public final FreezableAnimationState sliceState = new FreezableAnimationState();
    public final FreezableAnimationState waveState = new FreezableAnimationState();

    private boolean wasPoweredDown = false;
    private Mood savedMoodBeforePowerdown = null;

    @Override
    protected void updateWalkAnimation(float pPartialTick) {
        if (getSyncedEnergy() <= 0) {
            return;
        }
        float f;
        if(this.getPose() == Pose.STANDING) {
            f = Math.min(pPartialTick * 6F, 1f);
        } else {
            f = 0f;
        }
        this.walkAnimation.update(f, 0.2f);
    }

    public Optional<UUID> getOwnerUUID() {
        return this.entityData.get(OWNER_UUID);
    }

    public void setOwnerUUID(@Nullable UUID uuid) {
        this.entityData.set(OWNER_UUID, Optional.ofNullable(uuid));
    }

    public void clearOwner() {
        this.entityData.set(OWNER_UUID, Optional.empty());
    }

    public boolean hasOwner() {
        return this.entityData.get(OWNER_UUID).isPresent();
    }

    public boolean isOwnedBy(@Nullable LivingEntity entity) {
        if (entity == null) return false;
        return getOwnerUUID().map(entity.getUUID()::equals).orElse(false);
    }

    public ServerPlayer getOwner(ServerLevel serverLevel) {
        var id = getOwnerUUID();
        return id.map(buddyId -> serverLevel.getServer().getPlayerList().getPlayer(buddyId)).orElse(null);
    }

    public void setOwner(@Nullable Player player) {
        setOwnerUUID(player == null ? null : player.getUUID());
    }

    @Override
    public boolean isEffectiveAi() {
        return super.isEffectiveAi() && canAct();
    }

    public boolean isSleeping() {
        return this.entityData.get(DATA_SLEEPING);
    }

    public void setSleeping(boolean sleeping) {
        this.entityData.set(DATA_SLEEPING, sleeping);
        if (level() instanceof ServerLevel serverLevel) {
            if (sleeping) {
                armCooldown(serverLevel, Integer.MAX_VALUE);
                setMood(Mood.SLEEP);
                getNavigation().stop();
                setDeltaMovement(0, getDeltaMovement().y * 0.0, 0);
                setAggressive(false);
                setWorking(false);
                setSlamming(false);
                setMood(Mood.SLEEP);
            } else {
                armCooldown(serverLevel, 8);
            }
        }
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

    public boolean isSlicing() {
        return this.entityData.get(DATA_SLICING);
    }

    public void setSlicing(boolean slicing) {
        this.entityData.set(DATA_SLICING, slicing);
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

    public boolean isPlantEnabled() {
        return this.farmingEnabled && this.plantEnabled;
    }

    public boolean isTillEnabled() {
        return this.farmingEnabled && this.tillEnabled;
    }

    public void setMiningEnabled(boolean miningEnabled) {
        this.miningEnabled = miningEnabled;
    }

    private boolean isMiningEnabled() {
        return this.miningEnabled;
    }

    public void setHaulingEnabled(boolean haulingEnabled) {
        this.haulingEnabled = haulingEnabled;
    }

    public boolean isHaulingEnabled() {
        return this.haulingEnabled;
    }

    public void setAnimalEnabled(boolean enabled) { this.animalEnabled = enabled; }
    public boolean isAnimalEnabled() { return this.animalEnabled; }

    public void setShearEnabled(boolean enabled) { this.shearEnabled = enabled; }
    public boolean isShearEnabled() { return this.animalEnabled && this.shearEnabled; }

    public void setMilkEnabled(boolean enabled) { this.milkEnabled = enabled; }
    public boolean isMilkEnabled() { return this.animalEnabled && this.milkEnabled; }

    public void setBreedEnabled(boolean enabled) { this.breedEnabled = enabled; }
    public boolean isBreedEnabled() { return this.animalEnabled && this.breedEnabled; }

    public void setCullEnabled(boolean enabled) { this.cullEnabled = enabled; }
    public boolean isCullEnabled() { return this.animalEnabled && this.cullEnabled; }

    public void setLeashEnabled(boolean enabled) { this.leashEnabled = enabled; }
    public boolean isLeashEnabled() { return this.animalEnabled && this.leashEnabled; }

    public boolean cooldownActive(ServerLevel serverLevel) {
        return serverLevel.getGameTime() < this.cooldownUntil;
    }

    public void armCooldown(ServerLevel serverLevel, int ticks) {
        this.cooldownUntil = serverLevel.getGameTime() + ticks;
    }

    public void setQuarryEnabled(boolean quarryEnabled) {
        this.quarryEnabled = quarryEnabled;
    }

    private boolean isQuarryEnabled() {
        return this.miningEnabled & this.quarryEnabled;
    }

    public boolean canAct() {
        return !isSleeping() && getEnergyStored() > 0;
    }

    public boolean canPanic()   {
        return getBuddyRole() != BuddyRole.COMBAT;
    }

    public AttackMode getAttackMode() {
        int i = entityData.get(ATTACK_MODE);
        return AttackMode.values()[Mth.clamp(i, 0, AttackMode.values().length - 1)];
    }

    public void setAttackMode(AttackMode mode) {
        entityData.set(ATTACK_MODE, mode.ordinal());
        if (!level().isClientSide) {
            refreshCombatGoals();
        }
    }

    private static AttackMode cycleAttackMode(AttackMode attackMode, boolean backwards) {
        AttackMode[] attackValues = AttackMode.values();
        int attackModes = attackValues.length;
        int i = attackMode.ordinal();
        int next = backwards ? (i - 1 + attackModes) % attackModes : (i + 1) % attackModes;
        return attackValues[next];
    }

    @Override
    public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor pLevel, DifficultyInstance pDifficulty, MobSpawnType pReason, @Nullable SpawnGroupData pSpawnData, @Nullable CompoundTag pDataTag) {
        SpawnGroupData spawnData = super.finalizeSpawn(pLevel, pDifficulty, pReason, pSpawnData, pDataTag);
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
        if (slot == EquipmentSlot.MAINHAND) return mainInv.getStackInSlot(0);
        if (slot == EquipmentSlot.OFFHAND)  return ItemStack.EMPTY;
        return this.armorItems.get(slot.getIndex());
    }

    @Override
    public void setItemSlot(EquipmentSlot slot, ItemStack itemStack) {
        if (slot == EquipmentSlot.MAINHAND) {
            if (isAnyTool(itemStack)) {
                ItemStack originalItemStack = mainInv.getStackInSlot(0);
                ItemStack newItemStack = itemStack.copy();
                newItemStack.setCount(1);
                mainInv.setStackInSlot(0, newItemStack);
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
        return mainInv.getStackInSlot(0);
    }

    public int getBatterySlot() {
        return 3;
    }

    public ItemStack getBattery() {
        return mainInv.getStackInSlot(3);
    }

    public boolean isInventoryFull() {
        ItemStackHandler mainInventory = getMainInv();
        for (int slot = FIRST_CARGO_SLOT; slot <= lastCargoSlot(); slot++) {
            ItemStack itemStack = mainInventory.getStackInSlot(slot);
            if (itemStack.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    public boolean canFitInInventory(ItemStack incomingStack) {
        if (incomingStack == null || incomingStack.isEmpty()) {
            return false;
        }

        ItemStackHandler mainInventory = getMainInv();
        ItemStack toInsert = incomingStack.copy();
        for (int slot = FIRST_CARGO_SLOT; slot <= lastCargoSlot(); slot++) {
            if (!mainInventory.isItemValid(slot, toInsert)) {
                continue;
            }
            toInsert = mainInventory.insertItem(slot, toInsert, true);
            if (toInsert.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    public int getFirstCargoSlot() { return FIRST_CARGO_SLOT; }
    public int getLastCargoSlot() { return lastCargoSlot(); }

    public ItemStack addToCargo(ItemStack stack) {
        ItemStack remainder = stack.copy();
        for (int slot = FIRST_CARGO_SLOT; slot <= lastCargoSlot(); slot++) {
            remainder = mainInv.insertItem(slot, remainder, false);
            if (remainder.isEmpty()) return ItemStack.EMPTY;
        }
        return remainder;
    }

    private static final int FIRST_CARGO_SLOT = 9;
    private int lastCargoSlot() {
        int extraSlots = getStorageCellsExtraSlots();
        if (extraSlots >= 18) {
            return 35;
        } else if (extraSlots >= 9) {
            return 26;
        } else {
            return 17;
        }
    }

    @Override
    protected void dropCustomDeathLoot(DamageSource pSource, int pLooting, boolean pRecentlyHit) {
        super.dropCustomDeathLoot(pSource, pLooting, pRecentlyHit);
        dropAllFromHandler(getMainInv());
    }

    private void dropAllFromHandler(IItemHandler itemHandler) {
        for (int slotIndex = 0; slotIndex < itemHandler.getSlots(); slotIndex++) {
            ItemStack itemStack = itemHandler.getStackInSlot(slotIndex);
            if (!itemStack.isEmpty()) {
                spawnAtLocation(itemStack.copy());
                if (itemHandler instanceof ItemStackHandler mutableHandler) {
                    mutableHandler.setStackInSlot(slotIndex, ItemStack.EMPTY);
                } else {
                    itemHandler.extractItem(slotIndex, itemStack.getCount(), false);
                }
            }
        }
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
                consumeEnergy(5);
            }

            if (tickCount % 20 == 0) {
                BatteryItem.buddyDrainBatteries(this);
                tickAugmentRuntime();
                if (supportAuraEnabled()) {
                    SupportAuras.tickSupportLattice(this);
                }
            }

            int currentEnergy = this.energyStorage.getEnergyStored();
            if (currentEnergy != getSyncedEnergy()) {
                setSyncedEnergy(currentEnergy);
            }

            if (currentEnergy == 0 && !wasPoweredDown) {
                savedMoodBeforePowerdown = getMood();
                setMood(Mood.SLEEP);
                wasPoweredDown = true;
            } else if (currentEnergy > 0 && wasPoweredDown) {
                setMood(savedMoodBeforePowerdown != null ? savedMoodBeforePowerdown : Mood.NEUTRAL);
                savedMoodBeforePowerdown = null;
                wasPoweredDown = false;
            }

            if (getEnergyStored() > 0) {
                tickPropellerPhysics();
                tickDynamoMovement();
                tickMomentumExpiry();
                tickMagnet();
            }

            if (!isSleeping() && getEnergyStored() > 0 && isOutsideTether()) {
                BlockPos dock = getDock().orElse(null);
                if (dock != null) {
                    Vec3 center = Vec3.atCenterOf(dock);
                    getNavigation().moveTo(center.x, center.y, center.z, 1.2);
                }
            }
        } else {
            setupAnimationStates();
        }
    }

    private void setupAnimationStates(){
        if (getSyncedEnergy() <= 0) {
            if (isSleeping()) {
                if (!sleepPoseState.isStarted()) {
                    sleepPoseState.start(this.tickCount);
                }
                if (!idleAnimationState.isStarted()) {
                    idleAnimationState.start(this.tickCount);
                }
                idleAnimationState.freeze();
                wakeUpState.freeze();
                workingState.freeze();
                slamState.freeze();
                sliceState.freeze();
                waveState.freeze();
            } else {
                if (!idleAnimationState.isStarted()) {
                    idleAnimationState.start(this.tickCount);
                }
                idleAnimationState.freeze();
                sleepPoseState.freeze();
                wakeUpState.freeze();
                workingState.freeze();
                slamState.freeze();
                sliceState.freeze();
                waveState.freeze();
            }
            return;
        }
        idleAnimationState.unfreeze();
        sleepPoseState.unfreeze();
        wakeUpState.unfreeze();
        workingState.unfreeze();
        slamState.unfreeze();
        sliceState.unfreeze();
        waveState.unfreeze();

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

        if (isSlicing()) {
            if (!this.sliceState.isStarted()) {
                this.sliceState.start(this.tickCount);
            }
        } else {
            if (this.sliceState.isStarted()) this.sliceState.stop();
        }
    }

    @Override
    public void jumpFromGround() {
        super.jumpFromGround();
        if (this.canFly()) {
            Vec3 currentDelta = this.getDeltaMovement();

            double boostedY = currentDelta.y + 0.25D;
            if (boostedY > 0.70D) {
                boostedY = 0.70D;
            }

            this.setDeltaMovement(currentDelta.x, boostedY, currentDelta.z);
            this.hasImpulse = true;
        }
    }

    public void onTaskSuccess(TaskType taskType, BlockPos blockPos) {
        DiskHooks.tryGiveByproduct(this, taskType, blockPos);
        DiskHooks.trySpawnHologram(this, taskType);
        addDynamoEffect();
    }

    public ChassisMaterial getChassisMaterial() {
        int ordinal = this.entityData.get(CHASSIS_MATERIAL);
        int max = ChassisMaterial.values().length - 1;
        int clamped = Mth.clamp(ordinal, 0, max);
        return ChassisMaterial.values()[clamped];
    }

    public void setChassisMaterial(ChassisMaterial material) {
        this.entityData.set(CHASSIS_MATERIAL, material.ordinal());
    }

    public void computeChassis() {
        if (level().isClientSide) return;
        ChassisMaterial computed = computeChassisTier();
        ChassisMaterial current = getChassisMaterial();
        if (computed != current) {
            setChassisMaterial(computed);
        }
    }

    private ChassisMaterial computeChassisTier() {
        ChassisMaterial chassisMaterial = ChassisMaterial.ALUMINUM;
        for (int i = 1; i < 3; i++) {
            ItemStack stack = this.mainInv.getStackInSlot(i);
            if (stack.is(ModItems.REINFORCED_CHARGED_STEEL_PLATING.get())) {
                chassisMaterial = ChassisMaterial.CHARGED_STEEL;
            } else if (stack.is(ModItems.REINFORCED_NETHERITE_PLATING.get())) {
                if (chassisMaterial.ordinal() < ChassisMaterial.NETHERITE.ordinal()) {
                    chassisMaterial = ChassisMaterial.NETHERITE;
                }
            } else if (stack.is(ModItems.REINFORCED_STEEL_PLATING.get())) {
                if (chassisMaterial.ordinal() < ChassisMaterial.STEEL.ordinal()) {
                    chassisMaterial = ChassisMaterial.STEEL;
                }
            } else if (stack.is(ModItems.REINFORCED_IRON_PLATING.get())) {
                if (chassisMaterial.ordinal() < ChassisMaterial.IRON.ordinal()) {
                    chassisMaterial = ChassisMaterial.IRON;
                }
            }
        }

        return chassisMaterial;
    }

    public StorageCellsTier getStorageCellsTier() {
        int ordinal = entityData.get(STORAGE_CELLS_TIER);
        return StorageCellsTier.values()[Mth.clamp(ordinal, 0, StorageCellsTier.values().length-1)];
    }

    public void setStorageCellsTier(StorageCellsTier tier) {
        entityData.set(STORAGE_CELLS_TIER, tier.ordinal());
    }

    public boolean isEnderlinkActive() {
        return augmentEffects.enderLinkEnabled();
    }

    public int getStorageCellsExtraSlots() {
        return switch (getStorageCellsTier()) {
            case BASIC -> 9;
            case ADVANCED, ENDER_LINK -> 18;
            default -> 0;
        };
    }

    private StorageCellsTier computeStorageCellsTierFromMainInv() {
        StorageCellsTier best = StorageCellsTier.NONE;
        for (int slot = 1; slot <= 2; slot++) {
            ItemStack storageCellInSlot = mainInv.getStackInSlot(slot);
            if (storageCellInSlot.isEmpty()) continue;
            if (storageCellInSlot.is(ModItems.ENDERLINK_STORAGE_CELL.get())) {
                best = StorageCellsTier.ENDER_LINK;
            } else if (storageCellInSlot.is(ModItems.ADVANCED_STORAGE_CELL.get())) {
                if (best.ordinal() < StorageCellsTier.ADVANCED.ordinal()) best = StorageCellsTier.ADVANCED;
            } else if (storageCellInSlot.is(ModItems.BASIC_STORAGE_CELL.get())) {
                if (best.ordinal() < StorageCellsTier.BASIC.ordinal()) best = StorageCellsTier.BASIC;
            }
        }
        return best;
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> dataAccessor) {
        super.onSyncedDataUpdated(dataAccessor);
        if (CHASSIS_MATERIAL.equals(dataAccessor)) {

        }
    }

    public int getDisplayColorRGB() {
        return this.entityData.get(DISPLAY_RGB);
    }

    public void setDisplayColorRGB(int rgb) {
        this.entityData.set(DISPLAY_RGB, rgb & 0x00FFFFFF);
    }

    public Mood getMood() {
        int id = this.entityData.get(MOOD_ID);
        Mood[] values = Mood.values();
        return values[Math.max(0, Math.min(id, values.length - 1))];
    }

    public void setMood(Mood mood) {
        this.entityData.set(MOOD_ID, mood.ordinal());
    }

    private void cycleMood(boolean backwards) {
        Mood[] values = Mood.values();
        int n = values.length;
        int idx = getMood().ordinal();
        int next = (idx + (backwards ? -1 : 1) + n) % n;
        setMood(values[next]);
    }

    public ResourceLocation getMoodTexture() {
        return switch (getMood()) {
            case HAPPY -> resourceLocation("textures/entity/bytebuddy/faces/happy.png");
            case ANNOYED -> resourceLocation("textures/entity/bytebuddy/faces/annoyed.png");
            case SLEEP -> resourceLocation("textures/entity/bytebuddy/faces/sleep.png");
            case CONFUSED -> resourceLocation("textures/entity/bytebuddy/faces/confused.png");
            case CRYING -> resourceLocation("textures/entity/bytebuddy/faces/crying.png");
            case EVIL -> resourceLocation("textures/entity/bytebuddy/faces/evil.png");
            case PLEASED -> resourceLocation("textures/entity/bytebuddy/faces/pleased.png");
            case SAD -> resourceLocation("textures/entity/bytebuddy/faces/sad.png");
            case SURPRISED -> resourceLocation("textures/entity/bytebuddy/faces/surprised.png");
            default -> resourceLocation("textures/entity/bytebuddy/faces/neutral.png");
        };
    }

    private static ResourceLocation resourceLocation(String path) {
        return new ResourceLocation(ByteBuddies.MOD_ID, path);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag nbtData) {
        super.addAdditionalSaveData(nbtData);
        getOwnerUUID().ifPresent(uuid -> nbtData.putUUID("Owner", uuid));
        nbtData.putInt("role", this.buddyRole.ordinal());

        if (this.dockPos != null) {
            nbtData.put("dockPos", NbtUtils.writeBlockPos(this.dockPos));
        }

        nbtData.putBoolean("Sleeping", this.isSleeping());
        nbtData.putBoolean("FarmingEnabled", this.farmingEnabled);
        nbtData.putBoolean("HarvestEnabled", this.harvestEnabled);
        nbtData.putBoolean("PlantEnabled", this.plantEnabled);
        nbtData.putBoolean("TillEnabled", this.tillEnabled);
        nbtData.putBoolean("MiningEnabled", this.miningEnabled);
        nbtData.putBoolean("QuarryEnabled", this.quarryEnabled);
        nbtData.putBoolean("HaulingEnabled", this.haulingEnabled);

        nbtData.put("MainInv", this.mainInv.serializeNBT());

        nbtData.putInt("Energy", this.energyStorage.getEnergyStored());
        nbtData.putInt("ChassisMaterial", this.entityData.get(CHASSIS_MATERIAL));
        nbtData.putInt("StorageTier", this.entityData.get(STORAGE_CELLS_TIER));
        nbtData.putInt("AttackMode", this.entityData.get(ATTACK_MODE));
        nbtData.putInt("DisplayRGB", this.entityData.get(DISPLAY_RGB));

        Mood moodToSave = (wasPoweredDown && savedMoodBeforePowerdown != null)
                ? savedMoodBeforePowerdown
                : Mood.values()[Mth.clamp(this.entityData.get(MOOD_ID), 0, Mood.values().length - 1)];
        nbtData.putString("Mood", moodToSave.name());
    }

    @Override
    public void readAdditionalSaveData(@NotNull CompoundTag nbtData) {
        super.readAdditionalSaveData(nbtData);
        if (nbtData.hasUUID("Owner")) setOwnerUUID(nbtData.getUUID("Owner")); else clearOwner();

        boolean sleeping = nbtData.getBoolean("Sleeping");
        setSleeping(sleeping);
        setNoAi(sleeping);

        int roleOrdinal = Mth.clamp(nbtData.getInt("role"), 0, BuddyRole.values().length - 1);
        this.buddyRole = BuddyRole.values()[roleOrdinal];
        this.entityData.set(DATA_ROLE, roleOrdinal);

        if (nbtData.contains("dockPos")) {
            this.dockPos = NbtUtils.readBlockPos(nbtData.getCompound("dockPos"));
        } else {
            this.dockPos = null;
        }

        if (nbtData.contains("FarmingEnabled")) this.farmingEnabled = nbtData.getBoolean("FarmingEnabled");
        if (nbtData.contains("HarvestEnabled")) this.harvestEnabled = nbtData.getBoolean("HarvestEnabled");
        if (nbtData.contains("PlantEnabled")) this.plantEnabled = nbtData.getBoolean("PlantEnabled");
        if (nbtData.contains("TillEnabled")) this.tillEnabled = nbtData.getBoolean("TillEnabled");
        if (nbtData.contains("MiningEnabled")) this.miningEnabled = nbtData.getBoolean("MiningEnabled");
        if (nbtData.contains("QuarryEnabled")) this.quarryEnabled = nbtData.getBoolean("QuarryEnabled");
        if (nbtData.contains("HaulingEnabled")) this.haulingEnabled = nbtData.getBoolean("HaulingEnabled");


        if (nbtData.contains("MainInv")) this.mainInv.deserializeNBT(nbtData.getCompound("MainInv"));

        if (nbtData.contains("Energy")) {
            setEnergyUnsafe(nbtData.getInt("Energy"));
            setSyncedEnergy(this.energyStorage.getEnergyStored());
        }

        if (nbtData.contains("ChassisMaterial")) {
            int ordinal = Mth.clamp(nbtData.getInt("ChassisMaterial"), 0, ChassisMaterial.values().length - 1);
            this.entityData.set(CHASSIS_MATERIAL, ordinal);
        } else {
            this.entityData.set(CHASSIS_MATERIAL, defaultChassis.ordinal());
        }

        if (nbtData.contains("StorageTier")) {
            int ordinal = Mth.clamp(nbtData.getInt("StorageTier"), 0, StorageCellsTier.values().length - 1);
            this.entityData.set(STORAGE_CELLS_TIER, ordinal);
        } else {
            this.entityData.set(STORAGE_CELLS_TIER, StorageCellsTier.NONE.ordinal());
        }

        if (nbtData.contains("AttackMode")) {
            int ordinal = Mth.clamp(nbtData.getInt("AttackMode"), 0, AttackMode.values().length - 1);
            this.entityData.set(ATTACK_MODE, ordinal);
        }

        if (nbtData.contains("DisplayRGB")) this.entityData.set(DISPLAY_RGB, nbtData.getInt("DisplayRGB"));

        int moodOrdinal = 0;
        if (nbtData.contains("Mood")) {
            try {
                moodOrdinal = Mood.valueOf(nbtData.getString("Mood")).ordinal();
            } catch (IllegalArgumentException ignored) {}
        } else if (nbtData.contains("MoodId")) {
            moodOrdinal = nbtData.getInt("MoodId");
        }
        this.entityData.set(MOOD_ID, Mth.clamp(moodOrdinal, 0, Mood.values().length - 1));

        if (this.energyStorage.getEnergyStored() == 0) {
            savedMoodBeforePowerdown = Mood.values()[Mth.clamp(moodOrdinal, 0, Mood.values().length - 1)];
            this.entityData.set(MOOD_ID, Mood.SLEEP.ordinal());
            wasPoweredDown = true;
        }

        if (!level().isClientSide) {
            rebuildGoalsForRole();
            refreshEffects();
            refreshCombatGoals();
        }
    }

    @Override
    protected @NotNull InteractionResult mobInteract(Player player, InteractionHand interactionHand) {
        if (!level().isClientSide) {
            if (isSleeping() && !hasOwner()) {
                setOwner(player);
                player.displayClientMessage(Component.literal("Buddy bound to " + player.getName().getString()), true);
                if (player instanceof ServerPlayer serverPlayer) {
                    openStorageMenu(serverPlayer, this);
                }
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
            } else if (inHand.is(Items.CHEST)) {
                setBuddyRole(BuddyRole.STORAGE);
                inHand.shrink(1);
                setHaulingEnabled(true);
                player.displayClientMessage(Component.literal("Set bot role to " + buddyRole), true);
                return InteractionResult.sidedSuccess(level().isClientSide);
            } else if (inHand.is(Items.SHIELD)) {
                setBuddyRole(BuddyRole.COMBAT);
                inHand.shrink(1);
                setHaulingEnabled(true);
                player.displayClientMessage(Component.literal("Set bot role to " + buddyRole), true);
                return InteractionResult.sidedSuccess(level().isClientSide);
            } else if (inHand.getItem() instanceof DyeItem dyeItem) {
                DyeColor dye = dyeItem.getDyeColor();
                int rgb = dye.getFireworkColor();
                this.setDisplayColorRGB(rgb);

                if (!player.getAbilities().instabuild) {
                    inHand.shrink(1);
                }
                player.displayClientMessage(
                        Component.literal("Buddy color set to: " + dye.getName()),
                        true
                );
                return InteractionResult.sidedSuccess(level().isClientSide);
            }
            else if (inHand.is(Items.STICK)) {
                if (!level().isClientSide) {
                    boolean backwards = player.isShiftKeyDown();
                    cycleMood(backwards);
                    player.displayClientMessage(Component.literal("Face: " + getMood().name()), true);
                }
                return InteractionResult.sidedSuccess(level().isClientSide);
            }
            else if (inHand.getItem() instanceof SwordItem) {
                if (!level().isClientSide) {
                    boolean backwards = player.isShiftKeyDown();
                    AttackMode next = cycleAttackMode(getAttackMode(), backwards);
                    setAttackMode(next);
                    player.displayClientMessage(Component.literal("Mode: " + next.name()), true);
                }
                return InteractionResult.sidedSuccess(level().isClientSide);
            }


            if (isSleeping() && isOwnedBy(player) && mainHand.isEmpty() && offHand.isEmpty()) {
                if (getEnergyStored() > 0) {
                    awaken();
                } else if (player instanceof ServerPlayer serverPlayer) {
                    openStorageMenu(serverPlayer, this);
                }
                return InteractionResult.sidedSuccess(level().isClientSide);
            }

            if (!isSleeping() && !isWaking() && mainHand.isEmpty() && offHand.isEmpty()) {
                if (player instanceof ServerPlayer serverPlayer) {
                    openStorageMenu(serverPlayer, this);
                    return InteractionResult.sidedSuccess(level().isClientSide);
                }
            }
        }
        return super.mobInteract(player, interactionHand);
    }

    public static void openStorageMenu(ServerPlayer serverPlayer, ByteBuddyEntity byteBuddy) {
        byteBuddy.refreshEffects();

        MenuProvider provider = new SimpleMenuProvider((containerId, inventory, player) -> {
            int slots = byteBuddy.getStorageCellsExtraSlots();
            if (slots == 18) {
                return new ByteBuddyTripleMenu(containerId, inventory, byteBuddy);
            } else if (slots == 9) {
                return new ByteBuddyDoubleMenu(containerId, inventory, byteBuddy);
            } else {
                return new ByteBuddyMenu(containerId, inventory, byteBuddy);
            }
        }, Component.literal("ByteBuddy"));

        NetworkHooks.openScreen(serverPlayer, provider,
                buf -> buf.writeInt(byteBuddy.getId())
        );
    }

    private int openGuiCount = 0;
    @Nullable private UUID currentViewerId = null;

    public void onMenuOpened(Player player) {
        if (player == null || player.level().isClientSide) { return; }
        openGuiCount++;
        currentViewerId = player.getUUID();
    }

    public void onMenuClosed(Player player) {
        if (player == null || player.level().isClientSide) { return; }
        openGuiCount = Math.max(0, openGuiCount - 1);
        if (openGuiCount == 0) {
            currentViewerId = null;
        }
    }

    public boolean isInteracting() {
        return openGuiCount > 0;
    }

    @Nullable
    public Player getCurrentViewer() {
        if (!(level() instanceof ServerLevel serverLevel)) { return null; }
        if (currentViewerId == null) { return null; }
        return serverLevel.getPlayerByUUID(currentViewerId);
    }

    public void awaken() {
        if (!level().isClientSide && isSleeping()) {
            setSleeping(false);
            setNoAi(false);
            setWaking(true);
            if (getMood() == Mood.SLEEP) {
                setMood(Mood.NEUTRAL);
            }
        }
    }

    public ItemStackHandler getMainInv() {
        return this.mainInv;
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


    public void setBuddyRole(BuddyRole newRole) {
        if (newRole == null) return;
        this.buddyRole = newRole;
        this.entityData.set(DATA_ROLE, newRole.getId());
        reloadBuddy();
    }

    public BuddyRole getBuddyRole() {
        return BuddyRole.byId(this.entityData.get(DATA_ROLE));
    }

    public void setBuddyRoleById(int id) {
        setBuddyRole(BuddyRole.byId(id));
    }

    public Optional<BlockPos> getDock() {
        return Optional.ofNullable(this.dockPos);
    }

    public void setDock(BlockPos blockPos) {
        this.dockPos = blockPos.immutable();
        if (!level().isClientSide) {
            BotDebug.log(this, "[ByteBuddies] bot id=" + this.getId() + "dock set to " + blockPos);
            if (isEnderlinkActive()) {
                mainInv.migrateLocalCargoToDock();
            }
        }
    }

    public void clearDock() {
        this.dockPos = null;
    }

    public void refreshEffects() {
        this.diskEffects.recomputeFrom(mainInv);
        DiskHooks.applyDiskHealthBoost(this, diskEffects.healthBoostPercent());
        this.augmentEffects.recomputeFrom(this.mainInv, this);
        StorageCellsTier tierBefore = getStorageCellsTier();
        StorageCellsTier tierAfter = computeStorageCellsTierFromMainInv();
        if (!level().isClientSide ) {
            if (tierBefore != tierAfter) {
                setStorageCellsTier(tierAfter);
                enforceStorageCapacity();
            }
        }
    }

    private void enforceStorageCapacity() {
        if (level() instanceof ServerLevel serverLevel) {
            int extraSlots = getStorageCellsExtraSlots();

            final int coreStart = 9, coreEnd = 17;
            final int firstUpgradeStart = 18, firstUpgradeEnd = 26;
            final int secondUpgradeStart = 27, secondUpgradeEnd = 35;

            IItemHandler coreDestination = new RangeItemHandler(mainInv, coreStart, coreEnd);
            IItemHandler firstUpgradeDestination = (extraSlots >= 9) ? new RangeItemHandler(mainInv, firstUpgradeStart, firstUpgradeEnd) : null;
            IItemHandler secondUpgradeDestination = (extraSlots >= 18) ? new RangeItemHandler(mainInv, secondUpgradeStart, secondUpgradeEnd) : null;

            Function<ItemStack, ItemStack> tryInsert = itemStack -> {
                if (itemStack.isEmpty()) return ItemStack.EMPTY;
                ItemStack leftoverItems = ItemHandlerHelper.insertItemStacked(coreDestination, itemStack, false);
                if (leftoverItems.isEmpty()) return ItemStack.EMPTY;
                if (firstUpgradeDestination != null) {
                    leftoverItems = ItemHandlerHelper.insertItemStacked(firstUpgradeDestination, leftoverItems, false);
                    if (leftoverItems.isEmpty()) return ItemStack.EMPTY;
                }
                if (secondUpgradeDestination != null) {
                    leftoverItems = ItemHandlerHelper.insertItemStacked(secondUpgradeDestination, leftoverItems, false);
                }
                return leftoverItems;
            };

            if (extraSlots < 18) {
                for (int slot = secondUpgradeStart; slot <= secondUpgradeEnd; slot++) {
                    ItemStack stackInSlot = mainInv.getStackInSlot(slot);
                    if (stackInSlot.isEmpty()) continue;

                    mainInv.setStackInSlot(slot, ItemStack.EMPTY);
                    ItemStack leftover = tryInsert.apply(stackInSlot.copy());
                    if (!leftover.isEmpty()) {
                        Containers.dropItemStack(serverLevel, getX() + 0.5, getY() + 0.75, getZ() + 0.5, leftover);
                    }
                }
            }

            if (extraSlots < 9) {
                for (int slot = firstUpgradeStart; slot <= firstUpgradeEnd; slot++) {
                    ItemStack stackInSlot = mainInv.getStackInSlot(slot);
                    if (stackInSlot.isEmpty()) continue;

                    mainInv.setStackInSlot(slot, ItemStack.EMPTY);
                    ItemStack leftover = tryInsert.apply(stackInSlot.copy());
                    if (!leftover.isEmpty()) {
                        Containers.dropItemStack(serverLevel, getX() + 0.5, getY() + 0.75, getZ() + 0.5, leftover);
                    }
                }
            }

            if (getStorageCellsTier() == StorageCellsTier.ENDER_LINK) {
                mainInv.migrateLocalCargoToDock();
            }
        }
    }


    @Override
    public boolean fireImmune() {
        return augmentEffects.fireResistant();
    }

    public ItemStack getClipboardStack() {
        return mainInv.getStackInSlot(8);
    }

    @Nullable
    public BlockPos getFirstPos() {
        ItemStack clipboard = getClipboardStack();
        return ClipboardItem.getFirstPosition(clipboard).orElse(null);
    }

    @Nullable
    public BlockPos getSecondPos() {
        ItemStack clipboard = getClipboardStack();
        return ClipboardItem.getSecondPosition(clipboard).orElse(null);
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

    public boolean isOutsideTether() {
        int radius = effectiveRadius();
        if (radius <= 0) return false;
        BlockPos dock = getDock().orElse(null);
        if (dock == null) return false;
        Vec3 center = Vec3.atCenterOf(dock);
        double dx = this.getX() - center.x;
        double dz = this.getZ() - center.z;
        double hard = radius + 1.0;
        return (dx * dx + dz * dz) > hard * hard;
    }

    public boolean isBlockWithinTether(BlockPos pos) {
        int radius = effectiveRadius();
        if (radius <= 0) return true;
        BlockPos dock = getDock().orElse(null);
        if (dock == null) return true;
        Vec3 center = Vec3.atCenterOf(dock);
        double dx = pos.getX() + 0.5 - center.x;
        double dz = pos.getZ() + 0.5 - center.z;
        return (dx * dx + dz * dz) <= (double) (radius * radius);
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

    public float hologramChance() {
        return this.diskEffects.hologramChance();
    }

    public boolean consumeEnergy(int energyCost) {
        int adjustedEnergyCost = Math.max(1, Math.round(energyCost * energyCostMultiplier()));
        if (this.energyStorage.getEnergyStored() >= adjustedEnergyCost) {
            this.energyStorage.extractEnergy(adjustedEnergyCost, false);
            return true;
        }
        this.energyStorage.extractEnergy(this.energyStorage.getEnergyStored(), false);
        return false;
    }

    private void rebuildGoalsForRole() {
        for (Goal goals : this.roleGoals) this.goalSelector.removeGoal(goals);
        this.roleGoals.clear();

        switch (getBuddyRole()) {
            case FARMER -> {
                enableFarming();
                this.roleGoals.add(new GatedGoal(this, () -> this.canAct() && this.isPlantEnabled(), new PlantGoal(this), scaledCooldownTicks()));
                this.roleGoals.add(new GatedGoal(this, () ->  this.canAct() && this.isHarvestEnabled(), new HarvestGoal(this), scaledCooldownTicks()));
                this.roleGoals.add(new GatedGoal(this, () -> this.canAct() && this.isTillEnabled(), new TillGoal(this), scaledCooldownTicks()));
            }
            case MINER -> {
                enableMining();
                this.roleGoals.add(new GatedGoal(this, () -> this.canAct() && this.isQuarryEnabled(), new QuarryGoal(this), scaledCooldownTicks()));
            }
            case COMBAT -> {
                // combat goals later
            }
            case POTION -> {
                // brewing goals later
            }
            case STORAGE -> {
                enabledStorage();
                this.roleGoals.add(new GatedGoal(this, () -> this.canAct() && this.isHaulingEnabled(), new HaulerGoal(this), scaledCooldownTicks()));
            }
            case ANIMAL -> {
                enableAnimal();
                this.roleGoals.add(new GatedGoal(this, () -> this.canAct() && (this.isShearEnabled() || this.isMilkEnabled()), new AnimalHarvestGoal(this), scaledCooldownTicks()));
                this.roleGoals.add(new GatedGoal(this, () -> this.canAct() && (this.isBreedEnabled() || this.isCullEnabled()), new AnimalManageGoal(this), scaledCooldownTicks()));
                this.roleGoals.add(new GatedGoal(this, () -> this.canAct() && this.isLeashEnabled(), new LeashHerdGoal(this), scaledCooldownTicks()));
            }
        }

        int priority = 2;
        for (Goal goals : roleGoals) this.goalSelector.addGoal(priority++, goals);
    }

    private void disableTasks() {
        setFarmingEnabled(false);
        setHarvestEnabled(false);
        setPlantEnabled(false);
        setTillEnabled(false);
        setMiningEnabled(false);
        setQuarryEnabled(false);
        setHaulingEnabled(false);
        setAnimalEnabled(false);
        setShearEnabled(false);
        setMilkEnabled(false);
        setBreedEnabled(false);
        setCullEnabled(false);
        setLeashEnabled(false);
    }

    private void enableFarming() {
        setFarmingEnabled(true);
        setHarvestEnabled(true);
        setPlantEnabled(true);
        setTillEnabled(true);
    }

    private void enableMining() {
        setMiningEnabled(true);
        setQuarryEnabled(true);
    }

    private void enabledStorage() {
        setHaulingEnabled(true);
    }

    private void enableAnimal() {
        setAnimalEnabled(true);
        setShearEnabled(true);
        setMilkEnabled(true);
        setBreedEnabled(true);
        setCullEnabled(true);
        setLeashEnabled(true);
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
        return GoalUtil.isStandableTerrain(level, blockPos);
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

    private void tickAugmentRuntime() {
        if (augmentEffects.solarEnabled()) {
            if (this.level().isDay() && this.level().canSeeSky(this.blockPosition())) {
                this.getEnergyStorage().receiveEnergy((int)Math.round(augmentEffects.solarChargePerSecond()), false);
            }
        }

        if (augmentEffects.geothermalEnabled()) {
            if (isNearHeatSource()) {
                this.getEnergyStorage().receiveEnergy((int)Math.round(augmentEffects.geothermalChargePerSecond()), false);
            }
        }

        if (augmentEffects.arcWelderEnabled()) {
            if (this.getHealth() < this.getMaxHealth()) {
                if (this.getEnergyStorage().extractEnergy(5, true) > 0) {
                    this.getEnergyStorage().extractEnergy(5, false);
                    this.heal((float) augmentEffects.selfRepairPerSecond());
                }
            }
        }
    }

    private void tickPropellerPhysics() {
        if (this.canFly()) {
            boolean isOnGround = this.onGround();
            boolean isInWater = this.isInWater();
            boolean isInLava  = this.isInLava();

            if (!isOnGround && !isInWater && !isInLava) {
                Vec3 currentDelta = this.getDeltaMovement();

                if (currentDelta.y < 0.0D) {
                    double newY = currentDelta.y * 0.60D;

                    if (newY < -0.12D) {
                        newY = -0.12D;
                    }

                    double newX = currentDelta.x * 0.98D;
                    double newZ = currentDelta.z * 0.98D;

                    this.setDeltaMovement(newX, newY, newZ);
                    this.hasImpulse = true;
                }
            }
        }
    }


    private boolean isNearHeatSource() {
        BlockPos origin = this.blockPosition();
        int radius = 2;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -1; dy <= 2; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    BlockPos blockPos = origin.offset(dx, dy, dz);
                    BlockState blockState = this.level().getBlockState(blockPos);
                    if (blockState.is(Blocks.LAVA) || blockState.is(Blocks.LAVA_CAULDRON) || blockState.is(Blocks.MAGMA_BLOCK)) {
                        return true;
                    }
                    if (blockState.getBlock() instanceof AbstractFurnaceBlock && blockState.getValue(AbstractFurnaceBlock.LIT)) {
                        return true;
                    }
                    if (blockState.getBlock() instanceof CampfireBlock && blockState.getValue(CampfireBlock.LIT)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private void tickDynamoMovement() {
        if (!augmentEffects.dynamoEnabled()) {
            return;
        }

        double dx = this.getX() - augmentPrevX;
        double dy = this.getY() - augmentPrevY;
        double dz = this.getZ() - augmentPrevZ;

        augmentPrevX = this.getX();
        augmentPrevY = this.getY();
        augmentPrevZ = this.getZ();

        double moved = Math.sqrt(dx*dx + dy*dy + dz*dz);
        augmentMoveAccumMeters += moved;

        if (augmentMoveAccumMeters >= 1.0) {
            int meters = (int)Math.floor(augmentMoveAccumMeters);
            augmentMoveAccumMeters -= meters;
            int charge = (int)Math.round(augmentEffects.dynamoChargePerMeter() * meters);
            if (charge > 0) {
                this.getEnergyStorage().receiveEnergy(charge, false);
            }
        }
    }

    public void addDynamoEffect() {
        if (augmentEffects.dynamoEnabled()) {
            addMomentum(augmentEffects.momentumTicks(), augmentEffects.momentumSpeedMultiplier());
        }
    }

    private void addMomentum(int ticks, double speedMultiplier) {
        if (ticks <= 0) {
            return;
        }

        if (Math.abs(speedMultiplier - 1.0) <= 1.0e-4) {
            return;
        }

        CoreAttributeModifiers.applyTransientModifier(
                this,
                Attributes.MOVEMENT_SPEED,
                "dynamo_momentum",
                speedMultiplier - 1.0,
                AttributeModifier.Operation.MULTIPLY_TOTAL
        );
        this.getPersistentData().putInt(momentumUntil, this.tickCount + ticks);
    }

    private void tickMomentumExpiry() {
        int until = this.getPersistentData().getInt(momentumUntil);
        if (until > 0 && this.tickCount >= until) {
            CoreAttributeModifiers.removeModifier(this, Attributes.MOVEMENT_SPEED, "dynamo_momentum");
            this.getPersistentData().remove(momentumUntil);
        }
    }

    private void tickMagnet() {
        if (!augmentEffects.magnetEnabled()) {
            return;
        }
        if (this.level().isClientSide) {
            return;
        }

        double radius = augmentEffects.magnetRadiusBlocks();
        if (radius <= 0.0) {
            return;
        }

        AABB boundingBox = new AABB(
                this.getX() - radius, this.getY() - radius, this.getZ() - radius,
                this.getX() + radius, this.getY() + radius, this.getZ() + radius
        );

        List<ItemEntity> items = this.level().getEntitiesOfClass(ItemEntity.class, boundingBox);
        for (ItemEntity item : items) {
            if (!item.isAlive()) {
                continue;
            }

            Vec3 toBuddy = new Vec3(
                    this.getX() - item.getX(),
                    (this.getY() + (this.getBbHeight()/2)) - item.getY(),
                    this.getZ() - item.getZ()
            );
            double distance = toBuddy.length();
            if (distance < 0.01) {
                continue;
            }

            if (distance <= 0.9) {
                pickupItem(item);
            }

            double pullFactor = Mth.clamp((radius - distance) / radius, 0.08, 0.9);
            double accel = 0.10 * pullFactor;
            Vec3 push = toBuddy.normalize().scale(accel);

            Vec3 newVelocity = item.getDeltaMovement().add(push);

            double maxSpeed = 0.35;
            double lenSq = newVelocity.lengthSqr();
            if (lenSq > (maxSpeed * maxSpeed)) {
                newVelocity = newVelocity.normalize().scale(maxSpeed);
            }

            item.setDeltaMovement(newVelocity);
            item.hasImpulse = true;
        }
    }

    private void pickupItem(ItemEntity itemEntity) {
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        ItemStack stack = itemEntity.getItem();
        int before = stack.getCount();

        ItemStack leftover = InventoryUtil.mergeInto(this.getMainInv(), stack);
        int taken = before - leftover.getCount();

        if (taken <= 0) {
            return;
        }

        serverLevel.getChunkSource().broadcastAndSend(
                this,
                new ClientboundTakeItemEntityPacket(itemEntity.getId(), this.getId(), taken)
        );

        serverLevel.playSound(
                null,
                this.getX(), this.getY(), this.getZ(),
                SoundEvents.ITEM_PICKUP,
                SoundSource.PLAYERS,
                0.2F,
                1.0F + (this.getRandom().nextFloat() - this.getRandom().nextFloat()) * 0.4F
        );

        if (leftover.isEmpty()) {
            itemEntity.discard();
        } else {
            itemEntity.setItem(leftover);
        }
    }

    public void onMeleeHit(LivingEntity victim) {
        if (augmentEffects.shockOnHit()) {
            victim.hurt(this.damageSources().generic(), 2.0F);
            victim.knockback(0.4, this.getX() - victim.getX(), this.getZ() - victim.getZ());
        }
    }

    public float rangedInaccuracyMultiplier() {
        if (augmentEffects.gyroEnabled()) {
            return 0.80F;
        } else {
            return 1.00F;
        }
    }

    public boolean canFly() {
        return augmentEffects.propellerEnabled();
    }

    public boolean canSwim() {
        return augmentEffects.aquaEnabled();
    }

    @Override
    public boolean canDrownInFluidType(FluidType type) {
        return false;
    }

    public boolean augmentEnderLinkEnabled() {
        return augmentEffects.enderLinkEnabled();
    }

    public AugmentEffects getAugmentEffects() {
        return augmentEffects;
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
        HAUL,
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

    private Goal followOwnerGoal;
    private Goal ownerHurtBY;
    private Goal ownerHurt;
    private Goal hurtAnythingHostile;
    private Goal meleeAttackGoal;
    private Goal hurtByTargetGoal;

    public void refreshCombatGoals() {
        if (followOwnerGoal != null) goalSelector.removeGoal(followOwnerGoal);
        if (ownerHurtBY != null) targetSelector.removeGoal(ownerHurtBY);
        if (ownerHurt != null) targetSelector.removeGoal(ownerHurt);
        if (hurtAnythingHostile != null) targetSelector.removeGoal(hurtAnythingHostile);
        if (meleeAttackGoal != null) goalSelector.removeGoal(meleeAttackGoal);
        if (hurtByTargetGoal != null) targetSelector.removeGoal(hurtByTargetGoal);

        boolean ableToFight = getBuddyRole() == BuddyRole.COMBAT;
        boolean hasOwner = getOwnerUUID().isPresent();
        boolean hasDock = getDock().isPresent();
        if (!(ableToFight && hasOwner && !hasDock)) return;

        followOwnerGoal = new BuddyFollowOwnerGoal(this, 1.05, 4.0f, 16.0f, true);
        goalSelector.addGoal(4, followOwnerGoal);

        meleeAttackGoal = new BuddyMeleeAttackGoal(this, 1.2, 2,true);
        goalSelector.addGoal(3, meleeAttackGoal);

        hurtByTargetGoal = new BuddyHurtByTargetGoal(this);
        targetSelector.addGoal(0, hurtByTargetGoal);

        switch (getAttackMode()) {
            case PASSIVE -> {

            }
            case ASSIST -> {
                ownerHurtBY = new BuddyOwnerHurtByTargetGoal(this);
                ownerHurt = new BuddyOwnerHurtTargetGoal(this);
                targetSelector.addGoal(1, ownerHurtBY);
                targetSelector.addGoal(2, ownerHurt);
            }
            case AGGRESSIVE -> {
                hurtAnythingHostile = new NearestAttackableTargetGoal<>(
                        this,
                        Monster.class,
                        10,
                        true,
                        false,
                        this::isValidAggressiveTarget
                );
                targetSelector.addGoal(1, hurtAnythingHostile);
            }
        }
    }

    private boolean isValidAggressiveTarget(LivingEntity livingEntity) {
        if (livingEntity instanceof Player player) {
            if (getOwnerUUID().map(player.getUUID()::equals).orElse(false)) {
                return false;
            }
            return !player.isCreative() && !player.isSpectator();
        }
        return true;
    }

    private final LazyOptional<IItemHandler> itemHandler = LazyOptional.of(this::getMainInv);
    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ITEM_HANDLER) {
            return itemHandler.cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        itemHandler.invalidate();
    }
}
