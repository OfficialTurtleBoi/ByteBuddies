package net.turtleboi.turtlecore.client.renderer;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.turtleboi.turtlecore.client.util.ParticleSpawnQueue;
import net.turtleboi.turtlecore.network.CoreNetworking;
import net.turtleboi.turtlecore.network.packet.util.SendParticlesS2C;
import org.joml.Vector3f;

public class ShockwaveRenderer {
    private final long startTime;
    private final int durationTicks;

    public ShockwaveRenderer(long currentTime, int durationTicks) {
        this.startTime = currentTime;
        this.durationTicks = durationTicks;
    }

    public boolean isFinished() {
        long elapsedMillis = System.currentTimeMillis() - startTime;
        return elapsedMillis > durationTicks * 50L;
    }

    public static void triggerShockwave(LivingEntity entity, int radius) {
        Level world = entity.level();
        BlockPos center = entity.blockPosition().below();
        RandomSource random = world.getRandom();

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz > radius * radius) {
                    continue;
                }

                BlockPos pos = center.offset(dx, 0, dz);
                BlockState state = world.getBlockState(pos);
                if (!state.isAir() && state.getBlock() != Blocks.WATER) {
                    for (int i = 0; i < 40; i++) {
                        double spawnX = pos.getX() + 0.5 + (random.nextDouble() - 0.5);
                        double spawnY = pos.getY() + 1 + random.nextDouble() * 0.3;
                        double spawnZ = pos.getZ() + 0.5 + (random.nextDouble() - 0.5);

                        double offsetX = pos.getX() + 0.5 - entity.getX();
                        double offsetZ = pos.getZ() + 0.5 - entity.getZ();
                        double distance = Mth.sqrt((float)(offsetX * offsetX + offsetZ * offsetZ));
                        double normX = distance != 0 ? offsetX / distance : 0;
                        double normZ = distance != 0 ? offsetZ / distance : 0;

                        double velocityX = normX * (0.1 + random.nextDouble() * 0.1);
                        double velocityY = 0.5 + random.nextDouble() * 0.5;
                        double velocityZ = normZ * (0.1 + random.nextDouble() * 0.1);

                        long delayTicks = (long)(distance);
                        long delayMillis = delayTicks * 50;

                        ParticleSpawnQueue.schedule(delayMillis, () ->
                                CoreNetworking.sendToNear(new SendParticlesS2C(
                                        new BlockParticleOption(ParticleTypes.BLOCK, state),
                                        spawnX, spawnY, spawnZ,
                                        velocityX, velocityY, velocityZ
                                ), entity));
                    }
                }
            }
        }
    }
}
