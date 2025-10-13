package net.turtleboi.bytebuddies.worldgen;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.VerticalAnchor;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.placement.CountPlacement;
import net.minecraft.world.level.levelgen.placement.HeightRangePlacement;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;
import net.turtleboi.bytebuddies.ByteBuddies;

import java.util.List;

public class ModPlacedFeatures {
    public static final ResourceKey<PlacedFeature> ALUMINUM_ORE_PLACED_KEY = registerKey("aluminum_ore_placed");
    public static final ResourceKey<PlacedFeature> ALUMINUM_ORE_ABUNDANT_PLACED_KEY = registerKey("aluminum_ore_abundant_placed");
    public static final ResourceKey<PlacedFeature> ALUMINUM_ORE_RARE_PLACED_KEY = registerKey("aluminum_ore_rare_placed");

    public static final ResourceKey<PlacedFeature> BLUESTONE_ORE_PLACED_KEY = registerKey("bluestone_ore_placed");
    public static final ResourceKey<PlacedFeature> BLUESTONE_ORE_LOWER_PLACED_KEY = registerKey("bluestone_ore_lower_placed");

    public static void bootstrap(BootstrapContext<PlacedFeature> context) {
        var configuredFeatures = context.lookup(Registries.CONFIGURED_FEATURE);
        register(context, ALUMINUM_ORE_PLACED_KEY, configuredFeatures.getOrThrow(ModConfiguredFeatures.ALUMINUM_ORE_KEY),
                ModOrePlacement.orePlacement(CountPlacement.of(8), HeightRangePlacement.triangle(
                        VerticalAnchor.absolute(-64), VerticalAnchor.absolute(80))));

        register(context, ALUMINUM_ORE_ABUNDANT_PLACED_KEY, configuredFeatures.getOrThrow(ModConfiguredFeatures.ALUMINUM_ORE_KEY),
                ModOrePlacement.orePlacement(CountPlacement.of(16), HeightRangePlacement.uniform(
                        VerticalAnchor.absolute(40), VerticalAnchor.absolute(120))));

        register(context, ALUMINUM_ORE_RARE_PLACED_KEY, configuredFeatures.getOrThrow(ModConfiguredFeatures.ALUMINUM_ORE_KEY),
                ModOrePlacement.orePlacement(CountPlacement.of(4), HeightRangePlacement.uniform(
                        VerticalAnchor.absolute(-64), VerticalAnchor.absolute(80))));

        register(context, BLUESTONE_ORE_PLACED_KEY, configuredFeatures.getOrThrow(ModConfiguredFeatures.BLUESTONE_ORE_KEY),
                ModOrePlacement.commonOrePlacement(4, HeightRangePlacement.uniform(
                        VerticalAnchor.bottom(), VerticalAnchor.absolute(15))));

        register(context, BLUESTONE_ORE_LOWER_PLACED_KEY, configuredFeatures.getOrThrow(ModConfiguredFeatures.BLUESTONE_ORE_KEY),
                ModOrePlacement.commonOrePlacement(8, HeightRangePlacement.triangle(
                        VerticalAnchor.aboveBottom(-32), VerticalAnchor.aboveBottom(32))));
    }

    private static ResourceKey<PlacedFeature> registerKey(String name) {
        return ResourceKey.create(Registries.PLACED_FEATURE, ResourceLocation.fromNamespaceAndPath(ByteBuddies.MOD_ID, name));
    }

    private static void register(BootstrapContext<PlacedFeature> context, ResourceKey<PlacedFeature> key,
                                 Holder<ConfiguredFeature<?, ?>> configuration, List<PlacementModifier> modifiers) {
        context.register(key, new PlacedFeature(configuration, List.copyOf(modifiers)));
    }
}
