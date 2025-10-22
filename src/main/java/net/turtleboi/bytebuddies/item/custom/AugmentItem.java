package net.turtleboi.bytebuddies.item.custom;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.turtleboi.bytebuddies.entity.entities.ByteBuddyEntity;
import net.turtleboi.bytebuddies.item.ModItems;
import net.turtleboi.bytebuddies.util.AttributeModifierUtil;
import net.turtleboi.bytebuddies.init.ModTags;

public class AugmentItem extends Item {
    public AugmentItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    public static final class AugmentEffects {
        private boolean propellerEnabled = false;
        private boolean aquaEnabled = false;
        private boolean solarEnabled = false;
        private boolean geothermalEnabled = false;
        private boolean magnetEnabled = false;
        private boolean gyroEnabled = false;
        private boolean arcWelderEnabled = false;
        private boolean dynamoEnabled = false;

        private boolean fireResistant = false;
        private boolean shockOnHit = false;
        private boolean enderLinkEnabled = false;

        private double solarChargePerSecond = 0.0;
        private double geothermalChargePerSecond = 0.0;
        private double dynamoChargePerMeter = 0.0;

        private double magnetRadiusBlocks = 0.0;

        private int extraStorageSlots = 0;

        private double armorBonus = 0.0;
        private double moveSpeedMultiplier = 1.0;
        private double knockbackResistBonus = 0.0;

        private double selfRepairPerSecond = 0.0;
        private int momentumTicks = 0;
        private double momentumSpeedMultiplier = 1.0;

        private ByteBuddyEntity.ChassisMaterial chassisMaterial = ByteBuddyEntity.ChassisMaterial.ALUMINUM;

        public void recomputeFrom(ItemStackHandler buddyInventory, ByteBuddyEntity byteBuddy) {
            propellerEnabled = false;
            aquaEnabled = false;
            solarEnabled = false;
            geothermalEnabled = false;
            magnetEnabled = false;
            gyroEnabled = false;
            arcWelderEnabled = false;
            dynamoEnabled = false;

            fireResistant = false;
            shockOnHit = false;
            enderLinkEnabled = false;

            solarChargePerSecond = 0.0;
            geothermalChargePerSecond = 0.0;
            dynamoChargePerMeter = 0.0;

            magnetRadiusBlocks = 0.0;
            extraStorageSlots = 0;

            armorBonus = 0.0;
            moveSpeedMultiplier = 1.0;
            knockbackResistBonus = 0.0;

            selfRepairPerSecond = 0.0;
            momentumTicks = 0;
            momentumSpeedMultiplier = 1.0;

            chassisMaterial = ByteBuddyEntity.ChassisMaterial.ALUMINUM;

            for (int slot = 1; slot < 3; slot++) {
                ItemStack stack = buddyInventory.getStackInSlot(slot);
                if (stack.isEmpty()) {
                    continue;
                }

                if (stack.is(ModTags.Items.PLATING)) {
                    if (stack.is(ModItems.REINFORCED_CHARGED_STEEL_PLATING.get())) {
                        chassisMaterial = ByteBuddyEntity.ChassisMaterial.CHARGED_STEEL;
                        armorBonus = 16.0;
                        moveSpeedMultiplier = 1.05;
                        shockOnHit = true;
                    } else if (stack.is(ModItems.REINFORCED_NETHERITE_PLATING.get())) {
                        chassisMaterial = ByteBuddyEntity.ChassisMaterial.NETHERITE;
                        armorBonus = 20.0;
                        moveSpeedMultiplier = 0.75;
                        fireResistant = true;
                        knockbackResistBonus += 0.25;
                    } else if (stack.is(ModItems.REINFORCED_STEEL_PLATING.get())) {
                        chassisMaterial = ByteBuddyEntity.ChassisMaterial.STEEL;
                        armorBonus = 12.0;
                        moveSpeedMultiplier = 0.85;
                    } else if (stack.is(ModItems.REINFORCED_IRON_PLATING.get())) {
                        chassisMaterial = ByteBuddyEntity.ChassisMaterial.IRON;
                        armorBonus = 6.0;
                        moveSpeedMultiplier = 0.95;
                    }
                } else if (stack.is(ModTags.Items.STORAGE_CELL)) {
                    if (stack.is(ModItems.ENDERLINK_STORAGE_CELL.get())) {
                        extraStorageSlots = 18;
                        enderLinkEnabled = true;
                    } else if (stack.is(ModItems.ADVANCED_STORAGE_CELL.get())) {
                        extraStorageSlots = 18;
                        moveSpeedMultiplier *= 0.88;
                    } else if (stack.is(ModItems.BASIC_STORAGE_CELL.get())) {
                        extraStorageSlots = 9;
                        moveSpeedMultiplier *= 0.95;
                    }
                } else {
                    if (stack.is(ModItems.PROPELLER_UNIT.get())) {
                        propellerEnabled = true;
                        moveSpeedMultiplier *= 0.92;
                    } else if (stack.is(ModItems.AQUATIC_MOTOR.get())) {
                        aquaEnabled = true;
                    } else if (stack.is(ModItems.SOLAR_ARRAY.get())) {
                        solarEnabled = true;
                        solarChargePerSecond += 5.0;
                    } else if (stack.is(ModItems.GEOTHERMAL_REGULATOR.get())) {
                        geothermalEnabled = true;
                        geothermalChargePerSecond += 5.0;
                    } else if (stack.is(ModItems.MAGNETIC_CRESCENT.get())) {
                        magnetEnabled = true;
                        magnetRadiusBlocks = Math.max(magnetRadiusBlocks, 5.0);
                    } else if (stack.is(ModItems.DYNAMO_COIL.get())) {
                        dynamoEnabled = true;
                        dynamoChargePerMeter += 0.6;
                        momentumTicks = Math.max(momentumTicks, 25);
                        momentumSpeedMultiplier = Math.max(momentumSpeedMultiplier, 1.15);
                    } else if (stack.is(ModItems.ARC_WELDER.get())) {
                        arcWelderEnabled = true;
                        selfRepairPerSecond += 0.5;
                    } else if (stack.is(ModItems.GYROSCOPIC_STABILIZER.get())) {
                        gyroEnabled = true;
                        knockbackResistBonus += 0.25;
                    }
                }
            }

            moveSpeedMultiplier = Mth.clamp(moveSpeedMultiplier, 0.6, 1.85);

            AttributeModifierUtil.applyPermanentModifier(
                    byteBuddy,
                    Attributes.ARMOR,
                    "augment_armor",
                    armorBonus,
                    AttributeModifier.Operation.ADD_VALUE);

            AttributeModifierUtil.applyPermanentModifier(
                    byteBuddy,
                    Attributes.MOVEMENT_SPEED,
                    "augment_speed_mul",
                    moveSpeedMultiplier - 1.0,
                    AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);

            AttributeModifierUtil.applyPermanentModifier(
                    byteBuddy,
                    Attributes.KNOCKBACK_RESISTANCE,
                    "augment_kb_resist",
                    knockbackResistBonus,
                    AttributeModifier.Operation.ADD_VALUE);
        }

        public boolean propellerEnabled() { return propellerEnabled; }
        public boolean aquaEnabled() { return aquaEnabled; }
        public boolean solarEnabled() { return solarEnabled; }
        public boolean geothermalEnabled() { return geothermalEnabled; }
        public boolean magnetEnabled() { return magnetEnabled; }
        public boolean gyroEnabled() { return gyroEnabled; }
        public boolean arcWelderEnabled() { return arcWelderEnabled; }
        public boolean dynamoEnabled() { return dynamoEnabled; }

        public boolean fireResistant() { return fireResistant; }
        public boolean shockOnHit() { return shockOnHit; }
        public boolean enderLinkEnabled() { return enderLinkEnabled; }

        public double solarChargePerSecond() { return solarChargePerSecond; }
        public double geothermalChargePerSecond() { return geothermalChargePerSecond; }
        public double dynamoChargePerMeter() { return dynamoChargePerMeter; }

        public double magnetRadiusBlocks() { return magnetRadiusBlocks; }
        public int extraStorageSlots() { return extraStorageSlots; }

        public double selfRepairPerSecond() { return selfRepairPerSecond; }
        public int momentumTicks() { return momentumTicks; }
        public double momentumSpeedMultiplier() { return momentumSpeedMultiplier; }

        public ByteBuddyEntity.ChassisMaterial getChassisMaterial() { return chassisMaterial; }
    }
}
