package net.turtleboi.bytebuddies.item.custom;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.turtleboi.bytebuddies.block.custom.BluestoneOreBlock;
import net.turtleboi.bytebuddies.entity.ModEntities;
import net.turtleboi.bytebuddies.entity.entities.ByteBuddyEntity;
import net.turtleboi.bytebuddies.entity.entities.ByteBuddyEntity.*;
import net.turtleboi.bytebuddies.entity.entities.HologramBuddyEntity;
import net.turtleboi.bytebuddies.util.AttributeModifierUtil;
import net.turtleboi.bytebuddies.util.InventoryUtil;

import java.util.List;
import java.util.Locale;

public class FloppyDiskItem extends Item {
    private final FloppyTier tier;
    private final String colorKey;

    public FloppyDiskItem(Properties properties, String tier, String colorKey) {
        super(properties.rarity(FloppyTier.fromName(tier).getRarity()));
        this.tier = FloppyTier.fromName(tier);
        this.colorKey = colorKey;
    }

    public String getTierKey()  {
        return tier.name().toLowerCase(Locale.ROOT);
    }

    public String colorKey() {
        return colorKey;
    }

    public FloppyTier getTier() {
        return tier;
    }

    @Override
    public void appendHoverText(ItemStack floppyStack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        TextColor tierColor = TierColors.forTier(getTierKey());
        Component tierName  = Component.translatable("tooltip.bytebuddies.floppy_tier." + getTierKey())
                .withStyle(Style.EMPTY.withColor(tierColor));

        tooltip.add(Component.translatable("tooltip.bytebuddies.floppy.tier_line", tierName)
                .withStyle(ChatFormatting.GRAY));

        tooltip.add(Component.translatable("tooltip.bytebuddies.floppy.desc." + colorKey)
                .withStyle(ChatFormatting.DARK_GRAY));

        tooltip.add(Component.translatable(""));

        FloppyTier tier = FloppyTier.fromName(getTierKey());
        addWhenInstalled(tooltip, tier, colorKey);
    }

    public enum FloppyTier {
        COPPER(Rarity.COMMON, 1),
        IRON(Rarity.UNCOMMON, 2),
        GOLD(Rarity.RARE, 3);

        private final Rarity rarity;
        private final int tierIndex;

        FloppyTier(Rarity rarity, int index) {
            this.rarity = rarity;
            this.tierIndex = index;
        }

        public Rarity getRarity() {
            return rarity;
        }

        public int index() {
            return tierIndex;
        }

        public static FloppyTier fromName(String name) {
            return switch (name.toLowerCase(Locale.ROOT)) {
                case "copper" -> COPPER;
                case "iron"   -> IRON;
                case "gold"   -> GOLD;
                default       -> COPPER;
            };
        }
    }

    static final class TierColors {
        static TextColor forTier(String tier) {
            return switch (tier) {
                case "copper" -> TextColor.fromRgb(0xB87333);
                case "iron" -> TextColor.fromRgb(0xC0C0C0);
                case "gold" -> TextColor.fromRgb(0xFFD700);
                default -> TextColor.fromLegacyFormat(ChatFormatting.WHITE);
            };
        }
    }

    private static void addWhenInstalled(List<Component> tooltip, FloppyTier tier, String colorKey) {
        tooltip.add(Component.literal("When installed:").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));

