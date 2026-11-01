package jp.mc.voicechat.net;

import jp.mc.voicechat.McVoiceChat;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * クライアント → サーバー。マイクの音声 20ms 分を Opus で圧縮したもの。
 * seq は話者ごとの連番で、受け手が順番の入れ替わりや欠けを見分けるのに使う。
 *
 * @param open  ふだんの話し方（音声検知かプッシュトゥトーク）で話している。近くの人と通話相手に届ける
 * @param party プッシュトゥトークのキーを押していて、パーティーに入っている。パーティーの人に届ける
 */
public record MicAudioPayload(int seq, boolean open, boolean party, byte[] opus) implements CustomPacketPayload {
    private static final int FLAG_OPEN = 1;
    private static final int FLAG_PARTY = 2;

    public static final Type<MicAudioPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(McVoiceChat.MODID, "mic"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MicAudioPayload> STREAM_CODEC =
            CustomPacketPayload.codec(MicAudioPayload::write, MicAudioPayload::read);

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(seq);
        buf.writeByte((open ? FLAG_OPEN : 0) | (party ? FLAG_PARTY : 0));
        buf.writeByteArray(opus);
    }

    private static MicAudioPayload read(RegistryFriendlyByteBuf buf) {
        int seq = buf.readVarInt();
        int flags = buf.readByte();
        return new MicAudioPayload(seq, (flags & FLAG_OPEN) != 0, (flags & FLAG_PARTY) != 0,
                buf.readByteArray(McVoiceChat.MAX_OPUS_BYTES));
    }

    @Override
    public Type<MicAudioPayload> type() {
        return TYPE;
    }
}
