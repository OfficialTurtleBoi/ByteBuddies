package net.turtleboi.bytebuddies.init;

import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Block;
import net.turtleboi.bytebuddies.item.ModItems;
import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;

public enum ModTiers implements Tier {
    BUSTER(1170, 8.0f, 3.0f, 12, BlockTags.NEEDS_DIAMOND_TOOL, () -> Ingredient.of(ModItems.STEEL_INGOT)),
    TERRABLADE(1523, 8.5f, 3.5f, 18, BlockTags.NEEDS_DIAMOND_TOOL, () -> Ingredient.of(ModItems.CHARGED_STEEL_INGOT));

    private final int uses;
    private final float speed;
    private final float attackDamageBonus;
    private final int enchantmentValue;
    private final TagKey<Block> incorrectBlocksForDrops;
    private final Supplier<Ingredient> repairIngredient;

    ModTiers(int uses, float speed, float attackDamageBonus, int enchantmentValue,
             TagKey<Block> incorrectBlocksForDrops, Supplier<Ingredient> repairIngredient) {
        this.uses = uses;
        this.speed = speed;
        this.attackDamageBonus = attackDamageBonus;
        this.enchantmentValue = enchantmentValue;
        this.incorrectBlocksForDrops = incorrectBlocksForDrops;
        this.repairIngredient = repairIngredient;
    }

    @Override public int getUses() {
        return uses;
    }

    @Override public float getSpeed() {
        return speed;
    }

    @Override public float getAttackDamageBonus() {
        return attackDamageBonus;
    }

    @Override public int getEnchantmentValue() {
        return enchantmentValue;
    }

    @Override public @NotNull TagKey<Block> getIncorrectBlocksForDrops() {
        return incorrectBlocksForDrops;
    }

    @Override public @NotNull Ingredient getRepairIngredient() {
        return repairIngredient.get();
    }
}
