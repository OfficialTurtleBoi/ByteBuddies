package net.turtleboi.bytebuddies.item.custom;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.turtleboi.bytebuddies.component.ModDataComponents;
import net.turtleboi.bytebuddies.effects.ModEffects;
import net.turtleboi.bytebuddies.entity.entities.SwordSweepEntity;

import java.util.List;

public class TerrabladeItem extends SwordItem {
    public static final int MAX_CHARGE = 100;

    public TerrabladeItem(Tier tier, Properties properties) {
        super(tier, properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand interactionHand) {
        ItemStack stack = player.getItemInHand(interactionHand);

        if (!level.isClientSide) {
            int charge = stack.getOrDefault(ModDataComponents.CHARGE.get(), 0);
            boolean hasOvercharged = player.hasEffect(ModEffects.OVERCHARGED); // <-- your custom effect registry

            // CASE 1: Player has Overcharged → fire projectile for free
            if (hasOvercharged) {
                SwordSweepEntity projectile = new SwordSweepEntity(level);
                projectile.setOwner(player);
                projectile.setPos(player.getX(), player.getEyeY() - 0.1, player.getZ());
                projectile.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, 1.8F, 0.0F);

                level.addFreshEntity(projectile);
                player.getCooldowns().addCooldown(this, 40);
                player.swing(interactionHand, true);

                return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
            }

            // CASE 2: Player has full charge (100), but no Overcharged → grant effect, don't shoot
            if (charge >= MAX_CHARGE) {
                player.addEffect(new MobEffectInstance(ModEffects.OVERCHARGED, 600)); // 600 ticks = 30 seconds
                stack.set(ModDataComponents.CHARGE.get(), 0);
                player.swing(interactionHand, true);
                return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
            }
        }

        // CASE 3: Not overcharged and not full charge → nothing happens
        return InteractionResultHolder.pass(player.getItemInHand(interactionHand));
    }



    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        int charge = stack.getOrDefault(ModDataComponents.CHARGE.get(), 0);
        tooltip.add(Component.literal("Charge: " + charge + " / " + MAX_CHARGE).withStyle(ChatFormatting.AQUA));
    }

    public static int getCharge(ItemStack itemStack) {
        Integer charge = itemStack.get(ModDataComponents.CHARGE);
        return charge == null ? 0 : Mth.clamp(charge, 0, 100);
    }

    public static void setCharge(ItemStack itemStack, int charge) {
        itemStack.set(ModDataComponents.CHARGE, Mth.clamp(charge, 0, 100));
    }

    public static void addCharge(ItemStack itemStack, int delta) {
        setCharge(itemStack, getCharge(itemStack) + delta);
    }


}