package net.turtleboi.bytebuddies.block.custom;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RedstoneTorchBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

public class BluestoneOreBlock extends Block {
    public static final MapCodec<BluestoneOreBlock> CODEC = simpleCodec(BluestoneOreBlock::new);
    public static final BooleanProperty LIT = RedstoneTorchBlock.LIT;

    @Override
    public MapCodec<BluestoneOreBlock> codec() {
        return CODEC;
    }

    public BluestoneOreBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.defaultBlockState().setValue(LIT, Boolean.valueOf(false)));
    }

    @Override
    protected void attack(BlockState state, Level level, BlockPos pos, Player player) {
        interact(state, level, pos);
        super.attack(state, level, pos, player);
    }

    @Override
    public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
        if (!entity.isSteppingCarefully()) {
            interact(state, level, pos);
        }

        super.stepOn(level, pos, state, entity);
    }

    @Override
    protected ItemInteractionResult useItemOn(
            ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult
    ) {
        if (level.isClientSide) {
            spawnParticles(level, pos);
        } else {
            interact(state, level, pos);
        }

        return stack.getItem() instanceof BlockItem && new BlockPlaceContext(player, hand, stack, hitResult).canPlace()
                ? ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION
                : ItemInteractionResult.SUCCESS;
    }

    private static void interact(BlockState state, Level level, BlockPos pos) {
        spawnParticles(level, pos);
        if (!state.getValue(LIT)) {
            level.setBlock(pos, state.setValue(LIT, Boolean.valueOf(true)), 3);
        }
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return state.getValue(LIT);
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.getValue(LIT)) {
            level.setBlock(pos, state.setValue(LIT, Boolean.valueOf(false)), 3);
        }
    }

    @Override
    protected void spawnAfterBreak(BlockState state, ServerLevel level, BlockPos pos, ItemStack stack, boolean dropExperience) {
        super.spawnAfterBreak(state, level, pos, stack, dropExperience);
    }

    @Override
    public int getExpDrop(BlockState state, net.minecraft.world.level.LevelAccessor level, BlockPos pos,
                          BlockEntity blockEntity, Entity breaker, ItemStack tool) {
        return UniformInt.of(1, 5).sample(level.getRandom());
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (state.getValue(LIT)) {
            spawnParticles(level, pos);
        }
    }

    public static final Vector3f BLUESTONE_PARTICLE_COLOR = Vec3.fromRGB24(3127497).toVector3f();
    public static final DustParticleOptions BLUESTONE = new DustParticleOptions(BLUESTONE_PARTICLE_COLOR, 1.0F);

    private static void spawnParticles(Level level, BlockPos pos) {
        double d0 = 0.5625;
        RandomSource randomsource = level.random;

        for (Direction direction : Direction.values()) {
            BlockPos blockpos = pos.relative(direction);
            if (!level.getBlockState(blockpos).isSolidRender(level, blockpos)) {
                Direction.Axis direction$axis = direction.getAxis();
                double d1 = direction$axis == Direction.Axis.X ? 0.5 + 0.5625 * (double)direction.getStepX() : (double)randomsource.nextFloat();
                double d2 = direction$axis == Direction.Axis.Y ? 0.5 + 0.5625 * (double)direction.getStepY() : (double)randomsource.nextFloat();
                double d3 = direction$axis == Direction.Axis.Z ? 0.5 + 0.5625 * (double)direction.getStepZ() : (double)randomsource.nextFloat();
                level.addParticle(
                        BLUESTONE, (double)pos.getX() + d1, (double)pos.getY() + d2, (double)pos.getZ() + d3, 0.0, 0.0, 0.0
                );
            }
        }
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LIT);
    }
}
