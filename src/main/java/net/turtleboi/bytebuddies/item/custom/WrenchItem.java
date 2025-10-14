package net.turtleboi.bytebuddies.item.custom;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.turtleboi.bytebuddies.ByteBuddies;
import net.turtleboi.bytebuddies.block.custom.DockingStationBlock;
import net.turtleboi.bytebuddies.block.entity.DockingStationBlockEntity;
import net.turtleboi.bytebuddies.entity.entities.ByteBuddyEntity;

import java.util.List;
import java.util.Optional;

public class WrenchItem extends Item {
    private static final String dockPos = "DockPos";
    private static final String dockDimension = "DockDim";

    public WrenchItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext useOnContext) {
        final Level level = useOnContext.getLevel();
        final BlockPos blockPos = useOnContext.getClickedPos();
        final var blockState = level.getBlockState(blockPos);

        ByteBuddies.LOGGER.info("[ByteBuddies] Wrench.useOn: side={} pos={} block={}",
                level.isClientSide ? "CLIENT" : "SERVER", blockPos, blockState.getBlock().getClass().getSimpleName());

        Player player = useOnContext.getPlayer();
        ItemStack wrenchStack = useOnContext.getItemInHand();

        if (player != null && player.isShiftKeyDown()) {
            clearDock(wrenchStack);
            if (!level.isClientSide) ByteBuddies.LOGGER.info("[ByteBuddies] wrench: cleared link");
            player.displayClientMessage(Component.literal("Cleared wrench link"), true);
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        if (!level.isClientSide && blockState.getBlock() instanceof DockingStationBlock) {
            setDock(wrenchStack, level, blockPos);
            ByteBuddies.LOGGER.info("[ByteBuddies] wrench: stored dock {} in {}",
                    blockPos, level.dimension().location());
        }

        if (player != null) player.displayClientMessage(Component.literal(
                "Stored dock: " + blockPos.toShortString() + " (" + level.dimension().location() + ")"), true);

        return InteractionResult.sidedSuccess(level.isClientSide);
    }


    @Override
    public InteractionResult interactLivingEntity(ItemStack wrenchStack, Player player, LivingEntity target, InteractionHand hand) {
        if (!(target instanceof ByteBuddyEntity byteBuddy)) return InteractionResult.PASS;
        Level level = player.level();

        if (player.isShiftKeyDown()) {
            Optional<BlockPos> byteBuddyDock = byteBuddy.getDock();
            byteBuddy.clearDock();
            sendMessage(player, "Bot unbound from dock" + (byteBuddyDock.isPresent() ? " " + byteBuddyDock.get().toShortString() : ""));
            ByteBuddies.LOGGER.info(
                    "[ByteBuddies] wrench: unbound bot id={} from dock={}", byteBuddy.getId(), byteBuddyDock.map(BlockPos::toShortString).orElse("-"));
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        Optional<GlobalPos> dockBlockPosOpt = getDock(wrenchStack);
        if (dockBlockPosOpt.isEmpty()) {
            sendMessage(player, "Wrench is not linked to a dock (right-click a Docking Station first)");
            ByteBuddies.LOGGER.warn("[ByteBuddies] wrench: no dock stored when attempting to bind");
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        GlobalPos dockBlockPos = dockBlockPosOpt.get();
        if (!dockBlockPos.dimension().equals(level.dimension())) {
            sendMessage(player, "Dock is in different dimension: " + dockBlockPos.dimension().location());
            ByteBuddies.LOGGER.warn(
                    "[ByteBuddies] wrench: dim mismatch botDim={} dockDim={}", level.dimension().location(), dockBlockPos.dimension().location());
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        if (!(level.getBlockState(dockBlockPos.pos()).getBlock() instanceof DockingStationBlock)) {
            sendMessage(player, "Stored dock is missing. Clear the wrench link and re-set it.");
            ByteBuddies.LOGGER.warn("[ByteBuddies] wrench: stored dock not found at {}", dockBlockPos.pos());
            return InteractionResult.sidedSuccess(level.isClientSide);
        }


        byteBuddy.setDock(dockBlockPos.pos());
        if (level.getBlockEntity(dockBlockPos.pos()) instanceof DockingStationBlockEntity dockBlockEntity) {
            dockBlockEntity.addBoundBuddy(byteBuddy);
        }

        sendMessage(player, "Bot bound to dock " + dockBlockPos.pos().toShortString());
        ByteBuddies.LOGGER.info("[ByteBuddies] wrench: bound bot id={} to dock {}", byteBuddy.getId(), dockBlockPos.pos());
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    private static void setDock(ItemStack wrenchStack, Level level, BlockPos blockPos) {
        CompoundTag nbtData = wrenchStack.get(DataComponents.CUSTOM_DATA) != null
                ? wrenchStack.get(DataComponents.CUSTOM_DATA).copyTag()
                : new CompoundTag();

        nbtData.putLong(dockPos, blockPos.asLong());
        nbtData.putString(dockDimension, level.dimension().location().toString());

        wrenchStack.set(DataComponents.CUSTOM_DATA, CustomData.of(nbtData));
    }

    private static Optional<GlobalPos> getDock(ItemStack wrenchStack) {
        CustomData dataComponents = wrenchStack.get(DataComponents.CUSTOM_DATA);
        if (dataComponents == null) return Optional.empty();

        CompoundTag nbtData = dataComponents.copyTag();
        if (!nbtData.contains(dockPos) || !nbtData.contains(dockDimension)) return Optional.empty();

        BlockPos blockPos = BlockPos.of(nbtData.getLong(dockPos));
        ResourceLocation dimensionLocation = ResourceLocation.parse(nbtData.getString(dockDimension));
        ResourceKey<Level> levelKey = ResourceKey.create(Registries.DIMENSION, dimensionLocation);

        return Optional.of(GlobalPos.of(levelKey, blockPos));
    }

    private static void clearDock(ItemStack wrenchStack) {
        CustomData dataComponents = wrenchStack.get(DataComponents.CUSTOM_DATA);
        if (dataComponents == null) return;

        CompoundTag nbtData = dataComponents.copyTag();
        nbtData.remove(dockPos);
        nbtData.remove(dockDimension);
        wrenchStack.set(DataComponents.CUSTOM_DATA, CustomData.of(nbtData));
    }

    private static void sendMessage(Player player, String message) {
        if (player != null) {
            player.displayClientMessage(Component.literal(message), true);
        }
    }

    @Override
    public void appendHoverText(ItemStack wrenchItem, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(wrenchItem, context, tooltip, flag);
        CustomData dataComponents = wrenchItem.get(DataComponents.CUSTOM_DATA);
        if (dataComponents != null) {
            CompoundTag nbtData = dataComponents.copyTag();
            if (nbtData.contains(dockPos) && nbtData.contains(dockDimension)) {
                BlockPos blockPos = BlockPos.of(nbtData.getLong(dockPos));
                String dimensionString = nbtData.getString(dockDimension);
                tooltip.add(Component.literal("Linked Dock: " + blockPos.toShortString() + " (" + dimensionString + ")"));
                return;
            }
        }
        tooltip.add(Component.literal("Linked Dock: <none>"));
    }
}
