package net.turtleboi.bytebuddies.item.custom;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.util.Mth;
import net.minecraft.world.Containers;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.turtleboi.bytebuddies.entity.entities.ByteBuddyEntity;
import net.turtleboi.bytebuddies.entity.entities.ByteBuddyEntity.*;
import net.turtleboi.bytebuddies.util.InventoryUtil;

import java.util.List;

public class FloppyDiskItem extends Item {
    private final String tierKey;
    private final String colorKey;

    public FloppyDiskItem(Properties properties, String tierKey, String colorKey) {
        super(properties);
        this.tierKey = tierKey;
        this.colorKey = colorKey;
    }

    public String tierKey()  {
        return tierKey;
    }

    public String colorKey() {
        return colorKey;
    }

    @Override
    public void appendHoverText(ItemStack floppyStack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        TextColor tierColor = TierColors.forTier(tierKey);
        Component tierName  = Component.translatable("tooltip.bytebuddies.floppy_tier." + tierKey)
                .withStyle(Style.EMPTY.withColor(tierColor));

        tooltip.add(Component.translatable("tooltip.bytebuddies.floppy.tier_line", tierName)
                .withStyle(ChatFormatting.GRAY));

        tooltip.add(Component.translatable(""));

        tooltip.add(Component.translatable("tooltip.bytebuddies.floppy.desc." + colorKey)
                .withStyle(ChatFormatting.DARK_GRAY));
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

    public static final class DiskEffects {
        private float radiusMultipier = 1.0f;
        private float energyMultipier = 1.0f;
        private float speedMultipier = 1.0f;
        private float wearMultiplier = 1.0f;
        private float yieldBoostChance = 0.0f;
        private float byprodChance = 0.0f;
        private boolean supportAura = false;
        private boolean hologram = false;

        public void recomputeFrom(ItemStackHandler upgrades) {
            radiusMultipier = 1.0f; energyMultipier = 1.0f; speedMultipier = 1.0f; wearMultiplier = 1.0f;
            yieldBoostChance = 0.0f; byprodChance = 0.0f; supportAura = false; hologram = false;

            for (int i = 0; i < upgrades.getSlots(); i++) {
                ItemStack inSlot = upgrades.getStackInSlot(i);
                if (inSlot.isEmpty() || !(inSlot.getItem() instanceof FloppyDiskItem disk)) continue;

                String tier = disk.tierKey;
                switch (disk.colorKey) {
                    case "blue" -> radiusMultipier *= switch (tier) {
                        case "copper" -> 1.20f; case "iron" -> 1.35f; case "gold" -> 1.50f;
                        default -> throw new IllegalStateException("Unexpected value: " + tier);
                    };
                    case "green" -> energyMultipier *= switch (tier) {
                        case "copper" -> 0.90f; case "iron" -> 0.80f; case "gold" -> 0.70f;
                        default -> throw new IllegalStateException("Unexpected value: " + tier);
                    };
                    case "red" -> {
                        speedMultipier *= switch (tier) {
                            case "copper" -> 1.20f; case "iron" -> 1.35f; case "gold" -> 1.50f;
                            default -> throw new IllegalStateException("Unexpected value: " + tier);
                        };
                        energyMultipier *= switch (tier) {
                            case "copper" -> 1.30f; case "iron" -> 1.50f; case "gold" -> 1.70f;
                            default -> throw new IllegalStateException("Unexpected value: " + tier);
                        };
                    }
                    case "black" -> wearMultiplier *= switch (tier) {
                        case "copper" -> 0.85f; case "iron" -> 0.70f; case "gold" -> 0.55f;
                        default -> throw new IllegalStateException("Unexpected value: " + tier);
                    };
                    case "purple" -> yieldBoostChance += switch (tier) {
                        case "copper" -> 0.05f; case "iron" -> 0.10f; case "gold" -> 0.15f;
                        default -> throw new IllegalStateException("Unexpected value: " + tier);
                    };
                    case "yellow" -> byprodChance += switch (tier) {
                        case "copper" -> 0.10f; case "iron" -> 0.18f; case "gold" -> 0.26f;
                        default -> throw new IllegalStateException("Unexpected value: " + tier);
                    };
                    case "pink" -> supportAura = true;
                    case "cyan" -> hologram = true;
                }
            }
            
            energyMultipier = Mth.clamp(energyMultipier, 0.5f, 5.0f);
            speedMultipier = Mth.clamp(speedMultipier,  0.8f, 5.0f);
        }

        public float radiusMultiplier() {
            return radiusMultipier;
        }

        public float energyCostMultiplier() {
            return energyMultipier;
        }

        public float actionSpeedMultiplier() {
            return speedMultipier;
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

        public boolean supportAuraEnabled() {
            return supportAura;
        }

        public boolean hologramEnabled() {
            return hologram;
        }
    }

    public static final class DiskHooks {
        public static void applyPrimaryYieldBonus(List<ItemStack> drops, Block block, ByteBuddyEntity byteBuddy, float bonusChance) {
            if (bonusChance <= 0) return;
            if (block instanceof CropBlock) {
                for (ItemStack itemStack : drops) {
                    if (isPrimaryProduce(itemStack)) {
                        if (itemStack.getCount() > 0 && itemStack.getCount() < itemStack.getMaxStackSize() &&
                                byteBuddy.level().random.nextFloat() < bonusChance) {
                            itemStack.grow(1);
                            break;
                        }
                    }
                }
            }

            //more
        }

        public static void tryGiveByproduct(ByteBuddyEntity byteBuddy, TaskType taskType, BlockPos blockPos) {
            float byproductChance = byteBuddy.byproductChance();
            if (byproductChance <= 0 || byteBuddy.level().random.nextFloat() >= byproductChance) return;

            ItemStack byProductStack = switch (taskType) {
                case HARVEST -> new ItemStack(Items.STICK, 1);
                case FORESTRY -> byteBuddy.level().random.nextFloat() < 0.3f ? new ItemStack(Items.OAK_SAPLING) : new ItemStack(Items.STICK);
                case MINE -> new ItemStack(Items.FLINT, 1);
                case SHEAR -> new ItemStack(Items.STRING, 1);
                case MILK -> new ItemStack(Items.LEATHER, 1);
                case COMBAT -> new ItemStack(Items.BONE, 1);
                default -> ItemStack.EMPTY;
            };

            if (!byProductStack.isEmpty()) {
                ItemStack collectedItems = InventoryUtil.mergeInto(byteBuddy.getMainInv(), byProductStack);
                if (!collectedItems.isEmpty()) Containers.dropItemStack(
                        byteBuddy.level(),
                        blockPos.getX()+0.5,
                        blockPos.getY()+0.5,
                        blockPos.getZ()+0.5,
                        collectedItems
                );
            }
        }

        public static void trySpawnHologram(ByteBuddyEntity byteBuddy, TaskType taskType, BlockPos blockPos) {

        }

        private static boolean isPrimaryProduce(ItemStack produceStack) {
            return produceStack.is(Items.WHEAT) ||
                    produceStack.is(Items.CARROT) ||
                    produceStack.is(Items.POTATO) ||
                    produceStack.is(Items.BEETROOT);
        }
    }

}
