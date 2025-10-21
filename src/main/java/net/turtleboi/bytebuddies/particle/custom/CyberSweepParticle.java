package net.turtleboi.bytebuddies.particle.custom;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.AttackSweepParticle;
import net.minecraft.client.particle.SpriteSet;

public class CyberSweepParticle extends AttackSweepParticle {
    public CyberSweepParticle(ClientLevel level, double x, double y, double z, double quadSizeMultiplier, SpriteSet sprites) {
        super(level, x, y, z, quadSizeMultiplier, sprites);
    }
}
