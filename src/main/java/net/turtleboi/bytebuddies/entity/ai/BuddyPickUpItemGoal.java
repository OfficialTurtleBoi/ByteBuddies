package net.turtleboi.bytebuddies.entity.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundTakeItemEntityPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.turtleboi.bytebuddies.ByteBuddies;
import net.turtleboi.bytebuddies.entity.entities.ByteBuddyEntity;
import net.turtleboi.bytebuddies.util.InventoryUtil;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.List;

public class BuddyPickUpItemGoal extends Goal {
    private static final boolean DEBUG = false;
    private static final int DEBUG_RATE_TICKS = 10;

    private final ByteBuddyEntity byteBuddy;
    private final double speed;
    private final double scanRange;
    private final double grabDistance;
    @Nullable private Vec3 approachTarget = null;
    private final int repathCooldownTicks;

    private ItemEntity target;
    private long lastPathTick;
    private int debugTickCounter;

    public BuddyPickUpItemGoal(ByteBuddyEntity byteBuddy, double speed, double scanRange, double grabDistance, int repathCooldownTicks) {
        this.byteBuddy = byteBuddy;
        this.speed = speed;
        this.scanRange = scanRange;
        this.grabDistance = grabDistance;
        this.repathCooldownTicks = Math.max(2, repathCooldownTicks);
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (byteBuddy.level().isClientSide) {
            debugOnce("canUse=false (client side)");
            return false;
        }

        if (!byteBuddy.canAct()) {
            debugOnce("canUse=false (cannot act: sleeping or gated)");
            return false;
        }

        this.target = findVisibleItem();
        if (this.target == null || !this.target.isAlive()) {
            debugOnce("canUse=false (no visible item that fits)");
            return false;
        }

        ItemStack stack = this.target.getItem();
        if (stack.isEmpty()) {
            debugOnce("canUse=false (target stack empty)");
            return false;
        }

        boolean fits = byteBuddy.canFitInInventory(stack);
        debugOnce("canUse target=" + debugItem(this.target) + " fits=" + fits);
        return fits;
    }

    @Override
    public boolean canContinueToUse() {
        if (byteBuddy.level().isClientSide) {
            debugOnce("continue=false (client side)");
            return false;
        }
        if (!byteBuddy.canAct()) {
            debugOnce("continue=false (cannot act)");
            return false;
        }
        if (this.target == null || !this.target.isAlive()) {
            debugOnce("continue=false (target null/dead)");
            return false;
        }
        if (!byteBuddy.canFitInInventory(this.target.getItem())) {
            debugOnce("continue=false (no longer fits)");
            return false;
        }

        final double maxChase = this.scanRange + 2.0;
        final double distSq = byteBuddy.distanceToSqr(this.target);
        final boolean closeEnough = distSq <= (maxChase * maxChase);
        final boolean pathing = byteBuddy.getNavigation().isInProgress();

        boolean cont = closeEnough || pathing || isInPickupRange(this.target, this.grabDistance);
        if (!cont) {
            debugOnce("continue=false (too far and not pathing) dist=" + String.format("%.2f", Math.sqrt(distSq)));
        }
        return cont;
    }

    @Override
    public void start() {
        this.lastPathTick = 0L;
        this.approachTarget = (this.target != null) ? findPickupApproach(this.target) : null;

        if (this.approachTarget != null) {
            debugOnce("start: path→approach " + debugPos(approachTarget.x, approachTarget.y, approachTarget.z));
            byteBuddy.getNavigation().moveTo(approachTarget.x, approachTarget.y, approachTarget.z, this.speed);
        } else if (this.target != null) {
            debugOnce("start: fallback path→entity " + debugPos(this.target.getX(), this.target.getY(), this.target.getZ()));
            byteBuddy.getNavigation().moveTo(this.target, this.speed);
        }
    }


    @Override
    public void stop() {
        debugOnce("stop");
        this.target = null;
        byteBuddy.getNavigation().stop();
    }

    @Override
    public boolean isInterruptable() {
        return false;
    }

