package org.embeddedt.tinkerleveling.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.embeddedt.tinkerleveling.ClientHelper;
import org.embeddedt.tinkerleveling.TinkerPacketHandler.SlotChoice;
import org.embeddedt.tinkerleveling.TinkerPacketHandler.SlotChoiceMessage;
import slimeknights.tconstruct.library.tools.SlotType;

import java.util.ArrayList;
import java.util.List;

/**
 * Asks the player where the slots earned from a level up should go.
 * <p>
 * Every option is one button. Clicking a button assigns a single slot of that type; the server answers with an updated
 * list, or closes this screen once nothing is left to assign.
 */
public class SlotChoiceScreen extends Screen {
    private static final int BUTTON_WIDTH = 140;
    private static final int BUTTON_HEIGHT = 20;
    private static final int GAP = 6;

    private final SlotChoiceMessage message;
    private final List<Button> optionButtons = new ArrayList<>();
    private int top;

    public SlotChoiceScreen(SlotChoiceMessage message) {
        super(Component.translatable("screen.tinkerleveling.choose_slot"));
        this.message = message;
    }

    @Override
    protected void init() {
        optionButtons.clear();
        List<SlotChoice> choices = message.choices();
        int totalHeight = choices.size() * (BUTTON_HEIGHT + GAP);
        this.top = (this.height - totalHeight) / 2 + 8;
        int left = (this.width - BUTTON_WIDTH) / 2;
        for (int i = 0; i < choices.size(); i++) {
            SlotChoice choice = choices.get(i);
            Component label = Component.translatable(SlotType.KEY_DISPLAY + choice.slotType())
                    .withStyle(ChatFormatting.YELLOW)
                    .append(Component.literal(" x" + choice.count()).withStyle(ChatFormatting.GRAY));
            Button button = Button.builder(label, b -> ClientHelper.sendSlotChoice(message.toolId(), choice.slotType()))
                    .bounds(left, top + i * (BUTTON_HEIGHT + GAP), BUTTON_WIDTH, BUTTON_HEIGHT)
                    .build();
            optionButtons.add(addRenderableWidget(button));
        }
        addRenderableWidget(Button.builder(Component.translatable("screen.tinkerleveling.later"),
                        b -> this.onClose())
                .bounds(left, top + choices.size() * (BUTTON_HEIGHT + GAP) + GAP, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, top - 34, 0xFFFFFF);
        graphics.drawCenteredString(this.font,
                Component.translatable("screen.tinkerleveling.tool", Component.translatable(message.toolNameKey()))
                        .withStyle(ChatFormatting.AQUA),
                this.width / 2, top - 22, 0xFFFFFF);
        graphics.drawCenteredString(this.font,
                Component.translatable("screen.tinkerleveling.hint", totalPending()).withStyle(ChatFormatting.GRAY),
                this.width / 2, top - 10, 0xFFFFFF);
    }

    /** Number of slots still waiting to be assigned across every option */
    private int totalPending() {
        int total = 0;
        for (SlotChoice choice : message.choices()) {
            total += Mth.clamp(choice.count(), 0, Integer.MAX_VALUE);
        }
        return total;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
