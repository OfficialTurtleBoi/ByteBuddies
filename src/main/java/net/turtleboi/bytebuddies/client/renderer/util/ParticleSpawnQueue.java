package net.turtleboi.bytebuddies.client.renderer.util;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class ParticleSpawnQueue {
    private static final List<ScheduledParticleSpawn> queue = new CopyOnWriteArrayList<>();

    public static void schedule(long delayMillis, Runnable spawnTask) {
        long scheduledTime = System.currentTimeMillis() + delayMillis;
        queue.add(new ScheduledParticleSpawn(scheduledTime, spawnTask));
    }

    public static void tick() {
        long now = System.currentTimeMillis();
        List<ScheduledParticleSpawn> snapshot = new ArrayList<>(queue);
        for (ScheduledParticleSpawn spawn : snapshot) {
            if (spawn.scheduledTime <= now) {
                spawn.spawnTask.run();
                queue.remove(spawn);
            }
        }
    }

    private record ScheduledParticleSpawn(long scheduledTime, Runnable spawnTask){}
}
