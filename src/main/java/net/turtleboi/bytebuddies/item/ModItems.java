package net.turtleboi.bytebuddies.item;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SwordItem;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.turtleboi.bytebuddies.ByteBuddies;
import net.turtleboi.bytebuddies.init.ModTiers;
import net.turtleboi.bytebuddies.item.custom.*;

import java.util.HashMap;
import java.util.Map;

public class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ByteBuddies.MOD_ID);

    public static final DeferredItem<Item> BUSTER_SWORD = ITEMS.register("buster_sword",
            () -> new SwordItem(ModTiers.BUSTER, new Item.Properties()
                    .attributes(SwordItem.createAttributes(ModTiers.BUSTER, 5.5F, -2.875F))
                    .rarity(Rarity.UNCOMMON)));

    public static final DeferredItem<Item> TERRABLADE = ITEMS.register("terrablade",
            () -> new TerrabladeItem(ModTiers.BUSTER, new Item.Properties()
                    .attributes(SwordItem.createAttributes(ModTiers.BUSTER, 7.5F, -2.8F))
                    .rarity(Rarity.RARE)));

    public static final DeferredItem<Item> RAW_BAUXITE = ITEMS.register("raw_bauxite",
            () -> new Item(new Item.Properties()));

    public static final DeferredItem<Item> ALUMINUM_INGOT = ITEMS.register("aluminum_ingot",
            () -> new Item(new Item.Properties()));

    public static final DeferredItem<Item> ALUMINUM_NUGGET = ITEMS.register("aluminum_nugget",
            () -> new Item(new Item.Properties()));

    public static final DeferredItem<Item> CARBON_ALLOY = ITEMS.register("carbon_alloy",
            () -> new Item(new Item.Properties()));

    public static final DeferredItem<Item> STEEL_INGOT = ITEMS.register("steel_ingot",
            () -> new Item(new Item.Properties()));

    public static final DeferredItem<Item> STEEL_NUGGET = ITEMS.register("steel_nugget",
            () -> new Item(new Item.Properties()));

    public static final DeferredItem<Item> CHARGED_STEEL_INGOT = ITEMS.register("charged_steel_ingot",
            () -> new Item(new Item.Properties().rarity(Rarity.UNCOMMON)));

    public static final DeferredItem<Item> CHARGED_STEEL_NUGGET = ITEMS.register("charged_steel_nugget",
            () -> new Item(new Item.Properties().rarity(Rarity.UNCOMMON)));

    public static final DeferredItem<Item> BLUESTONE_DUST = ITEMS.register("bluestone_dust",
            () -> new Item(new Item.Properties()));

    public static final DeferredItem<Item> COPPER_PLATING = ITEMS.register("copper_plating",
            () -> new Item(new Item.Properties()));

    public static final DeferredItem<Item> IRON_PLATING = ITEMS.register("iron_plating",
            () -> new Item(new Item.Properties()));

    public static final DeferredItem<Item> GOLD_PLATING = ITEMS.register("gold_plating",
            () -> new Item(new Item.Properties()));

    public static final DeferredItem<Item> ALUMINUM_PLATING = ITEMS.register("aluminum_plating",
            () -> new Item(new Item.Properties()));

    public static final DeferredItem<Item> STEEL_PLATING = ITEMS.register("steel_plating",
            () -> new Item(new Item.Properties()));

    public static final DeferredItem<Item> CHARGED_STEEL_PLATING = ITEMS.register("charged_steel_plating",
            () -> new Item(new Item.Properties().rarity(Rarity.UNCOMMON)));

    public static final DeferredItem<Item> CARBON_PASTE = ITEMS.register("carbon_paste",
            () -> new Item(new Item.Properties()));

    public static final Map<String, DeferredItem<Item>> FLOPPY_DISKS = new HashMap<>();

    public static final String[] COLORS = {
            "black", "blue", "cyan", "green", "pink", "purple", "red", "yellow"
    };

    public static final String[] TIERS = {
            "copper", "iron", "gold"
    };

    public static void registerFloppies() {
        for (String color : COLORS) {
            for (String tier : TIERS) {
                String registryName = tier + "_" + color + "_floppy";
                DeferredItem<Item> item = ITEMS.register(registryName,
                        () -> new FloppyDiskItem(new Item.Properties().stacksTo(1), tier, color));
                FLOPPY_DISKS.put(registryName, item);
            }
        }
    }

    public static final DeferredItem<Item> SIMPLE_BATTERY = ITEMS.register("simple_battery",
            () -> new BatteryItem(new Item.Properties().stacksTo(1), "simple", 8000, 32));

    public static final DeferredItem<Item> ADVANCED_BATTERY = ITEMS.register("advanced_battery",
            () -> new BatteryItem(new Item.Properties().stacksTo(1), "advanced",  16000,  64));

    public static final DeferredItem<Item> BIOCELL_BATTERY = ITEMS.register("biocell_battery",
            () -> new BatteryItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON), "biocell",   48000,  64));

    public static final DeferredItem<Item> REINFORCED_BATTERY = ITEMS.register("reinforced_battery",
            () -> new BatteryItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON), "reinforced", 64000, 128));

    public static final DeferredItem<Item> SUPER_CHARGED_BATTERY = ITEMS.register("super_charged_battery",
            () -> new BatteryItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE), "supercharged", 128000, 512));

    public static final DeferredItem<Item> CHIP = ITEMS.register("chip",
            () -> new Item(new Item.Properties()));

    public static final DeferredItem<Item> SUPER_CHIP = ITEMS.register("super_chip",
            () -> new Item(new Item.Properties().rarity(Rarity.RARE)));

    public static final DeferredItem<Item> WRENCH = ITEMS.register("wrench",
            () -> new WrenchItem(new Item.Properties().stacksTo(1)));

    public static final DeferredItem<Item> CLIPBOARD = ITEMS.register("clipboard",
            () -> new ClipboardItem(new Item.Properties().stacksTo(1)));

    public static final DeferredItem<Item> PROPELLER_UNIT = ITEMS.register("propeller_unit",
            () -> new AugmentItem(new Item.Properties()));

    public static final DeferredItem<Item> AQUATIC_MOTOR = ITEMS.register("aquatic_motor",
            () -> new AugmentItem(new Item.Properties()));

    public static final DeferredItem<Item> SOLAR_ARRAY = ITEMS.register("solar_array",
            () -> new AugmentItem(new Item.Properties()));

    public static final DeferredItem<Item> GYROSCOPIC_STABILIZER = ITEMS.register("gyroscopic_stabilizer",
            () -> new AugmentItem(new Item.Properties()));

    public static final DeferredItem<Item> ARC_WELDER = ITEMS.register("arc_welder",
            () -> new AugmentItem(new Item.Properties()));

    public static final DeferredItem<Item> GEOTHERMAL_REGULATOR = ITEMS.register("geothermal_regulator",
            () -> new AugmentItem(new Item.Properties()));

    public static final DeferredItem<Item> DYNAMO_COIL = ITEMS.register("dynamo_coil",
            () -> new AugmentItem(new Item.Properties()));

    public static final DeferredItem<Item> MAGNETIC_CRESCENT = ITEMS.register("magnetic_crescent",
            () -> new AugmentItem(new Item.Properties()));

    public static final DeferredItem<Item> BASIC_STORAGE_CELL = ITEMS.register("basic_storage_cell",
            () -> new AugmentItem(new Item.Properties()));

    public static final DeferredItem<Item> ADVANCED_STORAGE_CELL = ITEMS.register("advanced_storage_cell",
            () -> new AugmentItem(new Item.Properties().rarity(Rarity.UNCOMMON)));

    public static final DeferredItem<Item> ENDERLINK_STORAGE_CELL = ITEMS.register("enderlink_storage_cell",
            () -> new AugmentItem(new Item.Properties().rarity(Rarity.RARE)));

    public static final DeferredItem<Item> REINFORCED_IRON_PLATING = ITEMS.register("reinforced_iron_plating",
            () -> new AugmentItem(new Item.Properties()));

    public static final DeferredItem<Item> REINFORCED_STEEL_PLATING = ITEMS.register("reinforced_steel_plating",
            () -> new AugmentItem(new Item.Properties().rarity(Rarity.UNCOMMON)));

    public static final DeferredItem<Item> REINFORCED_NETHERITE_PLATING = ITEMS.register("reinforced_netherite_plating",
            () -> new AugmentItem(new Item.Properties().rarity(Rarity.RARE)));

    public static final DeferredItem<Item> REINFORCED_CHARGED_STEEL_PLATING = ITEMS.register("reinforced_charged_steel_plating",
            () -> new AugmentItem(new Item.Properties().rarity(Rarity.RARE)));

    public static final DeferredItem<Item> BYTEBUDDY_SPAWN_EGG = ITEMS.register("byte_buddy_spawner",
            () -> new ByteBuddySpawnerItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));


    public static void register(IEventBus eventBus){
        registerFloppies();
        ITEMS.register(eventBus);
    }
}
