package net.turtleboi.bytebuddies.events;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.turtleboi.bytebuddies.ByteBuddies;
import net.turtleboi.bytebuddies.block.ModBlocks;
import net.turtleboi.bytebuddies.entity.entities.ByteBuddyEntity;
import net.turtleboi.bytebuddies.item.ModItems;
import net.turtleboi.bytebuddies.item.custom.ClipboardItem;

@EventBusSubscriber(modid = ByteBuddies.MOD_ID)
public class ModEvents {
    @SubscribeEvent
    public static void onLivingFall(LivingFallEvent event) {
        if (event.getEntity() instanceof ByteBuddyEntity) {
           event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLivingHurt(LivingDamageEvent.Post event) {
        if (event.getEntity() instanceof ByteBuddyEntity byteBuddy) {
            //ByteBuddies.LOGGER.warn("[ByteBuddies] DAMAGE buddy={} healthNow={}", byteBuddy.getId(), byteBuddy.getHealth());
        }

        if (event.getSource().getEntity() instanceof ByteBuddyEntity byteBuddy) {
            //ByteBuddies.LOGGER.warn("[ByteBuddies] DAMAGED buddy={} damage={} entity={}", byteBuddy.getId(), event.getOriginalDamage(), event.getEntity());
        }
    }

    @SubscribeEvent
    private static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        var stack = event.getItemStack();
        if (stack.getItem() instanceof ClipboardItem clipboard){
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.CONSUME);

            if (!event.getLevel().isClientSide()) {
                clipboard.handleClick(event.getEntity(), stack, event.getPos());
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
            ItemStack chargedOutPut = new ItemStack(chargedItem, itemStack.getCount());
            chargedOutPut.applyComponents(itemStack.getComponents());
            itemEntity.setItem(chargedOutPut);
        }
    }

    private static Item mapSteelToCharged(Item item) {
        if (item == ModItems.STEEL_INGOT.get()) return ModItems.CHARGED_STEEL_INGOT.get();
        if (item == ModItems.STEEL_NUGGET.get()) return ModItems.CHARGED_STEEL_NUGGET.get();
        if (item == ModBlocks.STEEL_BLOCK.get().asItem()) return ModBlocks.CHARGED_STEEL_BLOCK.get().asItem();
        return null;
    }
}
