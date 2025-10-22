package net.turtleboi.bytebuddies.datagen;

import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.client.model.generators.ItemModelBuilder;
import net.neoforged.neoforge.client.model.generators.ItemModelProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import net.neoforged.neoforge.registries.DeferredItem;
import net.turtleboi.bytebuddies.ByteBuddies;
import net.turtleboi.bytebuddies.item.ModItems;

import java.util.Optional;

public class ModItemModelProvider extends ItemModelProvider {
    public ModItemModelProvider(PackOutput output, ExistingFileHelper existingFileHelper) {
        super(output, ByteBuddies.MOD_ID, existingFileHelper);
    }

    @Override
    protected void registerModels() {
        basicItem(ModItems.RAW_BAUXITE.get());
        basicItem(ModItems.ALUMINUM_INGOT.get());
        basicItem(ModItems.ALUMINUM_NUGGET.get());
        basicItem(ModItems.CARBON_ALLOY.get());
        basicItem(ModItems.STEEL_INGOT.get());
        basicItem(ModItems.STEEL_NUGGET.get());
        basicItem(ModItems.CHARGED_STEEL_INGOT.get());
        basicItem(ModItems.CHARGED_STEEL_NUGGET.get());
        basicItem(ModItems.BLUESTONE_DUST.get());
        basicItem(ModItems.COPPER_PLATING.get());
        basicItem(ModItems.IRON_PLATING.get());
        basicItem(ModItems.GOLD_PLATING.get());
        basicItem(ModItems.ALUMINUM_PLATING.get());
        basicItem(ModItems.STEEL_PLATING.get());
        basicItem(ModItems.CHARGED_STEEL_PLATING.get());
        basicItem(ModItems.SIMPLE_BATTERY.get());
        basicItem(ModItems.ADVANCED_BATTERY.get());
        basicItem(ModItems.BIOCELL_BATTERY.get());
        basicItem(ModItems.REINFORCED_BATTERY.get());
        basicItem(ModItems.SUPER_CHARGED_BATTERY.get());
        basicItem(ModItems.CARBON_PASTE.get());
        basicItem(ModItems.CHIP.get());
        basicItem(ModItems.SUPER_CHIP.get());
        handheldItem(ModItems.WRENCH.get());
        basicItem(ModItems.CLIPBOARD.get());
        basicItem(ModItems.PROPELLER_UNIT.get());
        basicItem(ModItems.AQUATIC_MOTOR.get());
        basicItem(ModItems.SOLAR_ARRAY.get());
        basicItem(ModItems.GYROSCOPIC_STABILIZER.get());
        basicItem(ModItems.ARC_WELDER.get());
        basicItem(ModItems.GEOTHERMAL_REGULATOR.get());
        basicItem(ModItems.DYNAMO_COIL.get());
        basicItem(ModItems.MAGNETIC_CRESCENT.get());
        basicItem(ModItems.BASIC_STORAGE_CELL.get());
        basicItem(ModItems.ADVANCED_STORAGE_CELL.get());
        basicItem(ModItems.ENDERLINK_STORAGE_CELL.get());
        basicItem(ModItems.REINFORCED_IRON_PLATING.get());
        basicItem(ModItems.REINFORCED_STEEL_PLATING.get());
        basicItem(ModItems.REINFORCED_NETHERITE_PLATING.get());
        basicItem(ModItems.REINFORCED_CHARGED_STEEL_PLATING.get());
        ModItems.FLOPPY_DISKS.values().forEach(this::layeredFloppyModel);
        //withExistingParent(ModItems.BYTEBUDDY_SPAWN_EGG.getId().getPath(), mcLoc("item/template_spawn_egg"));
    }

    private ItemModelBuilder layeredFloppyModel(DeferredItem<Item> itemObject) {
        String path = itemObject.getId().getPath();

        var optionalParts = parse(path);
        if (optionalParts.isEmpty()) {
            System.out.println("[Datagen] Skip " + path + " — cannot parse floppy key.");
            return null;
        }
        var parts = optionalParts.get();

        ResourceLocation floppyBaseTex = materialTexture(ByteBuddies.MOD_ID, parts.color);
        ResourceLocation floppyTierTex = overlayTexture(ByteBuddies.MOD_ID, parts.tier);

        ItemModelBuilder itemModelBuilder = withExistingParent(path, mcLoc("item/generated"));
        int layer = 0;
        itemModelBuilder = itemModelBuilder.texture("layer" + layer++, floppyBaseTex);
        itemModelBuilder = itemModelBuilder.texture("layer" + layer, floppyTierTex);
        return itemModelBuilder;
    }

    public record FloppyParts(String tier, String color) {}

    public static Optional<FloppyParts> parse(String registryPath) {
        if (!registryPath.endsWith("_floppy")) return Optional.empty();
        String stem = registryPath.substring(0, registryPath.length() - "_floppy".length());
        int ix = stem.lastIndexOf('_');
        if (ix < 0) return Optional.empty();
        String tier = stem.substring(0, ix);
        String color  = stem.substring(ix + 1);
        return Optional.of(new FloppyParts(tier, color));
    }

    public static ResourceLocation materialTexture(String modId, String color) {
        return ResourceLocation.fromNamespaceAndPath(modId, "item/floppy/" + color + "_floppy_base");
    }

    public static ResourceLocation overlayTexture(String modId, String tier) {
        return ResourceLocation.fromNamespaceAndPath(modId, "item/floppy/" + tier + "_floppy_tier");
    }
}
