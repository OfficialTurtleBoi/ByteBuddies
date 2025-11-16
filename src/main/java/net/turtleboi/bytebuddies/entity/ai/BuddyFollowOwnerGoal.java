package net.turtleboi.bytebuddies.entity.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.turtleboi.bytebuddies.entity.entities.ByteBuddyEntity;

import javax.annotation.Nullable;
import java.util.EnumSet;

public class BuddyFollowOwnerGoal extends Goal {
    public static final int teleportDistance = 32;
    private static final int minHorizontalDistance = 2;
    private static final int maxVerticalScan = 2;

    private final ByteBuddyEntity byteBuddy;
    private LivingEntity owner;

    private final LevelReader level;
    private final PathNavigation navigation;

    private final double speed;
    private final float startDist;
    private final float stopDist;
    private final boolean canFly;
    private final boolean teleportIfStuck;

    private int timeToRecalculatePath;
    private float oldWaterCost;

    public BuddyFollowOwnerGoal(ByteBuddyEntity byteBuddy, double speed, float startDist, float stopDist, boolean teleportIfStuck) {
        this(byteBuddy, speed, startDist, stopDist, teleportIfStuck, byteBuddy.getNavigation() instanceof FlyingPathNavigation);
    }

    public BuddyFollowOwnerGoal(ByteBuddyEntity byteBuddy, double speed, float startDist, float stopDist, boolean teleportIfStuck, boolean canFly) {
        this.byteBuddy = byteBuddy;
        this.speed = speed;
        this.startDist = startDist;
        this.stopDist = stopDist;
        this.teleportIfStuck = teleportIfStuck;
        this.canFly = canFly;

        this.level = byteBuddy.level();
        this.navigation = byteBuddy.getNavigation();

        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        if (!(this.navigation instanceof GroundPathNavigation) && !(this.navigation instanceof FlyingPathNavigation)) {
            throw new IllegalArgumentException("Unsupported travel type for BuddyFollowOwnerGoal");
        }
    }

    @Override
    public boolean canUse() {
        ServerLevel serverLevel = levelAsServer(byteBuddy);
        if (serverLevel == null) {
            return false;
        }

        LivingEntity owner = byteBuddy.getOwner(serverLevel);
        if (owner == null) {
            return false;
        }
        if (owner.isSpectator()) {
            return false;
        }
        if (owner.level() != byteBuddy.level()) {
            return false;
        }
        if (unableToMove()) {
            return false;
        }
        if (byteBuddy.distanceToSqr(owner) < (double)(this.startDist * this.startDist)) {
            return false;
        }

        this.owner = owner;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        if (this.owner == null || !this.owner.isAlive()) {
            return false;
        }
        if (this.navigation.isDone()) {
            return false;
        }
        if (unableToMove()) {
            return false;
        }
        if (this.owner.level() != this.byteBuddy.level()) {
            return false;
        }
        return !(this.byteBuddy.distanceToSqr(this.owner) <= (double)(this.stopDist * this.stopDist));
    }

    private boolean unableToMove() {
        if (byteBuddy.getDock().isPresent()) return true;
        if (byteBuddy.isPassenger()) return true;
        if (byteBuddy.isLeashed()) return true;
        return byteBuddy.isSleeping();
    }

    @Override
    public void start() {
        this.timeToRecalculatePath = 0;
        this.oldWaterCost = this.byteBuddy.getPathfindingMalus(BlockPathTypes.WATER);
        this.byteBuddy.setPathfindingMalus(BlockPathTypes.WATER, 0.0F);
    }

    @Override
    public void stop() {
        this.owner = null;
        this.navigation.stop();
        this.byteBuddy.setPathfindingMalus(BlockPathTypes.WATER, this.oldWaterCost);
    }

    @Override
    public void tick() {
        if (this.owner == null) return;
        if (this.owner.level() != this.byteBuddy.level()) return;

        this.byteBuddy.getLookControl().setLookAt(this.owner, 10.0F, (float)this.byteBuddy.getMaxHeadXRot());

        if (--this.timeToRecalculatePath > 0) {
            return;
        }

        this.timeToRecalculatePath = this.adjustedTickDelay(10);
        double distSq = this.byteBuddy.distanceToSqr(this.owner);
        if (this.teleportIfStuck && distSq >= (double)(teleportDistance * teleportDistance)) {
            if (tryTeleportNearOwner(this.owner, minHorizontalDistance,
                    teleportDistance / 2)) {
                this.navigation.stop();
                return;
            }

            this.navigation.moveTo(this.owner, this.speed);
        } else {
            this.navigation.moveTo(this.owner, this.speed);
        }
    }

    private boolean tryTeleportNearOwner(LivingEntity owner, int minRadius, int maxRadius) {
        final int tries = 12;
        final double ownerX = owner.getX();
        final double ownerY = owner.getY();
        final double ownerZ = owner.getZ();

        for (int i = 0; i < tries; i++) {
            double angle = (2 * Math.PI * i) / tries;
            int radius = minRadius + byteBuddy.getRandom().nextInt(Math.max(1, maxRadius - minRadius + 1));
            int dx = (int)Math.round(Math.cos(angle) * radius);
            int dz = (int)Math.round(Math.sin(angle) * radius);

            int px = (int)Math.floor(ownerX) + dx;
            int pz = (int)Math.floor(ownerZ) + dz;

            if (Math.abs(px - owner.getX()) < minHorizontalDistance
                    && Math.abs(pz - owner.getZ()) < minHorizontalDistance) {
                continue;
            }

            BlockPos basePos = new BlockPos(px, (int)Math.floor(ownerY), pz);
            BlockPos safePos = findStandableColumnNear(basePos, maxVerticalScan);
            if (safePos != null && canTeleportTo(safePos)) {
                this.byteBuddy.teleportTo(safePos.getX() + 0.5, safePos.getY(), safePos.getZ() + 0.5);
                return true;
            }
        }
        return false;
    }

    @Nullable
    private BlockPos findStandableColumnNear(BlockPos blockPos, int yScan) {
        for (int dy = -yScan; dy <= yScan; dy++) {
            BlockPos standPos = blockPos.offset(0, dy, 0);
            if (ByteBuddyEntity.isStandableTerrain(byteBuddy.level(), standPos)) {
                return standPos;
            }
        }
        return null;
    }

    private boolean canTeleportTo(BlockPos pos) {
        BlockPathTypes pathType = WalkNodeEvaluator.getBlockPathTypeStatic(this.level, pos.mutable());
        if (pathType != BlockPathTypes.WALKABLE) {
            return false;
        }

        BlockState below = this.level.getBlockState(pos.below());
        if (!this.canFly && below.getBlock() instanceof LeavesBlock) {
            return false;
        }

        BlockPos offset = pos.subtract(this.byteBuddy.blockPosition());
        return this.level.noCollision(this.byteBuddy, this.byteBuddy.getBoundingBox().move(offset));
    }

    private @Nullable ServerLevel levelAsServer(ByteBuddyEntity byteBuddy) {
        return (byteBuddy.level() instanceof ServerLevel serverLevel) ? serverLevel : null;
    }
}
