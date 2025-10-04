package net.turtleboi.bytebuddies.entity.entities;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
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
import net.turtleboi.bytebuddies.entity.ai.FarmHarvesterGoal;
import net.turtleboi.bytebuddies.entity.ai.RandomWaveAtFriendGoal;
import net.turtleboi.bytebuddies.item.custom.FloppyDiskItem.*;
import net.turtleboi.bytebuddies.util.EnergyHooks;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class ByteBuddyEntity extends PathfinderMob implements IEnergyStorage {
    public enum BotRole { FARMER, MINER, COMBAT, POTION, STORAGE, ANIMAL }
    private final ItemStackHandler mainInv = new ItemStackHandler(27);
    private final ItemStackHandler upgradeInv = new ItemStackHandler(3);

    // --- Energy (Forge Energy-like) ---
    private final EnergyStorage energy = new EnergyStorage(100_000, 400, 400);

    // --- Identity & config ---
    private BotRole role = BotRole.FARMER;
    private final Set<Goal> roleGoals = new HashSet<>();
    @Nullable private BlockPos dockPos;
    private int baseRadius = 8;
    private final DiskEffects effects = new DiskEffects();

    private static final EntityDataAccessor<Boolean> DATA_SLEEPING =
            SynchedEntityData.defineId(ByteBuddyEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_WAKING =
            SynchedEntityData.defineId(ByteBuddyEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_WAVING =
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
        this.goalSelector.addGoal(2, new FarmHarvesterGoal(this));
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
        builder.define(DATA_ROLE, BotRole.FARMER.ordinal());
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

    @Override
    public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
                                                  MobSpawnType spawnType, @Nullable SpawnGroupData spawnGroupData) {
        SpawnGroupData spawnData = super.finalizeSpawn(level, difficulty, spawnType, spawnGroupData);
        setSleeping(true);
        setNoAi(true);
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

            if (supportAuraEnabled() && tickCount % 10 == 0) {
                SupportAuras.tickSupportLattice(this);
            }

            if ((tickCount % 10) == 0) EnergyHooks.drainBatteries(this);
        } else {
            setupAnimationStates();
        }
    }

    public void onTaskSuccess(TaskType task, BlockPos blockPos) {
        DiskHooks.tryGiveByproduct(this, task, blockPos);
        DiskHooks.trySpawnHologram(this, task, blockPos);
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
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("role", role.ordinal());
    }
    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setSleeping(tag.getBoolean("Sleeping"));
        role = BotRole.values()[tag.getInt("role")];
        setNoAi(isSleeping());
    }

    @Override
    protected @NotNull InteractionResult mobInteract(Player player, InteractionHand interactionHand) {
        if (!level().isClientSide) {
            if (isSleeping()) {
                awaken();
                return InteractionResult.CONSUME;
            } else if (player.getItemInHand(interactionHand).getItem() == Items.WHEAT) {
                setRole(BotRole.FARMER);
                player.getItemInHand(interactionHand).shrink(1);
                player.displayClientMessage(Component.literal("Set bot role to " + role), true);
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

    public IEnergyStorage getEnergy() {
        return energy;
    }

    public BotRole getRole() {
        // prefer network value (authoritative)
        int ord = this.entityData.get(DATA_ROLE);
        return BotRole.values()[Mth.clamp(ord, 0, BotRole.values().length-1)];
    }

    public void setRole(BotRole newRole) {
        if (newRole == null) return;
        this.role = newRole;
        this.entityData.set(DATA_ROLE, newRole.ordinal());
        rebuildGoalsForRole();   // swap the AI set immediately
        refreshEffects();        // optional: some effects may depend on role
    }

    public Optional<BlockPos> getDock() {
        return Optional.ofNullable(dockPos);
    }

    public void setDock(BlockPos pos) {
        this.dockPos = pos.immutable();
        if (!level().isClientSide) {
            LogUtils.getLogger().info("[ByteBuddies] bot id={} dock set to {}", this.getId(), pos);
        }
    }


    public void clearDock() {
        this.dockPos = null;
    }

    // --- Derived stats from floppy disks ---
    public void refreshEffects() {
        effects.recomputeFrom(upgradeInv);
    }

    public int effectiveRadius() {
        return (int)Math.ceil(baseRadius * effects.radiusMultiplier()); // Blue
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

    public boolean consumeEnergy(int cost) {
        int adj = Math.max(1, Math.round(cost * energyCostMultiplier()));
        if (energy.getEnergyStored() >= adj) { energy.extractEnergy(adj, false); return true; }
        return false;
    }

    private void rebuildGoalsForRole() {
        // Remove old role-bound goals
        for (Goal goals : roleGoals) this.goalSelector.removeGoal(goals);
        roleGoals.clear();

        // Install the new set
        switch (getRole()) {
            case FARMER -> {
                roleGoals.add(new FarmHarvesterGoal(this));
                // add: new PlanterGoal(this), new ForesterGoal(this) as you implement
            }
            case MINER -> {
                //roleGoals.add(new MineFaceGoal(this));   // implement similar to farming
                // roleGoals.add(new VeinGoal(this));
            }
            case COMBAT -> {
                //roleGoals.add(new GuardGoal(this));
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
        // Register them with the selector at a sensible priority
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
        public static void tickSupportLattice(ByteBuddyEntity bot) {
            Level lvl = bot.level();
            BlockPos center = bot.blockPosition();
            int r = 5; // scale by tier if you want

            // Nudge nearby crops forward rarely
            if (lvl.random.nextFloat() < 0.10f) {
                BlockPos.betweenClosedStream(center.offset(-r, -1, -r), center.offset(r, 2, r)).limit(24).forEach(p -> {
                    BlockState st = lvl.getBlockState(p);
                    if (st.getBlock() instanceof CropBlock crop && !crop.isMaxAge(st)) {
                        if (lvl.random.nextFloat() < 0.05f) {
                            lvl.setBlock(p, crop.getStateForAge(crop.getAge(st)+1), 3);
                        }
                    }
                });
            }

            // Tiny buff to owner (if nearby)
            List<Player> players = lvl.getEntitiesOfClass(Player.class, new AABB(center).inflate(5));
            for (Player pl : players) {
                pl.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 40, 0, true, false)); // Haste I for 2s refreshed
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