        switch (colorKey) {
            case "blue" -> {
                float multiplier = switch (tier) {
                    case COPPER -> 1.20f; case IRON -> 1.35f; case GOLD -> 1.50f;
                };
                tooltip.add(bonusLine("+", percentFromMultiplier(multiplier) + " task radius"));
            }
            case "green" -> {
                float multiplier = switch (tier) {
                    case COPPER -> 0.90f; case IRON -> 0.80f; case GOLD -> 0.70f;
                };
                tooltip.add(bonusLine("+", percentFromMultiplier(1.0f / multiplier) + " efficiency (lower energy cost)"));
                tooltip.add(malusLine(percentFromMultiplier(multiplier) + " energy cost"));
            }
            case "red" -> {
                float speedMultiplier = switch (tier) {
                    case COPPER -> 1.20f; case IRON -> 1.35f; case GOLD -> 1.50f;
                };
                float energyMultiplier = switch (tier) {
                    case COPPER -> 1.30f; case IRON -> 1.50f; case GOLD -> 1.70f;
                };
                tooltip.add(bonusLine("+", percentFromMultiplier(speedMultiplier) + " action speed"));
                tooltip.add(malusLine(percentFromMultiplier(energyMultiplier) + " efficiency (higher energy cost)"));
            }
            case "black" -> {
                float wearMultiplier = switch (tier) {
                    case COPPER -> 0.85f; case IRON -> 0.70f; case GOLD -> 0.55f;
                };
                float healthPercentage = switch (tier) {
                    case COPPER -> 1.0f; case IRON -> 1.50f; case GOLD -> 2.0f;
                };
                tooltip.add(bonusLine("+", percentFromMultiplier(1.0f / wearMultiplier) + " tool longevity (reduced wear)"));
                tooltip.add(bonusLine("+", percent(healthPercentage) + " Max Health"));
            }
            case "purple" -> {
                float add = switch (tier) {
                    case COPPER -> 0.33f; case IRON -> 0.425f; case GOLD -> 0.5f;
                };
                tooltip.add(bonusLine("+", percent(add) + " extra primary yield chance"));
                tooltip.add(Component.literal("Can exceed 100% for multiple extra rolls.").withStyle(ChatFormatting.DARK_GRAY));
            }
            case "yellow" -> {
                float add = switch (tier) {
                    case COPPER -> 0.5f; case IRON -> 0.625f; case GOLD -> 0.85f;
                };
                tooltip.add(bonusLine("+", percent(add) + " byproduct roll chance"));
                tooltip.add(Component.literal("Can exceed 100% for multiple byproduct rolls.").withStyle(ChatFormatting.DARK_GRAY));
            }
            case "pink" -> {
                float power = switch (tier) {
                    case COPPER -> 0.20f; case IRON -> 0.35f; case GOLD -> 0.50f;
                };
                tooltip.add(bonusLine("+", "Aura power " + percent(power)));
            }
            case "cyan" -> {
                float chance = switch (tier) {
                    case COPPER -> 0.125f; case IRON -> 0.175f; case GOLD -> 0.25f;
                };
                tooltip.add(bonusLine("+", "Spawn chance " + percent(chance) + " on success"));
            }
            default -> tooltip.add(Component.literal("No effects").withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    private static Component bonusLine(String sign, String text) {
        return Component.literal(sign + " " + text).withStyle(ChatFormatting.BLUE);
    }

    private static Component malusLine(String text) {
        return Component.literal("−" + " " + text).withStyle(ChatFormatting.RED);
    }

    private static String percentFromMultiplier(float multiplier) {
        float percentage = (multiplier - 1.0f) * 100.0f;
        if (percentage < 0.0f) {
            percentage = -percentage;
        }
        return trimPercent(percentage);
    }

    private static String percent(float value) {
        float percentage = value * 100.0f;
        return trimPercent(percentage);
    }

    private static String trimPercent(float pct) {
        float abs = Math.abs(pct);
        if (Math.abs(pct - Math.round(pct)) < 0.05f) {
            return Math.round(pct) + "%";
        } else if (abs < 10f) {
            return String.format(java.util.Locale.ROOT, "%.2f%%", pct);
        } else {
            return String.format(java.util.Locale.ROOT, "%.1f%%", pct);
        }
    }

    public static final class DiskEffects {
        private float radiusMultiplier = 1.0f;
        private float energyMultiplier = 1.0f;
        private float speedMultiplier = 1.0f;
        private float wearMultiplier = 1.0f;
        private float yieldBoostChance = 0.0f;
        private float byprodChance = 0.0f;
        private float healthBoostPct = 0.0f;
        private boolean hologramEnabled = false;
        private float hologramChance = 0.0f;
        private boolean supportAuraEnabled = false;
        private float supportAuraPower = 0.0f;

        public void recomputeFrom(ItemStackHandler upgrades) {
            radiusMultiplier = 1.0f;
            energyMultiplier = 1.0f;
            speedMultiplier = 1.0f;
            wearMultiplier = 1.0f;
            yieldBoostChance = 0.0f;
            byprodChance = 0.0f;
            healthBoostPct = 0.0f;
            hologramEnabled = false;
            hologramChance = 0.0f;
            supportAuraEnabled = false;
            supportAuraPower = 0.0f;

            for (int i = 0; i < upgrades.getSlots(); i++) {
                ItemStack inSlot = upgrades.getStackInSlot(i);
                if (inSlot.isEmpty() || !(inSlot.getItem() instanceof FloppyDiskItem disk)) continue;

                FloppyTier tier = disk.getTier();

                switch (disk.colorKey) {
                    case "blue" -> {
                        radiusMultiplier *= switch (tier) {
                            case COPPER -> 1.20f; case IRON -> 1.35f; case GOLD -> 1.50f;
                        };
                    }
                    case "green" -> {
                        energyMultiplier *= switch (tier) {
                            case COPPER -> 0.90f; case IRON -> 0.80f; case GOLD -> 0.70f;
                        };
                    }
                    case "red" -> {
                        speedMultiplier  *= switch (tier) {
                            case COPPER -> 1.20f; case IRON -> 1.35f; case GOLD -> 1.50f;
                        };
                        energyMultiplier *= switch (tier) {
                            case COPPER -> 1.30f; case IRON -> 1.50f; case GOLD -> 1.70f;
                        };
                    }
                    case "black" -> {
                        wearMultiplier   *= switch (tier) {
                            case COPPER -> 0.85f; case IRON -> 0.70f; case GOLD -> 0.55f;
                        };
                        healthBoostPct   += switch (tier) {
                            case COPPER -> 1.0f; case IRON -> 1.50f; case GOLD -> 2.0f;
                        };
                    }
                    case "purple" -> {
                        yieldBoostChance += switch (tier) {
                            case COPPER -> 0.33f; case IRON -> 0.425f; case GOLD -> 0.5f;
                        };
                    }
                    case "yellow" -> {
                        byprodChance += switch (tier) {
                            case COPPER -> 0.5f; case IRON -> 0.625f; case GOLD -> 0.85f;
                        };
                    }
                    case "pink" -> {
                        supportAuraEnabled = true;
                        supportAuraPower += switch (tier) {
                            case COPPER -> 0.20f; case IRON -> 0.35f; case GOLD -> 0.50f;
                        };
                    }
                    case "cyan" -> {
                        hologramEnabled = true;
                        hologramChance += switch (tier) {
                            case COPPER -> 0.125f; case IRON -> 0.175f; case GOLD -> 0.25f;
                        };
                    }
                }
            }

            energyMultiplier = Mth.clamp(energyMultiplier, 0.4f, 5.0f);
            speedMultiplier = Mth.clamp(speedMultiplier, 0.8f, 5.0f);
            wearMultiplier = Mth.clamp(wearMultiplier, 0.25f, 1.5f);

            yieldBoostChance = Mth.clamp(yieldBoostChance, 0.0f, 2.0f);
            byprodChance = Mth.clamp(byprodChance, 0.0f, 3.40f);
            hologramChance = Mth.clamp(hologramChance, 0.0f, 1.00f);
            supportAuraPower = Mth.clamp(supportAuraPower, 0.0f, 1.00f);
            healthBoostPct = Mth.clamp(healthBoostPct, 0.0f, 8.00f);
        }

        public float radiusMultiplier() {
            return radiusMultiplier;
        }
        public float energyCostMultiplier() {
            return energyMultiplier;
        }
        public float actionSpeedMultiplier() {
            return speedMultiplier;
        }
        public float toolWearMultiplier() {
            return wearMultiplier;
        }
        public float yieldPrimaryChance() {
            return yieldBoostChance;
        }
        public float secondaryByproductChance() {
            return byprodChance;
        }
        public float healthBoostPercent() {
            return healthBoostPct;
        }
        public boolean hologramEnabled() {
            return hologramEnabled;
        }
        public float hologramChance() {
            return hologramChance;
        }
        public boolean supportAuraEnabled() {
            return supportAuraEnabled;
        }
        public float supportAuraPower() {
            return supportAuraPower;
        }
    }

    public static final class DiskHooks {
        public static void applyPrimaryYieldBonus(List<ItemStack> drops, Block block, ByteBuddyEntity byteBuddy, float bonusChance) {
            if (bonusChance > 0.0f) {
                if (block instanceof CropBlock) {
                    int rolls = totalRollsFromChance(byteBuddy.level(), bonusChance);
                    if (rolls > 0) {
                        for (ItemStack itemStack : drops) {
                            if (isPrimaryProduce(itemStack)) {
                                for (int i = 0; i < rolls; i++) {
                                    if (itemStack.getCount() < itemStack.getMaxStackSize()) {
                                        itemStack.grow(1);
                                    } else {
                                        Containers.dropItemStack(byteBuddy.level(), byteBuddy.getX(), byteBuddy.getY(), byteBuddy.getZ(),
                                                new ItemStack(itemStack.getItem(), 1));
                                    }
                                }
                                break;
                            }
                        }
                    }
                }
            }
        }

        public static void tryGiveByproduct(ByteBuddyEntity byteBuddy, TaskType taskType, BlockPos blockPos) {
            float byproductChance = byteBuddy.byproductChance();
            if (byproductChance > 0.0f) {
                int rolls = totalRollsFromChance(byteBuddy.level(), byproductChance);
                if (rolls > 0) {
                    for (int i = 0; i < rolls; i++) {
                        ItemStack byProductStack = switch (taskType) {
                            case HARVEST -> new ItemStack(Items.WHEAT_SEEDS, 1);
                            case FORESTRY -> byteBuddy.level().random.nextFloat() < 0.3f ? new ItemStack(Items.OAK_SAPLING) : new ItemStack(Items.STICK);
                            case MINE -> new ItemStack(Items.FLINT, 1);
                            case SHEAR -> new ItemStack(Items.STRING, 1);
                            case MILK -> new ItemStack(Items.LEATHER, 1);
                            case COMBAT -> new ItemStack(Items.BONE, 1);
                            default -> ItemStack.EMPTY;
                        };

                        if (!byProductStack.isEmpty()) {
                            ItemStack leftover = InventoryUtil.mergeInto(byteBuddy.getMainInv(), byProductStack);
                            if (!leftover.isEmpty()) {
                                Containers.dropItemStack(byteBuddy.level(), blockPos.getX() + 0.5, blockPos.getY() + 0.5, blockPos.getZ() + 0.5, leftover);
                            }
                        }
                    }
                }
            }
        }

        private static int totalRollsFromChance(Level level, float chance) {
            int guaranteed = (int) Math.floor(chance);
            float fractional = chance - guaranteed;
            int extra = 0;
            if (fractional > 0.0f) {
                if (level.random.nextFloat() < fractional) {
                    extra = 1;
                }
            }
            return guaranteed + extra;
        }

        public static void applyDiskHealthBoost(ByteBuddyEntity byteBuddy, float healthBoostPct) {
            AttributeInstance attribute = byteBuddy.getAttribute(Attributes.MAX_HEALTH);

            if (attribute != null) {
                double oldMax = attribute.getValue();
                AttributeModifierUtil.removeModifier(
                        byteBuddy,
                        Attributes.MAX_HEALTH,
                        "disk_health"
                );

                if (healthBoostPct > 0.0f) {
                    AttributeModifierUtil.applyPermanentModifier(
                            byteBuddy,
                            Attributes.MAX_HEALTH,
                            "disk_health",
                            healthBoostPct,
                            AttributeModifier.Operation.ADD_MULTIPLIED_BASE
                    );
                }

                float newHealth = getNewHealth(byteBuddy, attribute, oldMax);
                byteBuddy.setHealth(newHealth);
            }
        }

        private static float getNewHealth(ByteBuddyEntity byteBuddy, AttributeInstance attribute, double oldMax) {
            double newMax = attribute.getValue();
            float current = byteBuddy.getHealth();
            float newHealth;
            if (oldMax > 0.0) {
                float ratio = current / (float) oldMax;
                newHealth = ratio * (float) newMax;
            } else {
                newHealth = (float) newMax;
            }

            if (newHealth < 1.0f) {
                newHealth = 1.0f;
            }
            if (newHealth > (float) newMax) {
                newHealth = (float) newMax;
            }
            return newHealth;
        }


        public static void trySpawnHologram(ByteBuddyEntity byteBuddy, ByteBuddyEntity.TaskType taskType) {
            boolean hologramEnabled = byteBuddy.hologramEnabled();
            float configuredChance = byteBuddy.hologramChance();
            float finalChance = configuredChance > 0.0f ? configuredChance : 0.25f;
            float percentRolled = byteBuddy.level().random.nextFloat();

            BlockPos buddyPos = byteBuddy.getOnPos();

            //ByteBuddies.LOGGER.info(
            //        "[Hologram] onTaskSuccess gate: hologramEnabled=" + hologramEnabled +
            //                " task=" + taskType +
            //                " pos=" + buddyPos +
            //                " chance=" + finalChance +
            //                " percentRolled=" + percentRolled +
            //                " side=" + (byteBuddy.level().isClientSide ? "CLIENT" : "SERVER"));

            boolean passed = false;
            if (hologramEnabled) {
                if (percentRolled < finalChance) {
                    passed = true;
                }
            }

            if (passed) {
                if (byteBuddy.level() instanceof ServerLevel serverLevel) {
                    BlockPos spawnBlockPos = buddyPos;
                    boolean validPos = false;
                    int maxRadius = 3;
                    for (int radius = 1; radius <= maxRadius; radius++) {
                        for (int dz = -radius; dz <= radius; dz++) {
                            for (int dx = -radius; dx <= radius; dx++) {
                                boolean onPerimeter = (Math.abs(dx) == radius) || (Math.abs(dz) == radius);
                                if (onPerimeter) {
                                    BlockPos candidate = buddyPos.offset(dx, 0, dz);
                                    boolean standableTerrain = ByteBuddyEntity.isStandableTerrain(serverLevel, candidate);
                                    if (standableTerrain) {
                                        spawnBlockPos = candidate.immutable();
                                        validPos = true;
                                        dx = radius;
                                        dz = radius;
                                    }
                                }
                            }
                        }
                        if (validPos) {
                            radius = maxRadius;
                        }
                    }

                    if (!validPos) {
                        for (int dy = -1; dy <= 1; dy++) {
                            if (dy != 0) {
                                BlockPos candidate = buddyPos.above(dy);
                                boolean standableTerrain = ByteBuddyEntity.isStandableTerrain(serverLevel, candidate);
                                if (standableTerrain) {
                                    spawnBlockPos = candidate.immutable();
                                    validPos = true;
                                }
                            }
                        }
                    }

                    for (int i = 0; i < 12; i++) {
                        double particleInterval = (Math.PI * 2 * i) / 12.0;
                        serverLevel.sendParticles(
                                BluestoneOreBlock.BLUESTONE,
                                spawnBlockPos.getX() + 0.5 + Math.cos(particleInterval) * 0.5,
                                spawnBlockPos.getY() + 0.1,
                                spawnBlockPos.getZ() + 0.5 + Math.sin(particleInterval) * 0.5,
                                1, 0, 0, 0, 0
                        );
                    }
                    serverLevel.playSound(
                            null,
                            spawnBlockPos,
                            net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_CHIME,
                            net.minecraft.sounds.SoundSource.PLAYERS,
                            0.6F, 1.5F
                    );

                    EntityType<? extends ByteBuddyEntity> hologramType = ModEntities.HOLOBUDDY.get();
                    int lifetimeTicks = 200;
                    HologramBuddyEntity hologram = HologramBuddyEntity.spawnFrom(byteBuddy, hologramType, spawnBlockPos, lifetimeTicks);

                    //ByteBuddies.LOGGER.info("[Hologram] spawnFrom result: hologram=" + (hologram != null ? ("ok id=" + hologram.getId()) : "null"));

                    if (hologram != null) {
                        ServerPlayer owner = byteBuddy.getOwner(serverLevel);
                        if (owner != null) {
                            //owner.displayClientMessage(
                            //        Component.literal(
                            //                "Hologram spawned for " + taskType + " @ " +
                            //                        spawnBlockPos.getX() + "," + spawnBlockPos.getY() + "," + spawnBlockPos.getZ()
                            //        ),
                            //        true
                            //);
                        }
                    }
                } else {
                    //ByteBuddies.LOGGER.info("[Hologram] passed chance but not on server side; skipping spawn.");
                }
            } else {
                //ByteBuddies.LOGGER.info("[Hologram] did not pass chance or disabled; no spawn.");
            }
        }



        private static boolean isPrimaryProduce(ItemStack st) {
            return st.is(Items.WHEAT) || st.is(Items.CARROT) || st.is(Items.POTATO) || st.is(Items.BEETROOT);
        }
    }

}
