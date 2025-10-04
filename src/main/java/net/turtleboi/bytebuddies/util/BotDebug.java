package net.turtleboi.bytebuddies.util;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.turtleboi.bytebuddies.ByteBuddies;
import net.turtleboi.bytebuddies.block.custom.BluestoneOreBlock;
import net.turtleboi.bytebuddies.entity.entities.ByteBuddyEntity;
import org.slf4j.Logger;

import java.util.Comparator;
import java.util.Optional;

public final class BotDebug {
    public enum GoalPhase {
        IDLE,
        SEEKING,
        MOVING,
        ACTING,
        COOLDOWN
    }

    public enum FailReason {
        NONE,
        WRONG_ROLE,
        NO_DOCK,
        NO_TARGET,
        OUT_OF_ENERGY,
        NOT_CROP,
        PATH_FAIL,
        INVENTORY_FULL
    }

    public static boolean ENABLED = true;
    public static boolean NAMEPLATE = false;
    public static boolean ACTIONBAR = true;
    public static boolean PARTICLES = true;

    private BotDebug() {}

    public static void log(ByteBuddyEntity byteBuddy, String logMessage) {
        if (ENABLED) {
            ByteBuddies.LOGGER.debug("[ByteBuddy:{}@{}] {}", byteBuddy.getId(), byteBuddy.blockPosition(), logMessage);
            if (NAMEPLATE) {
                byteBuddy.setCustomName(Component.literal(shorten(logMessage, 32)));
                byteBuddy.setCustomNameVisible(true);
            }

            if (ACTIONBAR && byteBuddy.level() instanceof ServerLevel serverLevel) {
                nearestPlayer(serverLevel, byteBuddy).ifPresent(
                        player -> player.displayClientMessage(Component.literal(logMessage), true)
                );
            }
        }
    }

    public static void mark(Level level, BlockPos blockPos) {
        if (PARTICLES && !level.isClientSide) {
            if (level instanceof ServerLevel serverLevel) {
                for (int i = 0; i < 8; i++) {
                    double particleInterval = (Math.PI * 2 * i) / 8;
                    serverLevel.sendParticles(BluestoneOreBlock.BLUESTONE,
                            blockPos.getX() + 0.5 + Math.cos(particleInterval) * 0.5,
                            blockPos.getY() + 0.1,
                            blockPos.getZ() + 0.5 + Math.sin(particleInterval) * 0.5,
                            1, 0, 0, 0, 0);
                }
            }
        }
    }

    private static Optional<Player> nearestPlayer(ServerLevel serverLevel, ByteBuddyEntity byteBuddy) {
        var list = serverLevel.getEntitiesOfClass(Player.class, new AABB(byteBuddy.blockPosition()).inflate(16));
        if (!list.isEmpty()) {
            list.sort(Comparator.comparingDouble(a -> a.distanceToSqr(byteBuddy)));
            return Optional.of(list.get(0));
        } else {
            return Optional.empty();
        }
    }

    private static String shorten(String s, int n) { return s.length() <= n ? s : s.substring(0, n); }
}
