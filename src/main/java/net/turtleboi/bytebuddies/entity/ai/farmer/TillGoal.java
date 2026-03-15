package net.turtleboi.bytebuddies.entity.ai.farmer;

import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import net.turtleboi.bytebuddies.block.entity.DockingStationBlockEntity;
import net.turtleboi.bytebuddies.entity.ai.TaskGoal;
import net.turtleboi.bytebuddies.entity.entities.ByteBuddyEntity;
import net.turtleboi.bytebuddies.entity.entities.ByteBuddyEntity.TaskType;
import net.turtleboi.bytebuddies.util.BotDebug;
import net.turtleboi.bytebuddies.util.BotDebug.GoalPhase;
import net.turtleboi.bytebuddies.util.GoalUtil;
import net.turtleboi.bytebuddies.util.ToolUtil;

import javax.annotation.Nullable;
import java.util.*;
import java.util.function.Consumer;

public class TillGoal extends TaskGoal {

    @Nullable private BlockPos claimedTillPos = null;
    private long nextClaimRenewTill = 0L;
    private static final int tillEnergyCost = 25;

    private static final int hydrationRadius = 4;
    private static final double dryPenalty = 10000.0;
    private static final double waterDistanceBonus = 500.0;
    private static final double dockDistanceBonus = 1.0;
    private static final double approachDistanceBonus = 25.0;

