package net.turtleboi.bytebuddies.datagen;

import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.tags.ItemTagsProvider;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import net.turtleboi.bytebuddies.ByteBuddies;
import net.turtleboi.bytebuddies.item.ModItems;
import net.turtleboi.bytebuddies.util.ModTags;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;

public class ModItemTagProvider extends ItemTagsProvider {
    public ModItemTagProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider,
                              CompletableFuture<TagLookup<Block>> blockTags, @Nullable ExistingFileHelper existingFileHelper) {
        super(output, lookupProvider, blockTags, ByteBuddies.MOD_ID, existingFileHelper);
    }

    @Override
    protected void addTags(HolderLookup.Provider provider) {
        tag(Tags.Items.ORES)
                .add(ModItems.RAW_BAUXITE.get());

        tag(ModTags.Items.BATTERY)
                .add(ModItems.SIMPLE_BATTERY.get())
                .add(ModItems.ADVANCED_BATTERY.get())
                .add(ModItems.BIOCELL_BATTERY.get())
                .add(ModItems.REINFORCED_BATTERY.get())
                .add(ModItems.SUPER_CHARGED_BATTERY.get());

        tag(ModTags.Items.AUGMENT)
                .add(ModItems.AQUATIC_MOTOR.get())
                .add(ModItems.SOLAR_ARRAY.get())
                .add(ModItems.GYROSCOPIC_STABILIZER.get())
                .add(ModItems.ARC_WELDER.get())
                .add(ModItems.GEOTHERMAL_REGULATOR.get())
                .add(ModItems.DYANAMO_COIL.get())
                .add(ModItems.REINFORCED_IRON_PLATING.get())
                .add(ModItems.REINFORCED_STEEL_PLATING.get())
                .add(ModItems.REINFORCED_NETHERITE_PLATING.get())
                .add(ModItems.REINFORCED_CHARGED_STEEL_PLATING.get());

        tag(ModTags.Items.PLATING)
                .add(ModItems.REINFORCED_IRON_PLATING.get())
                .add(ModItems.REINFORCED_STEEL_PLATING.get())
                .add(ModItems.REINFORCED_NETHERITE_PLATING.get())
                .add(ModItems.REINFORCED_CHARGED_STEEL_PLATING.get());
    }
}
