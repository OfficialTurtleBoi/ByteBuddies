package net.turtleboi.turtlecore.spells;

import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.FORGE)
public class SpellScheduler {
    private static final List<ScheduledSpellCast> spellEffect = new CopyOnWriteArrayList<>();

    public static void schedule(ServerLevel level, long delayTicks, Runnable spawnTask) {
        long scheduledTime = level.getGameTime() + Math.max(0L, delayTicks);
        spellEffect.add(new ScheduledSpellCast(level, scheduledTime, spawnTask));
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        List<ScheduledSpellCast> scheduledSpellEffects = new ArrayList<>(spellEffect);
        if (!scheduledSpellEffects.isEmpty()) {
            for (ScheduledSpellCast spellCast : scheduledSpellEffects) {
                long currentTick = spellCast.level.getGameTime();
                if (spellCast.scheduledTick <= currentTick) {
                    spellCast.castTask.run();
                    spellEffect.remove(spellCast);
                }
            }
        }
    }

    private record ScheduledSpellCast(ServerLevel level, long scheduledTick, Runnable castTask){}
}
