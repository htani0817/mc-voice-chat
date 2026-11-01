package jp.mc.voicechat.net;

import jp.mc.voicechat.McVoiceChat;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.UUID;

/**
 * クライアント → サーバー。通話の操作（発信・応答・切る）。
 * target は発信のときだけ使う（応答と切るは、いまの通話が相手になる）。
 */
public record CallPayload(Action action, UUID target) implements CustomPacketPayload {
    public enum Action {
        CALL,
        ACCEPT,
        /** 発信の取り消し・着信の拒否・通話の切断 */
        HANGUP
    }

    public static final UUID NOBODY = new UUID(0, 0);

    public static final Type<CallPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(McVoiceChat.MODID, "call"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CallPayload> STREAM_CODEC =
            CustomPacketPayload.codec(CallPayload::write, CallPayload::read);

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeByte(action.ordinal());
        buf.writeUUID(target);
    }

    private static CallPayload read(RegistryFriendlyByteBuf buf) {
        int id = buf.readByte();
        Action[] all = Action.values();
        // おかしな値は「切る」にしておく（害がない）
        return new CallPayload(id >= 0 && id < all.length ? all[id] : Action.HANGUP, buf.readUUID());
    }

    @Override
    public Type<CallPayload> type() {
        return TYPE;
    }
}
