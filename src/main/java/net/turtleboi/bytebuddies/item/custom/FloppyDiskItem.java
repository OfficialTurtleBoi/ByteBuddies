package net.turtleboi.bytebuddies.item.custom;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

public class FloppyDiskItem extends Item {
    private final String tierKey;
    private final String colorKey;

    public FloppyDiskItem(Properties props, String tierKey, String colorKey) {
        super(props);
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
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
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
        private static final TextColor COPPER = TextColor.fromRgb(0xB87333);
        private static final TextColor IRON = TextColor.fromRgb(0xC0C0C0);
        private static final TextColor GOLD = TextColor.fromRgb(0xFFD700);

        static TextColor forTier(String tier) {
            return switch (tier) {
                case "copper" -> COPPER;
                case "iron" -> IRON;
                case "gold" -> GOLD;
                default -> TextColor.fromLegacyFormat(ChatFormatting.WHITE);
            };
        }
    }
}
