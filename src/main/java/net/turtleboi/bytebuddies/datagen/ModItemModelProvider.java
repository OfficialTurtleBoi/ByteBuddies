package net.turtleboi.bytebuddies.datagen;

import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.client.model.generators.ItemModelProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import net.turtleboi.bytebuddies.ByteBuddies;
import net.turtleboi.bytebuddies.item.ModItems;

public class ModItemModelProvider extends ItemModelProvider {
    public ModItemModelProvider(PackOutput output, ExistingFileHelper existingFileHelper) {
        super(output, ByteBuddies.MOD_ID, existingFileHelper);
    }

    @Override
    protected void registerModels() {
        basicItem(ModItems.BATTERY.get());
        basicItem(ModItems.CHIP.get());
        withExistingParent(ModItems.BYTEBUDDY_SPAWN_EGG.getId().getPath(), mcLoc("item/template_spawn_egg"));
    }
}
