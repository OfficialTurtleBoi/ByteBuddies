package net.turtleboi.bytebuddies.entity.ai.miner;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import net.turtleboi.bytebuddies.block.entity.DockingStationBlockEntity;
import net.turtleboi.bytebuddies.entity.ai.TaskGoal;
import net.turtleboi.bytebuddies.entity.entities.ByteBuddyEntity;
import net.turtleboi.bytebuddies.entity.entities.ByteBuddyEntity.TaskType;
import net.turtleboi.bytebuddies.item.custom.ClipboardItem;
import net.turtleboi.bytebuddies.util.BotDebug;
import net.turtleboi.bytebuddies.util.BotDebug.GoalPhase;
import net.turtleboi.bytebuddies.util.GoalUtil;
import net.turtleboi.bytebuddies.util.InventoryUtil;
import net.turtleboi.bytebuddies.util.ToolUtil;

import javax.annotation.Nullable;
import java.util.*;
import java.util.function.Consumer;

public class QuarryGoal extends TaskGoal {

    @Nullable private BlockPos claimedMinePos = null;
    private long nextClaimRenewMine = 0L;
    private static final int mineEnergyCost = 30;

    public QuarryGoal(ByteBuddyEntity byteBuddy) {
        super(byteBuddy, 300, 1.5, 0.08, 0.18, 1.5);
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    protected String goalLabel() { return "MINER"; }

    @Override
    protected void setActionActive(boolean active) { byteBuddy.setSlamming(active); }

    @Override
    protected void releaseClaim() {
        DockingStationBlockEntity dockBlock = GoalUtil.dockBlockEntity(byteBuddy);
        if (claimedMinePos != null && dockBlock != null) {
            dockBlock.releaseClaim(TaskType.MINE, claimedMinePos, byteBuddy.getUUID());
        }
        claimedMinePos = null;
    }

    @Override
    protected void renewClaim(ServerLevel serverLevel) {
        GoalUtil.renewClaimIfNeeded(byteBuddy, serverLevel, TaskType.MINE,
                claimedMinePos, targetPos,
                serverLevel.getGameTime(), 5, claimTimeOut,
                () -> nextClaimRenewMine, t -> nextClaimRenewMine = t);
    }

    @Override
    protected boolean validateTarget(ServerLevel serverLevel, BlockState targetState) {
        if (!GoalUtil.canMineAt(byteBuddy.level(), targetPos)) {
            clearTarget();
            enterPhase(GoalPhase.IDLE, "target invalid");
            return false;
        }
        return true;
    }

    @Override
    protected void onReadyToAct(ServerLevel serverLevel, BlockPos pos, BlockState state) {
        if (!verifyClaimOrAbort(serverLevel, TaskType.MINE, claimedMinePos, pos)) return;

        firePos = pos;
        firePreState = state;

        int totalTicks = GoalUtil.toTicks(2.0);
        int startTicks = GoalUtil.toTicks(0.4);
        startTimedAnimation(totalTicks, startTicks, pos, state);

        BotDebug.log(byteBuddy, "MINE schedule: now=" + serverLevel.getGameTime() +
                " start=" + (serverLevel.getGameTime() + startTicks) +
                " end=" + (serverLevel.getGameTime() + totalTicks) +
                " firePos=" + firePos + " pre=" + state.getBlock().getName().getString());
    }

    @Override
    protected void performAction(BlockPos pos, BlockState pre) {
        performMine(pos, pre);
    }

    @Override
    protected void handleSeeking() {
        if (!timedOut(seekingTimeout)) return;

        if (targetReselectRetries++ < 2) {
            var plan = findMinePlan();
            if (plan.isPresent()) {
                targetPos = plan.get().breakPos();
                approachPos = plan.get().standPos();
                targetAnchor = GoalUtil.getEdgeAnchor(targetPos, approachPos);
                resetProgress();
                enterPhase(GoalPhase.MOVING, "retry seek -> moving");
            } else {
                enterPhase(GoalPhase.IDLE, "seek exhausted");
            }
        } else {
            enterPhase(GoalPhase.IDLE, "seek timeout");
        }
    }

    @Override
    public boolean canUse() {
        if (currentPhase == GoalPhase.ACTING) {
            if (byteBuddy.level() instanceof ServerLevel serverLevel) {
                return serverLevel.getGameTime() <= animationEnd;
            }
            return true;
        }

        if (targetPos != null) return true;

        if (byteBuddy.getBuddyRole() != ByteBuddyEntity.BuddyRole.MINER) {
            failTask(BotDebug.FailReason.WRONG_ROLE, "role=" + byteBuddy.getBuddyRole());
            return false;
        }

        if (byteBuddy.getDock().isEmpty()) {
            failTask(BotDebug.FailReason.NO_DOCK, "no station bound");
            return false;
        }

        if (!GoalUtil.ensureUse(byteBuddy, ToolUtil.ToolType.PICKAXE, mineEnergyCost, 1)) return false;

        var plan = findMinePlan();
        if (plan.isEmpty()) return false;

        this.targetPos = plan.get().breakPos();
        this.approachPos = plan.get().standPos();
        this.targetAnchor = GoalUtil.getEdgeAnchor(targetPos, approachPos);
        this.edgeAnchored = false;
        resetProgress();
        enterPhase(GoalPhase.MOVING, "to mine " + targetPos.toShortString());
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        if (currentPhase == GoalPhase.ACTING) {
            if (byteBuddy.level() instanceof ServerLevel serverLevel) {
                return serverLevel.getGameTime() <= animationEnd;
            }
            return true;
        }
        if (targetPos == null) return false;
        return GoalUtil.ensureUse(byteBuddy, ToolUtil.ToolType.PICKAXE, mineEnergyCost, 1);
    }

    private record MinePlan(BlockPos breakPos, BlockPos standPos, @Nullable Path path, double score) {}

    private Optional<MinePlan> findMinePlan() {
        BlockPos dock = byteBuddy.getDock().orElse(null);
        if (dock == null) return Optional.empty();

        Level level = byteBuddy.level();
        if (!(level instanceof ServerLevel serverLevel)) return Optional.empty();

        DockingStationBlockEntity dockBlock = GoalUtil.dockBlockEntity(byteBuddy);
        if (dockBlock == null) return Optional.empty();

        ItemStack clipboard = dockBlock.getClipboardStack();
        boolean hasRegion = ClipboardItem.getRegion(clipboard).isPresent();
        BlockPos firstPos = hasRegion ? ClipboardItem.getFirstPosition(clipboard).orElse(null) : null;
        BlockPos secondPos = hasRegion ? ClipboardItem.getSecondPosition(clipboard).orElse(null) : null;

        final ScanBox box = (firstPos != null && secondPos != null)
                ? makeRegionBox(serverLevel, firstPos, secondPos)
                : makeBehindDockBox(serverLevel, dock, byteBuddy.effectiveRadius(), dockBlock);

        ArrayList<MinePlan> candidates = new ArrayList<>();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int y = box.yMax; y >= box.yMin; y--) {
            if (box.mode == ScanMode.REGION) {
                for (int x = box.xMin; x <= box.xMax; x++) {
                    for (int z = box.zMin; z <= box.zMax; z++) {
                        tryAddCandidate(level, serverLevel, dockBlock, dock, y, x, z, cursor, candidates);
                    }
                }
            } else {
                final int half = box.bandHalfWidth;
                final BlockPos origin = box.origin;
                final Direction back = box.backDir;
                final Direction left = box.leftDir;
                for (int depth = 1; depth <= box.depth; depth++) {
                    for (int side = -half; side <= half; side++) {
                        int x = origin.getX() + back.getStepX() * depth + left.getStepX() * side;
                        int z = origin.getZ() + back.getStepZ() * depth + left.getStepZ() * side;
                        tryAddCandidate(level, serverLevel, dockBlock, dock, y, x, z, cursor, candidates);
                    }
                }
            }
            if (!candidates.isEmpty()) break;
        }

        if (candidates.isEmpty()) return Optional.empty();
        candidates.sort(Comparator.comparingDouble(MinePlan::score));
        MinePlan pick = candidates.get(0);

        if (!dockBlock.tryClaim(serverLevel, TaskType.MINE, pick.breakPos(), byteBuddy.getUUID(), claimTimeOut))
            return Optional.empty();

        this.claimedMinePos = pick.breakPos();
        this.nextClaimRenewMine = serverLevel.getGameTime() + 5;
        this.approachPlans = buildApproachPlans(level, pick.breakPos());
        this.anchorIndex = 0;
        this.approachPos = pick.standPos();
        this.targetAnchor = GoalUtil.getEdgeAnchor(pick.breakPos(), pick.standPos());
        this.edgeAnchored = false;

        return Optional.of(pick);
    }

