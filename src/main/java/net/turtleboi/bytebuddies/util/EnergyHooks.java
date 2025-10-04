package net.turtleboi.bytebuddies.util;

import net.minecraft.world.item.ItemStack;
import net.turtleboi.bytebuddies.ByteBuddies;
import net.turtleboi.bytebuddies.entity.entities.ByteBuddyEntity;
import net.turtleboi.bytebuddies.item.custom.BatteryItem;

public final class EnergyHooks {
    public static void drainBatteries(ByteBuddyEntity byteBuddy) {
        int missingEnergy = byteBuddy.getEnergy().getMaxEnergyStored() - byteBuddy.getEnergy().getEnergyStored();
        if (missingEnergy <= 0) {
            for (int i = 0; i < byteBuddy.getMainInv().getSlots(); i++) {
                ItemStack stackInSlot = byteBuddy.getMainInv().getStackInSlot(i);
                if (!stackInSlot.isEmpty() && stackInSlot.getItem() instanceof BatteryItem batteryItem) {
                    int neededEnergy = Math.min(missingEnergy, batteryItem.getIoRate());
                    int pulledEnergy = batteryItem.extract(stackInSlot, neededEnergy, false);
                    if (pulledEnergy > 0) {
                        int receivedEnergy = byteBuddy.getEnergy().receiveEnergy(pulledEnergy, false);
                        if (receivedEnergy < pulledEnergy)
                            batteryItem.setEnergy(stackInSlot, batteryItem.getEnergy(stackInSlot) + (pulledEnergy - receivedEnergy));

                        ByteBuddies.LOGGER.debug("[ByteBuddies] bot drained battery: +{}FE (slot {}), bot={}/{}",
                                receivedEnergy, i, byteBuddy.getEnergy().getEnergyStored(), byteBuddy.getEnergy().getMaxEnergyStored());

                        missingEnergy -= receivedEnergy;
                        if (missingEnergy <= 0) break;
                    }
                }
            }
        }
    }
}
