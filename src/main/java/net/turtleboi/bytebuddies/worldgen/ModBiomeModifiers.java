package net.turtleboi.bytebuddies.worldgen;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstapContext;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraftforge.common.world.BiomeModifier;
import net.minecraftforge.common.world.ForgeBiomeModifiers;
import net.minecraftforge.registries.ForgeRegistries;
import net.turtleboi.bytebuddies.ByteBuddies;

import java.util.LinkedHashSet;
import java.util.List;

public class ModBiomeModifiers {
    public static final ResourceKey<BiomeModifier> ADD_ALUMINUM_ORE = registerKey("add_aluminum_ore");
    public static final ResourceKey<BiomeModifier> ADD_ABUNDANT_ALUMINUM_ORE = registerKey("add_abundant_aluminum_ore");
    public static final ResourceKey<BiomeModifier> ADD_RARE_ALUMINUM_ORE = registerKey("add_rare_aluminum_ore");

    public static final ResourceKey<BiomeModifier> ADD_BLUESTONE_ORE = registerKey("add_bluestone_ore");
    public static final ResourceKey<BiomeModifier> ADD_LOWER_BLUESTONE_ORE = registerKey("add_lower_bluestone_ore");
    public static void bootstrap(BootstapContext<BiomeModifier> context) {
        HolderGetter<PlacedFeature> placedFeatures = context.lookup(Registries.PLACED_FEATURE);
        HolderGetter<Biome> biomes = context.lookup(Registries.BIOME);

        context.register(ADD_ALUMINUM_ORE, new ForgeBiomeModifiers.AddFeaturesBiomeModifier(
                HolderSet.direct(
                        biomes.getOrThrow(Biomes.PLAINS),
                        biomes.getOrThrow(Biomes.SUNFLOWER_PLAINS),
                        biomes.getOrThrow(Biomes.FOREST),
                        biomes.getOrThrow(Biomes.FLOWER_FOREST),
                        biomes.getOrThrow(Biomes.BIRCH_FOREST),
                        biomes.getOrThrow(Biomes.MANGROVE_SWAMP),
                        biomes.getOrThrow(Biomes.MEADOW)
                ),
                HolderSet.direct(
                        placedFeatures.getOrThrow(ModPlacedFeatures.ALUMINUM_ORE_PLACED_KEY)
                ),
                GenerationStep.Decoration.UNDERGROUND_ORES
        ));

        context.register(ADD_ABUNDANT_ALUMINUM_ORE, new ForgeBiomeModifiers.AddFeaturesBiomeModifier(
                HolderSet.direct(
                        biomes.getOrThrow(Biomes.JUNGLE),
                        biomes.getOrThrow(Biomes.SPARSE_JUNGLE),
                        biomes.getOrThrow(Biomes.BAMBOO_JUNGLE),
                        biomes.getOrThrow(Biomes.SAVANNA),
                        biomes.getOrThrow(Biomes.SAVANNA_PLATEAU),
                        biomes.getOrThrow(Biomes.WINDSWEPT_SAVANNA),
                        biomes.getOrThrow(Biomes.BADLANDS),
                        biomes.getOrThrow(Biomes.ERODED_BADLANDS),
                        biomes.getOrThrow(Biomes.WOODED_BADLANDS)
                ),
                HolderSet.direct(
                        placedFeatures.getOrThrow(ModPlacedFeatures.ALUMINUM_ORE_ABUNDANT_PLACED_KEY)
                ),
                GenerationStep.Decoration.UNDERGROUND_ORES
        ));

        context.register(ADD_RARE_ALUMINUM_ORE, new ForgeBiomeModifiers.AddFeaturesBiomeModifier(
                HolderSet.direct(
                        biomes.getOrThrow(Biomes.DESERT),
                        biomes.getOrThrow(Biomes.TAIGA),
                        biomes.getOrThrow(Biomes.SNOWY_TAIGA),
                        biomes.getOrThrow(Biomes.SNOWY_PLAINS),
                        biomes.getOrThrow(Biomes.SNOWY_SLOPES),
                        biomes.getOrThrow(Biomes.FROZEN_PEAKS),
                        biomes.getOrThrow(Biomes.JAGGED_PEAKS),
                        biomes.getOrThrow(Biomes.STONY_PEAKS)
                ),
                HolderSet.direct(
                        placedFeatures.getOrThrow(ModPlacedFeatures.ALUMINUM_ORE_RARE_PLACED_KEY)
                ),
                GenerationStep.Decoration.UNDERGROUND_ORES
        ));

        context.register(ADD_BLUESTONE_ORE, new ForgeBiomeModifiers.AddFeaturesBiomeModifier(
                HolderSet.direct(
                        biomes.getOrThrow(Biomes.PLAINS),
                        biomes.getOrThrow(Biomes.SUNFLOWER_PLAINS),
                        biomes.getOrThrow(Biomes.FOREST),
                        biomes.getOrThrow(Biomes.FLOWER_FOREST),
                        biomes.getOrThrow(Biomes.BIRCH_FOREST),
                        biomes.getOrThrow(Biomes.MANGROVE_SWAMP),
                        biomes.getOrThrow(Biomes.MEADOW)
                ),
                HolderSet.direct(
                        placedFeatures.getOrThrow(ModPlacedFeatures.BLUESTONE_ORE_PLACED_KEY)
                ),
                GenerationStep.Decoration.UNDERGROUND_ORES
        ));

        context.register(ADD_LOWER_BLUESTONE_ORE, new ForgeBiomeModifiers.AddFeaturesBiomeModifier(
                biomes.getOrThrow(BiomeTags.IS_OVERWORLD),
                HolderSet.direct(
                        placedFeatures.getOrThrow(ModPlacedFeatures.BLUESTONE_ORE_LOWER_PLACED_KEY)
                ),
                GenerationStep.Decoration.UNDERGROUND_ORES
        ));
    }

    private static ResourceKey<BiomeModifier> registerKey(String name) {
        return ResourceKey.create(ForgeRegistries.Keys.BIOME_MODIFIERS, new ResourceLocation(ByteBuddies.MOD_ID, name));
    }
}