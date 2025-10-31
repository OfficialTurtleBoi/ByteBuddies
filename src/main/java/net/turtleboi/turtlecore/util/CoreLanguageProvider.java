package net.turtleboi.turtlecore.util;

import net.minecraft.core.Holder;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.common.data.LanguageProvider;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.function.Supplier;

public class CoreLanguageProvider  extends LanguageProvider {
    protected final String modId;
    public CoreLanguageProvider (PackOutput output, String modId) {
        super(output, modId, "en_us");
        this.modId = modId;
    }

    public void addSimpleItemName(Supplier<? extends Item> supplier) {
        Item item = supplier.get();
        ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(item);
        if (itemId != null) {
            add(item, toName(itemId.getPath()));
        } else {
            throw new IllegalStateException("Item not registered: " + item);
        }
    }

    public void addSimpleNameBlock(Supplier<? extends Block> supplier) {
        Block block = supplier.get();
        ResourceLocation blockId = ForgeRegistries.BLOCKS.getKey(block);
        if (blockId != null) {
            add(block, toName(blockId.getPath()));
        } else {
            throw new IllegalStateException("Block not registered: " + block);
        }
    }

    public void addSimpleNameEffect(Supplier<? extends MobEffect> supplier) {
        MobEffect effect = supplier.get();
        ResourceLocation effectId = ForgeRegistries.MOB_EFFECTS.getKey(effect);
        if (effectId != null) {
            add(effect, toName(effectId.getPath()));
        } else {
            throw new IllegalStateException("ModEffect not registered: " + effect);
        }
    }

    public void addSimpleNameEnchant(Supplier<? extends Enchantment> supplier) {
        Enchantment enchantment = supplier.get();
        ResourceLocation enchantmentId = ForgeRegistries.ENCHANTMENTS.getKey(enchantment);
        if (enchantmentId != null) {
            add(enchantment, toName(enchantmentId.getPath()));
        } else {
            throw new IllegalStateException("Enchantment not registered: " + enchantment);
        }
    }

    public static String toName(String registryPath) {
        StringBuilder stringBuilder = new StringBuilder(registryPath.length() + 8);
        for (String part : registryPath.split("_")) {
            if (part.isEmpty()) continue;
            stringBuilder.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) stringBuilder.append(part.substring(1));
            stringBuilder.append(' ');
        }
        return stringBuilder.toString().trim();
    }

    @Override
    protected void addTranslations() {

    }
}