    @Override
    public void tick() {
        if (this.target == null || !this.target.isAlive()) {
            return;
        }

        byteBuddy.getLookControl().setLookAt(this.target, 15.0f, 15.0f);
        boolean facing = isFacing(target, 0.9);
        if (isInPickupRange(this.target, this.grabDistance)) {
            debugEvery("tick: AABB overlap → pickup");
            if (isInPickupRange(target, grabDistance) && facing) {
                tryPickup(target);
                return;
            }
        }

        final long now = byteBuddy.tickCount;
        if (now - lastPathTick >= repathCooldownTicks) {
            Vec3 newApproach = findPickupApproach(this.target);
            if (newApproach != null) {
                if (this.approachTarget == null || newApproach.distanceToSqr(this.approachTarget) > 0.25) {
                    this.approachTarget = newApproach;
                    debugEvery("tick: repath→approach " + debugPos(newApproach.x, newApproach.y, newApproach.z));
                    byteBuddy.getNavigation().moveTo(newApproach.x, newApproach.y, newApproach.z, this.speed);
                } else if (!byteBuddy.getNavigation().isInProgress()) {
                    debugEvery("tick: path done but no overlap → reassert approach");
                    byteBuddy.getNavigation().moveTo(approachTarget.x, approachTarget.y, approachTarget.z, this.speed);
                }
            } else {
                debugEvery("tick: no standable approach → path→item XYZ");
                byteBuddy.getNavigation().moveTo(this.target.getX(), this.target.getY(), this.target.getZ(), this.speed);
            }
            lastPathTick = now;
        }

        if (!isInPickupRange(this.target, this.grabDistance) && !byteBuddy.getNavigation().isInProgress()) {
            debugEvery("tick: nav done, no overlap → micro-walk");
            byteBuddy.getMoveControl().setWantedPosition(this.target.getX(), this.target.getY(), this.target.getZ(), Math.max(0.6, this.speed));
        }
    }


    private ItemEntity findVisibleItem() {
        double range = this.scanRange;
        AABB box = new AABB(
                byteBuddy.getX() - range, byteBuddy.getY() - range, byteBuddy.getZ() - range,
                byteBuddy.getX() + range, byteBuddy.getY() + range, byteBuddy.getZ() + range
        );

        List<ItemEntity> items = byteBuddy.level().getEntitiesOfClass(ItemEntity.class, box,
                itemEntity -> itemEntity.isAlive() && !itemEntity.getItem().isEmpty());

        ItemEntity best = null;
        double bestScore = Double.POSITIVE_INFINITY;

        for (ItemEntity itemEntity : items) {
            if (!byteBuddy.canFitInInventory(itemEntity.getItem())) {
                debugEvery("scan skip " + debugItem(itemEntity) + " fits=false");
                continue;
            }

            boolean fov = inFov(itemEntity);
            boolean los = hasEyesOn(itemEntity);
            double dist = byteBuddy.distanceToSqr(itemEntity);
            double penalty = (fov && los) ? 0.0 : 1.0;
            double score = dist + penalty;
            if (score < bestScore) {
                bestScore = score;
                best = itemEntity;
            }
        }

        if (best != null) {
            debugOnce("scan best " + debugItem(best) + " dist=" + String.format("%.2f", Math.sqrt(bestScore)));
        }
        return best;
    }

    private boolean hasEyesOn(Entity entity) {
        Vec3 origin = byteBuddy.getEyePosition();
        Vec3 eyeDest = entity.getEyePosition();
        HitResult hit = byteBuddy.level().clip(new ClipContext(
                origin, eyeDest,
                ClipContext.Block.VISUAL,
                ClipContext.Fluid.NONE,
                byteBuddy
        ));
        boolean miss = hit.getType() == HitResult.Type.MISS;
        debugEvery("LOS " + debugItemPos(entity) + " → " + hit.getType());
        return miss;
    }

    private boolean isFacing(Entity entity, double dotThreshold) {
        var look = byteBuddy.getViewVector(1.0F).normalize();
        var dir = entity.getEyePosition().subtract(byteBuddy.getEyePosition()).normalize();
        return look.dot(dir) >= dotThreshold;
    }

    private boolean inFov(Entity entity) {
        Vec3 look = byteBuddy.getViewVector(1.0F).normalize();
        Vec3 dir = entity.getEyePosition().subtract(byteBuddy.getEyePosition()).normalize();
        double dot = look.dot(dir);
        boolean validSight = dot > 0.2;
        debugEvery("FOV " + debugItemPos(entity) + " dot=" + String.format("%.3f", dot) + " validSight=" + validSight);
        return validSight;
    }

