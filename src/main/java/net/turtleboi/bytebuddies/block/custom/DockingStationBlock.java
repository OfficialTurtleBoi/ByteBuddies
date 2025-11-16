package net.turtleboi.bytebuddies.block.custom;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.LivingEntity;
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
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.network.NetworkHooks;
import net.turtleboi.bytebuddies.block.ModBlockEntities;
import net.turtleboi.bytebuddies.block.entity.DockingStationBlockEntity;
import net.turtleboi.bytebuddies.item.ModItems;
import net.turtleboi.bytebuddies.network.ModNetworking;
import net.turtleboi.bytebuddies.screen.custom.menu.DockingStationMenu;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class DockingStationBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;

    public DockingStationBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(OPEN, Boolean.FALSE));
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos blockPos, BlockState blockState) {
        return new DockingStationBlockEntity(blockPos, blockState);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {}

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState()
                .setValue(FACING, context.getHorizontalDirection().getOpposite())
                .setValue(OPEN, Boolean.FALSE);
    }

    @Override
    public void onRemove(BlockState blockState, Level level, BlockPos blockPos, BlockState newState, boolean movedByPiston) {
        if (blockState.getBlock() != newState.getBlock()){
            BlockEntity blockEntity = level.getBlockEntity(blockPos);
            if (blockEntity instanceof DockingStationBlockEntity dockingStation) {
                dockingStation.drops();
            }
        }
        super.onRemove(blockState, level, blockPos, newState, movedByPiston);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        ItemStack itemStack = player.getItemInHand(hand);
        if (itemStack.is(ModItems.WRENCH.get())) {
            return InteractionResult.PASS;
        }

        if (!level.isClientSide) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof DockingStationBlockEntity dockingStation) {
                if (player instanceof ServerPlayer serverPlayer) {
                    List<Integer> ids = dockingStation.findByteBuddyEntityIds((ServerLevel) level);

                    MenuProvider provider = new SimpleMenuProvider(
                            (containerId, inventory, interactingPlayer) ->
                                    new DockingStationMenu(containerId, inventory, dockingStation),
                            Component.literal("Docking Station")
                    );

                    NetworkHooks.openScreen(serverPlayer, provider,
                            buf -> {
                                buf.writeBlockPos(pos);
                                buf.writeVarInt(ids.size());
                                for (int id : ids) {
                                    buf.writeVarInt(id);
                                }
                            }
                    );
                }
                return InteractionResult.sidedSuccess(false);
            } else {
                throw new IllegalStateException("Our container provider is missing");
            }
        }

        return InteractionResult.sidedSuccess(true);
    }

    public static Direction getHorizontalFacing(BlockState blockState) {
        if (blockState.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            return blockState.getValue(BlockStateProperties.HORIZONTAL_FACING);
        }
        if (blockState.hasProperty(BlockStateProperties.FACING)) {
            Direction direction = blockState.getValue(BlockStateProperties.FACING);
            return direction.getAxis().isHorizontal() ? direction : Direction.NORTH;
        }
        return Direction.NORTH;
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, OPEN);
    }

    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
        if (level.isClientSide()) {
            return null;
        }

        return createTickerHelper(blockEntityType, ModBlockEntities.DOCKING_STATION_BE.get(),
                (blockLevel, blockPos, blockState, blockEntity) -> blockEntity.tick(blockLevel, blockPos, blockState));
    }
}
