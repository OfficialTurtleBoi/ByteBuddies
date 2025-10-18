package net.turtleboi.bytebuddies.datagen;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.PackOutput;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.common.data.LanguageProvider;
import net.neoforged.neoforge.registries.DeferredItem;
import net.turtleboi.bytebuddies.ByteBuddies;
import net.turtleboi.bytebuddies.block.ModBlocks;
import net.turtleboi.bytebuddies.entity.ModEntities;
import net.turtleboi.bytebuddies.item.ModItems;

import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

public class ModLanguageProvider extends LanguageProvider {
    public ModLanguageProvider(PackOutput output) {
        super(output, ByteBuddies.MOD_ID, "en_us");
    }

    @Override
    protected void addTranslations() {
        add("creativetab.bytebuddies.bytebuddies_tab", "ByteBuddies");
        addEntityType(ModEntities.BYTEBUDDY, "ByteBuddy");
        addSimpleNameBlock(ModBlocks.BAUXITE_ORE);
        addSimpleNameBlock(ModBlocks.DEEPSLATE_BAUXITE_ORE);
        addSimpleNameBlock(ModBlocks.ALUMINUM_BLOCK);
        addSimpleNameBlock(ModBlocks.STEEL_BLOCK);
        addSimpleNameBlock(ModBlocks.CHARGED_STEEL_BLOCK);
        addSimpleNameBlock(ModBlocks.BLUESTONE_ORE);
        addSimpleNameBlock(ModBlocks.DEEPSLATE_BLUESTONE_ORE);
        addSimpleNameBlock(ModBlocks.BLUESTONE_BLOCK);
        addSimpleNameBlock(ModBlocks.DOCKING_STATION);
        addSimpleNameBlock(ModBlocks.GENERATOR);
        addSimpleNameBlock(ModBlocks.SOLAR_PANEL);
        addSimpleItemName(ModItems.BUSTER_SWORD);
        addSimpleItemName(ModItems.RAW_BAUXITE);
        addSimpleItemName(ModItems.ALUMINUM_INGOT);
        addSimpleItemName(ModItems.ALUMINUM_NUGGET);
        addSimpleItemName(ModItems.CARBON_ALLOY);
        addSimpleItemName(ModItems.STEEL_INGOT);
        addSimpleItemName(ModItems.STEEL_NUGGET);
        addSimpleItemName(ModItems.CHARGED_STEEL_INGOT);
        addSimpleItemName(ModItems.CHARGED_STEEL_NUGGET);
        addSimpleItemName(ModItems.BLUESTONE_DUST);
        addSimpleItemName(ModItems.COPPER_PLATING);
        addSimpleItemName(ModItems.IRON_PLATING);
        addSimpleItemName(ModItems.GOLD_PLATING);
        addSimpleItemName(ModItems.ALUMINUM_PLATING);
        addSimpleItemName(ModItems.STEEL_PLATING);
        addSimpleItemName(ModItems.CHARGED_STEEL_PLATING);
        addSimpleItemName(ModItems.CARBON_PASTE);
        addSimpleItemName(ModItems.SIMPLE_BATTERY);
        addSimpleItemName(ModItems.ADVANCED_BATTERY);
        addSimpleItemName(ModItems.BIOCELL_BATTERY);
        addSimpleItemName(ModItems.REINFORCED_BATTERY);
        addSimpleItemName(ModItems.SUPER_CHARGED_BATTERY);
        addSimpleItemName(ModItems.CHIP);
        addSimpleItemName(ModItems.SUPER_CHIP);
        addSimpleItemName(ModItems.WRENCH);
        addSimpleItemName(ModItems.CLIPBOARD);
        addSimpleItemName(ModItems.AQUATIC_MOTOR);
        addSimpleItemName(ModItems.SOLAR_ARRAY);
        addSimpleItemName(ModItems.GYROSCOPIC_STABILIZER);
        addSimpleItemName(ModItems.ARC_WELDER);
        addSimpleItemName(ModItems.GEOTHERMAL_REGULATOR);
        addSimpleItemName(ModItems.DYANAMO_COIL);
        addSimpleItemName(ModItems.MAGNETIC_CRESCENT);
        addSimpleItemName(ModItems.BASIC_STORAGE_CELL);
        addSimpleItemName(ModItems.ADVANCED_STORAGE_CELL);
        addSimpleItemName(ModItems.ENDERLINK_STORAGE_CELL);
        addSimpleItemName(ModItems.REINFORCED_IRON_PLATING);
        addSimpleItemName(ModItems.REINFORCED_STEEL_PLATING);
        addSimpleItemName(ModItems.REINFORCED_NETHERITE_PLATING);
        addSimpleItemName(ModItems.REINFORCED_CHARGED_STEEL_PLATING);
        add(ModItems.BYTEBUDDY_SPAWN_EGG.get(), "ByteBuddy Spawn Egg");

        ModItems.FLOPPY_DISKS.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    DeferredItem<Item> item = entry.getValue();
                    String path = item.getId().getPath();
                    String display = floppyDisplayName(path).orElseGet(() -> toName(path));
                    add("item." + ByteBuddies.MOD_ID + "." + path, display);
                });

        add("tooltip.bytebuddies.floppy.tier_line", "Tier: %s");
        add("tooltip." + ByteBuddies.MOD_ID + ".floppy_tier.copper", "Copper");
        add("tooltip." + ByteBuddies.MOD_ID + ".floppy_tier.iron", "Iron");
        add("tooltip." + ByteBuddies.MOD_ID + ".floppy_tier.gold", "Gold");

        add("tooltip.bytebuddies.floppy.desc.black", "Reduces tool wear, increases chassis toughness, and adds limited self-repair.");
        add("tooltip.bytebuddies.floppy.desc.blue", "Expands sensing and action radius around the bot’s station or active job.");
        add("tooltip.bytebuddies.floppy.desc.cyan", "On a successful action, there’s a chance to spawn a short-lived hologram that performs parallel tasks");
        add("tooltip.bytebuddies.floppy.desc.green", "Reduces battery drain and consumable use where applicable.");
        add("tooltip.bytebuddies.floppy.desc.pink", "Supportive aura that buffs nearby allies and tasks.");
        add("tooltip.bytebuddies.floppy.desc.purple", "Improves yield from actions the bot performs.");
        add("tooltip.bytebuddies.floppy.desc.red", "Overclock for more power and speed at a higher energy cost.");
        add("tooltip.bytebuddies.floppy.desc.yellow", "When a task succeeds, roll for a byproduct tied to that task’s context");
    }

    private void addSimpleItemName(Supplier<? extends Item> supplier) {
        Item item = supplier.get();
        var itemId = BuiltInRegistries.ITEM.getKey(item);
        add(item, toName(itemId.getPath()));
    }

    private void addSimpleNameBlock(Supplier<? extends Block> supplier) {
        Block block = supplier.get();
        var blockId = BuiltInRegistries.BLOCK.getKey(block);
        add(block, toName(blockId.getPath()));
    }

    private static String toName(String registryPath) {
        StringBuilder stringBuilder = new StringBuilder(registryPath.length() + 8);
        for (String part : registryPath.split("_")) {
            if (part.isEmpty()) continue;
            stringBuilder.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) stringBuilder.append(part.substring(1));
            stringBuilder.append(' ');
        }
        return stringBuilder.toString().trim();
    }

    private static Optional<String> floppyDisplayName(String registryPath) {
        if (!registryPath.endsWith("_floppy")) return Optional.empty();
        String stem = registryPath.substring(0, registryPath.length() - "_floppy".length());
        int ix = stem.indexOf('_');
        if (ix <= 0 || ix >= stem.length() - 1) return Optional.empty();
        String color = stem.substring(ix + 1);
        return Optional.of(capitalize(color) + " Floppy Disk");
    }

    private static String capitalize(String string) {
        if (string == null || string.isEmpty()) return string;
        return Character.toUpperCase(string.charAt(0)) + (string.length() > 1 ? string.substring(1) : "");
    }
}
