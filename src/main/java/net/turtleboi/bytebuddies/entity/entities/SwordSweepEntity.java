package net.turtleboi.bytebuddies.entity.entities;

import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.turtleboi.bytebuddies.entity.ModEntities;
import net.turtleboi.bytebuddies.particle.ModParticles;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class SwordSweepEntity extends AbstractArrow {
    private float rotation;
    public Vec2 groundedOffset;

    private final Set<UUID> hitEntities = new HashSet<>();
    private int lifespan = 40; // ticks until auto-despawn (~2 seconds)

    public SwordSweepEntity(EntityType<? extends AbstractArrow> entityType, Level level) {
        super(entityType, level);
        this.setNoGravity(true); // sword sweeps shouldn't fall
        this.pickup = Pickup.DISALLOWED; // not pickable
    }

    public SwordSweepEntity(Level level) {
        this(ModEntities.SWORD_SWEEP.get(), level);
    }
    @Override
    public void tick() {
        super.tick();

        // Optional: spawn particles
        if (level().isClientSide) {
            for (int i = 0; i < 2; i++) {
                level().addParticle(
                        ModParticles.CYBER_SWEEP_PARTICLE.get(),
                        this.getX(), this.getY() + 0.1, this.getZ(),
                        0, 0, 0
                );
            }
        }

        // Manual hit detection to enable piercing
        for (Entity entity : level().getEntities(this, this.getBoundingBox().inflate(0.3))) {
            if (entity == this.getOwner() || !(entity instanceof LivingEntity target)) continue;
            if (hitEntities.contains(entity.getUUID())) continue;

            target.hurt(this.damageSources().thrown(this, this.getOwner()), 15.0F);
            hitEntities.add(entity.getUUID());
        }

        // Despawn after lifespan
        if (this.tickCount > lifespan) {
            this.discard();
        }
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        // Override but DON’T discard — we’re piercing
        // Damage is already handled in tick()
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        super.onHitBlock(result);

        // Optional: orient sprite based on hit direction (not mandatory for flat texture)
        Direction dir = result.getDirection();
        if (dir == Direction.SOUTH)  groundedOffset = new Vec2(215f,180f);
        if (dir == Direction.NORTH)  groundedOffset = new Vec2(215f, 0f);
        if (dir == Direction.EAST)   groundedOffset = new Vec2(215f,-90f);
        if (dir == Direction.WEST)   groundedOffset = new Vec2(215f,90f);
        if (dir == Direction.DOWN)   groundedOffset = new Vec2(115f,180f);
        if (dir == Direction.UP)     groundedOffset = new Vec2(285f,180f);

        // Optional: bounce or ignore blocks
        this.discard(); // stop at blocks — or remove this line to keep going
    }


    @Override
    protected ItemStack getDefaultPickupItem() {
        return ItemStack.EMPTY;
    }

    public float getRenderingRotation() {
        rotation += 0.5f;
        if(rotation >= 360) rotation = 0;
        return rotation;
    }

    public boolean isGrounded() {
        return inGround;
    }
}
