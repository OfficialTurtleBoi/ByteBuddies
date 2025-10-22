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
import net.turtleboi.bytebuddies.init.ModTags;
import net.turtleboi.bytebuddies.item.ModItems;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class ModRecipeProvider extends RecipeProvider implements IConditionBuilder {
    public ModRecipeProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        super(output, registries);
    }

    @Override
    protected void buildRecipes(RecipeOutput recipeOutput) {
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.BYTEBUDDY_SPAWN_EGG.get())
                .pattern("ACA")
                .pattern("ASA")
                .pattern("ABA")
                .define('A', ModItems.ALUMINUM_PLATING.get())
                .define('C', ModItems.CHIP.get())
                .define('S', ModBlocks.BLUESTONE_BLOCK.get())
                .define('B', Ingredient.of(ModTags.Items.BATTERY))
                .unlockedBy("has_aluminum_plating", has(ModItems.ALUMINUM_PLATING.get()))
                .unlockedBy("has_bluestone_block", has(ModBlocks.BLUESTONE_BLOCK.get()))
                .unlockedBy("has_chip", has(ModItems.CHIP.get()))
                .unlockedBy("has_battery", has(ModTags.Items.BATTERY))
                .save(recipeOutput, ByteBuddies.MOD_ID + ":spawner/byte_buddy_spawner");

        nineBlockCycle(recipeOutput,
                ModItems.ALUMINUM_NUGGET.get(), ModItems.ALUMINUM_INGOT.get(), ModBlocks.ALUMINUM_BLOCK.get(),
                "aluminum/cycle");

        nineBlockCycle(recipeOutput,
                ModItems.STEEL_NUGGET.get(), ModItems.STEEL_INGOT.get(), ModBlocks.STEEL_BLOCK.get(),
                "steel/cycle");

        nineBlockCycle(recipeOutput,
                ModItems.CHARGED_STEEL_NUGGET.get(), ModItems.CHARGED_STEEL_INGOT.get(), ModBlocks.CHARGED_STEEL_BLOCK.get(),
                "charged_steel/cycle");

        smeltAndBlast(recipeOutput,
                ModItems.RAW_BAUXITE.get(), ModItems.ALUMINUM_INGOT.get(),
                0.7f, 200, 100, "aluminum/bauxite_refine");

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

        ShapedRecipeBuilder.shaped(RecipeCategory.TOOLS, ModItems.CLIPBOARD.get())
                .pattern("APA")
                .pattern("ABA")
                .pattern("APA")
                .define('A', ModItems.ALUMINUM_INGOT)
                .define('B', ModItems.BLUESTONE_DUST)
                .define('P', Items.PAPER)
                .unlockedBy("has_bluestone", has(ModItems.BLUESTONE_DUST))
                .unlockedBy("has_aluminum", has(ModItems.ALUMINUM_INGOT))
                .unlockedBy("has_paper", has(Items.PAPER))
                .save(recipeOutput, ByteBuddies.MOD_ID + ":tools/clipboard");

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

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.SUPER_CHIP.get())
                .pattern("GQG")
                .pattern("RCB")
                .pattern("GIG")
                .define('Q', Items.QUARTZ)
                .define('R', Blocks.REDSTONE_BLOCK)
                .define('B', ModBlocks.BLUESTONE_BLOCK.get())
                .define('C', ModItems.CHIP.get())
                .define('I', Items.GOLD_INGOT)
                .define('G', Items.GLOWSTONE_DUST)
                .unlockedBy("has_chip", has(ModItems.CHIP.get()))
                .save(recipeOutput, ByteBuddies.MOD_ID + ":electronics/super_chip");

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

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.REINFORCED_IRON_PLATING.get())
                .pattern(" P ")
                .pattern("PBP")
                .pattern(" P ")
                .define('P', ModItems.IRON_PLATING.get())
                .define('B', Blocks.IRON_BLOCK)
                .unlockedBy("has_iron_plating", has(ModItems.IRON_PLATING.get()))
                .save(recipeOutput, ByteBuddies.MOD_ID + ":plating/reinforced_iron");

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.REINFORCED_STEEL_PLATING.get())
                .pattern(" P ")
                .pattern("PBP")
                .pattern(" P ")
                .define('P', ModItems.STEEL_PLATING.get())
                .define('B', ModBlocks.STEEL_BLOCK.get())
                .unlockedBy("has_steel_plating", has(ModItems.STEEL_PLATING.get()))
                .save(recipeOutput, ByteBuddies.MOD_ID + ":plating/reinforced_steel");

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.REINFORCED_NETHERITE_PLATING.get())
                .pattern(" P ")
                .pattern("PIP")
                .pattern(" P ")
                .define('P', Items.NETHERITE_SCRAP)
                .define('I', Items.NETHERITE_INGOT)
                .unlockedBy("has_netherite_scrap", has(Items.NETHERITE_SCRAP))
                .unlockedBy("has_netherite_ingot", has(Items.NETHERITE_INGOT))
                .save(recipeOutput, ByteBuddies.MOD_ID + ":plating/reinforced_netherite");

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.REINFORCED_CHARGED_STEEL_PLATING.get())
                .pattern(" P ")
                .pattern("PBP")
                .pattern(" P ")
                .define('P', ModItems.CHARGED_STEEL_PLATING.get())
                .define('B', ModBlocks.CHARGED_STEEL_BLOCK.get())
                .unlockedBy("has_charged_steel", has(ModItems.CHARGED_STEEL_INGOT.get()))
                .unlockedBy("has_charged_steel_block", has(ModBlocks.CHARGED_STEEL_BLOCK.get()))
                .save(recipeOutput, ByteBuddies.MOD_ID + ":plating/reinforced_charged_steel");

        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ModItems.CARBON_ALLOY.get())
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
                        3.5f,
                        1000
                )
                .unlockedBy("has_carbon_alloy", has(ModItems.CARBON_ALLOY.get()))
                .save(recipeOutput);

        ShapedRecipeBuilder.shaped(RecipeCategory.REDSTONE, ModItems.BASIC_STORAGE_CELL.get())
                .pattern("PIP")
                .pattern("SCS")
                .pattern("PRP")
                .define('I', Items.IRON_INGOT)
                .define('P', ModItems.IRON_PLATING.get())
                .define('R', Items.REDSTONE)
                .define('S', ModItems.BLUESTONE_DUST)
                .define('C', ModItems.CHIP.get())
                .unlockedBy("has_chip", has(ModItems.CHIP.get()))
                .save(recipeOutput, ByteBuddies.MOD_ID + ":storage/basic_cell");

        ShapedRecipeBuilder.shaped(RecipeCategory.REDSTONE, ModItems.ADVANCED_STORAGE_CELL.get())
                .pattern("PBP")
                .pattern("SCS")
                .pattern("PGP")
                .define('P', ModItems.GOLD_PLATING.get())
                .define('G', Items.GLOWSTONE_DUST)
                .define('B', Items.BLAZE_ROD)
                .define('S', ModItems.BASIC_STORAGE_CELL.get())
                .define('C', ModItems.CHIP.get())
                .unlockedBy("has_basic_cell", has(ModItems.BASIC_STORAGE_CELL.get()))
                .save(recipeOutput, ByteBuddies.MOD_ID + ":storage/advanced_cell");

        ShapedRecipeBuilder.shaped(RecipeCategory.REDSTONE, ModItems.ENDERLINK_STORAGE_CELL.get())
                .pattern("OEO")
                .pattern("SCS")
                .pattern("ODO")
                .define('E', Items.ENDER_EYE)
                .define('D', Blocks.ENDER_CHEST)
                .define('O', Blocks.OBSIDIAN)
                .define('S', ModItems.ADVANCED_STORAGE_CELL.get())
                .define('C', ModItems.SUPER_CHIP.get())
                .unlockedBy("has_adv_cell", has(ModItems.ADVANCED_STORAGE_CELL.get()))
                .save(recipeOutput, ByteBuddies.MOD_ID + ":storage/enderlink_cell");

        ShapedRecipeBuilder.shaped(RecipeCategory.REDSTONE, ModItems.PROPELLER_UNIT.get())
                .pattern(" F ")
                .pattern("AIA")
                .pattern(" F ")
                .define('I', Items.IRON_INGOT)
                .define('A', ModItems.ALUMINUM_INGOT)
                .define('F', Items.FEATHER)
                .unlockedBy("has_aluminum_ingot", has(ModItems.ALUMINUM_INGOT.get()))
                .save(recipeOutput, ByteBuddies.MOD_ID + ":augment/propeller_unit");

        ShapedRecipeBuilder.shaped(RecipeCategory.REDSTONE, ModItems.AQUATIC_MOTOR.get())
                .pattern(" K ")
                .pattern("AIA")
                .pattern(" K ")
                .define('I', Items.IRON_INGOT)
                .define('A', ModItems.ALUMINUM_INGOT)
                .define('K', Items.KELP)
                .unlockedBy("has_aluminum_ingot", has(ModItems.ALUMINUM_INGOT.get()))
                .save(recipeOutput, ByteBuddies.MOD_ID + ":augment/aquatic_motor");

        ShapedRecipeBuilder.shaped(RecipeCategory.REDSTONE, ModItems.SOLAR_ARRAY.get())
                .pattern("GGG")
                .pattern("QLQ")
                .pattern("DCD")
                .define('G', Blocks.GLASS)
                .define('Q', Items.QUARTZ)
                .define('L', Blocks.LAPIS_BLOCK)
                .define('C', ModItems.CHIP.get())
                .define('D', Items.GLOWSTONE_DUST)
                .unlockedBy("has_chip", has(ModItems.CHIP.get()))
                .save(recipeOutput, ByteBuddies.MOD_ID + ":augment/solar_array");

        ShapedRecipeBuilder.shaped(RecipeCategory.REDSTONE, ModItems.GYROSCOPIC_STABILIZER.get())
                .pattern(" G ")
                .pattern("BCB")
                .pattern(" G ")
                .define('B', ModItems.BLUESTONE_DUST.get())
                .define('C', ModItems.CHIP.get())
                .define('G', Items.GOLD_INGOT)
                .unlockedBy("has_chip", has(ModItems.CHIP.get()))
                .save(recipeOutput, ByteBuddies.MOD_ID + ":augment/gyroscopic_stabilizer");

        ShapedRecipeBuilder.shaped(RecipeCategory.REDSTONE, ModItems.ARC_WELDER.get())
                .pattern("RI ")
                .pattern("DCI")
                .pattern(" B ")
                .define('I', Items.IRON_INGOT)
                .define('R', Items.BLAZE_ROD)
                .define('C', ModItems.CHIP.get())
                .define('D', ModItems.BLUESTONE_DUST.get())
                .define('B', ModItems.ADVANCED_BATTERY.get())
                .unlockedBy("has_battery", has(ModItems.ADVANCED_BATTERY.get()))
                .save(recipeOutput, ByteBuddies.MOD_ID + ":augment/arc_welder");

        ShapedRecipeBuilder.shaped(RecipeCategory.REDSTONE, ModItems.GEOTHERMAL_REGULATOR.get())
                .pattern("OSO")
                .pattern("SBS")
                .pattern("OLO")
                .define('O', Blocks.OBSIDIAN)
                .define('L', Items.LAVA_BUCKET)
                .define('B', ModItems.REINFORCED_BATTERY.get())
                .define('S',  ModItems.STEEL_INGOT.get())
                .unlockedBy("has_reinforced_battery", has(ModItems.REINFORCED_BATTERY.get()))
                .save(recipeOutput, ByteBuddies.MOD_ID + ":augment/geothermal_regulator");

        ShapedRecipeBuilder.shaped(RecipeCategory.REDSTONE, ModItems.DYNAMO_COIL.get())
                .pattern(" R ")
                .pattern("CIC")
                .pattern(" R ")
                .define('C', Items.COPPER_INGOT)
                .define('R', Items.REDSTONE)
                .define('I', Items.IRON_INGOT)
                .unlockedBy("has_copper", has(Items.COPPER_INGOT))
                .save(recipeOutput, ByteBuddies.MOD_ID + ":augment/dynamo_coil");

        ShapedRecipeBuilder.shaped(RecipeCategory.REDSTONE, ModItems.MAGNETIC_CRESCENT.get())
                .pattern(" I ")
                .pattern("R I")
                .pattern("CB ")
                .define('I', Items.IRON_INGOT)
                .define('C', ModItems.CHIP.get())
                .define('R', Blocks.REDSTONE_BLOCK)
                .define('B', ModBlocks.BLUESTONE_BLOCK.get())
                .unlockedBy("has_chip", has(ModItems.CHIP.get()))
                .save(recipeOutput, ByteBuddies.MOD_ID + ":augment/magnetic_crescent");

        ShapedRecipeBuilder.shaped(RecipeCategory.COMBAT, ModItems.BUSTER_SWORD.get())
                .pattern(" S ")
                .pattern("IEI")
                .pattern(" T ")
                .define('S', ModItems.STEEL_INGOT.get())
                .define('E', ModBlocks.STEEL_BLOCK.get())
                .define('I', Items.IRON_INGOT)
                .define('T', Items.STICK)
                .unlockedBy("has_steel", has(ModItems.STEEL_INGOT.get()))
                .save(recipeOutput, ByteBuddies.MOD_ID + ":weapon/buster_sword");

        ShapedRecipeBuilder.shaped(RecipeCategory.COMBAT, ModItems.TERRABLADE.get())
                .pattern(" C ")
                .pattern("GEG")
                .pattern(" T ")
                .define('C', ModItems.CHARGED_STEEL_INGOT.get())
                .define('E', ModBlocks.CHARGED_STEEL_BLOCK.get())
                .define('G', Items.GOLD_INGOT)
                .define('T', Items.STICK)
                .unlockedBy("has_charged", has(ModItems.CHARGED_STEEL_INGOT.get()))
                .save(recipeOutput, ByteBuddies.MOD_ID + ":weapon/terrablade");

        ShapedRecipeBuilder.shaped(RecipeCategory.REDSTONE, ModBlocks.DOCKING_STATION.get())
                .pattern("ACA")
                .pattern("ABA")
                .pattern("ATA")
                .define('A', ModItems.ALUMINUM_INGOT.get())
                .define('B', ModBlocks.BLUESTONE_BLOCK.get())
                .define('C', ModItems.CHIP.get())
                .define('T', ModItems.ADVANCED_BATTERY.get())
                .unlockedBy("has_advanced_battery", has(ModItems.ADVANCED_BATTERY.get()))
                .unlockedBy("has_chip", has(ModItems.CHIP.get()))
                .unlockedBy("has_aluminum", has(ModItems.ALUMINUM_INGOT.get()))
                .unlockedBy("has_bluestone_block", has(ModBlocks.BLUESTONE_BLOCK.get()))
                .save(recipeOutput, ByteBuddies.MOD_ID + ":machines/docking_station");

        ShapedRecipeBuilder.shaped(RecipeCategory.REDSTONE, ModBlocks.GENERATOR.get())
                .pattern("AFA")
                .pattern("TBT")
                .pattern("ACA")
                .define('A', ModItems.ALUMINUM_INGOT.get())
                .define('B', ModBlocks.BLUESTONE_BLOCK.get())
                .define('F', Blocks.FURNACE)
                .define('C', ModItems.CHIP.get())
                .define('T', ModItems.ADVANCED_BATTERY.get())
                .unlockedBy("has_advanced_battery", has(ModItems.ADVANCED_BATTERY.get()))
                .unlockedBy("has_chip", has(ModItems.CHIP.get()))
                .unlockedBy("has_aluminum", has(ModItems.ALUMINUM_INGOT.get()))
                .unlockedBy("has_bluestone_block", has(ModBlocks.BLUESTONE_BLOCK.get()))
                .save(recipeOutput, ByteBuddies.MOD_ID + ":machines/generator");

        ShapedRecipeBuilder.shaped(RecipeCategory.REDSTONE, ModBlocks.SOLAR_PANEL.get())
                .pattern("SSS")
                .pattern("TBT")
                .pattern("ACA")
                .define('S', ModItems.SOLAR_ARRAY.get())
                .define('A', ModItems.ALUMINUM_INGOT.get())
                .define('B', ModBlocks.BLUESTONE_BLOCK.get())
                .define('C', ModItems.CHIP.get())
                .define('T', ModItems.ADVANCED_BATTERY.get())
                .unlockedBy("has_advanced_battery", has(ModItems.ADVANCED_BATTERY.get()))
                .unlockedBy("has_chip", has(ModItems.CHIP.get()))
                .unlockedBy("has_aluminum", has(ModItems.ALUMINUM_INGOT.get()))
                .unlockedBy("has_bluestone_block", has(ModBlocks.BLUESTONE_BLOCK.get()))
                .save(recipeOutput, ByteBuddies.MOD_ID + ":machines/solar_panel");

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

    private static void nineBlockCycle(RecipeOutput out,
                                       ItemLike nugget, ItemLike ingot, ItemLike block,
                                       String keyPrefix) {
        // 9 nuggets -> 1 ingot
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ingot)
                .pattern("NNN").pattern("NNN").pattern("NNN")
                .define('N', nugget)
                .unlockedBy("has_" + idOf(nugget), has(nugget))
                .save(out, keyPrefix + "/ingot_from_nuggets");

        // 1 ingot -> 9 nuggets
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, nugget, 9)
                .requires(ingot)
                .unlockedBy("has_" + idOf(ingot), has(ingot))
                .save(out, keyPrefix + "/nuggets_from_ingot");

        // 9 ingots -> 1 block
        ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, block)
                .pattern("AAA").pattern("AAA").pattern("AAA")
                .define('A', ingot)
                .unlockedBy("has_" + idOf(ingot), has(ingot))
                .save(out, keyPrefix + "/block_from_ingots");

        // 1 block -> 9 ingots
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ingot, 9)
                .requires(block)
                .unlockedBy("has_" + idOf(block), has(block))
                .save(out, keyPrefix + "/ingots_from_block");
    }

    private static void smeltAndBlast(RecipeOutput out, ItemLike input, ItemLike output, float xp, int smeltTime, int blastTime, String keyPrefix) {
        SimpleCookingRecipeBuilder.smelting(Ingredient.of(input), RecipeCategory.MISC, output, xp, smeltTime)
                .unlockedBy("has_" + idOf(input), has(input))
                .save(out, keyPrefix + "/smelting");
        SimpleCookingRecipeBuilder.blasting(Ingredient.of(input), RecipeCategory.MISC, output, xp, blastTime)
                .unlockedBy("has_" + idOf(input), has(input))
                .save(out, keyPrefix + "/blasting");
    }

    private static String idOf(ItemLike like) {
        return like.asItem().builtInRegistryHolder().key().location().getPath();
    }

    private static void simpleShaped(RecipeOutput out, ItemLike result, int count, String key, String[] pattern, Object... keys) {
        ShapedRecipeBuilder shapedRecipeBuilder = ShapedRecipeBuilder.shaped(RecipeCategory.MISC, result, count);
        for (String row : pattern) shapedRecipeBuilder = shapedRecipeBuilder.pattern(row);
        for (int i = 0; i < keys.length; i += 2) {
            shapedRecipeBuilder = shapedRecipeBuilder.define((Character) keys[i], (ItemLike) keys[i + 1]);
        }
        shapedRecipeBuilder.unlockedBy("has_any_" + key, has(result)).save(out, key);
    }

}
