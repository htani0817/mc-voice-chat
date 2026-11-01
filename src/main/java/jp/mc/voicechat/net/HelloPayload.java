package jp.mc.voicechat.net;

import jp.mc.voicechat.McVoiceChat;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * サーバー → クライアント。ログイン直後に「このサーバーはボイスチャットに対応している」を知らせる。
 * これが届くまでクライアントはマイクを使わない。
 */
public record HelloPayload(int protocol) implements CustomPacketPayload {
    public static final Type<HelloPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(McVoiceChat.MODID, "hello"));
    public static final StreamCodec<RegistryFriendlyByteBuf, HelloPayload> STREAM_CODEC =
            CustomPacketPayload.codec(HelloPayload::write, HelloPayload::read);

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(protocol);
    }

    private static HelloPayload read(RegistryFriendlyByteBuf buf) {
        return new HelloPayload(buf.readVarInt());
    }

    @Override
    public Type<HelloPayload> type() {
        return TYPE;
    }
}