    private boolean isInPickupRange(ItemEntity itemEntity, double grabRadius) {
        boolean aabbTouch = byteBuddy.getBoundingBox()
                .inflate(grabRadius / 2, byteBuddy.getBbWidth(), grabRadius / 2)
                .intersects(itemEntity.getBoundingBox());

        debugEvery("range " + debugItem(itemEntity) +
                " aabb=" + aabbTouch +
                " → " + (aabbTouch));

        return aabbTouch;
    }

    @Nullable
    private Vec3 findPickupApproach(ItemEntity item) {
        int[][] OFFS = {
                {0,0}, {1,0}, {-1,0}, {0,1}, {0,-1},  {1,1}, {1,-1}, {-1,1}, {-1,-1}
        };

        Level level = byteBuddy.level();
        BlockPos itemPos = BlockPos.containing(item.getX(), item.getY(), item.getZ());
        Vec3 best = null;
        double bestDist = Double.POSITIVE_INFINITY;

        for (int[] o : OFFS) {
            BlockPos p = itemPos.offset(o[0], 0, o[1]);
            if (!ByteBuddyEntity.isStandableForMove(byteBuddy, level, p)) {
                continue;
            }

            Vec3 center = new Vec3(p.getX() + 0.5, p.getY(), p.getZ() + 0.5);
            double d = center.distanceToSqr(item.position());
            if (d < bestDist) {
                bestDist = d;
                best = center;
            }
        }
        return best;
    }


    private void tryPickup(ItemEntity itemEntity) {
        if (!(byteBuddy.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        if (!itemEntity.isAlive()) {
            debugOnce("pickup aborted (target not alive)");
            return;
        }

        ItemStack stack = itemEntity.getItem();
        int before = stack.getCount();

        ItemStack leftover = InventoryUtil.mergeInto(byteBuddy.getMainInv(), stack);
        int taken = before - leftover.getCount();

        debugOnce("pickup " + debugItem(itemEntity) + " before=" + before + " taken=" + taken + " leftover=" + leftover.getCount());

        if (taken <= 0) {
            return;
        }

        serverLevel.getChunkSource().broadcastAndSend(
                byteBuddy,
                new ClientboundTakeItemEntityPacket(itemEntity.getId(), byteBuddy.getId(), taken)
        );

        serverLevel.playSound(
                null,
                byteBuddy.getX(), byteBuddy.getY(), byteBuddy.getZ(),
                SoundEvents.ITEM_PICKUP,
                SoundSource.PLAYERS,
                0.2F,
                1.0F + (byteBuddy.getRandom().nextFloat() - byteBuddy.getRandom().nextFloat()) * 0.4F
        );

        if (leftover.isEmpty()) {
            itemEntity.discard();
        } else {
            itemEntity.setItem(leftover);
        }

        byteBuddy.swing(InteractionHand.MAIN_HAND, true);
    }

    private void debugEvery(String message) {
        if (!DEBUG) { return; }
        if ((++debugTickCounter % DEBUG_RATE_TICKS) == 0) {
            ByteBuddies.LOGGER.info("[PickupGoal] {}", message);
        }
    }

    private void debugOnce(String message) {
        if (!DEBUG) { return; }
        ByteBuddies.LOGGER.info("[PickupGoal] {}", message);
    }

    private static String debugItem(ItemEntity itemEntity) {
        ItemStack s = itemEntity.getItem();
        return "Item{" + s.getDisplayName().getString() + " x" + s.getCount() + " @" +
                String.format("%.1f,%.1f,%.1f", itemEntity.getX(), itemEntity.getY(), itemEntity.getZ()) + "}";
    }

    private static String debugPos(double x, double y, double z) {
        return String.format("(%.2f, %.2f, %.2f)", x, y, z);
    }

    private static String debugItemPos(Entity entity) {
        if (entity instanceof ItemEntity itemEntity) {
            return debugItem(itemEntity);
        }
        return "Entity@" + String.format("(%.2f, %.2f, %.2f)", entity.getX(), entity.getY(), entity.getZ());
    }
}
