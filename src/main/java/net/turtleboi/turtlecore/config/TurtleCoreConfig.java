package net.turtleboi.turtlecore.config;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.List;

public class TurtleCoreConfig {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.ConfigValue<Boolean> TARGETTING_ARROW_TOGGLE;

    static {
        BUILDER.push("Targetting");

        TARGETTING_ARROW_TOGGLE = BUILDER
                .comment("Display visual targetting arrows at all times")
                .define ("targetting_arrow_toggle", true);

        BUILDER.pop();
        SPEC = BUILDER.build();
    }
}
