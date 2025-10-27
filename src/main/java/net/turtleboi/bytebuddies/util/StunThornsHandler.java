package net.turtleboi.bytebuddies.util;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.turtleboi.bytebuddies.effects.ModEffects;

public class StunThornsHandler {
    public static void setStunner(LivingEntity livingEntity, Entity stunner){
        if (!livingEntity.getPersistentData().contains("StunnedBy")) {
            //System.out.println(livingEntity + " stunned by " + stunner + "!");
            livingEntity.getPersistentData().putUUID("StunnedBy", stunner.getUUID());
        }
    }
    public static void stunByEntity(LivingEntity livingEntity, LivingEntity stunner){
        setStunner(livingEntity,stunner);
        Level level = stunner.level();
        if (stunner instanceof Player player) {
            livingEntity.hurt(level.damageSources().playerAttack(player), 6);
        }
        else{
            livingEntity.hurt(level.damageSources().mobAttack(stunner),6);
        }
        MobEffectInstance mobEffectInstance = new MobEffectInstance(ModEffects.STUNNED,100);
        livingEntity.addEffect(mobEffectInstance,stunner);
    }
}
