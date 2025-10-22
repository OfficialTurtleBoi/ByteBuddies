package net.turtleboi.bytebuddies.item.custom;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.turtleboi.bytebuddies.entity.ModEntities;
import net.turtleboi.bytebuddies.entity.entities.ByteBuddyEntity;

public class ByteBuddySpawnerItem extends Item {
    public ByteBuddySpawnerItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResult useOn(UseOnContext useOnContext) {
        Level level = useOnContext.getLevel();
        Player player = useOnContext.getPlayer();
        if (level instanceof ServerLevel serverLevel) {
            if (player == null) return InteractionResult.FAIL;

            BlockPos clickedPos = useOnContext.getClickedPos();
            Direction facingDirection = useOnContext.getClickedFace();
            BlockPos spawnPos = clickedPos.relative(facingDirection);

            double x = spawnPos.getX() + 0.5D;
            double y = spawnPos.getY();
            double z = spawnPos.getZ() + 0.5D;

            EntityType<ByteBuddyEntity> entityType = ModEntities.BYTEBUDDY.get();
            var buddy = entityType.create(serverLevel);
            if (buddy == null) return InteractionResult.FAIL;

            buddy.setPos(x, y, z);
            Vec3 toPlayer = player.position().subtract(buddy.position());
            float yaw = (float) (Mth.atan2(toPlayer.z, toPlayer.x) * (180.0F / Math.PI)) - 90.0F;

            buddy.setYRot(yaw);
            buddy.setXRot(0.0F);
            buddy.setYBodyRot(yaw);
            buddy.setYHeadRot(yaw);
            buddy.setMood(ByteBuddyEntity.Mood.SLEEP);
            serverLevel.addFreshEntity(buddy);

            ItemStack stack = useOnContext.getItemInHand();
            if (!player.getAbilities().instabuild) stack.shrink(1);
            return InteractionResult.CONSUME;
        } else {
            return InteractionResult.SUCCESS_NO_ITEM_USED;
        }
    }
}
