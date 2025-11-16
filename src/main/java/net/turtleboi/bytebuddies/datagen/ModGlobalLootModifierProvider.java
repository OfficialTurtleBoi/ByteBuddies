package net.turtleboi.bytebuddies.datagen;

import net.minecraft.data.PackOutput;
import net.minecraftforge.common.data.GlobalLootModifierProvider;
import net.turtleboi.bytebuddies.ByteBuddies;

public class ModGlobalLootModifierProvider extends GlobalLootModifierProvider {
    public ModGlobalLootModifierProvider(PackOutput output) {
        super(output, ByteBuddies.MOD_ID);
    }

    @Override
    protected void start() {

    }
}