    private List<Approach> buildApproachPlans(Level level, BlockPos blockPos) {
        ArrayList<Approach> list = new ArrayList<>(12);
        BlockPos[] horizontal = { blockPos.east(), blockPos.west(), blockPos.north(), blockPos.south() };
        BlockPos[] above = { blockPos.east().above(), blockPos.west().above(), blockPos.north().above(), blockPos.south().above() };
        BlockPos[] below = { blockPos.east().below(), blockPos.west().below(), blockPos.north().below(), blockPos.south().below() };

        Consumer<BlockPos> addIfGood = stand -> {
            if (!ByteBuddyEntity.isStandableForMove(byteBuddy, level, stand)) return;
            Vec3 anchor = GoalUtil.getEdgeAnchor(blockPos, stand);
            if (anchor == null) return;
            Path path = (byteBuddy.getNavigation() instanceof GroundPathNavigation nav) ? nav.createPath(stand, 0) : null;
            if (path == null) return;
            list.add(new Approach(stand, anchor, GoalUtil.hDistSq(byteBuddy.position(), anchor), path));
        };

        for (BlockPos pos : horizontal) addIfGood.accept(pos);
        for (BlockPos pos : above) addIfGood.accept(pos);
        for (BlockPos pos : below) addIfGood.accept(pos);
        list.sort(Comparator.comparingDouble(Approach::distSq));
        return list;
    }

