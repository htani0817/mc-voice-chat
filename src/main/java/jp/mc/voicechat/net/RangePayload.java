package jp.mc.voicechat.net;

import jp.mc.voicechat.McVoiceChat;
import jp.mc.voicechat.VoiceRange;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * クライアント → サーバー。自分の声の届く範囲。ログイン時と範囲を切り替えたときに送る。
 */
public record RangePayload(VoiceRange range) implements CustomPacketPayload {
    public static final Type<RangePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(McVoiceChat.MODID, "range"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RangePayload> STREAM_CODEC =
            CustomPacketPayload.codec(RangePayload::write, RangePayload::read);

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeByte(range.ordinal());
    }

    private static RangePayload read(RegistryFriendlyByteBuf buf) {
        return new RangePayload(VoiceRange.byId(buf.readByte()));
    }

    @Override
    public Type<RangePayload> type() {
        return TYPE;
    }
}
