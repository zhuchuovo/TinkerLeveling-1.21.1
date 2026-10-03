package org.embeddedt.tinkerleveling;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.embeddedt.tinkerleveling.client.SlotChoiceScreen;

public final class ClientHelper {
    private ClientHelper() {}

    public static void sendLevelUpMessage(int level, String toolNameKey) {
        Component toolName = Component.translatable(toolNameKey);
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

    /** Opens the slot selection screen, used when a level up left slots for the player to assign */
    public static void openSlotChoice(TinkerPacketHandler.SlotChoiceMessage message) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        if (message.choices().isEmpty()) {
            // nothing left to assign, drop the screen when it is the one we opened
            if (minecraft.screen instanceof SlotChoiceScreen) {
                minecraft.setScreen(null);
            }
            return;
        }
        minecraft.setScreen(new SlotChoiceScreen(message));
    }

    /** Sends the chosen slot type back to the server */
    public static void sendSlotChoice(java.util.UUID toolId, String slotType) {
        PacketDistributor.sendToServer(new TinkerPacketHandler.ChooseSlotMessage(toolId, slotType));
    }
}
