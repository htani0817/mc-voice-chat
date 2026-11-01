package jp.mc.voicechat.net;

import jp.mc.voicechat.McVoiceChat;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * サーバー → 全員。サーバー全体のボイスチャットの様子。
 * 誰かが入る・抜ける、通話が始まる・終わる、パーティーが変わるたびに全員へ送り直す。
 *
 * @param voiceUsers ボイスチャットの Mod を入れて入っている人（通話をかけられる人）
 * @param inCall     通話中の人（名前の上に「通話中」を出す）
 * @param parties    パーティーの一覧（パスワードそのものは含めない）
 */
public record VoiceStatePayload(List<UUID> voiceUsers, List<UUID> inCall, List<Party> parties) implements CustomPacketPayload {
    public record Party(int id, String name, boolean locked, UUID owner, List<UUID> members) {
    }

    /** 受け取る一覧の上限（壊れた通信で大きな配列を作らないため） */
    private static final int MAX_LIST = 4096;
    private static final int MAX_NAME = 64;

    public static final VoiceStatePayload EMPTY = new VoiceStatePayload(List.of(), List.of(), List.of());

    public static final Type<VoiceStatePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(McVoiceChat.MODID, "state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, VoiceStatePayload> STREAM_CODEC =
            CustomPacketPayload.codec(VoiceStatePayload::write, VoiceStatePayload::read);

    private void write(RegistryFriendlyByteBuf buf) {
        writeIds(buf, voiceUsers);
        writeIds(buf, inCall);
        buf.writeVarInt(parties.size());
        for (Party p : parties) {
            buf.writeVarInt(p.id());
            buf.writeUtf(p.name(), MAX_NAME);
            buf.writeBoolean(p.locked());
            buf.writeUUID(p.owner());
            writeIds(buf, p.members());
        }
    }

    private static VoiceStatePayload read(RegistryFriendlyByteBuf buf) {
        List<UUID> users = readIds(buf);
        List<UUID> calling = readIds(buf);
        int n = readSize(buf);
        List<Party> parties = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            parties.add(new Party(buf.readVarInt(), buf.readUtf(MAX_NAME), buf.readBoolean(), buf.readUUID(), readIds(buf)));
        }
        return new VoiceStatePayload(users, calling, parties);
    }

    private static void writeIds(RegistryFriendlyByteBuf buf, List<UUID> ids) {
        buf.writeVarInt(ids.size());
        for (UUID id : ids) {
            buf.writeUUID(id);
        }
    }

    private static List<UUID> readIds(RegistryFriendlyByteBuf buf) {
        int n = readSize(buf);
        List<UUID> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            out.add(buf.readUUID());
        }
        return out;
    }

    private static int readSize(RegistryFriendlyByteBuf buf) {
        int n = buf.readVarInt();
        if (n < 0 || n > MAX_LIST) {
            throw new IllegalArgumentException("一覧が大きすぎます: " + n);
        }
        return n;
    }

    @Override
    public Type<VoiceStatePayload> type() {
        return TYPE;
    }
}
