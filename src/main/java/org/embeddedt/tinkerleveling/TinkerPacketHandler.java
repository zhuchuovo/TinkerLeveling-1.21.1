package org.embeddedt.tinkerleveling;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.embeddedt.tinkerleveling.data.SlotTypeLoadable;
import slimeknights.tconstruct.library.tools.SlotType;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;

import java.util.List;
import java.util.UUID;

public final class TinkerPacketHandler {
    private static final String PROTOCOL_VERSION = "2";

    private TinkerPacketHandler() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar(PROTOCOL_VERSION)
                .playToClient(LevelUpMessage.TYPE, LevelUpMessage.STREAM_CODEC, LevelUpMessage::handle)
                .playToClient(SlotChoiceMessage.TYPE, SlotChoiceMessage.STREAM_CODEC, SlotChoiceMessage::handle)
                .playToServer(ChooseSlotMessage.TYPE, ChooseSlotMessage.STREAM_CODEC, ChooseSlotMessage::handle);
    }

    public static void sendLevelUp(int level, String toolNameKey, Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            PacketDistributor.sendToPlayer(serverPlayer, new LevelUpMessage(level, toolNameKey));
        }
    }

    public static void sendSlotChoice(UUID toolId, int level, String toolNameKey, List<SlotChoice> choices, Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            PacketDistributor.sendToPlayer(serverPlayer, new SlotChoiceMessage(toolId, level, toolNameKey, choices));
        }
    }

    /** One selectable slot type, with how many of it are waiting to be assigned */
    public record SlotChoice(String slotType, int count) {
        public static final StreamCodec<FriendlyByteBuf,SlotChoice> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, SlotChoice::slotType,
                ByteBufCodecs.VAR_INT, SlotChoice::count,
                SlotChoice::new);
    }

    private static final StreamCodec<FriendlyByteBuf,List<SlotChoice>> CHOICE_LIST_CODEC =
            SlotChoice.STREAM_CODEC.apply(ByteBufCodecs.list());

    public record LevelUpMessage(int level, String toolNameKey) implements CustomPacketPayload {
        public static final Type<LevelUpMessage> TYPE = new Type<>(TinkerLeveling.id("level_up"));
        public static final StreamCodec<FriendlyByteBuf, LevelUpMessage> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, LevelUpMessage::level,
                ByteBufCodecs.STRING_UTF8, LevelUpMessage::toolNameKey,
                LevelUpMessage::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        private static void handle(LevelUpMessage message, IPayloadContext context) {
            context.enqueueWork(() -> ClientHelper.sendLevelUpMessage(message.level(), message.toolNameKey()));
        }
    }

    /**
     * Tells the client which slot types the tool can still be given.
     * <p>
     * The tool name travels as a translation key rather than a resolved component so each client renders it in its own
     * language, and an empty {@code choices} list means "nothing left to assign".
     */
    public record SlotChoiceMessage(UUID toolId, int level, String toolNameKey, List<SlotChoice> choices) implements CustomPacketPayload {
        public static final Type<SlotChoiceMessage> TYPE = new Type<>(TinkerLeveling.id("slot_choice"));
        public static final StreamCodec<FriendlyByteBuf, SlotChoiceMessage> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8.map(UUID::fromString, UUID::toString), SlotChoiceMessage::toolId,
                ByteBufCodecs.VAR_INT, SlotChoiceMessage::level,
                ByteBufCodecs.STRING_UTF8, SlotChoiceMessage::toolNameKey,
                CHOICE_LIST_CODEC, SlotChoiceMessage::choices,
                SlotChoiceMessage::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        private static void handle(SlotChoiceMessage message, IPayloadContext context) {
            context.enqueueWork(() -> ClientHelper.openSlotChoice(message));
        }
    }

    /** Client answer to a {@link SlotChoiceMessage} */
    public record ChooseSlotMessage(UUID toolId, String slotType) implements CustomPacketPayload {
        public static final Type<ChooseSlotMessage> TYPE = new Type<>(TinkerLeveling.id("choose_slot"));
        public static final StreamCodec<FriendlyByteBuf, ChooseSlotMessage> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8.map(UUID::fromString, UUID::toString), ChooseSlotMessage::toolId,
                ByteBufCodecs.STRING_UTF8, ChooseSlotMessage::slotType,
                ChooseSlotMessage::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        private static void handle(ChooseSlotMessage message, IPayloadContext context) {
            ServerPlayer player = context.player() instanceof ServerPlayer serverPlayer ? serverPlayer : null;
            if (player == null) {
                return;
            }
            // only the owner of a tool may decide where its slots go, and the choice is validated against the
            // pending counters stored on the tool itself
            SlotType type;
            try {
                type = SlotTypeLoadable.resolve(message.slotType());
            } catch (RuntimeException e) {
                return;
            }
            ItemStack stack = ToolHelper.findByUUID(player, message.toolId());
            if (stack.isEmpty()) {
                return;
            }
            ToolStack tool = ToolStack.from(stack);
            TinkerLeveling.LEVELING_MODIFIER.get().applySlotChoice(tool, type, player);
        }
    }
}
