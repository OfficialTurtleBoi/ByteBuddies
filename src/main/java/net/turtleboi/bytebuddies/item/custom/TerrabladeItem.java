package net.turtleboi.bytebuddies.item.custom;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.turtleboi.bytebuddies.component.ModDataComponents;
import net.turtleboi.bytebuddies.entity.entities.SwordSweepEntity;

import java.util.List;

public class TerrabladeItem extends SwordItem {
    private static final int MAX_CHARGE = 5;

    public TerrabladeItem(Tier tier, Properties properties) {
        super(tier, properties);
    }

    @Override
    public boolean hurtEnemy(ItemStack itemStack, LivingEntity livingEntity, LivingEntity attacker) {
        if (!attacker.level().isClientSide) {
            int charge = itemStack.getOrDefault(ModDataComponents.CHARGE.get(), 0);
            if (charge < MAX_CHARGE) {
                itemStack.set(ModDataComponents.CHARGE.get(), charge + 1);
            }
        }
        return super.hurtEnemy(itemStack, livingEntity, attacker);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand interactionHand) {
        if (!level.isClientSide) {
            int charge = player.getItemInHand(interactionHand).getOrDefault(ModDataComponents.CHARGE.get(), 0);
            if (charge == MAX_CHARGE) {
                SwordSweepEntity projectile = new SwordSweepEntity(level);
                projectile.setOwner(player);
                projectile.setPos(player.getX(), player.getEyeY() - 0.1, player.getZ());
                projectile.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, 1.8F, 0.0F);

                level.addFreshEntity(projectile);

                player.getCooldowns().addCooldown(this, 40);
                player.swing(interactionHand, true);
            }
        }
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(interactionHand), level.isClientSide());
    }


    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        int charge = stack.getOrDefault(ModDataComponents.CHARGE.get(), 0);
        tooltip.add(Component.literal("Charge: " + charge + " / " + MAX_CHARGE).withStyle(ChatFormatting.AQUA));
    }




}