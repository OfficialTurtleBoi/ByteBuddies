package net.turtleboi.bytebuddies.block.custom;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.turtleboi.bytebuddies.block.ModBlockEntities;
import net.turtleboi.bytebuddies.block.entity.DockingStationBlockEntity;
import net.turtleboi.bytebuddies.block.entity.GeneratorBlockEntity;
import net.turtleboi.bytebuddies.block.entity.SolarPanelBlockEntity;
import net.turtleboi.bytebuddies.item.ModItems;
import net.turtleboi.bytebuddies.screen.custom.menu.DockingStationMenu;
import net.turtleboi.bytebuddies.screen.custom.menu.SolarPanelMenu;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class SolarPanelBlock extends BaseEntityBlock {
    public static final MapCodec<GeneratorBlock> CODEC = simpleCodec(GeneratorBlock::new);
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

    public SolarPanelBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos blockPos, BlockState blockState) {
        return new SolarPanelBlockEntity(blockPos, blockState);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected void onRemove(BlockState blockState, Level level, BlockPos blockPos, BlockState newState, boolean movedByPiston) {
        if (blockState.getBlock() != newState.getBlock()){
            BlockEntity blockEntity = level.getBlockEntity(blockPos);
            if (blockEntity instanceof SolarPanelBlockEntity solarPanelEntity) {
                solarPanelEntity.drops();
            }
        }
        super.onRemove(blockState, level, blockPos, newState, movedByPiston);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack itemStack, BlockState blockState, Level level, BlockPos blockPos,
                                              Player player, InteractionHand interactionHand, BlockHitResult hitResult) {
        if (!level.isClientSide()){
            BlockEntity blockEntity = level.getBlockEntity(blockPos);
            if (blockEntity instanceof SolarPanelBlockEntity solarPanelEntity) {
                if (player instanceof ServerPlayer serverPlayer) {
                    serverPlayer.openMenu(new SimpleMenuProvider((containerId, inventory, interactingPlayer) ->
                                    new SolarPanelMenu(containerId, inventory, solarPanelEntity),
                                    Component.literal("Solar Panel")),
                            buf -> buf.writeBlockPos(blockPos));
                    return ItemInteractionResult.sidedSuccess(level.isClientSide);
                }
            } else {
                throw new IllegalStateException("Our container provider is missing");
            }
        }

        return ItemInteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    protected RenderShape getRenderShape(BlockState blockState) {
        return RenderShape.MODEL;
    }

    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
        if (level.isClientSide()) {
            return null;
        }

        return createTickerHelper(blockEntityType, ModBlockEntities.SOLAR_PANEL_BE.get(),
                (blockLevel, blockPos, blockState, blockEntity) -> blockEntity.tick(blockLevel, blockPos, blockState));
    }
}
