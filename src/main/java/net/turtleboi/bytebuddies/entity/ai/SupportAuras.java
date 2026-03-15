package net.turtleboi.bytebuddies.entity.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.turtleboi.bytebuddies.entity.entities.ByteBuddyEntity;

import java.util.List;

public class SupportAuras {
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

        MobEffectInstance speedBuff = new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 40, 0, true, false);
        List<Player> players = level.getEntitiesOfClass(Player.class, new AABB(buddyPos).inflate(radius));
        for (Player player : players) {
            player.addEffect(new MobEffectInstance(speedBuff));
        }

        List<ByteBuddyEntity> byteBuddies = level.getEntitiesOfClass(ByteBuddyEntity.class, new AABB(buddyPos).inflate(radius));
        for (ByteBuddyEntity buddyEntity : byteBuddies) {
            buddyEntity.addEffect(new MobEffectInstance(speedBuff));
        }
    }
}
