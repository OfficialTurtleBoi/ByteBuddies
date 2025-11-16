package net.turtleboi.bytebuddies.events;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.turtleboi.bytebuddies.ByteBuddies;
import net.turtleboi.bytebuddies.block.ModBlocks;
import net.turtleboi.bytebuddies.effects.ModEffects;
import net.turtleboi.bytebuddies.entity.entities.ByteBuddyEntity;
import net.turtleboi.bytebuddies.item.ModItems;
import net.turtleboi.bytebuddies.item.custom.ClipboardItem;
import net.turtleboi.bytebuddies.item.custom.TerrabladeItem;
import net.turtleboi.turtlecore.effect.CoreEffects;

@Mod.EventBusSubscriber(modid = ByteBuddies.MOD_ID)
public class ModEvents {
    @SubscribeEvent
    public static void onLivingFall(LivingFallEvent event) {
        if (event.getEntity() instanceof ByteBuddyEntity) {
           event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLivingHurt(LivingDamageEvent event) {
        if (event.getEntity() instanceof ByteBuddyEntity byteBuddy) {
            //ByteBuddies.LOGGER.warn("[ByteBuddies] DAMAGE buddy={} healthNow={}", byteBuddy.getId(), byteBuddy.getHealth());
        }

        if (event.getSource().getEntity() instanceof ByteBuddyEntity byteBuddy) {
            //ByteBuddies.LOGGER.warn("[ByteBuddies] DAMAGED buddy={} damage={} entity={}", byteBuddy.getId(), event.getOriginalDamage(), event.getEntity());
        }

        DamageSource source = event.getSource();
        if (source.getEntity() instanceof LivingEntity attacker) {
            ItemStack weapon = attacker.getMainHandItem();

            if (weapon.getItem() instanceof TerrabladeItem) {
                float damageDealt = event.getAmount();
                int charge = TerrabladeItem.getCharge(weapon);
                int newCharge = Math.min(charge + Math.round(damageDealt), TerrabladeItem.MAX_CHARGE);
                TerrabladeItem.setCharge(weapon, newCharge);
            }

            LivingEntity victim = event.getEntity();
            Level level = victim.level();
            if (victim.hasEffect(ModEffects.SUPERCHARGED.get())) {
                if (victim instanceof Player player) {
                    attacker.hurt(level.damageSources().playerAttack(player), 6);
                } else {
                    attacker.hurt(level.damageSources().mobAttack(victim), 6);
                }
                MobEffectInstance stun = new MobEffectInstance(CoreEffects.STUNNED.get(), 100);
                attacker.addEffect(stun, victim);
            }
            if (victim instanceof ByteBuddyEntity byteBuddy) {
                if (byteBuddy.getAugmentEffects().shockOnHit()) {
                    byteBuddy.onMeleeHit(attacker);
                    MobEffectInstance stun = new MobEffectInstance(CoreEffects.STUNNED.get(), 100);
                    attacker.addEffect(stun, victim);
                }
            }
        }
    }

    private static final int chargeRadius = 3;
    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) {
            return;
        }

        Entity entity = event.getEntity();
        if (!(entity instanceof LightningBolt lightning)) {
            return;
        }

        ServerLevel level = (ServerLevel) event.getLevel();
        BlockPos center = lightning.blockPosition();

        chargeNearbyBlocks(level, center, chargeRadius);
        chargeNearbyItemEntities(level, center, chargeRadius);
    }

    private static void chargeNearbyBlocks(ServerLevel level, BlockPos center, int radius) {
        BlockPos min = center.offset(-radius, -1, -radius);
        BlockPos max = center.offset( radius,  1,  radius);
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            BlockState state = level.getBlockState(pos);
            if (state.is(ModBlocks.STEEL_BLOCK.get())) {
                level.setBlockAndUpdate(pos, ModBlocks.CHARGED_STEEL_BLOCK.get().defaultBlockState());
            }
        }
    }

    private static void chargeNearbyItemEntities(ServerLevel level, BlockPos center, int radius) {
        AABB boundingBox = new AABB(center).inflate(radius);
        for (ItemEntity itemEntity : level.getEntitiesOfClass(ItemEntity.class, boundingBox)) {
            ItemStack itemStack = itemEntity.getItem();
            if (itemStack.isEmpty()) continue;

            Item chargedItem = mapSteelToCharged(itemStack.getItem());
            if (chargedItem == null) continue;

            ItemStack chargedOutput = new ItemStack(chargedItem, itemStack.getCount());
            if (itemStack.hasTag()) {
                chargedOutput.setTag(itemStack.getTag().copy());
            }

            itemEntity.setItem(chargedOutput);
        }
    }


    private static Item mapSteelToCharged(Item item) {
        if (item == ModItems.STEEL_INGOT.get()) return ModItems.CHARGED_STEEL_INGOT.get();
        if (item == ModItems.STEEL_NUGGET.get()) return ModItems.CHARGED_STEEL_NUGGET.get();
        if (item == ModBlocks.STEEL_BLOCK.get().asItem()) return ModBlocks.CHARGED_STEEL_BLOCK.get().asItem();
        return null;
    }
}
