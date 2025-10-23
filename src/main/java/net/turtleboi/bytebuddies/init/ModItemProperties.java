package net.turtleboi.bytebuddies.init;

import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.turtleboi.bytebuddies.ByteBuddies;
import net.turtleboi.bytebuddies.component.ModDataComponents;
import net.turtleboi.bytebuddies.item.ModItems;
import net.turtleboi.bytebuddies.item.custom.TerrabladeItem;

public class ModItemProperties {
    public static void addCustomItemProperties() {
        ItemProperties.register(
                ModItems.TERRABLADE.get(),
                ResourceLocation.fromNamespaceAndPath(ByteBuddies.MOD_ID, "charge"),
                (stack, level, entity, seed) -> {
                    int charge = TerrabladeItem.getCharge(stack);
                    int idx = (int)Math.floor((charge / 100f) * 14f);
                    return idx / 14f;
                }
        );
    }
}
