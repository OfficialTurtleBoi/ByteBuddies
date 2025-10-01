package net.turtleboi.bytebuddies.datagen;

import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.tags.BlockTags;
import net.neoforged.neoforge.common.data.BlockTagsProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import net.turtleboi.bytebuddies.ByteBuddies;
import net.turtleboi.bytebuddies.block.ModBlocks;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;

public class ModBlockTagProvider extends BlockTagsProvider {
    public ModBlockTagProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider, @Nullable ExistingFileHelper existingFileHelper) {
        super(output, lookupProvider, ByteBuddies.MOD_ID, existingFileHelper);
    }

    @Override
    protected void addTags(HolderLookup.Provider provider) {
        tag(BlockTags.MINEABLE_WITH_PICKAXE)
                .add(ModBlocks.ALUMINUM_BLOCK.get())
                .add(ModBlocks.BLUESTONE_ORE.get())
                .add(ModBlocks.DEEPSLATE_BLUESTONE_ORE.get())
                .add(ModBlocks.BLUESTONE_BLOCK.get());

        tag(BlockTags.NEEDS_IRON_TOOL)
                .add(ModBlocks.ALUMINUM_BLOCK.get())
                .add(ModBlocks.BLUESTONE_ORE.get())
                .add(ModBlocks.DEEPSLATE_BLUESTONE_ORE.get());
    }
}
