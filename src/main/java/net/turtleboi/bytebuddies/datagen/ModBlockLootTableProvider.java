package net.turtleboi.bytebuddies.datagen;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.loot.BlockLootSubProvider;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.ApplyBonusCount;
import net.minecraft.world.level.storage.loot.functions.SetItemCountFunction;
import net.minecraft.world.level.storage.loot.providers.number.UniformGenerator;
import net.turtleboi.bytebuddies.block.ModBlocks;
import net.turtleboi.bytebuddies.item.ModItems;

import java.util.Set;

public class ModBlockLootTableProvider extends BlockLootSubProvider {
    protected ModBlockLootTableProvider(HolderLookup.Provider registries) {
        super(Set.of(), FeatureFlags.REGISTRY.allFlags(), registries);
    }

    @Override
    protected void generate() {
        add(ModBlocks.BAUXITE_ORE.get(),
                block -> createOreDrop(ModBlocks.BAUXITE_ORE.get(), ModItems.RAW_BAUXITE.get()));
        add(ModBlocks.DEEPSLATE_BAUXITE_ORE.get(),
                block -> createOreDrop(ModBlocks.DEEPSLATE_BAUXITE_ORE.get(), ModItems.RAW_BAUXITE.get()));
        dropSelf(ModBlocks.ALUMINUM_BLOCK.get());
        dropSelf(ModBlocks.STEEL_BLOCK.get());
        dropSelf(ModBlocks.CHARGED_STEEL_BLOCK.get());
        add(ModBlocks.BLUESTONE_ORE.get(),
                block -> createBluestoneOreDrops(ModBlocks.BLUESTONE_ORE.get()));
        add(ModBlocks.DEEPSLATE_BLUESTONE_ORE.get(),
                block -> createBluestoneOreDrops(ModBlocks.BLUESTONE_ORE.get()));
        dropSelf(ModBlocks.BLUESTONE_BLOCK.get());
        dropSelf(ModBlocks.DOCKING_STATION.get());
        dropSelf(ModBlocks.GENERATOR.get());
        dropSelf(ModBlocks.SOLAR_PANEL.get());
    }

    @Override
    protected Iterable<Block> getKnownBlocks() {
        return ModBlocks.BLOCKS.getEntries().stream().map(Holder::value)::iterator;
    }

    private LootTable.Builder createBluestoneOreDrops(Block block) {
        HolderLookup.RegistryLookup<Enchantment> registrylookup = this.registries.lookupOrThrow(Registries.ENCHANTMENT);
        return this.createSilkTouchDispatchTable(
                block,
                this.applyExplosionDecay(
                        block,
                        LootItem.lootTableItem(ModItems.BLUESTONE_DUST)
                                .apply(SetItemCountFunction.setCount(UniformGenerator.between(4.0F, 5.0F)))
                                .apply(ApplyBonusCount.addUniformBonusCount(registrylookup.getOrThrow(Enchantments.FORTUNE)))
                )
        );
    }
}
