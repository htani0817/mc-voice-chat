package jp.mc.voicechat.net;

import jp.mc.voicechat.McVoiceChat;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * クライアント → サーバー。パーティーVCの操作。
 * <ul>
 * <li>CREATE: name と password で作る（password が空ならパスワードなし）</li>
 * <li>JOIN: partyId に password で入る</li>
 * <li>LEAVE: いまのパーティーから抜ける</li>
 * <li>RENAME / SET_PASSWORD: いまのパーティーの名前・パスワードを変える（作った人だけ）</li>
 * <li>DELETE: partyId のパーティーを消す（作った人だけ）</li>
 * </ul>
 */
public record PartyPayload(Action action, int partyId, String name, String password) implements CustomPacketPayload {
    public enum Action { CREATE, JOIN, LEAVE, RENAME, SET_PASSWORD, DELETE }

    /** 通信で受け取る文字列の上限。長さの本当の判定はサーバーの PartyBook がする */
    private static final int MAX_TEXT = 64;

    public static final Type<PartyPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(McVoiceChat.MODID, "party"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PartyPayload> STREAM_CODEC =
            CustomPacketPayload.codec(PartyPayload::write, PartyPayload::read);

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeByte(action.ordinal());
        buf.writeVarInt(partyId);
        buf.writeUtf(name, MAX_TEXT);
        buf.writeUtf(password, MAX_TEXT);
    }

    private static PartyPayload read(RegistryFriendlyByteBuf buf) {
        int id = buf.readByte();
        Action[] all = Action.values();
        // おかしな値は「抜ける」にしておく（害がない）
        Action action = id >= 0 && id < all.length ? all[id] : Action.LEAVE;
        return new PartyPayload(action, buf.readVarInt(), buf.readUtf(MAX_TEXT), buf.readUtf(MAX_TEXT));
    }

    @Override
    public Type<PartyPayload> type() {
        return TYPE;
    }
}
