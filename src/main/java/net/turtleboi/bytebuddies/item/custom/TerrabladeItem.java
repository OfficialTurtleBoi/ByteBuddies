package net.turtleboi.bytebuddies.item.custom;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
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
import net.turtleboi.bytebuddies.effects.ModEffects;
import net.turtleboi.bytebuddies.entity.entities.SwordSweepEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class TerrabladeItem extends SwordItem {
    private static final String CHARGE_TAG = "Charge";
    private static final String SUPERCHARGED_TAG = "SuperCharged";
    public static final int MAX_CHARGE = 100;

    public TerrabladeItem(Tier pTier, int pAttackDamageModifier, float pAttackSpeedModifier, Item.Properties pProperties) {
        super(pTier, pAttackDamageModifier, pAttackSpeedModifier, pProperties);
    }

    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) return InteractionResultHolder.pass(stack);


        boolean active = isActive(stack);
        if (active || player.hasEffect(ModEffects.SUPERCHARGED.get())) {
            SwordSweepEntity projectile = new SwordSweepEntity(level);
            projectile.setOwner(player);
            projectile.setPos(player.getX(), player.getY() + (player.getBbHeight() / 2), player.getZ());
            Vec3 lookAngle = player.getLookAngle();
            float velocity = 1.63f;
            projectile.shoot(lookAngle.x, lookAngle.y, lookAngle.z, velocity, 0);
            Vec3 vec3 = player.getDeltaMovement();
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
                        ModEffects.SUPERCHARGED.get(), effectDuration, 0, false, true, true));
                player.swing(player.getUsedItemHand(), true);
            }
        }

        if (isActive(stack)) {
            if (charge <= 0) {
                setActive(stack, false);
                player.removeEffect(ModEffects.SUPERCHARGED.get());
                return;
            }

            int newCharge = charge- 1;
            setCharge(stack, newCharge);
            MobEffectInstance mobEffectInstance = player.getEffect(ModEffects.SUPERCHARGED.get());
            int effectDuration = (mobEffectInstance == null) ? 0 : mobEffectInstance.getDuration();
            if (effectDuration != newCharge) {
                player.addEffect(new MobEffectInstance(
                        ModEffects.SUPERCHARGED.get(),
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
    public void appendHoverText(ItemStack pStack, @Nullable Level pLevel, List<Component> pTooltipComponents, TooltipFlag pIsAdvanced) {
        super.appendHoverText(pStack, pLevel, pTooltipComponents, pIsAdvanced);
        int charge = getCharge(pStack);
        pTooltipComponents.add(
                Component.literal("Charge: " + charge + " / " + MAX_CHARGE)
                        .withStyle(ChatFormatting.AQUA)
        );
    }

    public static int getCharge(ItemStack itemStack) {
        CompoundTag tag = itemStack.getTag();
        int charge = 0;
        if (tag != null && tag.contains(CHARGE_TAG)) {
            charge = tag.getInt(CHARGE_TAG);
        }
        return Mth.clamp(charge, 0, MAX_CHARGE);
    }

    public static void setCharge(ItemStack itemStack, int charge) {
        CompoundTag tag = itemStack.getOrCreateTag();
        tag.putInt(CHARGE_TAG, Mth.clamp(charge, 0, MAX_CHARGE));
    }

    public static boolean isActive(ItemStack itemStack) {
        CompoundTag tag = itemStack.getTag();
        return tag != null && tag.getBoolean(SUPERCHARGED_TAG);
    }

    public static void setActive(ItemStack itemStack, boolean active) {
        CompoundTag tag = itemStack.getOrCreateTag();
        tag.putBoolean(SUPERCHARGED_TAG, active);
    }

}