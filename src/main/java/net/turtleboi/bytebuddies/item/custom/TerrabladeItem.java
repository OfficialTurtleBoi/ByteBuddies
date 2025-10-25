package net.turtleboi.bytebuddies.item.custom;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.turtleboi.bytebuddies.component.ModDataComponents;
import net.turtleboi.bytebuddies.effects.ModEffects;
import net.turtleboi.bytebuddies.entity.entities.SwordSweepEntity;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class TerrabladeItem extends SwordItem {
    public static final int MAX_CHARGE = 100;

    public TerrabladeItem(Tier tier, Properties properties) {
        super(tier, properties);
    }

    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) return InteractionResultHolder.pass(stack);


        boolean active = isActive(stack);
        if (active || player.hasEffect(ModEffects.SUPERCHARGED)) {
            SwordSweepEntity projectile = new SwordSweepEntity(level);
            projectile.setOwner(player);
            projectile.setPos(player.getX(), player.getY() + (player.getBbHeight() / 2), player.getZ());
            Vec3 lookAngle = player.getLookAngle();
            float velocity = 1.63f;
            projectile.shoot(lookAngle.x, lookAngle.y, lookAngle.z, velocity, 0);
            Vec3 vec3 = player.getKnownMovement();
            projectile.setDeltaMovement(projectile.getDeltaMovement().add(vec3.x, player.onGround() ? 0.0 : vec3.y, vec3.z));
            projectile.setProjectileDamage((float) player.getAttributeValue(Attributes.ATTACK_DAMAGE));
            level.addFreshEntity(projectile);
            player.getCooldowns().addCooldown(this, 10);
            player.swing(hand, true);
            return InteractionResultHolder.sidedSuccess(stack, false);
        }
        return InteractionResultHolder.pass(stack);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
        super.inventoryTick(stack, level, entity, slotId, isSelected);
        if (level.isClientSide || !(entity instanceof Player player)) return;
        int charge = getCharge(stack);
        if(!isActive(stack)) {
            if (charge >= MAX_CHARGE) {
                setActive(stack, true);
                int effectDuration = getCharge(stack);
                player.addEffect(new MobEffectInstance(
                        ModEffects.SUPERCHARGED, effectDuration, 0, false, true, true));
                player.swing(player.getUsedItemHand(), true);
            }
        }

        if (isActive(stack)) {
            if (charge <= 0) {
                setActive(stack, false);
                player.removeEffect(ModEffects.SUPERCHARGED);
                return;
            }

            int newCharge = charge- 1;
            setCharge(stack, newCharge);
            MobEffectInstance mobEffectInstance = player.getEffect(ModEffects.SUPERCHARGED);
            int effectDuration = (mobEffectInstance == null) ? 0 : mobEffectInstance.getDuration();
            if (effectDuration != newCharge) {
                player.addEffect(new MobEffectInstance(
                        ModEffects.SUPERCHARGED,
                        newCharge,
                        0,
                        false,
                        true,
                        true
                ));
            }
        }
    }

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || !ItemStack.isSameItem(oldStack, newStack);
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

    public static boolean isActive(ItemStack itemStack) {
        Boolean isSuperCharged = itemStack.get(ModDataComponents.SUPER_CHARGED.get());
        return isSuperCharged != null && isSuperCharged;
    }

    public static void setActive(ItemStack itemStack, boolean active) {
        itemStack.set(ModDataComponents.SUPER_CHARGED.get(), active);
    }
}