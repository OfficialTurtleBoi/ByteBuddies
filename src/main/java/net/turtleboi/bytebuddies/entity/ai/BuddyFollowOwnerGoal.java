package net.turtleboi.bytebuddies.entity.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.turtleboi.bytebuddies.entity.entities.ByteBuddyEntity;

import javax.annotation.Nullable;
import java.util.EnumSet;

public class BuddyFollowOwnerGoal extends Goal {
    private final ByteBuddyEntity byteBuddy;
    private LivingEntity owner;
    private final double speed;
    private final double startDist;
    private final double stopDist;
    private final boolean teleportIfStuck;

    public BuddyFollowOwnerGoal(ByteBuddyEntity byteBuddy, double speed, double startDist, double stopDist, boolean teleportIfStuck) {
        this.byteBuddy = byteBuddy;
        this.speed = speed;
        this.startDist = startDist;
        this.stopDist = stopDist;
        this.teleportIfStuck = teleportIfStuck;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override public boolean canUse() {
        var owner = byteBuddy.getOwner(levelAsServer(byteBuddy));
        if (owner == null) return false;
        if (byteBuddy.getDock().isPresent()) return false;
        if (byteBuddy.distanceToSqr(owner) < startDist * startDist) return false;
        this.owner = owner;
        return true;
    }

    @Override public boolean canContinueToUse() {
        if (owner == null || !owner.isAlive()) return false;
        return byteBuddy.distanceToSqr(owner) > stopDist * stopDist;
    }

    @Override public void tick() {
        if (owner == null || !owner.isAlive() || owner.level() != byteBuddy.level()) return;
        if (byteBuddy.level() instanceof ServerLevel) {
            byteBuddy.getLookControl().setLookAt(owner, 15.0f, 15.0f);
            if (!byteBuddy.getNavigation().isInProgress()) {
                byteBuddy.getNavigation().moveTo(owner, speed);
            }

            final double maxDist = 32.0;
            if (teleportIfStuck) {
                if (byteBuddy.distanceToSqr(owner) > (maxDist * maxDist)) {
                    if (tryTeleportNearOwner(owner, 2, 6)) {
                        byteBuddy.getNavigation().stop();
                    }
                }
            }
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

            BlockPos basePos = BlockPos.containing(ownerX, ownerY, ownerZ).offset(dx, 0, dz);
            BlockPos safePos = findStandableColumnNear(basePos, 2);
            if (safePos != null && ByteBuddyEntity.isStandableForMove(byteBuddy, byteBuddy.level(), safePos)) {
                byteBuddy.teleportTo(safePos.getX() + 0.5, safePos.getY(), safePos.getZ() + 0.5);
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

    private @Nullable ServerLevel levelAsServer(ByteBuddyEntity byteBuddy) {
        return (byteBuddy.level() instanceof ServerLevel serverLevel) ? serverLevel : null;
    }
}

