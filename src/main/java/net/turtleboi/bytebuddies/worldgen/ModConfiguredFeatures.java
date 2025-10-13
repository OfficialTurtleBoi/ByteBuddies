package net.turtleboi.bytebuddies.worldgen;

import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;
import net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration;
import net.minecraft.world.level.levelgen.structure.templatesystem.RuleTest;
import net.minecraft.world.level.levelgen.structure.templatesystem.TagMatchTest;
import net.turtleboi.bytebuddies.ByteBuddies;
import net.turtleboi.bytebuddies.block.ModBlocks;
import org.openjdk.nashorn.internal.ir.Block;

import java.util.List;

public class ModConfiguredFeatures {
    public static final ResourceKey<ConfiguredFeature<?, ?>> ALUMINUM_ORE_KEY = registerKey("aluminum_ore");
    public static final ResourceKey<ConfiguredFeature<?, ?>> BLUESTONE_ORE_KEY = registerKey("bluestone_ore");

    public static void bootstrap(BootstrapContext<ConfiguredFeature<?, ?>> context) {
        RuleTest stoneReplaceables = new TagMatchTest(BlockTags.STONE_ORE_REPLACEABLES);
        RuleTest deepslateReplaceables = new TagMatchTest(BlockTags.DEEPSLATE_ORE_REPLACEABLES);

        List<OreConfiguration.TargetBlockState> overworldAluminumOres = List.of(
                OreConfiguration.target(stoneReplaceables, ModBlocks.ALUMINUM_ORE.get().defaultBlockState()),
                OreConfiguration.target(deepslateReplaceables, ModBlocks.DEEPSLATE_ALUMINUM_ORE.get().defaultBlockState()));

        List<OreConfiguration.TargetBlockState> overworldBluestoneOres = List.of(
                OreConfiguration.target(stoneReplaceables, ModBlocks.BLUESTONE_ORE.get().defaultBlockState()),
                OreConfiguration.target(deepslateReplaceables, ModBlocks.DEEPSLATE_BLUESTONE_ORE.get().defaultBlockState()));

        register(context, ALUMINUM_ORE_KEY, Feature.ORE, new OreConfiguration(overworldAluminumOres, 5));
        register(context, BLUESTONE_ORE_KEY, Feature.ORE, new OreConfiguration(overworldBluestoneOres, 7));
    }

    public static ResourceKey<ConfiguredFeature<?, ?>> registerKey(String name) {
        return ResourceKey.create(Registries.CONFIGURED_FEATURE, ResourceLocation.fromNamespaceAndPath(ByteBuddies.MOD_ID, name));
    }

    private static <FC extends FeatureConfiguration, F extends Feature<FC>> void
        register (BootstrapContext<ConfiguredFeature<?, ?>> context, ResourceKey<ConfiguredFeature<?, ?>> key, F feature, FC configuration) {
        context.register(key, new ConfiguredFeature<>(feature, configuration));
    }
}
