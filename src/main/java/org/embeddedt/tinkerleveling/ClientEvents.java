package org.embeddedt.tinkerleveling;

import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import slimeknights.tconstruct.common.TinkerTags;
import slimeknights.tconstruct.library.tools.nbt.ModDataNBT;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;

import java.awt.Color;
import java.util.List;

@EventBusSubscriber(modid = TinkerLeveling.MODID, value = Dist.CLIENT)
public final class ClientEvents {
    private ClientEvents() {}

    @SubscribeEvent
    static void onTooltipEvent(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (!stack.is(TinkerTags.Items.MODIFIABLE)) {
            return;
        }
        ToolStack tool = ToolStack.copyFrom(stack);
        if (tool.getModifierLevel(TinkerLeveling.LEVELING_MODIFIER.getId()) > 0) {
            ModDataNBT levelData = tool.getPersistentData();
            int xp = levelData.getInt(ModToolLeveling.XP_KEY);
            int level = levelData.getInt(ModToolLeveling.LEVEL_KEY);
            List<Component> tooltips = event.getToolTip();
            int insertAt = Math.min(1, tooltips.size());
            tooltips.add(insertAt, Component.translatable("tooltip.tinkerleveling.xp")
                    .append(": ")
                    .append(Component.literal(xp + " / " + ModToolLeveling.getXpForLevelup(level, stack.getItem()))));
            tooltips.add(insertAt, getLevelTooltip(level));
        }
    }

    private static Component getLevelTooltip(int level) {
        return Component.translatable("tooltip.tinkerleveling.level")
                .append(": ")
                .append(getLevelString(level));
    }

    public static Component getLevelString(int level) {
        return Component.literal(getRawLevelString(level))
                .withStyle(style -> style.withColor(getLevelColor(level)));
    }

    private static String getRawLevelString(int level) {
        if (level <= 0) {
            return "";
        }

        String exactKey = "tooltip.tinkerleveling.level." + level;
        if (I18n.exists(exactKey)) {
            return I18n.get(exactKey);
        }

        int count = 1;
        while (I18n.exists("tooltip.tinkerleveling.level." + count)) {
            count++;
        }
        String name = I18n.get("tooltip.tinkerleveling.level." + (level % count));
        return name + "+".repeat(level / count);
    }

    private static int getLevelColor(int level) {
        float hue = 0.277777f * level;
        hue -= (int) hue;
        return Color.HSBtoRGB(hue, 0.75f, 0.8f);
    }
}
