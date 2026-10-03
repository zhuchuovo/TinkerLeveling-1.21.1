package org.embeddedt.tinkerleveling;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class TinkerConfig {
    private static final ModConfigSpec.Builder SERVER_BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.IntValue maximumLevels;
    public static final ModConfigSpec.DoubleValue defaultBaseXP;
    public static final ModConfigSpec.DoubleValue levelMultiplier;
    public static final ModConfigSpec.BooleanValue allowArmorExploits;
    public static final ModConfigSpec.BooleanValue showDatapackSource;
    public static final ModConfigSpec.BooleanValue debugLogging;
    public static final ModConfigSpec SERVER_CONFIG;

    static {
        maximumLevels = SERVER_BUILDER
                .comment("Maximum achievable level. If set to 0 there is no upper limit.",
                        "Data pack rules may lower this further with their own 'max_level', but never raise it.")
                .defineInRange("maximumLevels", 0, 0, Integer.MAX_VALUE);
        levelMultiplier = SERVER_BUILDER
                .comment("How much the XP cost multiplies per level.",
                        "This is the fallback used by data pack rules that do not define their own 'xp.multiplier'.")
                .defineInRange("levelMultiplier", 2.0, 1.0, 1000.0);
        defaultBaseXP = SERVER_BUILDER
                .comment("Base XP for the first level up, used when no data pack rule defines 'xp.base'.")
                .defineInRange("defaultBaseXP", 500.0, 1.0, 1.0E9);
        allowArmorExploits = SERVER_BUILDER
                .comment("Allow any damage to the player to count towards armor XP, not just mob damage.")
                .define("allowArmorExploits", false);
        showDatapackSource = SERVER_BUILDER
                .comment("Show the ID of the leveling rule that drives a tool in its tooltip.")
                .define("showDatapackSource", false);
        debugLogging = SERVER_BUILDER
                .comment("Log every level up, the rule that matched, and the slots that were granted.")
                .define("debugLogging", false);
        SERVER_CONFIG = SERVER_BUILDER.build();
    }

    private TinkerConfig() {}

    public static boolean canLevelUp(int currentLevel) {
        int maximum = maximumLevels.get();
        return maximum <= 0 || currentLevel < maximum;
    }
}
