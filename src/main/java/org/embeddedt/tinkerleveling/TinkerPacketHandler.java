package org.embeddedt.tinkerleveling;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class TinkerPacketHandler {
    private static final String PROTOCOL_VERSION = "1";

    private TinkerPacketHandler() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar(PROTOCOL_VERSION)
                .playToClient(LevelUpMessage.TYPE, LevelUpMessage.STREAM_CODEC, LevelUpMessage::handle);
    }

    public static void sendLevelUp(int level, Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            PacketDistributor.sendToPlayer(serverPlayer, new LevelUpMessage(level));
        }
    }

    public record LevelUpMessage(int level) implements CustomPacketPayload {
        public static final Type<LevelUpMessage> TYPE = new Type<>(TinkerLeveling.id("level_up"));
        public static final StreamCodec<FriendlyByteBuf, LevelUpMessage> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, LevelUpMessage::level,
                LevelUpMessage::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        private static void handle(LevelUpMessage message, IPayloadContext context) {
            context.enqueueWork(() -> ClientHelper.sendLevelUpMessage(message.level));
        }
    }
}

