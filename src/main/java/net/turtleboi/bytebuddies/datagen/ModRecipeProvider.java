package net.turtleboi.bytebuddies.datagen;

import net.minecraft.advancements.critereon.ItemPredicate;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.conditions.IConditionBuilder;
import net.turtleboi.bytebuddies.ByteBuddies;
import net.turtleboi.bytebuddies.block.ModBlocks;
import net.turtleboi.bytebuddies.item.ModItems;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class ModRecipeProvider extends RecipeProvider implements IConditionBuilder {
    public ModRecipeProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        super(output, registries);
    }

    @Override
    protected void buildRecipes(RecipeOutput recipeOutput) {
        ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, ModBlocks.ALUMINUM_BLOCK.get())
                .pattern("AAA")
                .pattern("AAA")
                .pattern("AAA")
                .define('A', ModItems.ALUMINUM_INGOT.get())
                .unlockedBy("has_aluminum_ingot", has(ModItems.ALUMINUM_INGOT.get()))
                .save(recipeOutput);

        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ModItems.ALUMINUM_INGOT.get(), 9)
                .requires(ModBlocks.ALUMINUM_BLOCK.get())
                .unlockedBy("has_aluminum_block", has(ModBlocks.ALUMINUM_BLOCK.get()))
                .save(recipeOutput, "aluminum_from_block");

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.ALUMINUM_INGOT.get())
                .pattern("NNN")
                .pattern("NNN")
                .pattern("NNN")
                .define('N', ModItems.ALUMINUM_NUGGET.get())
                .unlockedBy("has_aluminum_nugget", has(ModItems.ALUMINUM_NUGGET.get()))
                .save(recipeOutput, "aluminum_from_nuggets");

        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ModItems.ALUMINUM_NUGGET.get(), 9)
                .requires(ModItems.ALUMINUM_INGOT.get())
                .unlockedBy("has_aluminum_ingot", has(ModItems.ALUMINUM_INGOT.get()))
                .save(recipeOutput);

        ShapedRecipeBuilder.shaped(RecipeCategory.REDSTONE, ModBlocks.BLUESTONE_BLOCK.get())
                .pattern("BBB")
                .pattern("BBB")
                .pattern("BBB")
                .define('B', ModItems.BLUESTONE_DUST.get())
                .unlockedBy("has_bluestone", has(ModItems.BLUESTONE_DUST.get()))
                .save(recipeOutput);

        ShapelessRecipeBuilder.shapeless(RecipeCategory.REDSTONE, ModItems.BLUESTONE_DUST.get(), 9)
                .requires(ModBlocks.BLUESTONE_BLOCK.get())
                .unlockedBy("has_bluestone_block", has(ModBlocks.BLUESTONE_BLOCK.get()))
                .save(recipeOutput);

        ShapedRecipeBuilder.shaped(RecipeCategory.REDSTONE, ModItems.SIMPLE_BATTERY.get())
                .pattern("PXP")
                .pattern("IBI")
                .pattern("PCP")
                .define('I', Items.IRON_INGOT)
                .define('X', Items.COPPER_INGOT)
                .define('B', ModBlocks.BLUESTONE_BLOCK.get())
                .define('C', ModItems.CARBON_PASTE)
                .define('P', ModItems.COPPER_PLATING.get())
                .unlockedBy("has_bluestone_block", has(ModBlocks.BLUESTONE_BLOCK.get()))
                .unlockedBy("has_copper_plating", has(ModItems.COPPER_PLATING.get()))
                .unlockedBy("has_redstone", has(Items.REDSTONE))
                .unlockedBy("has_carbon_paste", has(ModItems.CARBON_PASTE))
                .save(recipeOutput);

        ShapedRecipeBuilder.shaped(RecipeCategory.REDSTONE, ModItems.ADVANCED_BATTERY.get())
                .pattern("PXP")
                .pattern("GBG")
                .pattern("PCP")
                .define('G', Items.GOLD_INGOT)
                .define('X', Items.COPPER_INGOT)
                .define('B', ModBlocks.BLUESTONE_BLOCK.get())
                .define('C', ModItems.CARBON_PASTE)
                .define('P', Ingredient.of(
                        ModItems.ALUMINUM_PLATING.get(),
                        ModItems.IRON_PLATING.get()
                ))
                .unlockedBy("has_bluestone_block", has(ModBlocks.BLUESTONE_BLOCK.get()))
                .unlockedBy("has_gold", has(Items.GOLD_INGOT))
                .unlockedBy("has_carbon_paste", has(ModItems.CARBON_PASTE))
                .unlockedBy("has_plate", inventoryTrigger(ItemPredicate.Builder.item()
                                .of(ModItems.ALUMINUM_PLATING.get(), ModItems.IRON_PLATING.get()).build()))
                .save(recipeOutput);

        ShapedRecipeBuilder.shaped(RecipeCategory.REDSTONE, ModItems.BIOCELL_BATTERY.get())
                .pattern("PVP")
                .pattern("HBH")
                .pattern("PMP")
                .define('V', Items.GLASS_BOTTLE)
                .define('H', Items.HONEYCOMB)
                .define('B', ModBlocks.BLUESTONE_BLOCK.get())
                .define('P', ModItems.GOLD_PLATING.get())
                .define('M', Blocks.MOSS_BLOCK)
                .unlockedBy("has_bluestone_block", has(ModBlocks.BLUESTONE_BLOCK.get()))
                .unlockedBy("has_gold_plating", has(ModItems.GOLD_PLATING.get()))
                .unlockedBy("has_honeycomb", has(Items.HONEYCOMB))
                .unlockedBy("has_moss", has(Blocks.MOSS_BLOCK))
                .save(recipeOutput);

        ShapedRecipeBuilder.shaped(RecipeCategory.REDSTONE, ModItems.REINFORCED_BATTERY.get())
                .pattern("PQP")
                .pattern("OBO")
                .pattern("PRP")
                .define('Q', Items.QUARTZ)
                .define('R', Blocks.REDSTONE_BLOCK)
                .define('B', ModBlocks.BLUESTONE_BLOCK.get())
                .define('P', ModItems.STEEL_PLATING.get())
                .define('O', Blocks.OBSIDIAN)
                .unlockedBy("has_bluestone_block", has(ModBlocks.BLUESTONE_BLOCK.get()))
                .unlockedBy("has_steel_plating", has(ModItems.STEEL_PLATING.get()))
                .unlockedBy("has_quartz", has(Items.QUARTZ))
                .unlockedBy("has_redstone_block", has(Blocks.REDSTONE_BLOCK))
                .unlockedBy("has_obsidian", has(Blocks.OBSIDIAN))
                .save(recipeOutput);

        ShapedRecipeBuilder.shaped(RecipeCategory.REDSTONE, ModItems.SUPER_CHARGED_BATTERY.get())
                .pattern("PNP")
                .pattern("ZBZ")
                .pattern("PEP")
                .define('N', Items.NETHER_STAR)
                .define('E', Items.ENDER_PEARL)
                .define('B', ModBlocks.BLUESTONE_BLOCK.get())
                .define('P', ModItems.CHARGED_STEEL_PLATING.get())
                .define('Z', Items.BLAZE_ROD)
                .unlockedBy("has_bluestone_block", has(ModBlocks.BLUESTONE_BLOCK.get()))
                .unlockedBy("has_charged_steel_plating", has(ModItems.CHARGED_STEEL_PLATING.get()))
                .unlockedBy("has_nether_star", has(Items.NETHER_STAR))
                .unlockedBy("has_ender_pearl", has(Items.ENDER_PEARL))
                .unlockedBy("has_blaze_rod", has(Items.BLAZE_ROD))
                .save(recipeOutput);

        ShapedRecipeBuilder.shaped(RecipeCategory.TOOLS, ModItems.WRENCH.get())
                .pattern(" I ")
                .pattern(" CI")
                .pattern("C  ")
                .define('I', Items.IRON_INGOT)
                .define('C', Items.COPPER_INGOT)
                .unlockedBy("has_iron", has(Items.IRON_INGOT))
                .unlockedBy("has_copper", has(Items.COPPER_INGOT))
                .save(recipeOutput);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.CHIP.get())
                .pattern("CGC")
                .pattern("QBQ")
                .pattern("RGR")
                .define('C', ModItems.CARBON_PASTE)
                .define('G', Items.GOLD_NUGGET)
                .define('Q', Items.QUARTZ)
                .define('B', ModItems.BLUESTONE_DUST.get())
                .define('R', Items.REDSTONE)
                .unlockedBy("has_bluestone", has(ModItems.BLUESTONE_DUST.get()))
                .unlockedBy("has_copper", has(Items.COPPER_INGOT))
                .unlockedBy("has_quartz", has(Items.QUARTZ))
                .unlockedBy("has_redstone", has(Items.REDSTONE))
                .save(recipeOutput);

        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ModItems.CARBON_PASTE.get(), 2)
                .requires(Items.COAL)
                .requires(Items.CLAY_BALL)
                .unlockedBy("has_coal", has(Items.COAL))
                .unlockedBy("has_clay", has(Items.CLAY_BALL))
                .save(recipeOutput);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.COPPER_PLATING.get(), 3)
                .pattern("CC")
                .define('C', Items.COPPER_INGOT)
                .unlockedBy("has_copper", has(Items.COPPER_INGOT))
                .save(recipeOutput);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.ALUMINUM_PLATING.get(), 3)
                .pattern("AA")
                .define('A', ModItems.ALUMINUM_INGOT.get())
                .unlockedBy("has_aluminum_ingot", has(ModItems.ALUMINUM_INGOT.get()))
                .save(recipeOutput);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.IRON_PLATING.get(), 3)
                .pattern("II")
                .define('I', Items.IRON_INGOT)
                .unlockedBy("has_iron", has(Items.IRON_INGOT))
                .save(recipeOutput);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.GOLD_PLATING.get(), 3)
                .pattern("GG")
                .define('G', Items.GOLD_INGOT)
                .unlockedBy("has_gold", has(Items.GOLD_INGOT))
                .save(recipeOutput);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.STEEL_PLATING.get(), 3)
                .pattern("SS")
                .define('S', ModItems.STEEL_INGOT)
                .unlockedBy("has_steel_ingot", has(ModItems.STEEL_INGOT))
                .save(recipeOutput);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.CHARGED_STEEL_PLATING.get(), 3)
                .pattern("CC")
                .define('C', ModItems.CHARGED_STEEL_INGOT)
                .unlockedBy("has_charged_steel_ingot", has(ModItems.CHARGED_STEEL_INGOT))
                .save(recipeOutput);

        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ModItems.CARBON_ALLOY.get(), 2)
                .requires(Items.IRON_INGOT)
                .requires(Items.IRON_INGOT)
                .requires(ModItems.CARBON_PASTE.get())
                .unlockedBy("has_carbon_paste", has(ModItems.CARBON_PASTE.get()))
                .unlockedBy("has_iron_ingot", has(Items.IRON_INGOT))
                .save(recipeOutput);

        SimpleCookingRecipeBuilder.blasting(
                        Ingredient.of(ModItems.CARBON_ALLOY.get()),
                        RecipeCategory.MISC,
                        ModItems.STEEL_INGOT.get(),
                        0.7f,
                        200
                )
                .unlockedBy("has_carbon_alloy", has(ModItems.CARBON_ALLOY.get()))
                .save(recipeOutput);

        var floppyTypes = Map.of(
                "black", floppyBase(Items.BLACK_DYE, Blocks.OBSIDIAN.asItem(), Items.IRON_NUGGET),
                "blue", floppyBase(Items.BLUE_DYE, Items.LAPIS_LAZULI, Items.COMPASS),
                "cyan", floppyBase(Items.CYAN_DYE, Items.GLASS_PANE, Items.ENDER_PEARL),
                "green", floppyBase(Items.GREEN_DYE, Items.QUARTZ, Items.GLOWSTONE_DUST),
                "pink", floppyBase(Items.PINK_DYE, Items.HONEYCOMB, Items.AMETHYST_SHARD),
                "purple", floppyBase(Items.PURPLE_DYE, Items.RABBIT_FOOT, Items.GOLD_NUGGET),
                "red", floppyBase(Items.RED_DYE, Items.BLAZE_POWDER, Blocks.REDSTONE_BLOCK.asItem()),
                "yellow", floppyBase(Items.YELLOW_DYE, Items.BONE_MEAL, Blocks.COMPOSTER.asItem())
        );

        for (String color : ModItems.COLORS) {
            baseColor baseColor = floppyTypes.get(color);
            String copperKey = floppyKey("copper", color);
            ShapedRecipeBuilder.shaped(RecipeCategory.MISC, floppyItem(copperKey))
                    .pattern("DPD")
                    .pattern("AHB")
                    .pattern("RCR")
                    .define('D', baseColor.dye())
                    .define('P', Items.PAPER)
                    .define('A', baseColor.relatableItemA())
                    .define('B', baseColor.relatableItemB())
                    .define('H', ModItems.CHIP.get())
                    .define('R', Items.REDSTONE)
                    .define('C', Items.COPPER_INGOT)
                    .unlockedBy("has_dye_" + color, has(baseColor.dye()))
                    .unlockedBy("has_first_core_" + color, has(baseColor.relatableItemA()))
                    .unlockedBy("has_second_core_" + color, has(baseColor.relatableItemB()))
                    .unlockedBy("has_redstone", has(Items.REDSTONE))
                    .save(recipeOutput, floppyId(copperKey));

            String ironKey = floppyKey("iron", color);
            ShapedRecipeBuilder.shaped(RecipeCategory.MISC, floppyItem(ironKey))
                    .pattern("IQI")
                    .pattern("KHK")
                    .pattern("GDG")
                    .define('I', ModItems.IRON_PLATING.get())
                    .define('Q', Items.QUARTZ)
                    .define('K', floppyItem(copperKey))
                    .define('H', ModItems.CHIP.get())
                    .define('G', Items.GLOWSTONE_DUST)
                    .define('D', baseColor.dye())
                    .unlockedBy("has_" + copperKey, has(floppyItem(copperKey)))
                    .unlockedBy("has_chip", has(ModItems.CHIP.get()))
                    .unlockedBy("has_iron_plating", has(ModItems.IRON_PLATING.get()))
                    .save(recipeOutput, floppyId(ironKey));

            String goldKey = floppyKey("gold", color);
            ShapedRecipeBuilder.shaped(RecipeCategory.MISC, floppyItem(goldKey))
                    .pattern("PEP")
                    .pattern("KHK")
                    .pattern("PDP")
                    .define('P', ModItems.GOLD_PLATING.get())
                    .define('E', Items.ENDER_EYE)
                    .define('K', floppyItem(ironKey))
                    .define('H', ModItems.CHIP.get())
                    .define('D', baseColor.dye())
                    .unlockedBy("has_" + ironKey, has(floppyItem(ironKey)))
                    .unlockedBy("has_chip", has(ModItems.CHIP.get()))
                    .unlockedBy("has_gold_plating", has(ModItems.GOLD_PLATING.get()))
                    .save(recipeOutput, floppyId(goldKey));
        }
    }

    private ItemLike floppyItem(String registryKey) {
        return ModItems.FLOPPY_DISKS.get(registryKey).get();
    }

    private static String floppyKey(String tier, String color) {
        return tier + "_" + color + "_floppy";
    }

    private static ResourceLocation floppyId(String registryKey) {
        String[] parts = registryKey.split("_");
        return ResourceLocation.fromNamespaceAndPath(ByteBuddies.MOD_ID, "floppy/" + parts[0] + "/" + parts[1]);
    }

    private static baseColor floppyBase(ItemLike dye, ItemLike relatableItemA, ItemLike relatableItemB) {
        return new baseColor(dye, relatableItemA, relatableItemB);
    }

    private record baseColor(ItemLike dye, ItemLike relatableItemA, ItemLike relatableItemB) {}
}
