package jp.mc.voicechat.net;

import jp.mc.voicechat.CallState;
import jp.mc.voicechat.McVoiceChat;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.UUID;

/**
 * サーバー → クライアント。受け取った人自身の通話の状態と相手。状態が変わるたびに送る。
 * 通話していないときの peer は CallPayload.NOBODY。
 */
public record CallStatePayload(CallState state, UUID peer) implements CustomPacketPayload {
    public static final Type<CallStatePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(McVoiceChat.MODID, "call_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CallStatePayload> STREAM_CODEC =
            CustomPacketPayload.codec(CallStatePayload::write, CallStatePayload::read);

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeByte(state.ordinal());
        buf.writeUUID(peer);
    }

    private static CallStatePayload read(RegistryFriendlyByteBuf buf) {
        return new CallStatePayload(CallState.byId(buf.readByte()), buf.readUUID());
    }

    @Override
    public Type<CallStatePayload> type() {
        return TYPE;
    }
}
