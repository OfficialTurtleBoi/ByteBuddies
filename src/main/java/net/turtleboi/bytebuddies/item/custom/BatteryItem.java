package net.turtleboi.bytebuddies.item.custom;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.InteractionResultHolder;
import net.turtleboi.bytebuddies.ByteBuddies;
import net.turtleboi.bytebuddies.entity.entities.ByteBuddyEntity;

import java.util.List;

public class BatteryItem extends Item {
    private static final String KEY_ENERGY = ByteBuddies.MOD_ID + ":energy";

    private final int capacity;
    private final int ioRate;
    private final String tierName;

    public BatteryItem(Properties properties, String tierName, int capacity, int ioRate) {
        super(properties.stacksTo(1));
        this.tierName = tierName;
        this.capacity = capacity;
        this.ioRate = ioRate;
    }

    public int getEnergy(ItemStack stack) {
        CustomData dataComponents = stack.get(DataComponents.CUSTOM_DATA);
        if (dataComponents == null) return 0;
        var nbtData = dataComponents.copyTag();
        return nbtData.contains(KEY_ENERGY) ? Math.max(0, nbtData.getInt(KEY_ENERGY)) : 0;
    }

    public void setEnergy(ItemStack batteryStack, int energyValue) {
        energyValue = Math.max(0, Math.min(capacity, energyValue));
        var dataComponents = batteryStack.get(DataComponents.CUSTOM_DATA);
        var nbtData = dataComponents != null ? dataComponents.copyTag() : new CompoundTag();
        nbtData.putInt(KEY_ENERGY, energyValue);
        batteryStack.set(DataComponents.CUSTOM_DATA, CustomData.of(nbtData));
    }

    public int extract(ItemStack batteryStack, int energyValue, boolean transferable) {
        int batteryEnergy = getEnergy(batteryStack);
        int extractableEnergy = Math.min(ioRate, Math.min(energyValue, batteryEnergy));
        if (!transferable && extractableEnergy > 0)
            setEnergy(batteryStack, batteryEnergy - extractableEnergy);
        return extractableEnergy;
    }

    public int receive(ItemStack batteryStack, int energyValue, boolean transferable) {
        int batteryEnergy = getEnergy(batteryStack);
        int receivableEnergy = Math.min(ioRate, Math.min(energyValue, capacity - batteryEnergy));
        if (!transferable && receivableEnergy > 0)
            setEnergy(batteryStack, batteryEnergy + receivableEnergy);
        return receivableEnergy;
    }

    public int getCapacity() {
        return capacity;
    }

    public int getIoRate() {
        return ioRate;
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack batteryStack, Player player, LivingEntity target, InteractionHand interactionHand) {
        if (target instanceof ByteBuddyEntity bot) {
            if (player.level().isClientSide) {
                return InteractionResult.SUCCESS;
            } else {
                int batteryEnergy = getEnergy(batteryStack);
                if (batteryEnergy <= 0) {
                    player.displayClientMessage(Component.literal("Battery is empty").withStyle(ChatFormatting.RED), true);
                    ByteBuddies.LOGGER.info("[ByteBuddies] battery: empty (tier={})", tierName);
                    return InteractionResult.CONSUME;
                }

                int missingEnergy = bot.getEnergy().getMaxEnergyStored() - bot.getEnergy().getEnergyStored();
                if (missingEnergy <= 0) {
                    player.displayClientMessage(Component.literal("Bot is already full").withStyle(ChatFormatting.YELLOW), true);
                    return InteractionResult.CONSUME;
                }

                int transferableEnergy = Math.min(ioRate, Math.min(batteryEnergy, missingEnergy));
                int extractableEnergy = extract(batteryStack, transferableEnergy, false);
                int receivableEnergy = bot.getEnergy().receiveEnergy(extractableEnergy, false);

                if (receivableEnergy < extractableEnergy)
                    setEnergy(batteryStack, getEnergy(batteryStack) + (extractableEnergy - receivableEnergy));

                player.displayClientMessage(Component.literal("Transferred " + receivableEnergy + " FE to bot"), true);
                ByteBuddies.LOGGER.info("[ByteBuddies] battery→bot: tier={} gave={}FE remain={}FE bot={}/{}",
                        tierName, receivableEnergy, getEnergy(batteryStack),
                        bot.getEnergy().getEnergyStored(), bot.getEnergy().getMaxEnergyStored());

                return InteractionResult.CONSUME;
            }
        } else {
            return InteractionResult.PASS;
        }
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack itemInHand = player.getItemInHand(hand);
        if (!level.isClientSide) {
            player.displayClientMessage(Component.literal(
                    batteryTier() + " " + getEnergy(itemInHand) + " / " + capacity + " FE"), true);
        }
        return InteractionResultHolder.success(itemInHand);
    }

    @Override
    public void appendHoverText(ItemStack batteryStack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(batteryStack, context, tooltip, flag);
        tooltip.add(Component.literal(batteryTier() + " Capacity: " + capacity + " FE").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("Charge: " + getEnergy(batteryStack) + " FE").withStyle(ChatFormatting.DARK_AQUA));
        tooltip.add(Component.literal("I/O per use: " + ioRate + " FE").withStyle(ChatFormatting.DARK_AQUA));
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return true;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        float fullEnergyBar = (float)getEnergy(stack) / (float)capacity;
        return Math.round(13.0F * fullEnergyBar);
    }

    @Override
    public int getBarColor(ItemStack batteryStack) {
        return 0x2EE6D0;
    }

    private String batteryTier() {
        return switch (tierName) {
        case "simple" -> "Simple Battery";
        case "advanced" -> "Advanced Battery";
        case "biocell" -> "Biocell Battery";
        case "reinforced" -> "Reinforced Battery";
        case "supercharged" -> "Super-Charged Battery";
        default -> "Battery";
        };
    }
}
