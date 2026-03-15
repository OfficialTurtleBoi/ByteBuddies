package net.turtleboi.bytebuddies.util;

import net.minecraft.world.entity.AnimationState;

public class FreezableAnimationState extends AnimationState {
    private boolean frozen = false;
    public void freeze() {
        frozen = true;
    }

    public void unfreeze() {
        frozen = false;
    }

    @Override
    public void updateTime(float pGameTime, float pSpeed) {
        if (!frozen) {
            super.updateTime(pGameTime, pSpeed);
        }
    }
}