    private void performMine(BlockPos pos, BlockState pre) {
        Level level = byteBuddy.level();
        if (!GoalUtil.canMineAt(level, pos)) {
            BotDebug.log(byteBuddy, "MINE invalid at " + pos.toShortString());
            releaseClaim();
            return;
        }

        if (!byteBuddy.consumeEnergy(mineEnergyCost)) {
            releaseClaim();
            return;
        }

        ToolUtil.applyToolWear(byteBuddy, ToolUtil.ToolType.PICKAXE, byteBuddy.toolWearMultiplier());

        if (level instanceof ServerLevel serverLevel) {
            var drops = Block.getDrops(pre, serverLevel, pos, null, byteBuddy, ItemStack.EMPTY);
            Vec3 c = pos.getCenter();
            int inserted = 0, dropped = 0;
            for (ItemStack stack : drops) {
                ItemStack rem = InventoryUtil.mergeInto(byteBuddy.getMainInv(), stack);
                if (!rem.isEmpty()) { Containers.dropItemStack(level, c.x, c.y, c.z, rem); dropped += rem.getCount(); }
                else inserted += stack.getCount();
            }
            level.destroyBlock(pos, false);
            BotDebug.log(byteBuddy, "MINE at " + pos.toShortString() + " inserted=" + inserted + " dropped=" + dropped);
            BotDebug.mark(level, pos);
        }

        byteBuddy.onTaskSuccess(TaskType.MINE, pos);
        releaseClaim();
    }

    private void tryAddCandidate(Level level, ServerLevel serverLevel, DockingStationBlockEntity dockBlock, BlockPos dock,
                                  int y, int x, int z, BlockPos.MutableBlockPos cursor, ArrayList<MinePlan> out) {
        cursor.set(x, y, z);
        if (!byteBuddy.isBlockWithinTether(cursor.immutable())) return;
        if (!GoalUtil.canMineAt(level, cursor)) return;
        if (dockBlock.isReserved(serverLevel, TaskType.MINE, cursor)) return;
        if (!level.getBlockState(cursor.above()).getCollisionShape(level, cursor.above()).isEmpty()) return;

        var plans = buildApproachPlans(level, cursor.immutable());
        if (plans.isEmpty()) return;

        Approach best = plans.get(0);
        double score = GoalUtil.hDistSq(byteBuddy.position(), best.approachAnchor());
        score += (dock.getY() - cursor.getY()) * 4.0;
        if (!level.getFluidState(cursor).isEmpty()) score += 1e6;

        out.add(new MinePlan(cursor.immutable(), best.targetPos(), best.path(), score));
    }

    private enum ScanMode { REGION, BAND }

    private static final class ScanBox {
        final ScanMode mode;
        final int xMin, xMax, zMin, zMax, yMin, yMax;
        final BlockPos origin;
        final Direction backDir, leftDir;
        final int depth, bandHalfWidth;

        ScanBox(ScanMode mode, int xMin, int xMax, int zMin, int zMax, int yMin, int yMax,
                BlockPos origin, Direction backDir, Direction leftDir, int depth, int bandHalfWidth) {
            this.mode = mode;
            this.xMin = xMin; this.xMax = xMax;
            this.zMin = zMin; this.zMax = zMax;
            this.yMin = yMin; this.yMax = yMax;
            this.origin = origin;
            this.backDir = backDir;
            this.leftDir = leftDir;
            this.depth = depth;
            this.bandHalfWidth = bandHalfWidth;
        }
    }

    private ScanBox makeRegionBox(ServerLevel level, BlockPos a, BlockPos b) {
        return new ScanBox(ScanMode.REGION,
                Math.min(a.getX(), b.getX()), Math.max(a.getX(), b.getX()),
                Math.min(a.getZ(), b.getZ()), Math.max(a.getZ(), b.getZ()),
                clampY(level, Math.min(a.getY(), b.getY())), clampY(level, Math.max(a.getY(), b.getY())),
                null, null, null, 0, 0);
    }

    private ScanBox makeBehindDockBox(ServerLevel level, BlockPos dock, int radius, DockingStationBlockEntity dockBlock) {
        Direction back = GoalUtil.backOfDock(dockBlock);
        Direction left = back.getClockWise();
        return new ScanBox(ScanMode.BAND, 0, 0, 0, 0,
                clampY(level, level.getMinBuildHeight()), clampY(level, dock.getY() + 2),
                dock, back, left, Math.max(1, radius * 2), Math.max(0, radius));
    }

    private int clampY(ServerLevel level, int y) {
        return Mth.clamp(y, level.getMinBuildHeight(), level.getMaxBuildHeight() - 1);
    }
}
