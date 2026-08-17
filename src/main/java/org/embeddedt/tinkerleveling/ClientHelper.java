package org.embeddedt.tinkerleveling;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;

public final class ClientHelper {
    private ClientHelper() {}

    public static void sendLevelUpMessage(int level) {
        Component toolName = Component.translatable("message.tinkerleveling.tool");
        Component message;
        String specialKey = "message.levelup." + level;
        if (I18n.exists(specialKey)) {
            message = Component.translatable(specialKey, toolName)
                    .withStyle(ChatFormatting.DARK_AQUA);
        } else {
            message = Component.translatable("message.levelup.generic", toolName)
                    .append(ClientEvents.getLevelString(level));
        }
        if (Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.sendSystemMessage(message);
        }
    }
}

