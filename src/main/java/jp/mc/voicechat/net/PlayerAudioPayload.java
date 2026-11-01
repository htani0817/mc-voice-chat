package jp.mc.voicechat.net;

import jp.mc.voicechat.McVoiceChat;
import jp.mc.voicechat.VoiceKind;
import jp.mc.voicechat.VoiceRange;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.UUID;

/**
 * サーバー → クライアント。誰かの声。
 * 話者の位置と範囲も付けて送り、受け手はそれで音の大きさと左右を決める。
 * kind が近接以外（通話・パーティー）なら、受け手は距離と向きを使わずにそのままの大きさで鳴らす。
 */
public record PlayerAudioPayload(UUID speaker, int seq, double x, double y, double z, VoiceRange range, VoiceKind kind,
                                 byte[] opus) implements CustomPacketPayload {
    public static final Type<PlayerAudioPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(McVoiceChat.MODID, "audio"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PlayerAudioPayload> STREAM_CODEC =
            CustomPacketPayload.codec(PlayerAudioPayload::write, PlayerAudioPayload::read);

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeUUID(speaker);
        buf.writeVarInt(seq);
        buf.writeDouble(x);
        buf.writeDouble(y);
        buf.writeDouble(z);
        buf.writeByte(range.ordinal());
        buf.writeByte(kind.ordinal());
        buf.writeByteArray(opus);
    }

    private static PlayerAudioPayload read(RegistryFriendlyByteBuf buf) {
        return new PlayerAudioPayload(buf.readUUID(), buf.readVarInt(), buf.readDouble(), buf.readDouble(), buf.readDouble(),
                VoiceRange.byId(buf.readByte()), VoiceKind.byId(buf.readByte()), buf.readByteArray(McVoiceChat.MAX_OPUS_BYTES));
    }

    @Override
    public Type<PlayerAudioPayload> type() {
        return TYPE;
    }
}
