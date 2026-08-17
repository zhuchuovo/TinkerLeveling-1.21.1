package org.embeddedt.tinkerleveling;

import net.minecraft.world.item.Item;
import net.neoforged.neoforge.common.ModConfigSpec;

public final class TinkerConfig {
    private static final ModConfigSpec.Builder SERVER_BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.IntValue maximumLevels;
    public static final ModConfigSpec.IntValue defaultBaseXP;
    public static final ModConfigSpec.DoubleValue levelMultiplier;
    public static final ModConfigSpec.BooleanValue allowArmorExploits;
    public static final ModConfigSpec SERVER_CONFIG;

    static {
        maximumLevels = SERVER_BUILDER
                .comment("Maximum achievable level. If set to 0 there is no upper limit.")
                .defineInRange("maximumLevels", 0, 0, Integer.MAX_VALUE);
        levelMultiplier = SERVER_BUILDER
                .comment("How much the XP cost multiplies per level (minimum 2).")
                .defineInRange("levelMultiplier", 2.0, 2.0, Double.MAX_VALUE);
        defaultBaseXP = SERVER_BUILDER
                .comment("Base XP used when no more specific entry is present for the tool.")
                .defineInRange("defaultBaseXP", 500, 1, Integer.MAX_VALUE);
        allowArmorExploits = SERVER_BUILDER
                .comment("Allow any damage to the player to count towards armor XP, not just mob damage.")
                .define("allowArmorExploits", false);
        SERVER_CONFIG = SERVER_BUILDER.build();
    }

    private TinkerConfig() {}

    public static int getBaseXpForTool(Item item) {
        return defaultBaseXP.get();
    }

    public static boolean canLevelUp(int currentLevel) {
        int maximum = maximumLevels.get();
        return maximum <= 0 || currentLevel < maximum;
    }
}