    public TillGoal(ByteBuddyEntity byteBuddy) {
        super(byteBuddy, 160, 1.25, 0.08, 0.18, 1.25);
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    protected String goalLabel() { return "FARMER"; }

    @Override
    protected void setActionActive(boolean active) { byteBuddy.setSlamming(active); }

    @Override
    protected void releaseClaim() {
        DockingStationBlockEntity dockBlock = GoalUtil.dockBlockEntity(byteBuddy);
        if (claimedTillPos != null && dockBlock != null) {
            dockBlock.releaseClaim(TaskType.TILL, claimedTillPos, byteBuddy.getUUID());
        }
        claimedTillPos = null;
    }

    @Override
    protected void renewClaim(ServerLevel serverLevel) {
        GoalUtil.renewClaimIfNeeded(byteBuddy, serverLevel, TaskType.TILL,
                claimedTillPos, targetPos,
                serverLevel.getGameTime(), 5, claimTimeOut,
                () -> nextClaimRenewTill,
                ticks -> nextClaimRenewTill = ticks);
    }

    @Override
    protected boolean validateTarget(ServerLevel serverLevel, BlockState targetState) {
        if (!GoalUtil.canTillAt(byteBuddy.level(), targetPos)) {
            clearTarget();
            enterPhase(GoalPhase.IDLE, "till site invalid, rescan");
            return false;
        }
        return true;
    }

    @Override
    protected void onReadyToAct(ServerLevel serverLevel, BlockPos pos, BlockState state) {
        if (!verifyClaimOrAbort(serverLevel, TaskType.TILL, claimedTillPos, pos)) return;

        firePos = pos;
        firePreState = state;

        int totalTicks = GoalUtil.toTicks(2.0);
        int startTicks = GoalUtil.toTicks(0.4);
        startTimedAnimation(totalTicks, startTicks, pos, state);

        BotDebug.log(byteBuddy, "TILL schedule: now=" + serverLevel.getGameTime() +
                " start=" + (serverLevel.getGameTime() + startTicks) +
                " end=" + (serverLevel.getGameTime() + totalTicks) +
                " firePos=" + firePos + " preState=" + state.getBlock().getName().getString());
    }

    @Override
    protected void performAction(BlockPos pos, BlockState pre) {
        performTill(pos);
    }

    @Override
    protected void handleSeeking() {
        if (!timedOut(seekingTimeout)) return;

        if (targetReselectRetries++ < 2) {
            TillPlan plan = findTillPlan();
            if (plan != null) {
                if (byteBuddy.level() instanceof ServerLevel serverLevel) {
                    DockingStationBlockEntity dockBlock = GoalUtil.dockBlockEntity(byteBuddy);
                    if (dockBlock != null && !dockBlock.tryClaim(serverLevel, TaskType.TILL,
                            plan.blockPos(), byteBuddy.getUUID(), claimTimeOut)) {
                        enterPhase(GoalPhase.SEEKING, "seek retry (claim failed)");
                        return;
                    }
                    claimedTillPos = plan.blockPos();
                    nextClaimRenewTill = serverLevel.getGameTime() + 5;
                }
                targetPos = plan.blockPos();
                approachPos = plan.approachPos();
                targetAnchor = GoalUtil.getEdgeAnchor(targetPos, approachPos);
                approachPlans = buildApproachPlans(byteBuddy.level(), targetPos);
                anchorIndex = 0;
                resetProgress();
                enterPhase(GoalPhase.MOVING, "retry seek -> moving");
            } else {
                enterPhase(GoalPhase.IDLE, "seek timeout, no target");
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

        if (byteBuddy.getBuddyRole() != ByteBuddyEntity.BuddyRole.FARMER) {
            failTask(BotDebug.FailReason.WRONG_ROLE, "role=" + byteBuddy.getBuddyRole());
            return false;
        }

        if (byteBuddy.getDock().isEmpty()) {
            failTask(BotDebug.FailReason.NO_DOCK, "no station bound");
            return false;
        }

        if (!GoalUtil.ensureUse(byteBuddy, ToolUtil.ToolType.HOE, tillEnergyCost, 1)) return false;

        TillPlan plan = findTillPlan();
        if (plan == null) return false;

        if (byteBuddy.level() instanceof ServerLevel serverLevel) {
            DockingStationBlockEntity dockBlock = GoalUtil.dockBlockEntity(byteBuddy);
            if (dockBlock != null) {
                if (claimedTillPos == null || !claimedTillPos.equals(plan.blockPos())) {
                    if (!dockBlock.tryClaim(serverLevel, TaskType.TILL, plan.blockPos(), byteBuddy.getUUID(), claimTimeOut)) {
                        return false;
                    }
                    claimedTillPos = plan.blockPos();
                    nextClaimRenewTill = serverLevel.getGameTime() + 5;
                }
            }
        }

        targetPos = plan.blockPos();
        approachPos = plan.approachPos();
        targetAnchor = GoalUtil.getEdgeAnchor(targetPos, approachPos);
        edgeAnchored = false;
        approachPlans = buildApproachPlans(byteBuddy.level(), targetPos);
        anchorIndex = 0;
        resetProgress();
        enterPhase(GoalPhase.MOVING, "to till site " + targetPos.toShortString());
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
        return GoalUtil.ensureUse(byteBuddy, ToolUtil.ToolType.HOE, tillEnergyCost, 1);
    }

    private record TillPlan(BlockPos blockPos, BlockPos approachPos, Path path, boolean isHydrated) {}
    private record ScoredTillPlan(TillPlan tillPlan, double candidateScore) {}

    @Nullable
    private TillPlan findTillPlan() {
        BlockPos dockPos = byteBuddy.getDock().orElse(null);
        if (dockPos == null) return null;

        final int radius = byteBuddy.effectiveRadius();
        Level level = byteBuddy.level();
        DockingStationBlockEntity dockBlock = GoalUtil.dockBlockEntity(byteBuddy);
        if (!(level instanceof ServerLevel serverLevel)) return null;

        MutableBlockPos cursor = new MutableBlockPos();
        List<ScoredTillPlan> scores = new ArrayList<>();
        Vec3 buddyPos = byteBuddy.position();

        for (int y = -1; y <= 2; y++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    cursor.set(dockPos.getX() + dx, dockPos.getY() + y, dockPos.getZ() + dz);
                    if (!byteBuddy.isBlockWithinTether(cursor.immutable())) continue;
                    if (!GoalUtil.canTillAt(level, cursor)) continue;
                    if (dockBlock != null && dockBlock.isReserved(serverLevel, TaskType.TILL, cursor)) continue;
                    int waterDist = waterDistance(level, cursor);
                    if (waterDist == 999) continue;

                    var plans = buildApproachPlans(level, cursor.immutable());
                    if (plans.isEmpty()) continue;
                    Approach best = plans.get(0);

                    boolean isHydrated = (waterDist <= hydrationRadius);
                    double score = 0.0;
                    if (!isHydrated) {
                        score += dryPenalty;
                    } else {
                        score += Math.min(waterDist, hydrationRadius) * waterDistanceBonus;
                    }
                    double ddx = cursor.getX() - dockPos.getX();
                    double ddz = cursor.getZ() - dockPos.getZ();
                    score += (ddx * ddx + ddz * ddz) * dockDistanceBonus;
                    Vec3 anchor = best.approachAnchor();
                    double adx = buddyPos.x - anchor.x;
                    double adz = buddyPos.z - anchor.z;
                    score += (adx * adx + adz * adz) * approachDistanceBonus;

                    scores.add(new ScoredTillPlan(
                            new TillPlan(cursor.immutable(), best.targetPos(), best.path(), isHydrated),
                            score));
                }
            }
        }

        if (scores.isEmpty()) return null;
        scores.sort(Comparator.comparingDouble(ScoredTillPlan::candidateScore));
        TillPlan chosen = scores.get(0).tillPlan();

        if (dockBlock != null) {
            if (!dockBlock.tryClaim(serverLevel, TaskType.TILL, chosen.blockPos(), byteBuddy.getUUID(), claimTimeOut)) return null;
            claimedTillPos = chosen.blockPos();
            nextClaimRenewTill = serverLevel.getGameTime() + 5;
        }

        this.approachPlans = buildApproachPlans(level, chosen.blockPos());
        this.anchorIndex = 0;
        this.approachPos = chosen.approachPos();
        this.targetAnchor = GoalUtil.getEdgeAnchor(chosen.blockPos(), chosen.approachPos());
        this.edgeAnchored = false;

        return chosen;
    }

    private List<Approach> buildApproachPlans(Level level, BlockPos blockPos) {
        ArrayList<Approach> list = new ArrayList<>(8);
        BlockPos[] horizontal = { blockPos.east(), blockPos.west(), blockPos.north(), blockPos.south() };
        BlockPos[] above = { blockPos.east().above(), blockPos.west().above(), blockPos.north().above(), blockPos.south().above() };

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
        list.sort(Comparator.comparingDouble(Approach::distSq));
        return list;
    }

    private void performTill(BlockPos blockPos) {
        Level level = byteBuddy.level();
        if (level instanceof ServerLevel serverLevel) {
            if (!verifyClaimOrAbort(serverLevel, TaskType.TILL, claimedTillPos, blockPos)) return;
        }

        if (!GoalUtil.canTillAt(level, blockPos)) {
            BotDebug.log(byteBuddy, "TILL invalid at " + blockPos.toShortString());
            releaseClaim();
            return;
        }

        if (!byteBuddy.consumeEnergy(tillEnergyCost)) {
            failTask(BotDebug.FailReason.OUT_OF_ENERGY, "need=" + tillEnergyCost);
            releaseClaim();
            return;
        }

        ToolUtil.applyToolWear(byteBuddy, ToolUtil.ToolType.HOE, byteBuddy.toolWearMultiplier());

        level.playSound(null, blockPos, SoundEvents.HOE_TILL, SoundSource.BLOCKS,
                0.8f + (level.random.nextFloat() * 0.4f), 0.9f + (level.random.nextFloat() * 0.2f));

        if (level instanceof ServerLevel serverLevel) {
            int pts = 12;
            for (int i = 0; i < pts; i++) {
                double angle = (Math.PI * 2 * i) / pts;
                serverLevel.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, level.getBlockState(blockPos)),
                        blockPos.getX() + 0.5 + Math.cos(angle) * 0.5, blockPos.getY() + 0.125,
                        blockPos.getZ() + 0.5 + Math.sin(angle) * 0.5, 1, 0, 0, 0, 0.05);
            }
        }

        if (level.getBlockState(blockPos.above()).canBeReplaced()) {
            level.setBlock(blockPos.above(), Blocks.AIR.defaultBlockState(), 3);
        }

        BlockState soil = level.getBlockState(blockPos);
        level.setBlock(blockPos, soil.is(Blocks.COARSE_DIRT) ? Blocks.DIRT.defaultBlockState() : Blocks.FARMLAND.defaultBlockState(), 3);

        byteBuddy.onTaskSuccess(TaskType.TILL, blockPos);
        releaseClaim();
    }

    private int waterDistance(Level level, BlockPos blockPos) {
        MutableBlockPos cursor = new MutableBlockPos();
        int best = 999;
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -hydrationRadius; dx <= hydrationRadius; dx++) {
                for (int dz = -hydrationRadius; dz <= hydrationRadius; dz++) {
                    cursor.set(blockPos.getX() + dx, blockPos.getY() + dy, blockPos.getZ() + dz);
                    if (level.getFluidState(cursor).is(FluidTags.WATER)) {
                        int score = Math.max(Math.abs(dx), Math.abs(dz));
                        if (score < best) best = score;
                        if (best == 0) return 0;
                    }
                }
            }
        }
        return best;
    }
}
